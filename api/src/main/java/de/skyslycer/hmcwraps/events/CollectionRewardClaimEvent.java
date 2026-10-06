package de.skyslycer.hmcwraps.events;

import de.skyslycer.hmcwraps.collection.CollectionReward;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fired on the server thread before a milestone's rewards are granted. Cancelling the event leaves the
 * milestone unclaimed, so it can be claimed later.
 */
public class CollectionRewardClaimEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String collectionId;
    private final String milestoneId;
    private final List<CollectionReward> rewards;
    private boolean cancelled;

    public CollectionRewardClaimEvent(@NotNull Player player, @NotNull String collectionId, @NotNull String milestoneId,
                                      @NotNull List<CollectionReward> rewards) {
        super(player);
        this.collectionId = collectionId;
        this.milestoneId = milestoneId;
        this.rewards = List.copyOf(rewards);
    }

    public @NotNull String getCollectionId() {
        return collectionId;
    }

    public @NotNull String getMilestoneId() {
        return milestoneId;
    }

    public @NotNull List<CollectionReward> getRewards() {
        return rewards;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
