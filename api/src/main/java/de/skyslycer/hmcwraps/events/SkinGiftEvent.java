package de.skyslycer.hmcwraps.events;

import de.skyslycer.hmcwraps.shop.PurchaseKind;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired on the sender's thread before a gift is charged. Cancelling the event aborts the gift and
 * nothing is withdrawn or granted.
 */
public class SkinGiftEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID recipientId;
    private final String recipientName;
    private final PurchaseKind kind;
    private final String targetId;
    private final double amount;
    private final String currency;
    private final String message;
    private boolean cancelled;

    public SkinGiftEvent(@NotNull Player sender, @NotNull UUID recipientId, @NotNull String recipientName,
                         @NotNull PurchaseKind kind, @NotNull String targetId, double amount, @NotNull String currency,
                         @Nullable String message) {
        super(sender);
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.kind = kind;
        this.targetId = targetId;
        this.amount = amount;
        this.currency = currency;
        this.message = message;
    }

    /** The player paying for the gift. */
    public @NotNull Player getSender() {
        return getPlayer();
    }

    public @NotNull UUID getRecipientId() {
        return recipientId;
    }

    public @NotNull String getRecipientName() {
        return recipientName;
    }

    public @NotNull PurchaseKind getKind() {
        return kind;
    }

    public @NotNull String getTargetId() {
        return targetId;
    }

    public double getAmount() {
        return amount;
    }

    public @NotNull String getCurrency() {
        return currency;
    }

    public @Nullable String getMessage() {
        return message;
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
