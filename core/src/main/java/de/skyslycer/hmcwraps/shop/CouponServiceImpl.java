package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.economy.PurchaseTransactionService;
import de.skyslycer.hmcwraps.repository.CouponRepository;
import de.skyslycer.hmcwraps.repository.PlayerRepository;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coupon evaluation and bookkeeping.
 *
 * <p>Evaluation is a read-only preview for menus and messages; the binding decision happens inside
 * {@link #reservation()} which reserves a redemption atomically (usage limit check and insert in one
 * database transaction). A reservation is released again whenever the purchase fails, so a player never
 * loses a coupon because of an unrelated error.</p>
 */
public final class CouponServiceImpl implements CouponService, PurchaseTransactionService.CouponReservation {

    private final HMCWrapsPlugin plugin;
    private final ShopRegistry registry;
    private final CouponRepository repository;
    private final PlayerRepository players;
    private final Clock clock;
    private final Map<UUID, String> selectedCoupons = new ConcurrentHashMap<>();

    public CouponServiceImpl(@NotNull HMCWrapsPlugin plugin, @NotNull ShopRegistry registry,
                             @NotNull CouponRepository repository, @NotNull PlayerRepository players,
                             @NotNull Clock clock) {
        this.plugin = plugin;
        this.registry = registry;
        this.repository = repository;
        this.players = players;
        this.clock = clock;
    }

    /** The reservation hook handed to the transaction service. */
    public @NotNull PurchaseTransactionService.CouponReservation reservation() {
        return this;
    }

    @Override
    public boolean isEnabled() {
        return registry.couponsEnabled();
    }

    @Override
    public @NotNull Optional<Coupon> getCoupon(@NotNull String code) {
        return registry.coupon(code);
    }

    @Override
    public @NotNull Collection<Coupon> getCoupons() {
        return registry.coupons().values();
    }

