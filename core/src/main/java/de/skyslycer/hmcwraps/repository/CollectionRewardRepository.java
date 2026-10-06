package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Claimed collection milestones. Claims are unique per player, collection and milestone. */
public interface CollectionRewardRepository {

    /** The milestones a player already claimed inside a collection. */
    @NotNull CompletionStage<Set<String>> claimed(@NotNull UUID playerId, @NotNull String collectionId);

    /** Every claimed milestone of a player, across collections. */
    @NotNull CompletionStage<Set<String>> claimedMilestones(@NotNull UUID playerId);

    /**
     * Claims a milestone exactly once.
     *
     * @return whether this call inserted the claim (false means it was claimed before)
     */
    @NotNull CompletionStage<Boolean> claim(@NotNull UUID playerId, @NotNull String collectionId, @NotNull String milestoneId);

    /** Releases a claim; used when granting the rewards failed so the player can try again. */
    @NotNull CompletionStage<Boolean> release(@NotNull UUID playerId, @NotNull String collectionId, @NotNull String milestoneId);
}
