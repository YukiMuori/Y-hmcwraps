package de.skyslycer.hmcwraps.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/** Fired after a shop section was rotated (for example the daily shop). */
public class ShopRefreshEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String shopId;
    private final String cycleKey;
    private final List<String> entryIds;
    private final Instant nextRefresh;
    private final boolean forced;

    public ShopRefreshEvent(@NotNull String shopId, @NotNull String cycleKey, @NotNull List<String> entryIds,
                            @NotNull Instant nextRefresh, boolean forced) {
        this.shopId = shopId;
        this.cycleKey = cycleKey;
        this.entryIds = List.copyOf(entryIds);
        this.nextRefresh = nextRefresh;
        this.forced = forced;
    }

    public @NotNull String getShopId() {
        return shopId;
    }

    /** The deterministic key of the new rotation (for example the day id). */
    public @NotNull String getCycleKey() {
        return cycleKey;
    }

    public @NotNull List<String> getEntryIds() {
        return entryIds;
    }

    public @NotNull Instant getNextRefresh() {
        return nextRefresh;
    }

    /** Whether an administrator forced the refresh. */
    public boolean isForced() {
        return forced;
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
