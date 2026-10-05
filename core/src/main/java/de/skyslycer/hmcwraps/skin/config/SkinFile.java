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
    private List<String> categories = new ArrayList<>();
    private SkinIconConfiguration icon = new SkinIconConfiguration();
    private SkinCompatibilityConfiguration compatibility = new SkinCompatibilityConfiguration();
    private SkinPriceConfiguration price;
    private String permission;
    private boolean preview = true;
    private Wrap cosmetic = new Wrap();

    public boolean isEnabled() { return enabled; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRarity() { return rarity; }
    public List<String> getCategories() { return categories == null ? List.of() : categories; }
    public SkinIconConfiguration getIcon() { return icon == null ? new SkinIconConfiguration() : icon; }
    public SkinCompatibilityConfiguration getCompatibility() { return compatibility == null ? new SkinCompatibilityConfiguration() : compatibility; }
    public SkinPriceConfiguration getPrice() { return price; }
    public String getPermission() { return permission; }
    public boolean isPreview() { return preview; }
    public Wrap getCosmetic() { return cosmetic == null ? new Wrap() : cosmetic; }
}
