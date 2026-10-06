package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code shops.yml} file: daily rotation, featured section, bundles and event shops.
 *
 * <p>Everything the shop displays is configured here; no price, slot, discount or rotation is hard-coded
 * in the plugin.</p>
 */
@ConfigSerializable
public class ShopFile {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("daily-shop")
    private DailyShopConfiguration dailyShop = new DailyShopConfiguration();

    @Setting("featured")
    private FeaturedConfiguration featured = new FeaturedConfiguration();

    @Setting("bundles")
    private Map<String, BundleConfiguration> bundles = new LinkedHashMap<>();

    @Setting("events")
    private Map<String, EventShopConfiguration> events = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public DailyShopConfiguration getDailyShop() {
        return dailyShop == null ? new DailyShopConfiguration() : dailyShop;
    }

    public FeaturedConfiguration getFeatured() {
        return featured == null ? new FeaturedConfiguration() : featured;
    }

    public Map<String, BundleConfiguration> getBundles() {
        return bundles == null ? Map.of() : Map.copyOf(bundles);
    }

    public Map<String, EventShopConfiguration> getEvents() {
        return events == null ? Map.of() : Map.copyOf(events);
    }
}
