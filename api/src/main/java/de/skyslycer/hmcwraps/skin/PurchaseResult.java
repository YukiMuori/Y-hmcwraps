package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.Nullable;

public record PurchaseResult(Status status, @Nullable String detail) {
    public enum Status { SUCCESS, ALREADY_OWNED, LOCKED, PERMISSION_DENIED, PROVIDER_UNAVAILABLE, INSUFFICIENT_FUNDS, PAYMENT_FAILED, STORAGE_FAILED, BUSY }
    public boolean successful() { return status == Status.SUCCESS || status == Status.ALREADY_OWNED; }
}
