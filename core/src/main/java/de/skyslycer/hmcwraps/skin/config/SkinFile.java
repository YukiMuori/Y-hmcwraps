package de.skyslycer.hmcwraps.skin.config;

import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.List;

@ConfigSerializable
public class SkinFile {
    private boolean enabled = true;
    private String id;
    private String displayName;
    private String rarity = "common";
    private String collection;
    private List<String> categories = new ArrayList<>();
    private SkinIconConfiguration icon = new SkinIconConfiguration();
    private SkinCompatibilityConfiguration compatibility = new SkinCompatibilityConfiguration();

    // Compact, DeluxeMenus-style aliases. The advanced icon/compatibility/cosmetic sections
    // remain supported for existing installations.
    private String material;
    private String itemModel;
    private List<String> lore = new ArrayList<>();
    private List<String> compatibleMaterials = new ArrayList<>();
    private List<String> compatibleItems = new ArrayList<>();

    private SkinPriceConfiguration price;
    private String permission;
    private boolean preview = true;
    private Wrap cosmetic = new Wrap();

    public boolean isEnabled() { return enabled; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRarity() { return rarity; }
    public String getCollection() { return collection; }
    public List<String> getCategories() { return categories == null ? List.of() : categories; }

    public SkinIconConfiguration getIcon() {
        if ((material != null && !material.isBlank()) || (itemModel != null && !itemModel.isBlank())) {
            return new SkinIconConfiguration("material", compactMaterial(), displayName, lore, itemModel);
        }
        return icon == null ? new SkinIconConfiguration() : icon;
    }

    public SkinCompatibilityConfiguration getCompatibility() {
        SkinCompatibilityConfiguration advanced = compatibility == null ? new SkinCompatibilityConfiguration() : compatibility;
        List<String> materials = new ArrayList<>(advanced.getMaterials());
        List<String> items = new ArrayList<>(advanced.getItems());
        if (compatibleMaterials != null) materials.addAll(compatibleMaterials);
        if (compatibleItems != null) items.addAll(compatibleItems);
        return new SkinCompatibilityConfiguration(materials, items);
    }

    public SkinPriceConfiguration getPrice() { return price; }
    public String getPermission() { return permission; }
    public boolean isPreview() { return preview; }

    public Wrap getCosmetic() {
        Wrap result = cosmetic == null ? new Wrap() : cosmetic;
        if (itemModel != null && !itemModel.isBlank()) {
            result.setId(compactMaterial());
            result.setItemModel(itemModel.trim());
        }
        return result;
    }

    private String compactMaterial() {
        return material == null || material.isBlank() ? "PAPER" : material.trim();
    }
}
