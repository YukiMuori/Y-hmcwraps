package de.skyslycer.hmcwraps.collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single configured collection reward.
 *
 * @param type   the reward kind
 * @param value  the type specific payload (skin id, permission node, command, message, item reference)
 * @param amount the amount for money and collection-xp rewards
 * @param icon   an optional icon reference for menus
 */
public record CollectionReward(@NotNull CollectionRewardType type, @Nullable String value, double amount,
                               @Nullable String icon) {

    public static @NotNull CollectionReward money(double amount) {
        return new CollectionReward(CollectionRewardType.MONEY, null, amount, null);
    }

    public static @NotNull CollectionReward skin(@NotNull String skinId) {
        return new CollectionReward(CollectionRewardType.SKIN, skinId, 0, null);
    }

    public static @NotNull CollectionReward bundle(@NotNull String bundleId) {
        return new CollectionReward(CollectionRewardType.BUNDLE, bundleId, 0, null);
    }

    public static @NotNull CollectionReward permission(@NotNull String permission) {
        return new CollectionReward(CollectionRewardType.PERMISSION, permission, 0, null);
    }

    public static @NotNull CollectionReward command(@NotNull String command) {
        return new CollectionReward(CollectionRewardType.COMMAND, command, 0, null);
    }

    public static @NotNull CollectionReward item(@NotNull String itemReference) {
        return new CollectionReward(CollectionRewardType.ITEM, itemReference, 0, null);
    }

    public static @NotNull CollectionReward message(@NotNull String message) {
        return new CollectionReward(CollectionRewardType.MESSAGE, message, 0, null);
    }

    public static @NotNull CollectionReward collectionXp(double amount) {
        return new CollectionReward(CollectionRewardType.COLLECTION_XP, null, amount, null);
    }
}
