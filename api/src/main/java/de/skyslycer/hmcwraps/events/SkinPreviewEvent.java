package de.skyslycer.hmcwraps.events;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired on the server thread before a skin preview starts. The preview always works on a copy of the
 * supplied item, so cancelling this event simply prevents the preview from being shown.
 */
public class SkinPreviewEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemSkin skin;
    private final Wrap wrap;
    private final ItemStack item;
    private final String source;
    private boolean cancelled;

    public SkinPreviewEvent(@NotNull Player player, @NotNull ItemSkin skin, @NotNull Wrap wrap, @NotNull ItemStack item,
                            @Nullable String source) {
        super(player);
        this.skin = skin;
        this.wrap = wrap;
        this.item = item;
        this.source = source == null ? "unknown" : source;
    }

    public @NotNull ItemSkin getSkin() {
        return skin;
    }

    /** The legacy wrap payload used to render the preview. */
    public @NotNull Wrap getWrap() {
        return wrap;
    }

    /** The displayed copy of the item; modifying it does not touch the player's inventory. */
    public @NotNull ItemStack getItem() {
        return item;
    }

    /** The origin of the preview, for example {@code menu} or {@code command}. */
    public @NotNull String getSource() {
        return source;
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
