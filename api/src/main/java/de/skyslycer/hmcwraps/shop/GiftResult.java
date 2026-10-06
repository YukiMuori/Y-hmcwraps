package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome of a gift transaction.
 *
 * @param status        the high level outcome
 * @param giftId        the stable gift id
 * @param transactionId the economic transaction id
 * @param charged       the amount charged from the sender
 * @param detail        an optional machine readable detail
 */
public record GiftResult(@NotNull TransactionStatus status, @NotNull String giftId, @NotNull String transactionId,
                         double charged, @Nullable String detail) {

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
