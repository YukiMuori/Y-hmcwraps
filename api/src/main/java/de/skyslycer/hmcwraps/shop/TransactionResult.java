package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome of a transactional purchase.
 *
 * @param status        the high level outcome
 * @param transactionId the stable transaction id, usable for support requests and idempotent retries
 * @param charged       the amount that was actually charged
 * @param refunded      the amount that was refunded because the grants could not be stored
 * @param detail        an optional machine readable detail such as the failed provider id
 */
public record TransactionResult(@NotNull TransactionStatus status, @NotNull String transactionId, double charged,
                                double refunded, @Nullable String detail) {

    public boolean successful() {
        return status == TransactionStatus.SUCCESS || status == TransactionStatus.ALREADY_OWNED;
    }

    /** Whether an administrator has to look at this transaction. */
    public boolean needsAttention() {
        return status == TransactionStatus.STORAGE_FAILED && charged > refunded;
    }

    public static @NotNull TransactionResult of(@NotNull TransactionStatus status, @NotNull String transactionId,
                                                @Nullable String detail) {
        return new TransactionResult(status, transactionId, 0, 0, detail);
    }
}
