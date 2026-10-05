package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Snapshot-based compatibility dispatch; optional item systems enter only through providers. */
public final class CompatibilityRegistry {
    private final HMCWrapsPlugin plugin;
    private volatile List<CompatibilityProvider> providers;

    public CompatibilityRegistry(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
        this.providers = List.of(
                new VanillaCompatibilityProvider(),
                new HookCompatibilityProvider(plugin, "nexo", "Nexo", "nexo:"),
                new HookCompatibilityProvider(plugin, "itemsadder", "ItemsAdder", "itemsadder:"),
                new HookCompatibilityProvider(plugin, "oraxen", "Oraxen", "oraxen:"),
                new HookCompatibilityProvider(plugin, "craftengine", "CraftEngine", "craftengine:"),
                new HookCompatibilityProvider(plugin, "mythic", "MythicCrucible", "mythic:"));
    }

    public synchronized void register(CompatibilityProvider provider) {
        java.util.Objects.requireNonNull(provider, "provider");
        String id = provider.id();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Compatibility provider id must not be blank");
        List<CompatibilityProvider> next = new ArrayList<>(providers);
        next.removeIf(existing -> existing.id().equalsIgnoreCase(id));
        next.add(provider);
        providers = List.copyOf(next);
    }

    public List<CompatibilityProvider> providers() { return providers; }

    public Set<String> getItemIds(ItemStack item) {
        if (item == null || item.getType().isAir()) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (CompatibilityProvider provider : providers) {
            try {
                if (!provider.isAvailable()) continue;
                String value = provider.getItemId(item);
                if (value != null && !value.isBlank()) ids.add(normalize(value));
            } catch (LinkageError | RuntimeException exception) {
                plugin.getLogger().warning("Compatibility provider '" + provider.id() + "' failed to inspect an item: " + exception.getMessage());
            }
        }
        // The legacy modifiers retain original custom-item identifiers in PDC while a wrap is applied.
        // Add them so a skin can still be selected for an already wrapped Nexo/ItemsAdder/etc. item.
        if (plugin.getWrapper().getWrap(item) != null) {
            var modifiers = plugin.getWrapper().getModifiers();
            add(ids, "nexo", modifiers.nexo().getOriginalId(item));
            add(ids, "itemsadder", modifiers.itemsAdder().getOriginalId(item));
            add(ids, "oraxen", modifiers.oraxen().getOriginalId(item));
            add(ids, "craftengine", modifiers.craftEngine().getOriginalId(item));
            add(ids, "mythic", modifiers.mythic().getOriginalId(item));
            add(ids, "executableitems", modifiers.executableItems().getOriginalId(item));
            add(ids, "mmoitems", modifiers.mmoItems().getOriginalId(item));
        }
        return Set.copyOf(ids);
    }

    private static void add(Set<String> ids, String provider, String id) {
        if (id != null && !id.isBlank()) {
            String normalized = normalize(id);
            ids.add(normalized.contains(":") ? normalized : provider + ":" + normalized);
        }
    }

    public boolean matches(ItemStack item, List<String> configuredIds) {
        return matches(getItemIds(item), configuredIds);
    }

    public boolean matches(Set<String> actualIds, List<String> configuredIds) {
        if (actualIds == null || actualIds.isEmpty() || configuredIds == null || configuredIds.isEmpty()) return false;
        return configuredIds.stream().map(CompatibilityRegistry::normalize).anyMatch(actualIds::contains);
    }

    public Material getEffectiveMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) return Material.AIR;
        var wrap = plugin.getWrapper().getWrap(item);
        if (wrap != null) {
            String original = plugin.getWrapper().getModifiers().armorImitation().getOriginalMaterial(item);
            if (original != null && !original.isBlank()) {
                Material originalMaterial = Material.matchMaterial(original);
                if (originalMaterial != null) return originalMaterial;
            }
        }
        return item.getType();
    }

    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).trim(); }

    private static final class VanillaCompatibilityProvider implements CompatibilityProvider {
        @Override public String id() { return "vanilla"; }
        @Override public boolean isAvailable() { return true; }
        @Override public String getItemId(ItemStack item) {
            return item == null || item.getType().isAir() ? null : "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class HookCompatibilityProvider implements CompatibilityProvider {
        private final HMCWrapsPlugin plugin;
        private final String id;
        private final String pluginName;
        private final String namespace;

        private HookCompatibilityProvider(HMCWrapsPlugin plugin, String id, String pluginName, String namespace) {
            this.plugin = plugin;
            this.id = id;
            this.pluginName = pluginName;
            this.namespace = namespace;
        }

        @Override public String id() { return id; }
        @Override public boolean isAvailable() {
            return plugin.getHookAccessor() != null && org.bukkit.Bukkit.getPluginManager().isPluginEnabled(pluginName);
        }
        @Override public String getItemId(ItemStack item) {
            if (item == null || item.getType().isAir() || plugin.getHookAccessor() == null) return null;
            String itemId = plugin.getHookAccessor().getIdFromHook(item);
            return itemId != null && itemId.toLowerCase(Locale.ROOT).startsWith(namespace) ? itemId : null;
        }
    }
}