    @Override
    public @NotNull CompletionStage<CouponResult> evaluate(@NotNull UUID playerId, @NotNull String code,
                                                          @NotNull String channel, @NotNull String sectionId, double amount,
                                                          @NotNull Collection<String> skinIds,
                                                          @NotNull Collection<String> bundleIds,
                                                          @NotNull Collection<String> categoryIds) {
        if (!isEnabled()) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.DISABLED, null, "coupons disabled"));
        }
        Coupon coupon = registry.coupon(code).orElse(null);
        if (coupon == null) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.UNKNOWN, null, code));
        }
        if (!coupon.active()) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.DISABLED, coupon, "inactive"));
        }
        if (coupon.expiredAt(Instant.now(clock))) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.EXPIRED, coupon, "expired"));
        }
        if (amount < coupon.minSpend()) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.MIN_SPEND, coupon,
                    PricingService.format(coupon.minSpend())));
        }
        String normalizedChannel = channel.toLowerCase(Locale.ROOT).trim();
        String normalizedSection = sectionId == null ? "" : sectionId.toLowerCase(Locale.ROOT).trim();
        if (!applies(coupon, skinIds, bundleIds, categoryIds, normalizedChannel, normalizedSection)) {
            return AsyncUtil.completed(CouponResult.rejected(CouponResult.Status.NOT_APPLICABLE, coupon, normalizedChannel));
        }
        return repository.uses(coupon.code())
                .thenCombine(repository.usesByPlayer(coupon.code(), playerId), (uses, playerUses) -> {
                    if (coupon.maxUses() >= 0 && uses >= coupon.maxUses()) {
                        return CouponResult.rejected(CouponResult.Status.EXHAUSTED, coupon, String.valueOf(uses));
                    }
                    if (coupon.maxUsesPerPlayer() >= 0 && playerUses >= coupon.maxUsesPerPlayer()) {
                        return CouponResult.rejected(CouponResult.Status.ALREADY_USED, coupon, String.valueOf(playerUses));
                    }
                    double discount = PricingService.round(amount - coupon.apply(amount));
                    return CouponResult.applied(coupon, discount);
                })
                .exceptionally(error -> {
                    report("Could not read the usage counters of coupon " + coupon.code() + ": " + AsyncUtil.describe(error));
                    return CouponResult.rejected(CouponResult.Status.STORAGE_ERROR, coupon, "usage counters unavailable");
                });
    }

    private boolean applies(Coupon coupon, Collection<String> skinIds, Collection<String> bundleIds,
                            Collection<String> categoryIds, String channel, String sectionId) {
        if (!coupon.skins().isEmpty() && skinIds.stream().noneMatch(coupon.skins()::contains)) {
            return false;
        }
        if (!coupon.bundles().isEmpty() && bundleIds.stream().noneMatch(coupon.bundles()::contains)) {
            return false;
        }
        if (!coupon.categories().isEmpty() && categoryIds.stream().noneMatch(coupon.categories()::contains)) {
            return false;
        }
        if (coupon.channels().isEmpty()) {
            return true;
        }
        return coupon.channels().contains("all")
                || coupon.channels().contains(channel)
                || (!sectionId.isEmpty() && coupon.channels().contains(sectionId));
    }

    @Override
    public @NotNull CompletionStage<Reservation> reserve(@NotNull String code, @NotNull UUID playerId,
                                                        @NotNull String transactionId, double amount, double discount) {
        Coupon coupon = registry.coupon(code).orElse(null);
        if (coupon == null) {
            return AsyncUtil.completed(Reservation.UNKNOWN);
        }
        return AsyncUtil.safe(() -> repository.reserve(coupon.code(), playerId, transactionId, amount, discount,
                        coupon.maxUses(), coupon.maxUsesPerPlayer()))
                .thenApply(reservation -> switch (reservation) {
                    case RESERVED -> Reservation.RESERVED;
                    case EXHAUSTED -> Reservation.EXHAUSTED;
                    case ALREADY_USED -> Reservation.ALREADY_USED;
                })
                .exceptionally(error -> {
                    report("Could not reserve a redemption of coupon " + coupon.code() + ": " + AsyncUtil.describe(error));
                    return Reservation.ERROR;
                });
    }

    @Override
    public @NotNull CompletionStage<Boolean> release(@NotNull String transactionId) {
        return AsyncUtil.safe(() -> repository.release(transactionId)).exceptionally(error -> false);
    }

    @Override
    public @NotNull CompletionStage<Boolean> recordRedemption(@NotNull String code, @NotNull UUID playerId,
                                                              @NotNull String transactionId, double amount, double discount) {
        return AsyncUtil.safe(() -> repository.reserve(code, playerId, transactionId, amount, discount, -1, -1))
                .thenApply(reservation -> reservation == CouponRepository.Reservation.RESERVED)
                .exceptionally(error -> false);
    }

    @Override
    public @NotNull CompletionStage<Integer> getUses(@NotNull String code) {
        return AsyncUtil.safe(() -> repository.uses(code)).exceptionally(error -> 0);
    }

    @Override
    public @NotNull CompletionStage<Integer> getUses(@NotNull String code, @NotNull UUID playerId) {
        return AsyncUtil.safe(() -> repository.usesByPlayer(code, playerId)).exceptionally(error -> 0);
    }

    @Override
    public @NotNull CompletionStage<Boolean> reload() {
        return AsyncUtil.completed(registry.load());
    }

    @Override
    public @NotNull CompletionStage<Boolean> setSelectedCoupon(@NotNull UUID playerId, @Nullable String code) {
        String normalized = code == null || code.isBlank() ? null : code.toUpperCase(Locale.ROOT).trim();
        if (normalized == null) {
            selectedCoupons.remove(playerId);
        } else {
            selectedCoupons.put(playerId, normalized);
        }
        return AsyncUtil.safe(() -> players.setSelectedCoupon(playerId, normalized)).exceptionally(error -> {
            report("Could not persist the selected coupon of " + playerId + ": " + AsyncUtil.describe(error));
            return false;
        });
    }

    @Override
    public @NotNull CompletionStage<Optional<String>> getSelectedCoupon(@NotNull UUID playerId) {
        String cached = selectedCoupons.get(playerId);
        if (cached != null) {
            return AsyncUtil.completed(Optional.of(cached));
        }
        return AsyncUtil.safe(() -> players.selectedCoupon(playerId))
                .thenApply(optional -> {
                    optional.ifPresent(code -> selectedCoupons.put(playerId, code));
                    return optional;
                })
                .exceptionally(error -> Optional.empty());
    }

    @Override
    public @Nullable String cachedSelectedCoupon(@NotNull UUID playerId) {
        return selectedCoupons.get(playerId);
    }

    /** Loads the persisted selection when a player joins so menus show the right coupon immediately. */
    public void preload(@NotNull UUID playerId) {
        getSelectedCoupon(playerId);
    }

    /** Forgets a player's cached coupon selection when they disconnect. */
    public void forget(@NotNull UUID playerId) {
        selectedCoupons.remove(playerId);
    }

    @Override
    public void sendFeedback(@NotNull Player player, @NotNull CouponResult result) {
        switch (result.status()) {
            case APPLIED -> send(player, "shop.coupon.applied", TagResolver.resolver(
                    Placeholder.unparsed("code", result.coupon() == null ? "" : result.coupon().code()),
                    Placeholder.unparsed("discount", PricingService.format(result.discount()))));
            case UNKNOWN -> send(player, "shop.coupon.unknown");
            case DISABLED -> send(player, "shop.coupon.disabled");
            case EXPIRED -> send(player, "shop.coupon.expired");
            case EXHAUSTED -> send(player, "shop.coupon.exhausted");
            case ALREADY_USED -> send(player, "shop.coupon.already-used");
            case MIN_SPEND -> send(player, "shop.coupon.min-spend", Placeholder.unparsed("amount",
                    result.detail() == null ? "0" : result.detail()));
            case NOT_APPLICABLE -> send(player, "shop.coupon.not-applicable");
            case STORAGE_ERROR -> send(player, "shop.coupon.storage-error");
        }
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        String value = plugin.getLanguageManager().get(player, key);
        StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, value, resolvers));
    }

    private void report(String message) {
        plugin.getLogger().warning(message);
    }
}
