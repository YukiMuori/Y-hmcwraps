package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Aggregated skin statistics. Values are derived from the ownership and transaction tables; they are
 * cached per player and invalidated when a transaction completes.
 */
public interface ProfileService {

    /** Computes (or returns the cached) profile of a player. */
    @NotNull CompletionStage<SkinProfile> getProfile(@NotNull UUID playerId);

    /** The cached profile, or {@code null} when it was not loaded yet. */
    @Nullable SkinProfile cachedProfile(@NotNull UUID playerId);

    /** Starts loading a profile in the background, for example on join. */
    void preload(@NotNull UUID playerId);

    /** Drops the cached profile of a player, for example after a purchase. */
    void invalidate(@NotNull UUID playerId);

    /** Adds collection experience points to a player's statistics. */
    @NotNull CompletionStage<Boolean> addCollectionXp(@NotNull UUID playerId, double amount);

    /** The collection experience points of a player. */
    @NotNull CompletionStage<Double> getCollectionXp(@NotNull UUID playerId);
}
