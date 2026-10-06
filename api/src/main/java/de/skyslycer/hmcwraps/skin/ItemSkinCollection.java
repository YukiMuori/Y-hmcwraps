package de.skyslycer.hmcwraps.skin;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** A named series/set of skins that can contain subcategories such as swords, tools and armor. */
public final class ItemSkinCollection {
    private final String id;
    private final String displayNameKey;
    private final ItemStack icon;
    private final int priority;
    private final Set<String> categoryIds;

    public ItemSkinCollection(@NotNull String id, @NotNull String displayNameKey, @Nullable ItemStack icon,
                              int priority, @NotNull Set<String> categoryIds) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        this.displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
        this.icon = icon == null ? null : icon.clone();
        this.priority = priority;
        this.categoryIds = Set.copyOf(categoryIds);
        if (this.id.isBlank()) throw new IllegalArgumentException("Collection id must not be blank");
    }

    public @NotNull String id() { return id; }
    public @NotNull String displayNameKey() { return displayNameKey; }
    public @Nullable ItemStack icon() { return icon == null ? null : icon.clone(); }
    public int priority() { return priority; }
    public @NotNull Set<String> categoryIds() { return categoryIds; }
}
