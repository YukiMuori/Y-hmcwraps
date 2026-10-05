package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent player-to-skin ownership contract. Implementations must be safe for concurrent calls. */
public interface OwnershipManager {
    @NotNull CompletionStage<Boolean> hasSkin(@NotNull UUID playerId, @NotNull String skinId);
    @NotNull CompletionStage<Boolean> unlockSkin(@NotNull UUID playerId, @NotNull String skinId);
    @NotNull CompletionStage<Set<String>> getOwnedSkinIds(@NotNull UUID playerId);
}
