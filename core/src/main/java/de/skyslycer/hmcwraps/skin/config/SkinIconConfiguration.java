package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.List;

@ConfigSerializable
public class SkinIconConfiguration {
    private String type = "material";
    private String id = "PAPER";
    private String name;
    private List<String> lore = new ArrayList<>();
    private Integer modelId;
    private String itemModel;
    private String tooltipStyle;
    private String skullTexture;
    private String skullOwner;
    private Boolean glow;
    private Boolean glintOverride;
    private Boolean hideTooltip;

    /** Builds an icon description programmatically, for example for collection item rewards. */
    public SkinIconConfiguration(String type, String id, String name, List<String> lore) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
    }

    public String getType() { return type == null ? "material" : type; }
    public String getId() { return id == null ? "PAPER" : id; }
    public String getName() { return name; }
    public List<String> getLore() { return lore == null ? List.of() : lore; }
    public Integer getModelId() { return modelId; }
    public String getItemModel() { return itemModel; }
    public String getTooltipStyle() { return tooltipStyle; }
    public String getSkullTexture() { return skullTexture; }
    public String getSkullOwner() { return skullOwner; }
    public Boolean getGlow() { return glow; }
    public Boolean getGlintOverride() { return glintOverride; }
    public Boolean getHideTooltip() { return hideTooltip; }
}
