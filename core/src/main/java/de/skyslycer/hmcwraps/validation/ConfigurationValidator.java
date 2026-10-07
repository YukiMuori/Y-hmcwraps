package de.skyslycer.hmcwraps.validation;

import de.skyslycer.hmcwraps.serialization.Config;
import de.skyslycer.hmcwraps.serialization.files.CollectionFile;
import de.skyslycer.hmcwraps.serialization.files.WrapFile;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.serialization.wrap.WrappableItem;
import de.skyslycer.hmcwraps.shop.config.BundleConfiguration;
import de.skyslycer.hmcwraps.shop.config.CouponConfiguration;
import de.skyslycer.hmcwraps.shop.config.CouponFile;
import de.skyslycer.hmcwraps.shop.config.EventShopConfiguration;
import de.skyslycer.hmcwraps.shop.config.ShopFile;
import de.skyslycer.hmcwraps.skin.EconomyProvider;
import de.skyslycer.hmcwraps.skin.config.CategoryConfiguration;
import de.skyslycer.hmcwraps.skin.config.RarityConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinCompatibilityConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinFile;
import de.skyslycer.hmcwraps.skin.config.SkinCollectionConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinMenuConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinPriceConfiguration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Read-only validation of HMCWraps configuration and the references between its files. */
public final class ConfigurationValidator {
    private static final Pattern LANGUAGE_REFERENCE = Pattern.compile("<(lang|glyph):([^>]+)>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> BUILT_IN_LOCALES = Set.of("en", "it");
    private static final Set<String> ICON_TYPES = Set.of("material", "vanilla", "head", "nexo", "itemsadder",
            "oraxen", "craftengine", "mythic", "executableitems", "mmoitems", "custom");
    private static final Set<String> SORT_OPTIONS = Set.of("rarity", "name", "price", "owned", "category");
    private static final Set<String> FILTER_OPTIONS = Set.of("all", "owned", "unowned", "purchasable", "free");
    private static final Set<String> CLICK_ACTIONS = Set.of("apply", "preview", "buy", "purchase");

    private final Path dataFolder;
    private final Map<String, EconomyProvider> economyProviders;

    public ConfigurationValidator(Path dataFolder) {
        this(dataFolder, List.of());
    }

    public ConfigurationValidator(Path dataFolder, Collection<EconomyProvider> providers) {
        this.dataFolder = dataFolder.toAbsolutePath().normalize();
        Map<String, EconomyProvider> normalized = new HashMap<>();
        if (providers != null) {
            for (EconomyProvider provider : providers) {
                if (provider != null && provider.id() != null) normalized.put(normalizeProvider(provider.id()), provider);
            }
        }
        this.economyProviders = Map.copyOf(normalized);
    }

    /** Validates files without creating, rewriting, migrating, or reloading any configuration. */
    public Report validate() {
        Context context = new Context();
        Config config = loadConfig(context);
        Map<String, Set<String>> collections = new LinkedHashMap<>();
        Map<String, Path> collectionSources = new HashMap<>();
        List<WrapSource> wrapSources = new ArrayList<>();
        Map<String, Path> wrapIds = new LinkedHashMap<>();

        if (config != null) {
            validateMainConfig(context, config);
            if (config.getLegacyWraps().isEnabled()) {
                addCollections(context, dataFolder.resolve("config.yml"), config.getCollections(), collections, collectionSources);
                if (config.getItems() == null) {
                    context.error(dataFolder.resolve("config.yml"), "invalid-section", Map.of("section", "items"));
                } else {
                    wrapSources.add(new WrapSource(dataFolder.resolve("config.yml"), config.getItems()));
                }
                loadCollectionFiles(context, collections, collectionSources);
                loadWrapFiles(context, wrapSources);
                for (WrapSource source : wrapSources) {
                    validateWrapSource(context, source, collections, wrapIds);
                }
            }
        }

        Set<String> rarityIds = loadRarities(context);
        Set<String> categoryIds = loadCategories(context);
        Set<String> skinCollectionIds = loadSkinCollections(context, categoryIds);
        Set<String> skinIds = validateSkinFiles(context, rarityIds, categoryIds, skinCollectionIds, wrapIds);
        validateItemSkinMenu(context, categoryIds);
        validateShopFiles(context, skinIds, categoryIds);
        Map<String, Set<String>> languageKeys = loadLanguages(context);
        validateLanguageReferences(context, languageKeys, config);

        List<Issue> issues = context.issues.stream()
                .sorted(Comparator.comparing((Issue issue) -> issue.severity().ordinal())
                        .thenComparing(Issue::path).thenComparing(Issue::key))
                .toList();
        return new Report(context.filesChecked, issues);
    }

    private Config loadConfig(Context context) {
        Path path = dataFolder.resolve("config.yml");
        ConfigurationNode node = readYaml(context, path, true);
        return node == null ? null : deserialize(context, path, node, Config.class);
    }

    private void validateMainConfig(Context context, Config config) {
        Path path = dataFolder.resolve("config.yml");
        if (config.getLegacyWraps().isEnabled()) {
            if (config.getCollections() == null) context.error(path, "invalid-section", Map.of("section", "collections"));
            if (config.getItems() == null) context.error(path, "invalid-section", Map.of("section", "items"));
        }
        if (config.getLanguage() == null || normalizeLocale(config.getLanguage().getDefaultLanguage()).isBlank()) {
            context.warning(path, "invalid-default-language", Map.of());
        }
    }

    private void loadCollectionFiles(Context context, Map<String, Set<String>> collections,
                                    Map<String, Path> collectionSources) {
        Path directory = dataFolder.resolve("collections");
        for (Path path : yamlFiles(context, directory, 10, "collections-directory-missing")) {
            ConfigurationNode node = readYaml(context, path, false);
            if (node == null) continue;
            CollectionFile collectionFile = deserialize(context, path, node, CollectionFile.class);
            if (collectionFile == null || !collectionFile.isEnabled()) continue;
            addCollections(context, path, collectionFile.getCollections(), collections, collectionSources);
        }
    }

    private void addCollections(Context context, Path path, Map<String, List<String>> definitions,
                                Map<String, Set<String>> collections, Map<String, Path> collectionSources) {
        if (definitions == null) {
            context.error(path, "invalid-section", Map.of("section", "collections"));
            return;
        }
        for (Map.Entry<String, List<String>> entry : definitions.entrySet()) {
            String id = entry.getKey();
            if (id == null || id.isBlank()) {
                context.error(path, "empty-collection-id", Map.of());
                continue;
            }
            Path previous = collectionSources.put(id, path);
            if (previous != null) {
                context.warning(path, "duplicate-collection-id", Map.of("id", id, "first", relative(previous)));
            }
            List<String> materials = entry.getValue();
            if (materials == null) {
                context.error(path, "invalid-collection-materials", Map.of("id", id));
                continue;
            }
            Set<String> validMaterials = new LinkedHashSet<>();
            for (String materialName : materials) {
                if (materialName == null || materialName.isBlank() || !isMaterial(materialName.trim())) {
                    context.error(path, "unknown-collection-material", Map.of("id", id, "material", String.valueOf(materialName)));
                } else {
                    validMaterials.add(materialName.trim().toUpperCase(Locale.ROOT));
                }
            }
            collections.put(id, validMaterials);
        }
    }

    private void loadWrapFiles(Context context, List<WrapSource> sources) {
        Path directory = dataFolder.resolve("wraps");
        for (Path path : yamlFiles(context, directory, 10, "wraps-directory-missing")) {
            ConfigurationNode node = readYaml(context, path, false);
            if (node == null) continue;
            WrapFile wrapFile = deserialize(context, path, node, WrapFile.class);
            if (wrapFile == null || !wrapFile.isEnabled()) continue;
            if (wrapFile.getItems() == null) {
                context.error(path, "invalid-section", Map.of("section", "items"));
            } else {
                sources.add(new WrapSource(path, wrapFile.getItems()));
            }
        }
    }

    private void validateWrapSource(Context context, WrapSource source,
                                    Map<String, Set<String>> collections, Map<String, Path> wrapIds) {
        if (source.items() == null) {
            context.error(source.path(), "invalid-section", Map.of("section", "items"));
            return;
        }
        for (Map.Entry<String, WrappableItem> typeEntry : source.items().entrySet()) {
            String type = typeEntry.getKey();
            if (type == null || type.isBlank()) {
                context.error(source.path(), "empty-wrap-type", Map.of());
                continue;
            }
            if (!isKnownWrapType(type, collections)) {
                context.error(source.path(), "unknown-wrap-type", Map.of("type", type));
            }
            WrappableItem wrappable = typeEntry.getValue();
            if (wrappable == null || wrappable.getWraps() == null) {
                context.error(source.path(), "invalid-wrap-list", Map.of("type", type));
                continue;
            }
            for (Map.Entry<String, Wrap> wrapEntry : wrappable.getWraps().entrySet()) {
                String mapId = wrapEntry.getKey();
                Wrap wrap = wrapEntry.getValue();
                if (wrap == null) {
                    context.error(source.path(), "invalid-wrap", Map.of("type", type, "id", String.valueOf(mapId)));
                    continue;
                }
                String id = wrap.getUuid();
                if (id == null || id.isBlank()) {
                    if (mapId != null && mapId.length() >= 3) {
                        id = mapId;
                    } else {
                        context.error(source.path(), "missing-wrap-id", Map.of("type", type, "id", String.valueOf(mapId)));
                        continue;
                    }
                }
                if (id.equals("-")) {
                    context.error(source.path(), "reserved-wrap-id", Map.of("id", id));
                }
                Path previous = wrapIds.putIfAbsent(id, source.path());
                if (previous != null) {
                    context.error(source.path(), "duplicate-wrap-id", Map.of("id", id, "first", relative(previous)));
                }
            }
        }
    }

    private boolean isKnownWrapType(String configuredType, Map<String, Set<String>> collections) {
        String type = configuredType.trim();
        if (collections.containsKey(type)) return true;
        String[] materials = type.split(",", -1);
        if (materials.length == 0) return false;
        for (String material : materials) {
            if (material.isBlank() || !isMaterial(material.trim())) return false;
        }
        return true;
    }

    private Set<String> loadRarities(Context context) {
        Path path = dataFolder.resolve("rarities.yml");
        ConfigurationNode node = readYaml(context, path, true);
        if (node == null) return Set.of();
        RarityConfiguration configuration = deserialize(context, path, node, RarityConfiguration.class);
        if (configuration == null) return Set.of();
        Map<String, RarityConfiguration.Entry> rarities = configuration.getRarities();
        Set<String> ids = new LinkedHashSet<>();
        if (configuration.isEnabled()) {
            for (Map.Entry<String, RarityConfiguration.Entry> entry : rarities.entrySet()) {
                String id = normalize(entry.getKey());
                RarityConfiguration.Entry value = entry.getValue();
                if (id.isBlank() || value == null || value.getDisplayNameKey() == null || value.getDisplayNameKey().isBlank()) {
                    context.error(path, "invalid-rarity", Map.of("id", String.valueOf(entry.getKey())));
                    continue;
                }
                if (!ids.add(id)) context.error(path, "duplicate-rarity-id", Map.of("id", entry.getKey()));
                context.directTranslationReferences.add(new TranslationReference(path, value.getDisplayNameKey(), null));
            }
        } else {
            context.error(path, "rarities-disabled", Map.of());
        }
        if (configuration.isEnabled() && ids.isEmpty()) context.error(path, "no-valid-rarities", Map.of());
        return Set.copyOf(ids);
    }

    private Set<String> loadCategories(Context context) {
        Path path = dataFolder.resolve("categories.yml");
        ConfigurationNode node = readYaml(context, path, true);
        if (node == null) return Set.of();
        CategoryConfiguration configuration = deserialize(context, path, node, CategoryConfiguration.class);
        if (configuration == null) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        if (configuration.isEnabled()) {
            for (Map.Entry<String, CategoryConfiguration.Entry> entry : configuration.getCategories().entrySet()) {
                String id = normalize(entry.getKey());
                CategoryConfiguration.Entry value = entry.getValue();
                if (id.isBlank() || value == null || value.getDisplayNameKey() == null || value.getDisplayNameKey().isBlank()) {
                    context.error(path, "invalid-category", Map.of("id", String.valueOf(entry.getKey())));
                    continue;
                }
                if (!ids.add(id)) context.error(path, "duplicate-category-id", Map.of("id", entry.getKey()));
                context.directTranslationReferences.add(new TranslationReference(path, value.getDisplayNameKey(), null));
                validateIcon(context, path, "category " + id, value.getIcon());
            }
        } else {
            context.warning(path, "categories-disabled", Map.of());
        }
        return Set.copyOf(ids);
    }

    private Set<String> loadSkinCollections(Context context, Set<String> categoryIds) {
        Path path = dataFolder.resolve("skin-collections.yml");
        ConfigurationNode node = readYaml(context, path, true);
        if (node == null) return Set.of();
        SkinCollectionConfiguration configuration = deserialize(context, path, node, SkinCollectionConfiguration.class);
        if (configuration == null || !configuration.isEnabled()) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (Map.Entry<String, SkinCollectionConfiguration.Entry> entry : configuration.getCollections().entrySet()) {
            String id = normalize(entry.getKey());
            SkinCollectionConfiguration.Entry value = entry.getValue();
            if (id.isBlank() || value == null || value.getDisplayNameKey() == null || value.getDisplayNameKey().isBlank()) {
                context.error(path, "invalid-skin-collection", Map.of("id", String.valueOf(entry.getKey())));
                continue;
            }
            if (!ids.add(id)) context.error(path, "duplicate-skin-collection", Map.of("id", id));
            context.directTranslationReferences.add(new TranslationReference(path, value.getDisplayNameKey(), null));
            validateIcon(context, path, "skin collection " + id, value.getIcon());
            for (String category : value.getCategories()) {
                if (category != null && !category.isBlank() && !categoryIds.contains(normalize(category))) {
                    context.warning(path, "unknown-skin-collection-category", Map.of("id", id, "category", category));
                }
            }
        }
        return Set.copyOf(ids);
    }

    private Set<String> validateSkinFiles(Context context, Set<String> rarityIds, Set<String> categoryIds,
                                          Set<String> skinCollectionIds, Map<String, Path> wrapIds) {
        Path directory = dataFolder.resolve("skins");
        Map<String, Path> skinIds = new HashMap<>();
        Map<String, Path> cosmeticIds = new HashMap<>(wrapIds);
        List<Path> paths = yamlFiles(context, directory, 8, "skins-directory-missing");
        for (Path path : paths) {
            if (path.getFileName().toString().equalsIgnoreCase("README.yml")) continue;
            ConfigurationNode node = readYaml(context, path, false);
            if (node == null) continue;
            SkinFile file = deserialize(context, path, node, SkinFile.class);
            if (file == null || !file.isEnabled()) continue;

            String id = normalize(file.getId());
            if (id.isBlank()) {
                context.error(path, "missing-skin-id", Map.of());
                continue;
            }
            Path previousSkin = skinIds.putIfAbsent(id, path);
            if (previousSkin != null) context.error(path, "duplicate-skin-id", Map.of("id", id, "first", relative(previousSkin)));
            if (file.getDisplayName() == null || file.getDisplayName().isBlank()) {
                context.error(path, "missing-skin-name", Map.of("id", id));
            }
            String rarity = normalize(file.getRarity());
            if (!rarityIds.contains(rarity)) context.error(path, "unknown-rarity", Map.of("id", id, "rarity", rarity));
            for (String category : file.getCategories()) {
                if (category != null && !category.isBlank() && !categoryIds.contains(normalize(category))) {
                    context.warning(path, "unknown-category", Map.of("id", id, "category", category));
                }
            }
            if (file.getCollection() != null && !file.getCollection().isBlank()
                    && !skinCollectionIds.contains(normalize(file.getCollection()))) {
                context.warning(path, "unknown-skin-collection-reference", Map.of("id", id, "collection", file.getCollection()));
            }

            SkinCompatibilityConfiguration compatibility = file.getCompatibility();
            List<String> compatibleItems = compatibility.getItems().stream()
                    .filter(value -> value != null && !value.isBlank()).toList();
            boolean validMaterial = false;
            for (String materialName : compatibility.getMaterials()) {
                if (materialName == null || materialName.isBlank() || !isMaterial(materialName.trim())) {
                    context.warning(path, "unknown-skin-material", Map.of("id", id, "material", String.valueOf(materialName)));
                } else {
                    validMaterial = true;
                }
            }
            if (!validMaterial && compatibleItems.isEmpty()) {
                context.error(path, "missing-compatibility", Map.of("id", id));
            }

            validateIcon(context, path, "skin " + id, file.getIcon());
            validatePrice(context, path, id, file.getPrice());

            Wrap cosmetic = file.getCosmetic();
            String cosmeticId = cosmetic.getUuid();
            if (cosmeticId == null || cosmeticId.isBlank()) cosmeticId = "yhm-skin:" + id;
            Path previousCosmetic = cosmeticIds.putIfAbsent(cosmeticId, path);
            if (previousCosmetic != null) {
                context.error(path, "duplicate-cosmetic-id", Map.of("id", id, "uuid", cosmeticId,
                        "first", relative(previousCosmetic)));
            }
        }
        return Set.copyOf(skinIds.keySet());
    }

    /** Validates {@code shops.yml} and {@code coupons.yml} against the catalog and each other. */
    private void validateShopFiles(Context context, Set<String> skinIds, Set<String> categoryIds) {
        Path shopsPath = dataFolder.resolve("shops.yml");
        ConfigurationNode shopsNode = readYaml(context, shopsPath, false);
        Set<String> bundleIds = new LinkedHashSet<>();
        if (shopsNode != null) {
            ShopFile shops = deserialize(context, shopsPath, shopsNode, ShopFile.class);
            if (shops != null) {
                Map<String, BundleConfiguration> bundles = shops.getBundles();
                for (Map.Entry<String, BundleConfiguration> entry : bundles == null ? Map.<String, BundleConfiguration>of().entrySet() : bundles.entrySet()) {
                    BundleConfiguration bundle = entry.getValue();
                    String id = normalize(entry.getKey());
                    if (bundle == null || !bundle.isEnabled()) continue;
                    if (id.isBlank()) {
                        context.error(shopsPath, "empty-bundle-id", Map.of());
                        continue;
                    }
                    bundleIds.add(id);
                    if (bundle.getSkins().isEmpty()) {
                        context.error(shopsPath, "empty-bundle", Map.of("id", id));
                    }
                    for (String skinId : bundle.getSkins()) {
                        if (!skinIds.contains(normalize(skinId))) {
                            context.error(shopsPath, "unknown-bundle-skin",
                                    Map.of("id", id, "skin", String.valueOf(skinId)));
                        }
                    }
                    if (bundle.getDiscount() < 0 || bundle.getDiscount() > 100) {
                        context.error(shopsPath, "invalid-bundle-discount", Map.of("id", id));
                    }
                    validatePrice(context, shopsPath, "bundle:" + id, bundle.getPrice());
                    String mode = normalize(bundle.getPurchaseMode());
                    if (!Set.of("full", "missing-only", "both").contains(mode)) {
                        context.warning(shopsPath, "unknown-purchase-mode",
                                Map.of("id", id, "mode", String.valueOf(bundle.getPurchaseMode())));
                    }
                }
                if (shops.getDailyShop() != null && shops.getDailyShop().isEnabled()) {
                    validateEntries(context, shopsPath, "daily shop pool", shops.getDailyShop().getPool(), skinIds,
                            bundleIds);
                    validatePrice(context, shopsPath, "daily-shop", shops.getDailyShop().getPrice());
                    String resetTime = shops.getDailyShop().getResetTime();
                    if (resetTime != null && !resetTime.isBlank() && !resetTime.trim().matches("\\d{1,2}:\\d{2}")) {
                        context.warning(shopsPath, "invalid-reset-time", Map.of("value", resetTime));
                    }
                }
                if (shops.getFeatured() != null && shops.getFeatured().isEnabled()) {
                    validateEntries(context, shopsPath, "featured entries", shops.getFeatured().getEntries(), skinIds,
                            bundleIds);
                    validateEntries(context, shopsPath, "featured pool", shops.getFeatured().getAutomaticPool(), skinIds,
                            bundleIds);
                    validatePrice(context, shopsPath, "featured", shops.getFeatured().getPrice());
                }
                Map<String, EventShopConfiguration> events = shops.getEvents();
                Map<String, Path> eventIds = new LinkedHashMap<>();
                for (Map.Entry<String, EventShopConfiguration> entry : events == null ? Map.<String, EventShopConfiguration>of().entrySet() : events.entrySet()) {
                    EventShopConfiguration event = entry.getValue();
                    String id = normalize(entry.getKey());
                    if (event == null || !event.isEnabled()) continue;
                    if (id.isBlank()) {
                        context.error(shopsPath, "empty-event-id", Map.of());
                        continue;
                    }
                    Path previous = eventIds.putIfAbsent(id, shopsPath);
                    if (previous != null) {
                        context.error(shopsPath, "duplicate-event-id", Map.of("id", id));
                    }
                    if (event.getName() == null || event.getName().isBlank()) {
                        context.warning(shopsPath, "missing-event-name", Map.of("id", id));
                    }
                    Instant start = parseInstant(event.getStart());
                    Instant end = parseInstant(event.getEnd());
                    if (start == null || end == null) {
                        context.error(shopsPath, "invalid-event-window",
                                Map.of("id", id, "start", String.valueOf(event.getStart()), "end", String.valueOf(event.getEnd())));
                    } else if (!end.isAfter(start)) {
                        context.error(shopsPath, "invalid-event-window",
                                Map.of("id", id, "start", String.valueOf(event.getStart()), "end", String.valueOf(event.getEnd())));
                    }
                    if (event.getEntries().isEmpty()) {
                        context.warning(shopsPath, "empty-event", Map.of("id", id));
                    }
                    validateEntries(context, shopsPath, "event " + id, event.getEntries(), skinIds, bundleIds);
                    validatePrice(context, shopsPath, "event:" + id, event.getPrice());
                }
            }
        }

        Path couponsPath = dataFolder.resolve("coupons.yml");
        ConfigurationNode couponsNode = readYaml(context, couponsPath, false);
        if (couponsNode == null) return;
        CouponFile coupons = deserialize(context, couponsPath, couponsNode, CouponFile.class);
        if (coupons == null) return;
        Set<String> codes = new LinkedHashSet<>();
        Map<String, CouponConfiguration> configured = coupons.getCoupons();
        for (Map.Entry<String, CouponConfiguration> entry : configured == null ? Map.<String, CouponConfiguration>of().entrySet() : configured.entrySet()) {
            CouponConfiguration coupon = entry.getValue();
            String code = entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (coupon == null) continue;
            if (code.isBlank()) {
                context.error(couponsPath, "empty-coupon-code", Map.of());
                continue;
            }
            if (!codes.add(code)) {
                context.error(couponsPath, "duplicate-coupon-code", Map.of("code", code));
            }
            String type = normalize(coupon.getType());
            Double value = coupon.getValue();
            if (!type.equals("percentage") && !type.equals("fixed")) {
                context.error(couponsPath, "unknown-coupon-type", Map.of("code", code, "type", String.valueOf(coupon.getType())));
            } else if (value == null || !Double.isFinite(value) || value <= 0
                    || (type.equals("percentage") && value > 100)) {
                context.error(couponsPath, "invalid-coupon-value", Map.of("code", code));
            }
            if (coupon.getMinSpend() != null && (!Double.isFinite(coupon.getMinSpend()) || coupon.getMinSpend() < 0)) {
                context.error(couponsPath, "invalid-coupon-limit", Map.of("code", code, "field", "min-spend"));
            }
            if (coupon.getMaxUses() != null && coupon.getMaxUses() != -1 && coupon.getMaxUses() < 1) {
                context.error(couponsPath, "invalid-coupon-limit", Map.of("code", code, "field", "max-uses"));
            }
            if (coupon.getMaxUsesPerPlayer() != null && coupon.getMaxUsesPerPlayer() != -1 && coupon.getMaxUsesPerPlayer() < 1) {
                context.error(couponsPath, "invalid-coupon-limit", Map.of("code", code, "field", "max-uses-per-player"));
            }
            if (coupon.getExpires() != null && !coupon.getExpires().isBlank() && parseInstant(coupon.getExpires()) == null) {
                context.error(couponsPath, "invalid-coupon-expiry",
                        Map.of("code", code, "date", coupon.getExpires()));
            }
            for (String skinId : coupon.getSkins()) {
                if (!skinIds.contains(normalize(skinId))) {
                    context.error(couponsPath, "unknown-coupon-skin", Map.of("code", code, "skin", String.valueOf(skinId)));
                }
            }
            for (String bundleId : coupon.getBundles()) {
                if (!bundleIds.contains(normalize(bundleId))) {
                    context.error(couponsPath, "unknown-coupon-bundle", Map.of("code", code, "bundle", String.valueOf(bundleId)));
                }
            }
            for (String categoryId : coupon.getCategories()) {
                if (!categoryIds.contains(normalize(categoryId))) {
                    context.error(couponsPath, "unknown-coupon-category",
                            Map.of("code", code, "category", String.valueOf(categoryId)));
                }
            }
        }
    }

    private void validateEntries(Context context, Path path, String section, List<String> entries,
                                 Set<String> skinIds, Set<String> bundleIds) {
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.trim();
            String type = "skin";
            if (value.contains(":")) {
                String[] parts = value.split(":", 2);
                type = normalize(parts[0]);
                value = parts[1];
            }
            String id = normalize(value);
            if (type.equals("bundle")) {
                if (!bundleIds.contains(id)) {
                    context.error(path, "unknown-shop-bundle", Map.of("section", section, "bundle", id));
                }
            } else if (type.equals("skin")) {
                if (!skinIds.contains(id)) {
                    context.error(path, "unknown-shop-skin", Map.of("section", section, "skin", id));
                }
            } else {
                context.warning(path, "unknown-shop-entry-type", Map.of("section", section, "type", type));
            }
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Fall through to the other supported formats.
        }
        try {
            return ZonedDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // Fall through to the date-only format.
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Validates a {@code shops.yml} price: provider, currency and a positive finite amount. */
    private void validatePrice(Context context, Path path, String ownerId,
                               de.skyslycer.hmcwraps.shop.config.ShopPriceConfiguration price) {
        if (price == null || price.getAmount() == null) return;
        Double amount = price.getAmount();
        String provider = price.getProvider();
        String currency = price.getCurrency();
        if (!Double.isFinite(amount) || amount <= 0 || provider == null || provider.isBlank()
                || currency == null || currency.isBlank()) {
            context.error(path, "invalid-price", Map.of("id", ownerId));
            return;
        }
        if (normalize(provider).equals("auto")) return;
        EconomyProvider economyProvider = economyProviders.get(normalizeProvider(provider));
        if (economyProvider == null) {
            context.warning(path, "provider-unavailable", Map.of("id", ownerId, "provider", provider));
            return;
        }
        if (!economyProvider.supportsCurrency(currency)) {
            context.warning(path, "unsupported-currency",
                    Map.of("id", ownerId, "provider", provider, "currency", currency));
        }
    }

    private void validatePrice(Context context, Path path, String skinId, SkinPriceConfiguration price) {
        if (price == null || !price.isConfigured()) return;
        Double amount = price.getAmount();
        if (amount == null || !Double.isFinite(amount) || amount <= 0
                || price.getProvider().isBlank() || price.getCurrency().isBlank()) {
            context.error(path, "invalid-price", Map.of("id", skinId));
            return;
        }
        EconomyProvider provider = economyProviders.get(normalizeProvider(price.getProvider()));
        if (provider == null) {
            context.warning(path, "provider-unavailable", Map.of("id", skinId, "provider", price.getProvider()));
            return;
        }
        try {
            if (!provider.isAvailable()) {
                context.warning(path, "provider-unavailable", Map.of("id", skinId, "provider", price.getProvider()));
            } else if (!provider.supportsCurrency(price.getCurrency())) {
                context.error(path, "unsupported-currency", Map.of("id", skinId, "currency", price.getCurrency(),
                        "provider", price.getProvider()));
            }
        } catch (LinkageError | RuntimeException exception) {
            context.warning(path, "provider-check-failed", Map.of("provider", price.getProvider(),
                    "reason", safeMessage(exception)));
        }
    }

    private void validateIcon(Context context, Path path, String owner, SkinIconConfiguration icon) {
        if (icon == null) return;
        String type = normalize(icon.getType());
        String id = icon.getId();
        if (!ICON_TYPES.contains(type)) {
            context.error(path, "unknown-icon-type", Map.of("owner", owner, "type", type));
        }
        if ((type.equals("material") || type.equals("vanilla"))
                && (id == null || id.isBlank() || !isMaterial(id.trim()))) {
            context.error(path, "unknown-icon-material", Map.of("owner", owner, "id", String.valueOf(id)));
        }
        if (icon.getModelId() != null && icon.getModelId() < 0) {
            context.warning(path, "negative-model-id", Map.of("owner", owner));
        }
        validateNamespacedKey(context, path, owner, "item-model", icon.getItemModel());
        validateNamespacedKey(context, path, owner, "tooltip-style", icon.getTooltipStyle());
    }

    private void validateNamespacedKey(Context context, Path path, String owner, String field, String value) {
        if (value == null || value.isBlank()) return;
        if (!isNamespacedKey(value)) {
            context.warning(path, "invalid-namespaced-key", Map.of("owner", owner, "field", field, "value", value));
        }
    }

    private void validateItemSkinMenu(Context context, Set<String> categoryIds) {
        Path path = dataFolder.resolve("itemskin-gui.yml");
        ConfigurationNode root = readYaml(context, path, true);
        if (root == null) return;
        ConfigurationNode guiNode = root.node("gui");
        if (guiNode.virtual()) {
            context.error(path, "missing-gui-section", Map.of());
            return;
        }
        SkinMenuConfiguration menu = deserialize(context, path, guiNode, SkinMenuConfiguration.class);
        if (menu == null) return;

        int size = menu.getSize();
        boolean validSize = size >= 9 && size <= 54 && size % 9 == 0;
        if (!validSize) context.error(path, "invalid-gui-size", Map.of("size", String.valueOf(size)));
        validateIcon(context, path, "GUI filler", menu.getFiller());
        validateIcon(context, path, "GUI previous button", menu.getPrevious().getItem());
        validateIcon(context, path, "GUI next button", menu.getNext().getItem());
        validateIcon(context, path, "GUI close button", menu.getClose().getItem());
        validateIcon(context, path, "GUI sort button", menu.getSort().getItem());
        validateIcon(context, path, "GUI filter button", menu.getFilter().getItem());
        validateIcon(context, path, "GUI search button", menu.getSearch().getItem());
        validateIcon(context, path, "GUI collection button", menu.getCollection().getItem());
        validateIcon(context, path, "GUI unskin button", menu.getUnskin().getItem());
        validateIcon(context, path, "GUI shop button", menu.getShop().getItem());

        Map<Integer, String> occupied = new HashMap<>();
        if (menu.isItemEnabled()) addGuiSlot(context, path, occupied, "target item", menu.getItemSlot(), size, validSize, true);
        if (menu.getPrevious().isEnabled()) addGuiSlot(context, path, occupied, "previous button", menu.getPrevious().getSlot(), size, validSize, true);
        if (menu.getNext().isEnabled()) addGuiSlot(context, path, occupied, "next button", menu.getNext().getSlot(), size, validSize, true);
        if (menu.getClose().isEnabled()) addGuiSlot(context, path, occupied, "close button", menu.getClose().getSlot(), size, validSize, true);
        if (menu.getSort().isEnabled()) addGuiSlot(context, path, occupied, "sort button", menu.getSort().getSlot(), size, validSize, true);
        if (menu.getFilter().isEnabled()) addGuiSlot(context, path, occupied, "filter button", menu.getFilter().getSlot(), size, validSize, true);
        if (menu.getSearch().isEnabled()) addGuiSlot(context, path, occupied, "search button", menu.getSearch().getSlot(), size, validSize, true);
        if (menu.getCollection().isEnabled()) addGuiSlot(context, path, occupied, "collection button", menu.getCollection().getSlot(), size, validSize, true);
        if (menu.getUnskin().isEnabled()) addGuiSlot(context, path, occupied, "unskin button", menu.getUnskin().getSlot(), size, validSize, true);
        if (menu.getShop().isEnabled()) addGuiSlot(context, path, occupied, "shop button", menu.getShop().getSlot(), size, validSize, true);
        Set<Integer> contentSlots = new HashSet<>();
        for (Integer slot : menu.getContentSlots()) {
            if (slot == null) continue;
            if (!contentSlots.add(slot)) context.warning(path, "duplicate-gui-slot", Map.of("slot", String.valueOf(slot)));
            addGuiSlot(context, path, occupied, "content", slot, size, validSize, false);
        }
        for (Map.Entry<String, SkinMenuConfiguration.Button> entry : menu.getCategories().entrySet()) {
            String category = entry.getKey();
            SkinMenuConfiguration.Button button = entry.getValue();
            if (category == null || button == null) {
                context.error(path, "invalid-category-slot", Map.of("category", String.valueOf(category)));
                continue;
            }
            validateIcon(context, path, "GUI category " + category, button.getItem());
            if (!categoryIds.contains(normalize(category))) {
                context.warning(path, "unknown-menu-category", Map.of("category", category));
            }
            if (button.isEnabled()) addGuiSlot(context, path, occupied, "category " + category,
                    button.getSlot(), size, validSize, false);
        }
        // Legacy slot-only category definitions remain readable for existing installations.
        for (Map.Entry<String, Integer> entry : menu.getCategorySlots().entrySet()) {
            String category = entry.getKey();
            Integer slot = entry.getValue();
            if (category == null || slot == null) {
                context.error(path, "invalid-category-slot", Map.of("category", String.valueOf(category)));
                continue;
            }
            if (!categoryIds.contains(normalize(category))) {
                context.warning(path, "unknown-menu-category", Map.of("category", category));
            }
            addGuiSlot(context, path, occupied, "category " + category, slot, size, validSize, false);
        }

        List<String> sorts = configuredOptions(root, "sorting", "options", List.of("rarity", "name", "price", "owned", "category"), context, path);
        List<String> filters = configuredOptions(root, "filters", "options", List.of("all", "owned", "unowned", "purchasable", "free"), context, path);
        for (String sort : sorts) if (!SORT_OPTIONS.contains(normalize(sort))) context.warning(path, "unknown-sort-option", Map.of("value", sort));
        for (String filter : filters) if (!FILTER_OPTIONS.contains(normalize(filter))) context.warning(path, "unknown-filter-option", Map.of("value", filter));
        if (sorts.stream().map(ConfigurationValidator::normalize).noneMatch(normalize(menu.getDefaultSort())::equals)) {
            context.warning(path, "unknown-default-sort", Map.of("value", menu.getDefaultSort()));
        }
        if (!Set.of("ascending", "descending").contains(normalize(menu.getDefaultSortOrder()))) {
            context.warning(path, "unknown-sort-order", Map.of("value", menu.getDefaultSortOrder()));
        }
        for (Map.Entry<String, String> action : menu.getClickActions().entrySet()) {
            if (action.getKey() == null || action.getValue() == null || !CLICK_ACTIONS.contains(normalize(action.getValue()))) {
                context.warning(path, "unknown-click-action", Map.of("action", String.valueOf(action.getValue())));
            }
        }
        if (!menu.isShowLocked()) context.warning(path, "show-locked-ignored", Map.of());
    }

    private List<String> configuredOptions(ConfigurationNode root, String section, String key,
                                           List<String> fallback, Context context, Path path) {
        ConfigurationNode node = root.node(section, key);
        if (node.virtual()) return fallback;
        try {
            List<String> options = node.getList(String.class);
            return options == null || options.isEmpty() ? fallback : options.stream().filter(value -> value != null).toList();
        } catch (Exception exception) {
            context.error(path, "invalid-gui-options", Map.of("section", section + "." + key,
                    "reason", safeMessage(exception)));
            return fallback;
        }
    }

    private void addGuiSlot(Context context, Path path, Map<Integer, String> occupied, String owner,
                            int slot, int size, boolean validSize, boolean allowDisabled) {
        if (allowDisabled && slot < 0) return;
        if (!validSize) return;
        if (slot < 0 || slot >= size) {
            context.error(path, "invalid-gui-slot", Map.of("owner", owner, "slot", String.valueOf(slot),
                    "size", String.valueOf(size)));
            return;
        }
        String previous = occupied.putIfAbsent(slot, owner);
        if (previous != null) {
            context.warning(path, "overlapping-gui-slot", Map.of("slot", String.valueOf(slot),
                    "first", previous, "second", owner));
        }
    }

    private Map<String, Set<String>> loadLanguages(Context context) {
        Path directory = dataFolder.resolve("lang");
        Map<String, Set<String>> languages = new LinkedHashMap<>();
        Map<String, Path> languageSources = new HashMap<>();
        for (Path path : yamlFiles(context, directory, 1, "language-directory-missing")) {
            ConfigurationNode node = readYaml(context, path, false);
            if (node == null) continue;
            String fileName = path.getFileName().toString();
            String locale = normalize(stripExtension(fileName));
            if (!locale.matches("[a-z]{2,3}")) {
                context.warning(path, "invalid-locale-filename", Map.of("locale", locale));
            }
            if (locale.isBlank()) continue;
            Path previous = languageSources.putIfAbsent(locale, path);
            if (previous != null) context.warning(path, "duplicate-locale-file", Map.of("locale", locale,
                    "first", relative(previous)));
            Set<String> keys = new LinkedHashSet<>();
            collectTranslationKeys(node, "", keys);
            languages.put(locale, Set.copyOf(keys));

            Set<String> builtInKeys = builtInLocaleKeys(locale);
            if (builtInKeys != null) {
                for (String missing : builtInKeys) {
                    if (!keys.contains(missing)) {
                        context.warning(path, "missing-translation", Map.of("locale", locale, "key", missing));
                    }
                }
            }
        }
        if (languages.isEmpty()) context.warning(directory, "no-language-files", Map.of());
        return Map.copyOf(languages);
    }

    private Set<String> builtInLocaleKeys(String locale) {
        if (!BUILT_IN_LOCALES.contains(locale)) return null;
        String resource = "lang/" + locale + ".yml";
        try (InputStream stream = ConfigurationValidator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) return null;
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Set<String> keys = new LinkedHashSet<>();
            collectBukkitTranslationKeys(yaml, "", keys);
            return keys;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void collectBukkitTranslationKeys(ConfigurationSection section, String prefix, Set<String> keys) {
        for (String childName : section.getKeys(false)) {
            String key = prefix.isEmpty() ? childName : prefix + "." + childName;
            Object value = section.get(childName);
            if (value instanceof ConfigurationSection child) {
                collectBukkitTranslationKeys(child, key, keys);
            } else if (value instanceof String) {
                keys.add(key);
            }
        }
    }

    private void collectTranslationKeys(ConfigurationNode node, String prefix, Set<String> keys) {
        if (node.raw() instanceof String) keys.add(prefix);
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String childName = String.valueOf(entry.getKey());
            String key = prefix.isEmpty() ? childName : prefix + "." + childName;
            collectTranslationKeys(entry.getValue(), key, keys);
        }
        List<? extends ConfigurationNode> children = node.childrenList();
        for (int index = 0; index < children.size(); index++) {
            collectTranslationKeys(children.get(index), prefix + "[" + index + "]", keys);
        }
    }

    private void validateLanguageReferences(Context context, Map<String, Set<String>> languageKeys, Config config) {
        String defaultLocale = "en";
        if (config != null && config.getLanguage() != null) defaultLocale = normalizeLocale(config.getLanguage().getDefaultLanguage());
        Set<String> defaultKeys = languageKeys.get(defaultLocale);
        if (defaultKeys == null) {
            context.warning(dataFolder.resolve("config.yml"), "default-language-file-missing", Map.of("locale", defaultLocale));
        }
        for (TranslationReference reference : context.directTranslationReferences) {
            if (defaultKeys == null || !defaultKeys.contains(reference.key())) {
                context.warning(reference.path(), "missing-translation", Map.of("locale", defaultLocale, "key", reference.key()));
            }
        }
        for (TranslationReference reference : context.tagReferences) {
            Set<String> fileLocaleKeys = reference.locale() == null ? null : languageKeys.get(reference.locale());
            boolean existsForFileLocale = fileLocaleKeys != null && fileLocaleKeys.contains(reference.key());
            boolean existsInDefault = defaultKeys != null && defaultKeys.contains(reference.key());
            if (!existsForFileLocale && !existsInDefault) {
                String locale = reference.locale() == null ? defaultLocale : reference.locale();
                context.warning(reference.path(), "missing-translation", Map.of("locale", locale, "key", reference.key()));
            }
        }
    }

    private ConfigurationNode readYaml(Context context, Path path, boolean required) {
        if (!Files.isRegularFile(path)) {
            if (required) context.error(path, "required-file-missing", Map.of());
            return null;
        }
        context.filesChecked++;
        try {
            ConfigurationNode node = YamlConfigurationLoader.builder()
                    .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
                    .path(path)
                    .build().load();
            collectLanguageReferences(context, path, node);
            return node;
        } catch (Exception exception) {
            context.error(path, "invalid-yaml", Map.of("reason", safeMessage(exception)));
            return null;
        }
    }

    private <T> T deserialize(Context context, Path path, ConfigurationNode node, Class<T> type) {
        try {
            T value = node.get(type);
            if (value == null) {
                context.error(path, "invalid-schema", Map.of("type", type.getSimpleName(), "reason", "empty or null document"));
            }
            return value;
        } catch (Exception exception) {
            context.error(path, "invalid-schema", Map.of("type", type.getSimpleName(), "reason", safeMessage(exception)));
            return null;
        }
    }

    private List<Path> yamlFiles(Context context, Path directory, int maxDepth, String missingKey) {
        if (!Files.exists(directory)) {
            context.warning(directory, missingKey, Map.of());
            return List.of();
        }
        if (!Files.isDirectory(directory)) {
            context.error(directory, "not-a-directory", Map.of());
            return List.of();
        }
        try (Stream<Path> paths = Files.find(directory, maxDepth,
                (path, attributes) -> attributes.isRegularFile() && isYaml(path))) {
            return paths.sorted().toList();
        } catch (IOException exception) {
            context.error(directory, "could-not-read-directory", Map.of("reason", safeMessage(exception)));
            return List.of();
        }
    }

    private void collectLanguageReferences(Context context, Path path, ConfigurationNode node) {
        collectLanguageReferences(context, path, node, "");
    }

    private void collectLanguageReferences(Context context, Path path, ConfigurationNode node, String location) {
        if (node.raw() instanceof String text) {
            Matcher matcher = LANGUAGE_REFERENCE.matcher(text);
            while (matcher.find()) {
                String kind = matcher.group(1).toLowerCase(Locale.ROOT);
                String key = matcher.group(2).trim();
                if (kind.equals("glyph")) key = "glyphs." + key;
                if (!key.isBlank()) context.tagReferences.add(new TranslationReference(path, key, localeForPath(path)));
            }
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String key = location.isEmpty() ? String.valueOf(entry.getKey()) : location + "." + entry.getKey();
            collectLanguageReferences(context, path, entry.getValue(), key);
        }
        List<? extends ConfigurationNode> children = node.childrenList();
        for (int index = 0; index < children.size(); index++) {
            collectLanguageReferences(context, path, children.get(index), location + "[" + index + "]");
        }
    }

    private String localeForPath(Path path) {
        Path langFolder = dataFolder.resolve("lang").normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(langFolder.toAbsolutePath().normalize())) return null;
        return normalize(stripExtension(normalized.getFileName().toString()));
    }

    private String relative(Path path) {
        try {
            return dataFolder.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException exception) {
            return path.toString().replace('\\', '/');
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(0, dot);
    }

    private static boolean isMaterial(String value) {
        try {
            return Material.matchMaterial(value) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isNamespacedKey(String value) {
        try {
            return NamespacedKey.fromString(value) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String normalizeProvider(String value) {
        return normalize(value).replace('-', '_');
    }

    private static String normalizeLocale(String value) {
        String normalized = normalize(value).replace('_', '-');
        return normalized.isBlank() ? "" : normalized.split("-", 2)[0];
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private final class Context {
        private final List<Issue> issues = new ArrayList<>();
        private final List<TranslationReference> tagReferences = new ArrayList<>();
        private final List<TranslationReference> directTranslationReferences = new ArrayList<>();
        private int filesChecked;

        private void error(Path path, String key, Map<String, String> arguments) {
            issues.add(new Issue(Severity.ERROR, relative(path), key, arguments));
        }

        private void warning(Path path, String key, Map<String, String> arguments) {
            issues.add(new Issue(Severity.WARNING, relative(path), key, arguments));
        }
    }

    private record WrapSource(Path path, Map<String, WrappableItem> items) { }
    private record TranslationReference(Path path, String key, String locale) { }

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String path, String key, Map<String, String> arguments) {
        public Issue {
            arguments = Map.copyOf(arguments);
        }
    }

    public record Report(int filesChecked, List<Issue> issues) {
        public Report {
            issues = List.copyOf(issues);
        }

        public long errorCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }

        public long warningCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        }

        public boolean isValid() { return errorCount() == 0; }
    }
}
