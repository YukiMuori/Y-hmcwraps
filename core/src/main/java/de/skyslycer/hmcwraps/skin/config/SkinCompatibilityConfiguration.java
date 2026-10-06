package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.List;

@ConfigSerializable
public class SkinCompatibilityConfiguration {
    private List<String> materials = new ArrayList<>();
    private List<String> items = new ArrayList<>();

    public SkinCompatibilityConfiguration() {
    }

    public SkinCompatibilityConfiguration(List<String> materials, List<String> items) {
        this.materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public List<String> getMaterials() { return materials == null ? List.of() : materials; }
    public List<String> getItems() { return items == null ? List.of() : items; }
}
