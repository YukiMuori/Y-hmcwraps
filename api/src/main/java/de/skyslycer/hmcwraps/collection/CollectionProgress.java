package de.skyslycer.hmcwraps.collection;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * The progress of one player inside a collection.
 *
 * @param collectionId the collection id
 * @param owned        how many collection skins the player owns
 * @param total        how many skins the collection contains
 * @param percentage   the completion percentage (0-100, one decimal)
 * @param milestones   the milestone states, sorted by requirement
 */
public record CollectionProgress(@NotNull String collectionId, int owned, int total, double percentage,
                                 @NotNull List<MilestoneState> milestones) {

    public CollectionProgress {
        collectionId = collectionId.toLowerCase(Locale.ROOT).trim();
        milestones = List.copyOf(milestones);
    }

    /** Whether every collection skin is owned. */
    public boolean complete() {
        return total > 0 && owned >= total;
    }

    /** How many milestones can be claimed right now. */
    public long claimableMilestones() {
        return milestones.stream().filter(MilestoneState::claimable).count();
    }

    /** Rounds the percentage for display. */
    public int roundedPercentage() {
        return (int) Math.floor(percentage);
    }
}
