package de.skyslycer.hmcwraps.shop;

import java.util.Locale;

/** How a bundle reacts to skins the buyer already owns. */
public enum BundlePurchaseMode {
    /** Only the full bundle can be bought; owning any skin makes the bundle unavailable. */
    FULL("full"),
    /** Only the missing skins can be bought, priced from the bundle's price. */
    MISSING_ONLY("missing-only"),
    /** The buyer chooses: full bundle price or a dynamic price for the missing skins. */
    BOTH("both");

    private final String id;

    BundlePurchaseMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BundlePurchaseMode fromId(String input) {
        if (input == null) {
            return BOTH;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (BundlePurchaseMode mode : values()) {
            if (mode.id.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return mode;
            }
        }
        return BOTH;
    }
}
