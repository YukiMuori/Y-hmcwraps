package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.collection.CollectionMilestone;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;

/** A named series/set of skins that can contain subcategories such as swords, tools and armor. */
public final class ItemSkinCollection {
    private final String id;
    private final String displayNameKey;
    private final ItemStack icon;
    private final int priority;
    private final Set<String> categoryIds;
    private final List<CollectionMilestone> milestones;

    public ItemSkinCollection(@NotNull String id, @NotNull String displayNameKey, @Nullable ItemStack icon,
                              int priority, @NotNull Set<String> categoryIds) {
        this(id, displayNameKey, icon, priority, categoryIds, List.of());
    }

    public ItemSkinCollection(@NotNull String id, @NotNull String displayNameKey, @Nullable ItemStack icon,
                              int priority, @NotNull Set<String> categoryIds,
                              @NotNull List<CollectionMilestone> milestones) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        this.displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
        this.icon = icon == null ? null : icon.clone();
        this.priority = priority;
        this.categoryIds = Set.copyOf(categoryIds);
        this.milestones = milestones.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CollectionMilestone::required)).toList();
        if (this.id.isBlank()) throw new IllegalArgumentException("Collection id must not be blank");
    }

    /** The claimable milestones of this collection, sorted by requirement. */
    public @NotNull List<CollectionMilestone> milestones() {
        return milestones;
    }

    public @NotNull String id() { return id; }
    public @NotNull String displayNameKey() { return displayNameKey; }
    public @Nullable ItemStack icon() { return icon == null ? null : icon.clone(); }
    public int priority() { return priority; }
    public @NotNull Set<String> categoryIds() { return categoryIds; }
}
