package de.skyslycer.hmcwraps.collection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A claimable step inside a skin collection.
 *
 * @param id          stable milestone id, unique inside its collection
 * @param displayName a MiniMessage display name
 * @param required    how many skins of the collection must be owned (or the required percentage when {@code percentage} is true)
 * @param percentage  whether {@code required} is a percentage instead of an absolute skin count
 * @param icon        an optional icon reference for menus
 * @param rewards     the rewards granted when claimed
 */
public record CollectionMilestone(@NotNull String id, @NotNull String displayName, double required, boolean percentage,
                                  @Nullable String icon, @NotNull List<CollectionReward> rewards) {

    public CollectionMilestone {
        Objects.requireNonNull(id, "id");
        id = id.toLowerCase(Locale.ROOT).trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("Milestone id must not be blank");
        }
        Objects.requireNonNull(displayName, "displayName");
        if (!Double.isFinite(required) || required < 0) {
            throw new IllegalArgumentException("Milestone requirement must be a non-negative finite number");
        }
        rewards = List.copyOf(rewards);
    }

    /** Whether the milestone is reached for the supplied progress. */
    public boolean reached(int owned, int total) {
        if (total <= 0) {
            return false;
        }
        double needed = percentage ? total * (Math.min(100, required) / 100D) : required;
        return owned >= Math.ceil(needed);
    }
}
