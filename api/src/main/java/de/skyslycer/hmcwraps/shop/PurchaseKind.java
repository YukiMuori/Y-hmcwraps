package de.skyslycer.hmcwraps.shop;

import java.util.Locale;

/** The kind of object a purchase or gift record refers to. */
public enum PurchaseKind {
    SKIN("skin"),
    BUNDLE("bundle"),
    GIFT_SKIN("gift-skin"),
    GIFT_BUNDLE("gift-bundle");

    private final String id;

    PurchaseKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PurchaseKind fromId(String input) {
        if (input == null) {
            return SKIN;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (PurchaseKind kind : values()) {
            if (kind.id.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return kind;
            }
        }
        return SKIN;
    }

    /** Whether this record was created by gifting another player. */
    public boolean gift() {
        return this == GIFT_SKIN || this == GIFT_BUNDLE;
    }
}
