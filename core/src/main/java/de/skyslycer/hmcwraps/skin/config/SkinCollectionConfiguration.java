package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigSerializable
public class SkinCollectionConfiguration {
    private boolean enabled = true;
    private Map<String, Entry> collections = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public Map<String, Entry> getCollections() { return collections == null ? Map.of() : collections; }

    @ConfigSerializable
    public static class Entry {
        private int priority;
        private String displayNameKey;
        private SkinIconConfiguration icon;
        private List<String> categories = new ArrayList<>();

        public int getPriority() { return priority; }
        public String getDisplayNameKey() { return displayNameKey; }
        public SkinIconConfiguration getIcon() { return icon; }
        public List<String> getCategories() { return categories == null ? List.of() : categories; }
    }
}
