package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Pluggable persistence boundary for v2 player skin ownership. */
public interface StorageProvider extends OwnershipManager, AutoCloseable {
    @NotNull String id();
    @NotNull CompletionStage<Boolean> initialize();
    /** Whether the provider has finished initialization and can safely serve requests. */
    default boolean isReady() { return true; }
    /** Optional audit-aware unlock path; providers may record purchases separately. */
    default @NotNull CompletionStage<Boolean> unlockPurchasedSkin(@NotNull java.util.UUID playerId, @NotNull String skinId) {
        return unlockSkin(playerId, skinId);
    }
    /** Whether ownership transfer is implemented as an atomic, fail-closed operation. */
    default boolean supportsSkinTransfers() { return false; }
    /** Atomically moves an owned skin between players; unsupported providers must fail closed. */
    default @NotNull CompletionStage<Boolean> transferSkin(@NotNull UUID fromPlayer, @NotNull UUID toPlayer, @NotNull String skinId) {
        return CompletableFuture.completedFuture(false);
    }
    @Override void close();
}
