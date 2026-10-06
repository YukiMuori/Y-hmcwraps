package de.skyslycer.hmcwraps.shop;

import java.util.Locale;

/**
 * The shop section a purchase originates from. Coupons and integrations can target
 * individual channels without coupling shop code to a concrete GUI.
 */
public enum ShopChannel {
    /** The rotating daily offer. */
    DAILY("daily"),
    /** The hand-picked (or automatic) featured selection. */
    FEATURED("featured"),
    /** A timed event shop such as a holiday shop. */
    EVENT("event"),
    /** A bundle purchase (also used when a bundle is bought from featured/event sections). */
    BUNDLE("bundle"),
    /** A direct purchase not initiated from a shop screen. */
    DIRECT("direct");

    private final String id;

    ShopChannel(String id) {
        this.id = id;
    }

    /** Stable identifier used in configuration and coupon definitions. */
    public String id() {
        return id;
    }

    /** Resolves a configured channel id, defaulting to {@link #DIRECT}. */
    public static ShopChannel fromId(String input) {
        if (input == null) {
            return DIRECT;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim();
        for (ShopChannel channel : values()) {
            if (channel.id.equals(normalized) || channel.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return channel;
            }
        }
        return DIRECT;
    }
}
