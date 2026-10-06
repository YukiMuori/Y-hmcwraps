package de.skyslycer.hmcwraps.events;

import de.skyslycer.hmcwraps.shop.PurchaseQuote;
import de.skyslycer.hmcwraps.shop.ShopChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the server thread before a skin is charged. Cancelling the event aborts the purchase with
 * the {@code CANCELLED} transaction status and nothing is withdrawn.
 */
public class SkinPurchaseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PurchaseQuote quote;
    private final String couponCode;
    private final ShopChannel channel;
    private boolean cancelled;

    public SkinPurchaseEvent(@NotNull Player player, @NotNull PurchaseQuote quote, @NotNull String couponCode,
                             @NotNull ShopChannel channel) {
        super(player);
        this.quote = quote;
        this.couponCode = couponCode;
        this.channel = channel;
    }

    /** The server-side calculated quote the player is about to pay. */
    public @NotNull PurchaseQuote getQuote() {
        return quote;
    }

    /** The coupon applied to the quote, or an empty string. */
    public @NotNull String getCouponCode() {
        return couponCode;
    }

    /** The shop section the purchase originates from. */
    public @NotNull ShopChannel getChannel() {
        return channel;
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
