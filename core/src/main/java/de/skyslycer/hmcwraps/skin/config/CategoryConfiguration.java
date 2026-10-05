package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.HashMap;
import java.util.Map;

@ConfigSerializable
public class CategoryConfiguration {
    private boolean enabled = true;
    private Map<String, Entry> categories = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public Map<String, Entry> getCategories() { return categories == null ? Map.of() : categories; }

    @ConfigSerializable
    public static class Entry {
        private int priority;
        private String displayNameKey;
        private SkinIconConfiguration icon;
        public int getPriority() { return priority; }
        public String getDisplayNameKey() { return displayNameKey; }
        public SkinIconConfiguration getIcon() { return icon; }
    }
}
