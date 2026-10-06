package de.skyslycer.hmcwraps.collection;

import java.util.Locale;

/** The reward kinds a collection milestone can grant. */
public enum CollectionRewardType {
    /** Deposits money through the configured economy provider. */
    MONEY("money"),
    /** Grants ownership of another skin. */
    SKIN("skin"),
    /** Grants a permission through a permissions plugin when one is available. */
    PERMISSION("permission"),
    /** Runs a console command with the {@code <player>} placeholder. */
    COMMAND("command"),
    /** Gives a configured item to the player. */
    ITEM("item"),
    /** Grants every skin of a bundle. */
    BUNDLE("bundle"),
    /** Sends a configurable message. */
    MESSAGE("message"),
    /** Adds collection experience to the player's profile statistics. */
    COLLECTION_XP("collection-xp");

    private final String id;

    CollectionRewardType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CollectionRewardType fromId(String input) {
        if (input == null) {
            return MESSAGE;
        }
        String normalized = input.toLowerCase(Locale.ROOT).trim().replace('_', '-');
        for (CollectionRewardType type : values()) {
            if (type.id.equals(normalized) || type.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return type;
            }
        }
        return MESSAGE;
    }
}
