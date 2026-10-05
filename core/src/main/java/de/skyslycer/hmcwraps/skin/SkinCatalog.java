package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.skin.config.CategoryConfiguration;
import de.skyslycer.hmcwraps.skin.config.RarityConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinFile;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinPriceConfiguration;
import de.skyslycer.hmcwraps.skin.ItemIconFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Loads the new v2 catalog once per reload and publishes immutable snapshots to menus/API callers. */
public final class SkinCatalog {
    private final HMCWrapsPlugin plugin;
    private final ItemIconFactory iconFactory;
    private volatile Map<String, ItemSkin> skins = Map.of();
    private volatile Map<String, ItemSkinRarity> rarities = Map.of();
    private volatile Map<String, ItemSkinCategory> categories = Map.of();
    private volatile Map<String, SkinIconConfiguration> skinIconConfigurations = Map.of();
    private volatile Map<String, SkinIconConfiguration> categoryIconConfigurations = Map.of();

    public SkinCatalog(HMCWrapsPlugin plugin, ItemIconFactory iconFactory) {
        this.plugin = plugin;
        this.iconFactory = iconFactory;
    }

    public boolean load() {
        skins = Map.of();
        rarities = Map.of();
        categories = Map.of();
        skinIconConfigurations = Map.of();
        categoryIconConfigurations = Map.of();
        try {
            Files.createDirectories(HMCWraps.SKINS_PATH);
            copyIfAbsent("rarities.yml", HMCWraps.RARITIES_PATH);
            copyIfAbsent("categories.yml", HMCWraps.CATEGORIES_PATH);
            copyIfAbsent("itemskin-gui.yml", HMCWraps.ITEMSKIN_GUI_PATH);
            copyIfAbsent("skins/ruby_sword.yml", HMCWraps.SKINS_PATH.resolve("ruby_sword.yml"));
            copyIfAbsent("skins/README.yml", HMCWraps.SKINS_PATH.resolve("README.yml"));

            RarityConfiguration rarityConfiguration = load(HMCWraps.RARITIES_PATH, RarityConfiguration.class);
            CategoryConfiguration categoryConfiguration = load(HMCWraps.CATEGORIES_PATH, CategoryConfiguration.class);
            if (rarityConfiguration == null || categoryConfiguration == null) return false;

            Map<String, ItemSkinRarity> nextRarities = loadRarities(rarityConfiguration);
            Map<String, SkinIconConfiguration> nextCategoryIcons = new LinkedHashMap<>();
            Map<String, ItemSkinCategory> nextCategories = loadCategories(categoryConfiguration, nextCategoryIcons);
            Map<String, SkinIconConfiguration> parsedIconConfigurations = new LinkedHashMap<>();
            Map<String, ItemSkin> parsedSkins = loadSkins(nextRarities, nextCategories, parsedIconConfigurations);
            Map<String, ItemSkin> nextSkins = new LinkedHashMap<>();
            Map<String, SkinIconConfiguration> nextSkinIcons = new LinkedHashMap<>();
            for (ItemSkin skin : parsedSkins.values()) {
                if (plugin.getWrapsLoader().registerAdditionalWrap(skin.cosmetic())) {
                    nextSkins.put(skin.id(), skin);
                    SkinIconConfiguration iconConfiguration = parsedIconConfigurations.get(skin.id());
                    if (iconConfiguration != null) nextSkinIcons.put(skin.id(), iconConfiguration);
                } else {
                    plugin.getLogger().warning("Skin '" + skin.id() + "' could not register its legacy cosmetic payload; its id may collide with a legacy wrap.");
                }
            }
            if (nextRarities.isEmpty()) {
                plugin.logSevere("No valid skin rarities were loaded. Check plugins/HMCWraps/rarities.yml.");
                return false;
            }
            rarities = Map.copyOf(nextRarities);
            categories = Map.copyOf(nextCategories);
            categoryIconConfigurations = Map.copyOf(nextCategoryIcons);
            skins = Map.copyOf(nextSkins);
            skinIconConfigurations = Map.copyOf(nextSkinIcons);
            plugin.getLogger().info("Loaded " + skins.size() + " v2 item skins, " + rarities.size() + " rarities and " + categories.size() + " categories.");
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.logSevere("Could not load the v2 skin catalog.", exception);
            return false;
        }
    }

