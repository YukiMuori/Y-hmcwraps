package de.skyslycer.hmcwraps.skin;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/** A display category for item skins, independent of legacy material collections. */
public final class ItemSkinCategory {
    private final String id;
    private final String displayNameKey;
    private final ItemStack icon;
    private final int priority;

    public ItemSkinCategory(@NotNull String id, @NotNull String displayNameKey,
                            @Nullable ItemStack icon, int priority) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        this.displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
        this.icon = icon == null ? null : icon.clone();
        this.priority = priority;
        if (this.id.isBlank()) throw new IllegalArgumentException("Category id must not be blank");
    }

    public @NotNull String id() { return id; }
    public @NotNull String displayNameKey() { return displayNameKey; }
    public @Nullable ItemStack icon() { return icon == null ? null : icon.clone(); }
    public int priority() { return priority; }
}
