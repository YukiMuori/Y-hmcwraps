package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.shop.config.BundleConfiguration;
import de.skyslycer.hmcwraps.shop.config.CouponConfiguration;
import de.skyslycer.hmcwraps.shop.config.CouponFile;
import de.skyslycer.hmcwraps.shop.config.DailyShopConfiguration;
import de.skyslycer.hmcwraps.shop.config.EventShopConfiguration;
import de.skyslycer.hmcwraps.shop.config.FeaturedConfiguration;
import de.skyslycer.hmcwraps.shop.config.ShopFile;
import de.skyslycer.hmcwraps.shop.config.ShopPriceConfiguration;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.SkinCatalog;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.util.YamlFileStore;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loads, validates and publishes the shop definition files ({@code shops.yml} and {@code coupons.yml}).
 *
 * <p>The registry is the single source of truth for what the shop sells. Loading is fail-safe: an invalid
 * entry is skipped with a clear warning instead of breaking the whole shop, and the previously loaded
 * snapshot stays active when a reload fails.</p>
 */
public final class ShopRegistry {

    /** The default currency key used when a section does not configure one. */
    private final HMCWrapsPlugin plugin;
    private final SkinCatalog catalog;
    private final YamlFileStore shopsFile;
    private final YamlFileStore couponsFile;

    private volatile ShopFile shopFile = new ShopFile();
    private volatile CouponFile couponFile = new CouponFile();
    private volatile List<ShopEntry> featured = List.of();
    private volatile Map<String, Bundle> bundles = Map.of();
    private volatile List<EventShop> events = List.of();
    private volatile Map<String, Coupon> coupons = Map.of();
    private volatile List<String> dailyPool = List.of();
    private volatile boolean loaded;

    public ShopRegistry(@NotNull HMCWrapsPlugin plugin, @NotNull SkinCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.shopsFile = new YamlFileStore(HMCWraps.SHOPS_PATH, "shops.yml", () -> plugin.getResource("shops.yml"));
        this.couponsFile = new YamlFileStore(HMCWraps.COUPONS_PATH, "coupons.yml", () -> plugin.getResource("coupons.yml"));
    }

    /** The {@code shops.yml} store, used by the admin editor. */
    public @NotNull YamlFileStore shopsFile() {
        return shopsFile;
    }

