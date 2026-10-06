package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The outcome of evaluating a coupon. */
public record CouponResult(@NotNull Status status, @Nullable Coupon coupon, double discount, @Nullable String detail) {

    public enum Status {
        /** The coupon is usable and a discount was calculated. */
        APPLIED,
        /** No coupon with that code exists. */
        UNKNOWN,
        /** The coupon is disabled by configuration. */
        DISABLED,
        /** The coupon expired. */
        EXPIRED,
        /** The global usage limit was reached. */
        EXHAUSTED,
        /** The player already used the coupon as often as allowed. */
        ALREADY_USED,
        /** The order value is below the configured minimum. */
        MIN_SPEND,
        /** The order does not contain an applicable skin, bundle, category or channel. */
        NOT_APPLICABLE,
        /** Usage counters could not be read; the coupon is refused so nothing is under-charged. */
        STORAGE_ERROR
    }

    public static @NotNull CouponResult rejected(@NotNull Status status, @Nullable Coupon coupon, @Nullable String detail) {
        return new CouponResult(status, coupon, 0, detail);
    }

    public static @NotNull CouponResult applied(@NotNull Coupon coupon, double discount) {
        return new CouponResult(Status.APPLIED, coupon, discount, null);
    }

    public boolean usable() {
        return status == Status.APPLIED;
    }
}
