package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Small per-player shop preferences and profile counters. */
public interface PlayerRepository {

    /** The coupon a player selected in the shop. */
    @NotNull CompletionStage<Optional<String>> selectedCoupon(@NotNull UUID playerId);

    /** Stores or clears the coupon a player selected. */
    @NotNull CompletionStage<Boolean> setSelectedCoupon(@NotNull UUID playerId, String code);

    /** Adds collection experience to a player's profile. */
    @NotNull CompletionStage<Double> addCollectionXp(@NotNull UUID playerId, double amount);

    /** The collection experience of a player. */
    @NotNull CompletionStage<Double> collectionXp(@NotNull UUID playerId);
}
