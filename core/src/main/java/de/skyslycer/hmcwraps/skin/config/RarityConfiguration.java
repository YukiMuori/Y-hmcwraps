package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.HashMap;
import java.util.Map;

@ConfigSerializable
public class RarityConfiguration {
    private boolean enabled = true;
    private Map<String, Entry> rarities = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public Map<String, Entry> getRarities() { return rarities == null ? Map.of() : rarities; }

    @ConfigSerializable
    public static class Entry {
        private int priority;
        private String displayNameKey;
        public int getPriority() { return priority; }
        public String getDisplayNameKey() { return displayNameKey; }
    }
}
