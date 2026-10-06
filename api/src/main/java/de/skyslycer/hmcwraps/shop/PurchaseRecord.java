package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A durable audit record of an economic transaction. Records are written before the payment and
 * updated afterwards so a crash between withdrawal and grant is always recoverable.
 */
public record PurchaseRecord(@NotNull String transactionId, @NotNull UUID playerId, @NotNull PurchaseKind kind,
                             @NotNull String targetId, double amount, @NotNull String currency, @NotNull String provider,
                             @NotNull TransactionStatus status, long createdAt, @Nullable Long completedAt,
                             @Nullable String detail, @Nullable String couponCode,
                             @Nullable UUID recipientId) {

    /** Whether this record finished successfully. */
    public boolean completed() {
        return status == TransactionStatus.SUCCESS || status == TransactionStatus.ALREADY_OWNED;
    }
}