    /** The {@code coupons.yml} store, used by the admin editor. */
    public @NotNull YamlFileStore couponsFile() {
        return couponsFile;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Loads both files, publishing new snapshots only when the files were readable. */
    public boolean load() {
        ShopFile nextShopFile;
        CouponFile nextCouponFile;
        try {
            nextShopFile = shopsFile.load().get(ShopFile.class);
            nextCouponFile = couponsFile.load().get(CouponFile.class);
        } catch (IOException | RuntimeException exception) {
            plugin.logSevere("Could not read the shop configuration files (shops.yml / coupons.yml).", exception);
            return false;
        }
        if (nextShopFile == null) {
            plugin.logSevere("shops.yml is empty; using the previously loaded shop definition.");
            return false;
        }
        String defaultCurrency = plugin.getConfiguration() == null
                ? "coins" : plugin.getConfiguration().getEconomy().getDefaultCurrency();

        Map<String, Bundle> loadedBundles = loadBundles(nextShopFile, defaultCurrency);
        List<ShopEntry> loadedFeatured = loadFeatured(nextShopFile, loadedBundles, defaultCurrency);
        List<EventShop> loadedEvents = loadEvents(nextShopFile, loadedBundles, defaultCurrency);
        List<String> pool = loadDailyPool(nextShopFile, loadedBundles);
        Map<String, Coupon> loadedCoupons = nextCouponFile == null ? Map.of() : loadCoupons(nextCouponFile);

        shopFile = nextShopFile;
        if (nextCouponFile != null) {
            couponFile = nextCouponFile;
        }
        bundles = loadedBundles;
        featured = loadedFeatured;
        events = loadedEvents;
        dailyPool = pool;
        coupons = loadedCoupons;
        loaded = true;
        plugin.getLogger().info("Loaded " + loadedBundles.size() + " bundles, " + loadedFeatured.size()
                + " featured entries, " + loadedEvents.size() + " event shops and " + loadedCoupons.size()
                + " coupons (" + pool.size() + " daily pool candidates).");
        if (!plugin.getEconomyService().isAvailable()) {
            plugin.getLogger().warning("No economy provider is available. The shop will display its prices but"
                    + " paid purchases are disabled until Vault or ExcellentEconomy is installed"
                    + " (or economy.provider is set correctly).");
        }
        return true;
    }

    /** Whether the shop is enabled in configuration. */
    public boolean isEnabled() {
        return shopFile.isEnabled() && (plugin.getConfiguration() == null || plugin.getConfiguration().getShop().isEnabled());
    }

    public @NotNull DailyShopConfiguration dailyConfiguration() {
        return shopFile.getDailyShop();
    }

    public @NotNull FeaturedConfiguration featuredConfiguration() {
        return shopFile.getFeatured();
    }

    public @NotNull List<ShopEntry> featured() {
        return featured;
    }

    public @NotNull Map<String, Bundle> bundles() {
        return bundles;
    }

    public @NotNull Collection<EventShop> events() {
        return events;
    }

    public @NotNull Map<String, Coupon> coupons() {
        return coupons;
    }

    /** The candidate skin ids of the daily rotation. */
    public @NotNull List<String> dailyPool() {
        return dailyPool;
    }

    public boolean couponsEnabled() {
        return couponFile.isEnabled();
    }

    public @NotNull Optional<Bundle> bundle(@NotNull String id) {
        return Optional.ofNullable(bundles.get(normalize(id)));
    }

    public @NotNull Optional<Coupon> coupon(@NotNull String code) {
        return Optional.ofNullable(coupons.get(code.toUpperCase(Locale.ROOT).trim()));
    }

    /** Applies a mutation to {@code shops.yml}, saves it and reloads the registry. */
    public boolean editShops(@NotNull Consumer<ConfigurationNode> mutation) {
        return edit(shopsFile, mutation);
    }

    /** Applies a mutation to {@code coupons.yml}, saves it and reloads the registry. */
    public boolean editCoupons(@NotNull Consumer<ConfigurationNode> mutation) {
        return edit(couponsFile, mutation);
    }

    private boolean edit(YamlFileStore store, Consumer<ConfigurationNode> mutation) {
        try {
            store.update(mutation);
        } catch (IOException | RuntimeException exception) {
            plugin.logSevere("Could not save " + store.resourceName() + ".", exception);
            return false;
        }
        return load();
    }

    private Map<String, Bundle> loadBundles(ShopFile file, String defaultCurrency) {
        Map<String, Bundle> result = new LinkedHashMap<>();
        file.getBundles().forEach((id, configuration) -> {
            if (id == null || id.isBlank() || configuration == null) {
                return;
            }
            String bundleId = normalize(id);
            if (!configuration.isEnabled()) {
                return;
            }
            SplitPrice price = price(configuration.getPrice(), defaultCurrency);
            if (price == null) {
                plugin.getLogger().warning("Skipping bundle '" + bundleId
                        + "': it needs a positive 'price.amount' and a currency.");
                return;
            }
            List<String> skins = new ArrayList<>();
            for (String skinId : configuration.getSkins()) {
                if (skinId == null || skinId.isBlank()) {
                    continue;
                }
                String normalized = normalize(skinId);
                if (catalog.skinMap().containsKey(normalized)) {
                    skins.add(normalized);
                } else {
                    plugin.getLogger().warning("Bundle '" + bundleId + "' references unknown skin '" + normalized + "'.");
                }
            }
            if (skins.isEmpty()) {
                plugin.getLogger().warning("Skipping bundle '" + bundleId + "': it contains no known skin.");
                return;
            }
            ItemStack icon = plugin.getItemIconFactory().create(configuration.getIcon(), configuration.getName());
            try {
                result.put(bundleId, new Bundle(bundleId, configuration.getName() == null
                        ? bundleId : configuration.getName(), skins, price.price(), configuration.getDiscount(),
                        BundlePurchaseMode.fromId(configuration.getPurchaseMode()), configuration.getPermission(), icon));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipping bundle '" + bundleId + "': " + exception.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    private List<ShopEntry> loadFeatured(ShopFile file, Map<String, Bundle> availableBundles, String defaultCurrency) {
        FeaturedConfiguration configuration = file.getFeatured();
        if (!configuration.isEnabled()) {
            return List.of();
        }
        SplitPrice price = price(configuration.getPrice(), defaultCurrency);
        List<ShopEntry> entries = new ArrayList<>();
        for (String raw : configuration.getEntries()) {
            ShopEntry entry = parseEntry(raw, price == null ? null : price.price(), configuration.getDiscount(),
                    availableBundles, "featured");
            if (entry != null) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty() && configuration.isAutomatic()) {
            Map<String, Integer> pool = new LinkedHashMap<>();
            for (String raw : configuration.getAutomaticPool()) {
                pool.put(normalize(raw), 0);
            }
            List<String> candidates = pool.isEmpty()
                    ? catalog.getSkins().stream().filter(skin -> skin.price() != null).map(ItemSkin::id).toList()
                    : pool.keySet().stream().filter(id -> catalog.skinMap().containsKey(id)).toList();
            String seed = configuration.isAutomaticRotate() ? currentCycleKey(file) : "static";
            List<String> selected = ShopRotation.rotate(candidates, "featured", seed, configuration.getAutomaticCount());
            for (String skinId : selected) {
                ShopEntry entry = parseEntry(skinId, price == null ? null : price.price(), configuration.getDiscount(),
                        availableBundles, "featured");
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return List.copyOf(entries);
    }

    /** The daily shop cycle key, used to rotate the automatic featured selection with the daily shop. */
    private String currentCycleKey(ShopFile file) {
        DailyShopConfiguration daily = file.getDailyShop();
        if (!daily.isEnabled() || ShopRotation.Interval.fromId(daily.getRefresh()) == ShopRotation.Interval.NEVER) {
            return "static";
        }
        Instant now = Instant.now();
        return ShopRotation.cycleKey(now, ShopRotation.parseZone(daily.getZone()),
                ShopRotation.Interval.fromId(daily.getRefresh()), ShopRotation.parseResetTime(daily.getResetTime()));
    }

    private List<EventShop> loadEvents(ShopFile file, Map<String, Bundle> availableBundles, String defaultCurrency) {
        List<EventShop> result = new ArrayList<>();
        file.getEvents().forEach((id, configuration) -> {
            if (id == null || id.isBlank() || configuration == null || !configuration.isEnabled()) {
                return;
            }
            String eventId = normalize(id);
            Instant start = parseInstant(configuration.getStart());
            Instant end = parseInstant(configuration.getEnd());
            if (start == null || end == null || !end.isAfter(start)) {
                plugin.getLogger().warning("Skipping event shop '" + eventId
                        + "': 'start' and 'end' must be valid ISO dates and 'end' must be after 'start'.");
                return;
            }
            SplitPrice price = price(configuration.getPrice(), defaultCurrency);
            List<ShopEntry> entries = new ArrayList<>();
            for (String raw : configuration.getEntries()) {
                ShopEntry entry = parseEntry(raw, price == null ? null : price.price(), configuration.getDiscount(),
                        availableBundles, "event:" + eventId);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (entries.isEmpty()) {
                plugin.getLogger().warning("Skipping event shop '" + eventId + "': it has no valid entries.");
                return;
            }
            ItemStack icon = plugin.getItemIconFactory().create(configuration.getIcon(),
                    configuration.getName() == null ? eventId : configuration.getName());
            try {
                result.add(new EventShop(eventId, configuration.getName() == null ? eventId : configuration.getName(),
                        start, end, entries, price == null ? null : price.price(), configuration.getPermission(), icon));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipping event shop '" + eventId + "': " + exception.getMessage());
            }
        });
        result.sort(java.util.Comparator.comparing(EventShop::start));
        return List.copyOf(result);
    }

    private List<String> loadDailyPool(ShopFile file, Map<String, Bundle> availableBundles) {
        DailyShopConfiguration configuration = file.getDailyShop();
        List<String> requested = configuration.getPool();
        Set<String> excluded = new LinkedHashSet<>();
        for (String raw : configuration.getExclude()) {
            if (raw != null && !raw.isBlank()) {
                excluded.add(normalize(raw));
            }
        }
        List<String> candidates = new ArrayList<>();
        if (requested.isEmpty()) {
            for (ItemSkin skin : catalog.getSkins()) {
                if (skin.price() != null && skin.price().amount() > 0) {
                    candidates.add(skin.id());
                }
            }
        } else {
            for (String raw : requested) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String normalized = normalize(raw);
                if (!catalog.skinMap().containsKey(normalized)) {
                    plugin.getLogger().warning("daily-shop pool references unknown skin '" + normalized + "'.");
                    continue;
                }
                candidates.add(normalized);
            }
        }
        if (configuration.isIncludeUnpriced() && requested.isEmpty()) {
            for (ItemSkin skin : catalog.getSkins()) {
                if (skin.price() == null) {
                    candidates.add(skin.id());
                }
            }
        }
        return candidates.stream().filter(skinId -> !excluded.contains(skinId)).distinct().sorted().toList();
    }

    private Map<String, Coupon> loadCoupons(CouponFile file) {
        if (!file.isEnabled()) {
            return Map.of();
        }
        Map<String, Coupon> result = new LinkedHashMap<>();
        file.getCoupons().forEach((code, configuration) -> {
            if (code == null || code.isBlank() || configuration == null) {
                return;
            }
            buildCoupon(code, configuration).ifPresent(coupon -> result.put(coupon.code(), coupon));
        });
        return Map.copyOf(result);
    }

    private Optional<Coupon> buildCoupon(String code, CouponConfiguration configuration) {
        String normalized = code.toUpperCase(Locale.ROOT).trim();
        if (configuration.getValue() == null || !Double.isFinite(configuration.getValue()) || configuration.getValue() <= 0) {
            plugin.getLogger().warning("Skipping coupon '" + normalized + "': 'value' must be a positive number.");
            return Optional.empty();
        }
        CouponType type = CouponType.fromId(configuration.getType());
        if (type == CouponType.PERCENTAGE && configuration.getValue() > 100) {
            plugin.getLogger().warning("Skipping coupon '" + normalized + "': a percentage above 100 is not allowed.");
            return Optional.empty();
        }
        Instant expires = parseInstant(configuration.getExpires());
        if (configuration.getExpires() != null && !configuration.getExpires().isBlank() && expires == null) {
            plugin.getLogger().warning("Coupon '" + normalized + "' has an unreadable 'expires' value; it will never expire.");
        }
        Set<String> skins = normalizeSet(configuration.getSkins());
        for (String skinId : skins) {
            if (!catalog.skinMap().containsKey(skinId)) {
                plugin.getLogger().warning("Coupon '" + normalized + "' references unknown skin '" + skinId + "'.");
            }
        }
        try {
            return Optional.of(new Coupon(normalized, type, configuration.getValue(),
                    configuration.getMaxUses() == null ? -1 : configuration.getMaxUses(),
                    configuration.getMaxUsesPerPlayer() == null ? 1 : configuration.getMaxUsesPerPlayer(),
                    expires, configuration.getMinSpend() == null ? 0 : configuration.getMinSpend(), skins,
                    normalizeSet(configuration.getBundles()), normalizeSet(configuration.getCategories()),
                    normalizeSet(configuration.getChannels()), configuration.isActive()));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Skipping coupon '" + normalized + "': " + exception.getMessage());
            return Optional.empty();
        }
    }

    private @Nullable ShopEntry parseEntry(String raw, @Nullable SkinPrice price, double discount,
                                           Map<String, Bundle> availableBundles, String source) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        String type = "skin";
        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            type = parts[0].toLowerCase(Locale.ROOT).trim();
            value = parts[1].trim();
        }
        String id = normalize(value);
        if (type.equals("bundle")) {
            if (!availableBundles.containsKey(id)) {
                plugin.getLogger().warning(source + " references unknown bundle '" + id + "'.");
                return null;
            }
            return new ShopEntry(ShopEntryType.BUNDLE, id, price, discount, null, null, null);
        }
        if (type.equals("skin")) {
            if (!catalog.skinMap().containsKey(id)) {
                plugin.getLogger().warning(source + " references unknown skin '" + id + "'.");
                return null;
            }
            return new ShopEntry(ShopEntryType.SKIN, id, price, discount, null, null, null);
        }
        plugin.getLogger().warning(source + " uses unsupported entry type '" + type + "'.");
        return null;
    }

    private @Nullable SplitPrice price(@Nullable ShopPriceConfiguration configuration, String defaultCurrency) {
        if (configuration == null) {
            return null;
        }
        String currency = configuration.getCurrency() == null || configuration.getCurrency().isBlank()
                ? defaultCurrency : configuration.getCurrency();
        Double amount = configuration.getAmount();
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            if (amount != null) {
                plugin.getLogger().warning("Ignoring a configured price with a non-positive amount.");
            }
            return null;
        }
        String provider = configuration.getProvider();
        try {
            return new SplitPrice(new SkinPrice(provider, currency, amount));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ignoring an invalid configured price: " + exception.getMessage());
            return null;
        }
    }

    /** Parses an ISO instant or a plain date; returns {@code null} when the value is unreadable. */
    public @Nullable Instant parseInstant(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Fall through to the date-only and local date time formats.
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

    private static Set<String> normalizeSet(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(ShopRegistry::normalize).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    private record SplitPrice(SkinPrice price) {
    }
}
