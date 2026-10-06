package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent per-player skin favorites. */
public interface FavoriteRepository {

    /** The favorited skin ids of a player, newest first. */
    @NotNull CompletionStage<Set<String>> favorites(@NotNull UUID playerId);

    /** Whether the skin is currently favorited. */
    @NotNull CompletionStage<Boolean> isFavorite(@NotNull UUID playerId, @NotNull String skinId);

    /** Adds or removes a favorite. */
    @NotNull CompletionStage<Boolean> setFavorite(@NotNull UUID playerId, @NotNull String skinId, boolean favorite);
}
