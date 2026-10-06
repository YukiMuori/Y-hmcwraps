package de.skyslycer.hmcwraps.repository;

import de.skyslycer.hmcwraps.shop.PurchaseKind;
import de.skyslycer.hmcwraps.shop.PurchaseRecord;
import de.skyslycer.hmcwraps.shop.TransactionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * The durable transaction journal. A record is written <em>before</em> money leaves the player's balance
 * and completed afterwards, which is what makes the purchase flow crash-safe and idempotent.
 */
public interface PurchaseRepository {

    /** Stores a pending transaction; completes exceptionally when the transaction id already exists. */
    @NotNull CompletionStage<Boolean> begin(@NotNull PurchaseRecord record);

    /** Finds a transaction by its id. */
    @NotNull CompletionStage<Optional<PurchaseRecord>> find(@NotNull String transactionId);

    /** Updates the status of an existing transaction. */
    @NotNull CompletionStage<Boolean> complete(@NotNull String transactionId, @NotNull TransactionStatus status,
                                               @Nullable String detail);

    /** The newest transactions of a player. */
    @NotNull CompletionStage<List<PurchaseRecord>> history(@NotNull UUID playerId, int limit);

    /** Whether the player already completed a transaction of that kind for that target. */
    @NotNull CompletionStage<Boolean> hasCompleted(@NotNull UUID playerId, @NotNull PurchaseKind kind, @NotNull String targetId);

    /** How many completed transactions of a player match the supplied kinds. */
    @NotNull CompletionStage<Integer> countCompleted(@NotNull UUID playerId, @NotNull PurchaseKind... kinds);

    /** The timestamp of a player's first completed transaction, if any. */
    @NotNull CompletionStage<Optional<Long>> firstCompletedAt(@NotNull UUID playerId);

    /**
     * Transactions that are still pending. Used at startup to report interrupted purchases so an
     * administrator can reconcile them instead of losing track of a charged player.
     */
    @NotNull CompletionStage<List<PurchaseRecord>> pendingOlderThan(long timestamp);
}
