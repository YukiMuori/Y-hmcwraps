package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.repository.PurchaseRepository;
import de.skyslycer.hmcwraps.shop.PurchaseKind;
import de.skyslycer.hmcwraps.shop.PurchaseRecord;
import de.skyslycer.hmcwraps.shop.TransactionResult;
import de.skyslycer.hmcwraps.shop.TransactionStatus;
import de.skyslycer.hmcwraps.skin.EconomyProvider;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.skin.TransactionalStorage;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The single economic transaction engine of the plugin.
 *
 * <p>Every paid operation (shop purchase, bundle purchase, gift) runs through {@link #execute}, which
 * guarantees the order of operations that makes the flow crash safe:</p>
 *
 * <ol>
 *     <li>a per player/target in-flight guard rejects double clicks and race conditions;</li>
 *     <li>ownership is <em>re-read from storage</em> and the payable amount is recalculated server-side
 *         (values sent by a GUI are never trusted);</li>
 *     <li>an optional coupon redemption is reserved atomically, so a usage limit cannot be exceeded;</li>
 *     <li>a journal row is written <em>before</em> money leaves the account, making the operation
 *         recoverable and idempotent after a crash;</li>
 *     <li>the balance is checked and the amount is withdrawn;</li>
 *     <li>the ownerships are granted as one atomic batch;</li>
 *     <li>on any failure the payment is refunded and already granted ownerships are revoked; when the
 *         refund itself fails the transaction is flagged for an administrator instead of being lost.</li>
 * </ol>
 */
public final class PurchaseTransactionService {

    private final TransactionalStorage storage;
    private final PurchaseRepository journal;
    private final Consumer<String> logger;
    private final Set<TransactionKey> inFlight = ConcurrentHashMap.newKeySet();

    public PurchaseTransactionService(@NotNull TransactionalStorage storage, @NotNull PurchaseRepository journal,
                                      @NotNull Consumer<String> logger) {
        this.storage = storage;
        this.journal = journal;
        this.logger = logger;
    }

    /**
     * Executes a transaction.
     *
     * @param request    the server-side calculated request
     * @param dispatcher economy calls are marshalled through this so providers that are not thread safe
     *                   are always called on the correct thread
     * @return the outcome; the returned stage never completes exceptionally
     */
    public @NotNull CompletionStage<TransactionResult> execute(@NotNull TransactionRequest request,
                                                               @NotNull EconomyDispatcher dispatcher) {
        if (request.grantSkinIds().isEmpty()) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.INVALID, request.transactionId(),
                    "no skins to grant"));
        }
        TransactionKey key = new TransactionKey(request.playerId(), request.kind(), request.targetId());
        if (!inFlight.add(key)) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.BUSY, request.transactionId(),
                    request.targetId()));
        }
        return plan(request)
                .thenCompose(plan -> {
                    TransactionRequest resolved = request.withPlan(plan);
                    return plan.alreadyOwned()
                            ? AsyncUtil.completed(report(resolved, plan, TransactionStatus.ALREADY_OWNED, null, 0, 0))
                            : reserve(resolved, plan, dispatcher);
                })
                .exceptionally(error -> {
                    logger.accept("Transaction " + request.transactionId() + " failed: " + AsyncUtil.describe(error));
                    return TransactionResult.of(TransactionStatus.ERROR, request.transactionId(), AsyncUtil.describe(error));
                })
                .whenComplete((ignored, error) -> inFlight.remove(key));
    }

    /**
     * Computes what a transaction would do without charging anything. Used by the shop to quote prices
     * and by menus to display them; {@link #execute} recalculates the same plan again.
     */
    public @NotNull CompletionStage<TransactionPlan> plan(@NotNull TransactionRequest request) {
        return AsyncUtil.safe(() -> storage.getOwnedSkinIds(request.owner()))
                .thenApply(owned -> plan(request, owned == null ? Set.of() : owned));
    }

    private TransactionPlan plan(TransactionRequest request, Set<String> owned) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String skinId : request.grantSkinIds()) {
            if (skinId != null && !skinId.isBlank()) {
                normalized.add(skinId.toLowerCase(Locale.ROOT).trim());
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        Set<String> alreadyOwned = new LinkedHashSet<>();
        for (String skinId : normalized) {
            if (owned.contains(skinId)) {
                alreadyOwned.add(skinId);
            } else {
                missing.add(skinId);
            }
        }
        double amount = request.amountFor(missing);
        if (!Double.isFinite(amount) || amount < 0) {
            amount = 0;
        }
        return new TransactionPlan(missing, alreadyOwned, amount);
    }

    private CompletionStage<TransactionResult> reserve(TransactionRequest request, TransactionPlan plan,
                                                       EconomyDispatcher dispatcher) {
        return reserveCoupon(request).thenCompose(status -> {
            if (status != CouponReservation.Status.RESERVED) {
                return AsyncUtil.completed(report(request, plan, TransactionStatus.COUPON_REJECTED, status.name(), 0, 0));
            }
            return writeJournal(request, plan).thenCompose(written -> {
                if (!written) {
                    return releaseCoupon(request)
                            .thenApply(released -> report(request, plan, TransactionStatus.STORAGE_FAILED, "journal", 0, 0));
                }
                return pay(request, plan, dispatcher);
            });
        });
    }

    private CompletionStage<CouponReservation.Status> reserveCoupon(TransactionRequest request) {
        if (!request.hasCoupon() || request.couponReservation() == null) {
            return AsyncUtil.completed(CouponReservation.Status.RESERVED);
        }
        return AsyncUtil.safe(() -> request.couponReservation().reserve(request.couponCode(), request.playerId(),
                        request.transactionId(), request.planAmount(), request.discount()))
                .thenApply(status -> status == null ? CouponReservation.Status.ERROR : status)
                .exceptionally(error -> {
                    logger.accept("Coupon reservation failed for " + request.transactionId() + ": " + AsyncUtil.describe(error));
                    return CouponReservation.Status.ERROR;
                });
    }

    private CompletionStage<Boolean> releaseCoupon(TransactionRequest request) {
        if (!request.hasCoupon() || request.couponReservation() == null) {
            return AsyncUtil.completed(Boolean.TRUE);
        }
        return AsyncUtil.safe(() -> request.couponReservation().release(request.transactionId()))
                .exceptionally(error -> false);
    }

    private CompletionStage<Boolean> writeJournal(TransactionRequest request, TransactionPlan plan) {
        PurchaseRecord record = new PurchaseRecord(request.transactionId(), request.playerId(), request.kind(),
                request.targetId(), plan.amount(), request.price().currency(), request.price().provider(),
                TransactionStatus.PENDING, System.currentTimeMillis(), null, null,
                request.hasCoupon() ? request.couponCode() : null, request.recipientId());
        return AsyncUtil.safe(() -> journal.begin(record)).exceptionally(error -> {
            logger.accept("Could not write the transaction journal for " + request.transactionId() + ": "
                    + AsyncUtil.describe(error));
            return false;
        });
    }

    private CompletionStage<TransactionResult> pay(TransactionRequest request, TransactionPlan plan,
                                                   EconomyDispatcher dispatcher) {
        EconomyProvider provider = request.provider();
        return AsyncUtil.safe(() -> dispatcher.balance(request.playerId(), provider, request.price().currency()))
                .thenCompose(balance -> {
                    if (balance == null || !Double.isFinite(balance) || balance < plan.amount()) {
                        return fail(request, plan, TransactionStatus.INSUFFICIENT_FUNDS, request.price().currency());
                    }
                    return AsyncUtil.safe(() -> dispatcher.withdraw(request.playerId(), provider,
                            request.price().currency(), plan.amount())).thenCompose(withdrawn -> {
                        if (!Boolean.TRUE.equals(withdrawn)) {
                            return fail(request, plan, TransactionStatus.PAYMENT_FAILED, provider.id());
                        }
                        return grant(request, plan);
                    });
                });
    }

    private CompletionStage<TransactionResult> grant(TransactionRequest request, TransactionPlan plan) {
        return AsyncUtil.safe(() -> storage.grantAll(request.owner(), plan.missingSkinIds(), request.source()))
                .handle((granted, error) -> {
                    if (error != null) {
                        logger.accept("Granting skins failed for transaction " + request.transactionId() + ": "
                                + AsyncUtil.describe(error));
                        return rollback(request, plan, granted == null ? Set.of() : granted);
                    }
                    Set<String> stored = granted == null ? Set.of() : granted;
                    Set<String> stillMissing = new LinkedHashSet<>(plan.missingSkinIds());
                    stillMissing.removeAll(stored);
                    if (stillMissing.isEmpty()) {
                        return complete(request, plan);
                    }
                    logger.accept("Only " + stored.size() + " of " + plan.missingSkinIds().size()
                            + " ownerships were stored for transaction " + request.transactionId() + "; rolling back.");
                    return rollback(request, plan, stored);
                })
                .thenCompose(stage -> stage);
    }

    private CompletionStage<TransactionResult> complete(TransactionRequest request, TransactionPlan plan) {
        return AsyncUtil.safe(() -> journal.complete(request.transactionId(), TransactionStatus.SUCCESS, null))
                .exceptionally(error -> {
                    logger.accept("Transaction " + request.transactionId()
                            + " succeeded but its journal row could not be completed: " + AsyncUtil.describe(error)
                            + " (ownership was granted; the row is reconciled on the next start)");
                    return false;
                })
                .thenApply(ignored -> report(request, plan, TransactionStatus.SUCCESS, null, plan.amount(), 0));
    }

    private CompletionStage<TransactionResult> fail(TransactionRequest request, TransactionPlan plan,
                                                    TransactionStatus status, String detail) {
        return AsyncUtil.safe(() -> journal.complete(request.transactionId(), status, detail))
                .exceptionally(error -> false)
                .thenCompose(ignored -> releaseCoupon(request)
                        .thenApply(released -> report(request, plan, status, detail, 0, 0)));
    }

    private CompletionStage<TransactionResult> rollback(TransactionRequest request, TransactionPlan plan, Set<String> granted) {
        return revoke(request, granted).thenCompose(revoked -> {
            if (!revoked) {
                logger.accept("Ownerships of transaction " + request.transactionId()
                        + " could not be fully revoked; an administrator has to review this transaction.");
            }
            return refund(request, plan);
        });
    }

    private CompletionStage<Boolean> revoke(TransactionRequest request, Set<String> granted) {
        if (granted.isEmpty()) {
            return AsyncUtil.completed(Boolean.TRUE);
        }
        return AsyncUtil.safe(() -> storage.revokeAll(request.owner(), granted)).exceptionally(error -> {
            logger.accept("Could not revoke ownerships of transaction " + request.transactionId() + ": "
                    + AsyncUtil.describe(error));
            return false;
        });
    }

    private CompletionStage<TransactionResult> refund(TransactionRequest request, TransactionPlan plan) {
        return AsyncUtil.safe(() -> request.dispatcher().deposit(request.playerId(), request.provider(),
                        request.price().currency(), plan.amount()))
                .handle((refunded, error) -> Boolean.TRUE.equals(refunded) ? plan.amount() : 0D)
                .thenCompose(refunded -> {
                    if (refunded <= 0) {
                        logger.accept("REFUND FAILED for transaction " + request.transactionId() + " ("
                                + plan.amount() + " " + request.price().currency() + "): " + request.description()
                                + " - reconcile this transaction manually.");
                    }
                    String detail = refunded > 0 ? "refunded" : "refund-failed";
                    return AsyncUtil.safe(() -> journal.complete(request.transactionId(), TransactionStatus.STORAGE_FAILED, detail))
                            .exceptionally(error -> false)
                            .thenCompose(ignored -> releaseCoupon(request)
                                    .thenApply(released -> report(request, plan, TransactionStatus.STORAGE_FAILED, detail,
                                            plan.amount(), refunded)));
                });
    }

    private TransactionResult report(TransactionRequest request, TransactionPlan plan, TransactionStatus status,
                                     @Nullable String detail, double charged, double refunded) {
        if (status != TransactionStatus.SUCCESS && status != TransactionStatus.ALREADY_OWNED) {
            logger.accept("Transaction " + request.transactionId() + " (" + request.description() + ") -> " + status
                    + (detail == null ? "" : " [" + detail + "]"));
        }
        return new TransactionResult(status, request.transactionId(), charged, refunded, detail);
    }

    /**
     * Reconciles journal rows interrupted by a crash. Called once during startup: a pending transaction is
     * marked successful when the player owns the granted skins and flagged for review otherwise.
     */
    public @NotNull CompletionStage<Integer> reconcilePending(long olderThan,
                                                              @NotNull Function<PurchaseRecord, CompletionStage<Boolean>> ownershipCheck) {
        return AsyncUtil.safe(() -> journal.pendingOlderThan(olderThan))
                .thenCompose(records -> {
                    if (records.isEmpty()) {
                        return AsyncUtil.completed(0);
                    }
                    List<CompletionStage<Boolean>> reconciliations = records.stream()
                            .map(record -> AsyncUtil.safe(() -> ownershipCheck.apply(record))
                                    .thenCompose(owned -> AsyncUtil.safe(() -> journal.complete(record.transactionId(),
                                                    Boolean.TRUE.equals(owned) ? TransactionStatus.SUCCESS : TransactionStatus.STORAGE_FAILED,
                                                    Boolean.TRUE.equals(owned) ? "reconciled-after-restart" : "interrupted"))
                                            .exceptionally(error -> false))
                                    .exceptionally(error -> false))
                            .toList();
                    return AsyncUtil.allOf(reconciliations).thenApply(results -> {
                        int recovered = 0;
                        for (int index = 0; index < results.size(); index++) {
                            if (Boolean.TRUE.equals(results.get(index))) {
                                recovered++;
                            } else {
                                PurchaseRecord record = records.get(index);
                                logger.accept("Interrupted transaction " + record.transactionId() + " ("
                                        + record.amount() + " " + record.currency() + ", player " + record.playerId()
                                        + ", target " + record.targetId() + ") could not be verified; review it.");
                            }
                        }
                        if (recovered > 0) {
                            logger.accept("Reconciled " + recovered + " interrupted transaction(s) after the restart.");
                        }
                        return recovered;
                    });
                })
                .exceptionally(error -> {
                    logger.accept("Could not reconcile interrupted transactions: " + AsyncUtil.describe(error));
                    return 0;
                });
    }

    /**
     * A server-side calculated transaction request.
     *
     * @param playerId          the paying player
     * @param ownerId           the player receiving the ownership ({@code null} means the payer, gifts set the recipient)
     * @param kind              what is being bought
     * @param targetId          the skin/bundle id
     * @param grantSkinIds      every skin the purchase may grant
     * @param amountFor         the price strategy; receives the skins that still have to be granted
     * @param discount          an absolute discount (coupons) already validated server-side
     * @param planAmount        the amount calculated for the current plan, used for coupon bookkeeping
     * @param couponCode        the coupon code, when a coupon is applied
     * @param couponReservation the reservation hook that atomically counts coupon usage
     * @param price             the base price (provider/currency)
     * @param provider          the resolved economy provider
     * @param recipientId       the recipient for gifts, otherwise {@code null}
     * @param source            the ownership audit source ({@code purchase}, {@code gift}, ...)
     * @param description       a human readable description used in logs
     * @param transactionId     the stable transaction id
     * @param dispatcher        the economy dispatcher used for refunds
     */
    public record TransactionRequest(@NotNull UUID playerId, @Nullable UUID ownerId, @NotNull PurchaseKind kind,
                                     @NotNull String targetId, @NotNull Collection<String> grantSkinIds,
                                     @NotNull Function<Set<String>, Double> amountFor, double discount, double planAmount,
                                     @Nullable String couponCode, @Nullable CouponReservation couponReservation,
                                     @NotNull SkinPrice price, @NotNull EconomyProvider provider,
                                     @Nullable UUID recipientId, @NotNull String source, @NotNull String description,
                                     @NotNull String transactionId, @NotNull EconomyDispatcher dispatcher) {

        public TransactionRequest {
            grantSkinIds = List.copyOf(grantSkinIds);
        }

        /** The player whose ownership decides what has to be granted. */
        public @NotNull UUID owner() {
            return ownerId == null ? playerId : ownerId;
        }

        boolean hasCoupon() {
            return couponCode != null && !couponCode.isBlank();
        }

        double amountFor(Set<String> missing) {
            Double value = amountFor.apply(missing);
            return value == null || !Double.isFinite(value) ? 0 : value;
        }

        /** A copy of this request carrying the plan amount that was just calculated. */
        public @NotNull TransactionRequest withPlan(@NotNull TransactionPlan plan) {
            return new TransactionRequest(playerId, ownerId, kind, targetId, grantSkinIds, amountFor, discount,
                    plan.amount(), couponCode, couponReservation, price, provider, recipientId, source, description,
                    transactionId, dispatcher);
        }
    }

    /** The plan of a transaction: what would be granted and what it would cost. */
    public record TransactionPlan(@NotNull Set<String> missingSkinIds, @NotNull Set<String> ownedSkinIds, double amount) {

        public TransactionPlan {
            missingSkinIds = Set.copyOf(missingSkinIds);
            ownedSkinIds = Set.copyOf(ownedSkinIds);
        }

        public boolean alreadyOwned() {
            return missingSkinIds.isEmpty();
        }
    }

    /** Marshals economy calls onto the correct thread. */
    public interface EconomyDispatcher {
        CompletionStage<Double> balance(UUID playerId, EconomyProvider provider, String currency);

        CompletionStage<Boolean> withdraw(UUID playerId, EconomyProvider provider, String currency, double amount);

        CompletionStage<Boolean> deposit(UUID playerId, EconomyProvider provider, String currency, double amount);
    }

    /** Atomically reserves a coupon redemption; implemented by the coupon service. */
    public interface CouponReservation {
        enum Status {
            RESERVED,
            EXHAUSTED,
            ALREADY_USED,
            UNKNOWN,
            ERROR
        }

        CompletionStage<Status> reserve(String code, UUID playerId, String transactionId, double amount, double discount);

        CompletionStage<Boolean> release(String transactionId);
    }

    private record TransactionKey(UUID playerId, PurchaseKind kind, String targetId) {
    }
}
