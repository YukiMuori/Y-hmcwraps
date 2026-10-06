package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A durable record of a gifted object. Gifts are always written before they are granted, so an
 * interrupted gift can be reconciled by an administrator instead of silently disappearing.
 */
public record GiftRecord(@NotNull String giftId, @NotNull UUID senderId, @Nullable String senderName,
                         @NotNull UUID recipientId, @Nullable String recipientName, @NotNull PurchaseKind kind,
                         @NotNull String targetId, double amount, @NotNull String currency, @NotNull String provider,
                         @Nullable String message, long createdAt, @Nullable Long deliveredAt, boolean notified) {
}
