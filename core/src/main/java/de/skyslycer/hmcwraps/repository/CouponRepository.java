package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent coupon usage counters. */
public interface CouponRepository {

    /** The reason a reservation succeeded or failed. */
    enum Reservation {
        /** The redemption was reserved and counted. */
        RESERVED,
        /** The global usage limit is reached. */
        EXHAUSTED,
        /** The player reached their personal usage limit. */
        ALREADY_USED
    }

    /**
     * Atomically checks the usage limits and reserves one redemption. The check and the insert happen in
     * the same database transaction, therefore two concurrent purchases can never exceed a coupon's limit.
     *
     * @param maxUses          the global limit, {@code -1} for unlimited
     * @param maxUsesPerPlayer the per player limit, {@code -1} for unlimited
     */
    @NotNull CompletionStage<Reservation> reserve(@NotNull String code, @NotNull UUID playerId, @NotNull String transactionId,
                                                 double amount, double discount, int maxUses, int maxUsesPerPlayer);

    /** Releases a reservation after a failed purchase, freeing the coupon for another attempt. */
    @NotNull CompletionStage<Boolean> release(@NotNull String transactionId);

    /** How often a coupon was redeemed in total. */
    @NotNull CompletionStage<Integer> uses(@NotNull String code);

    /** How often a player redeemed a coupon. */
    @NotNull CompletionStage<Integer> usesByPlayer(@NotNull String code, @NotNull UUID playerId);

    /** How many coupons a player redeemed in total, used by the skin profile. */
    @NotNull CompletionStage<Integer> countByPlayer(@NotNull UUID playerId);
}
