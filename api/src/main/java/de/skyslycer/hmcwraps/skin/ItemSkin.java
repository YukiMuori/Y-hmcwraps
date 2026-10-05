package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Public, immutable catalog entry for a cosmetic skin. The legacy {@link Wrap} is retained
 * as the application payload so existing item modifiers and PDC data remain readable.
 */
public final class ItemSkin {
    private final String id;
    private final String displayName;
    private final String rarityId;
    private final Set<String> categoryIds;
    private final List<Material> compatibleMaterials;
    private final List<String> compatibleItems;
    private final SkinPrice price;
    private final String permission;
    private final boolean previewEnabled;
    private final ItemStack icon;
    private final Wrap cosmetic;

    public ItemSkin(@NotNull String id, @NotNull String displayName, @NotNull String rarityId,
                    @NotNull Set<String> categoryIds, @NotNull List<Material> compatibleMaterials,
                    @NotNull List<String> compatibleItems, @Nullable SkinPrice price,
                    @Nullable String permission, boolean previewEnabled,
                    @Nullable ItemStack icon, @NotNull Wrap cosmetic) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.rarityId = Objects.requireNonNull(rarityId, "rarityId").toLowerCase(Locale.ROOT).trim();
        this.categoryIds = Set.copyOf(categoryIds);
        this.compatibleMaterials = List.copyOf(compatibleMaterials);
        this.compatibleItems = List.copyOf(compatibleItems);
        this.price = price;
        this.permission = permission;
        this.previewEnabled = previewEnabled;
        this.icon = icon == null ? null : icon.clone();
        this.cosmetic = Objects.requireNonNull(cosmetic, "cosmetic");
        if (this.id.isBlank()) throw new IllegalArgumentException("Skin id must not be blank");
    }

    public @NotNull String id() { return id; }
    public @NotNull String displayName() { return displayName; }
    public @NotNull String rarityId() { return rarityId; }
    public @NotNull Set<String> categoryIds() { return categoryIds; }
    public @NotNull List<Material> compatibleMaterials() { return compatibleMaterials; }
    public @NotNull List<String> compatibleItems() { return compatibleItems; }
    public @Nullable SkinPrice price() { return price; }
    public @Nullable String permission() { return permission; }
    public boolean previewEnabled() { return previewEnabled; }
    public @Nullable ItemStack icon() { return icon == null ? null : icon.clone(); }
    public @NotNull Wrap cosmetic() { return cosmetic; }
}
