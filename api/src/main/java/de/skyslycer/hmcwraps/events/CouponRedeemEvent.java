package de.skyslycer.hmcwraps.events;

import de.skyslycer.hmcwraps.shop.Coupon;
import de.skyslycer.hmcwraps.shop.ShopChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the server thread when a coupon is about to be applied to a purchase. Cancelling the event
 * aborts the redemption and the shop charges the undiscounted price.
 */
public class CouponRedeemEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Coupon coupon;
    private final double originalAmount;
    private final double discount;
    private final ShopChannel channel;
    private boolean cancelled;

    public CouponRedeemEvent(@NotNull Player player, @NotNull Coupon coupon, double originalAmount, double discount,
                             @NotNull ShopChannel channel) {
        super(player);
        this.coupon = coupon;
        this.originalAmount = originalAmount;
        this.discount = discount;
        this.channel = channel;
    }

    public @NotNull Coupon getCoupon() {
        return coupon;
    }

    public double getOriginalAmount() {
        return originalAmount;
    }

    public double getDiscount() {
        return discount;
    }

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