    private Map<String, ItemSkinRarity> loadRarities(RarityConfiguration config) {
        Map<String, ItemSkinRarity> values = new LinkedHashMap<>();
        if (!config.isEnabled()) return values;
        config.getRarities().forEach((id, entry) -> {
            if (id == null || id.isBlank() || entry == null || entry.getDisplayNameKey() == null || entry.getDisplayNameKey().isBlank()) {
                plugin.getLogger().warning("Skipping a rarity with an empty id or display-name-key.");
                return;
            }
            values.put(normalize(id), new ItemSkinRarity(id, entry.getPriority(), entry.getDisplayNameKey()));
        });
        return values;
    }

    private Map<String, ItemSkinCategory> loadCategories(CategoryConfiguration config, Map<String, SkinIconConfiguration> iconConfigurations) {
        Map<String, ItemSkinCategory> values = new LinkedHashMap<>();
        if (!config.isEnabled()) return values;
        config.getCategories().forEach((id, entry) -> {
            if (id == null || id.isBlank() || entry == null || entry.getDisplayNameKey() == null || entry.getDisplayNameKey().isBlank()) {
                plugin.getLogger().warning("Skipping a category with an empty id or display-name-key.");
                return;
            }
            var iconConfig = entry.getIcon();
            var icon = iconConfig == null ? null : iconFactory.create(iconConfig, "");
            String normalizedId = normalize(id);
            values.put(normalizedId, new ItemSkinCategory(id, entry.getDisplayNameKey(), icon, entry.getPriority()));
            if (iconConfig != null) iconConfigurations.put(normalizedId, iconConfig);
        });
        return values;
    }

    private Map<String, ItemSkin> loadSkins(Map<String, ItemSkinRarity> availableRarities,
                                             Map<String, ItemSkinCategory> availableCategories,
                                             Map<String, SkinIconConfiguration> iconConfigurations) throws IOException {
        Map<String, ItemSkin> values = new LinkedHashMap<>();
        Set<String> cosmeticIds = new java.util.HashSet<>();
        try (Stream<Path> paths = Files.find(HMCWraps.SKINS_PATH, 8,
                (path, attributes) -> attributes.isRegularFile()
                        && (path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")))) {
            for (Path path : paths.sorted().toList()) {
                if (path.getFileName().toString().equalsIgnoreCase("README.yml")) continue;
                SkinFile file;
                try {
                    file = load(path, SkinFile.class);
                } catch (Exception exception) {
                    plugin.logSevere("Could not parse skin file " + HMCWraps.PLUGIN_PATH.relativize(path) + ".", exception);
                    continue;
                }
                if (file == null || !file.isEnabled()) continue;
                ItemSkin skin = createSkin(file, path, availableRarities, availableCategories);
                if (skin == null) continue;
                if (values.containsKey(skin.id())) {
                    plugin.getLogger().warning("Duplicate skin id '" + skin.id() + "' in " + path + "; skipping this entry.");
                    continue;
                }
                if (!cosmeticIds.add(skin.cosmetic().getUuid())) {
                    plugin.getLogger().warning("Skin '" + skin.id() + "' repeats a cosmetic UUID in another skin file; skipping.");
                    continue;
                }
                values.put(skin.id(), skin);
                iconConfigurations.put(skin.id(), file.getIcon());
            }
        }
        return values;
    }

    private @Nullable ItemSkin createSkin(SkinFile file, Path path,
                                           Map<String, ItemSkinRarity> availableRarities,
                                           Map<String, ItemSkinCategory> availableCategories) {
        String id = file.getId() == null ? "" : normalize(file.getId());
        if (id.isBlank()) {
            plugin.getLogger().warning("Skin file " + path.getFileName() + " has no id; skipping.");
            return null;
        }
        if (file.getDisplayName() == null || file.getDisplayName().isBlank()) {
            plugin.getLogger().warning("Skin '" + id + "' has no display-name; skipping.");
            return null;
        }
        String rarityId = normalize(file.getRarity() == null ? "" : file.getRarity());
        if (!availableRarities.containsKey(rarityId)) {
            plugin.getLogger().warning("Skin '" + id + "' refers to unknown rarity '" + rarityId + "'; skipping.");
            return null;
        }
        List<String> categoryIds = file.getCategories().stream().filter(value -> value != null && !value.isBlank()).map(SkinCatalog::normalize).distinct().toList();
        for (String category : categoryIds) {
            if (!availableCategories.containsKey(category)) plugin.getLogger().warning("Skin '" + id + "' refers to unknown category '" + category + "'.");
        }

        List<Material> materials = new ArrayList<>();
        for (String materialName : file.getCompatibility().getMaterials()) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) plugin.getLogger().warning("Unknown compatibility material '" + materialName + "' for skin '" + id + "'.");
            else if (!materials.contains(material)) materials.add(material);
        }
        List<String> compatibleItems = file.getCompatibility().getItems().stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (materials.isEmpty() && compatibleItems.isEmpty()) {
            plugin.getLogger().warning("Skin '" + id + "' has no valid compatibility entries; skipping.");
            return null;
        }

        Wrap cosmetic = file.getCosmetic();
        if (cosmetic.getUuid() == null || cosmetic.getUuid().isBlank()) cosmetic.setUuid("yhm-skin:" + id);
        if (plugin.getWrapsLoader().getWraps().containsKey(cosmetic.getUuid())) {
            plugin.getLogger().warning("Skin '" + id + "' cosmetic uuid collides with an existing wrap: " + cosmetic.getUuid());
            return null;
        }
        ItemStack icon = iconFactory.create(file.getIcon(), file.getDisplayName());
        SkinPriceConfiguration priceConfiguration = file.getPrice();
        SkinPrice price = createPrice(priceConfiguration, id);
        if (priceConfiguration != null && priceConfiguration.isConfigured() && price == null) {
            plugin.getLogger().warning("Skin '" + id + "' has an invalid configured price; skipping it instead of making it free.");
            return null;
        }
        String permission = file.getPermission() == null || file.getPermission().isBlank() ? null : file.getPermission().trim();
        return new ItemSkin(id, file.getDisplayName(), rarityId, Set.copyOf(categoryIds), materials, compatibleItems,
                price, permission, file.isPreview(), icon, cosmetic);
    }

