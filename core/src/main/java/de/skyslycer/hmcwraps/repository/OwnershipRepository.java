package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent skin ownership. Implementations must be safe for concurrent calls. */
public interface OwnershipRepository {

    /** Whether the player owns the skin. */
    @NotNull CompletionStage<Boolean> has(@NotNull UUID playerId, @NotNull String skinId);

    /** Every skin id the player owns, newest first. */
    @NotNull CompletionStage<Set<String>> owned(@NotNull UUID playerId);

    /**
     * Grants ownership if it is not present yet.
     *
     * @param source the audit source ({@code purchase}, {@code gift}, {@code reward}, {@code trade}, ...)
     * @return whether the player owns the skin afterwards
     */
    @NotNull CompletionStage<Boolean> grant(@NotNull UUID playerId, @NotNull String skinId, @NotNull String source);

    /** Grants several skins as one atomic operation; either all or none are stored. */
    @NotNull CompletionStage<Boolean> grantAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds, @NotNull String source);

    /** Removes ownership; used to compensate a failed transaction. */
    @NotNull CompletionStage<Boolean> revoke(@NotNull UUID playerId, @NotNull String skinId);

    /** Moves one ownership row from one player to another atomically; fails when the source does not own it. */
    @NotNull CompletionStage<Boolean> transfer(@NotNull UUID fromPlayer, @NotNull UUID toPlayer, @NotNull String skinId);

    /** Whether the repository can run the multi-skin operations the shop needs. */
    default boolean supportsTransactions() {
        return true;
    }
}
