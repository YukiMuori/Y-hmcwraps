package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Persisted shop rotations. Storing the rotation is what makes it survive restarts, reloads and crashes. */
public interface ShopStateRepository {

    /** The stored rotation of a shop, if one exists. */
    @NotNull CompletionStage<Optional<ShopRotationState>> find(@NotNull String shopId);

    /** Inserts or replaces the rotation of a shop. */
    @NotNull CompletionStage<Boolean> save(@NotNull ShopRotationState state);

    /** Removes the stored rotation of a shop (used when the feature is disabled). */
    @NotNull CompletionStage<Boolean> clear(@NotNull String shopId);
}
