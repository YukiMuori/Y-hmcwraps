package de.skyslycer.hmcwraps.collection;

import org.jetbrains.annotations.NotNull;

/**
 * The state of a milestone for one player.
 *
 * @param milestone the milestone definition
 * @param reached   whether the player owns enough skins
 * @param claimed   whether the rewards were already claimed
 */
public record MilestoneState(@NotNull CollectionMilestone milestone, boolean reached, boolean claimed) {

    /** Whether the milestone can be claimed right now. */
    public boolean claimable() {
        return reached && !claimed;
    }
}
