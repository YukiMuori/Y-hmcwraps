package de.skyslycer.hmcwraps.shop;

import java.util.Locale;

/** The supported coupon discount kinds. */
public enum CouponType {
    /** A percentage of the price, for example {@code 20} for -20%. */
    PERCENTAGE("percentage"),
    /** A fixed amount of the coupon's currency. */
    FIXED("fixed");

    private final String id;

    CouponType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CouponType fromId(String input) {
        if (input == null) {
            return PERCENTAGE;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        for (CouponType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return PERCENTAGE;
    }
}
