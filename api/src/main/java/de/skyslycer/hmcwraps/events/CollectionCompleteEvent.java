package de.skyslycer.hmcwraps.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired on the server thread when a player completes a skin collection. */
public class CollectionCompleteEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String collectionId;
    private final int owned;
    private final int total;

    public CollectionCompleteEvent(@NotNull Player player, @NotNull String collectionId, int owned, int total) {
        super(player);
        this.collectionId = collectionId;
        this.owned = owned;
        this.total = total;
    }

    public @NotNull String getCollectionId() {
        return collectionId;
    }

    public int getOwned() {
        return owned;
    }

    public int getTotal() {
        return total;
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