    private @Nullable SkinPrice createPrice(@Nullable SkinPriceConfiguration config, String skinId) {
        if (config == null || !config.isConfigured()) return null;
        if (config.getAmount() == null || !Double.isFinite(config.getAmount()) || config.getAmount() <= 0) {
            plugin.getLogger().warning("Skin '" + skinId + "' price amount must be finite and greater than zero.");
            return null;
        }
        if (config.getCurrency().isBlank() || config.getProvider().isBlank()) {
            plugin.getLogger().warning("Skin '" + skinId + "' price requires a provider and currency.");
            return null;
        }
        try {
            return new SkinPrice(config.getProvider(), config.getCurrency(), config.getAmount());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid price for skin '" + skinId + "': " + exception.getMessage());
            return null;
        }
    }

    public List<ItemSkin> getSkins() {
        return skins.values().stream().sorted(Comparator.comparing(ItemSkin::id)).toList();
    }

    public Map<String, ItemSkin> skinMap() { return skins; }
    public Map<String, ItemSkinRarity> rarityMap() { return rarities; }
    public Map<String, ItemSkinCategory> categoryMap() { return categories; }
    public @Nullable SkinIconConfiguration skinIconConfiguration(String skinId) {
        return skinIconConfigurations.get(normalize(skinId));
    }
    public @Nullable SkinIconConfiguration categoryIconConfiguration(String categoryId) {
        return categoryIconConfigurations.get(normalize(categoryId));
    }

    private <T> T load(Path path, Class<T> type) throws IOException {
        return YamlConfigurationLoader.builder()
                .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
                .path(path)
                .build().load().get(type);
    }

    private void copyIfAbsent(String resourceName, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        if (Files.notExists(destination)) {
            try (InputStream resource = plugin.getResource(resourceName)) {
                if (resource != null) Files.copy(resource, destination);
            }
        }
    }

    private static String normalize(String input) { return input.toLowerCase(Locale.ROOT).trim(); }
}
