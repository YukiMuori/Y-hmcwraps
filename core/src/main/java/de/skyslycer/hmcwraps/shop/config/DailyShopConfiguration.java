package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/** The {@code daily-shop} section of {@code shops.yml}. */
@ConfigSerializable
public class DailyShopConfiguration {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("slots")
    private Integer slots = 6;

    /** {@code daily}, {@code weekly} or {@code never}. */
    @Setting("refresh")
    private String refresh = "daily";

    /** Time of day the shop rotates, in the configured zone. */
    @Setting("reset-time")
    private String resetTime = "00:00";

    /** Zone used to calculate the rotation boundary. UTC keeps the shop identical on every server. */
    @Setting("zone")
    private String zone = "UTC";

    /** Stable salt: change it to obtain a different (but still deterministic) rotation sequence. */
    @Setting("rotation-salt")
    private String rotationSalt = "hmcwraps";

    /** Optional pool of skin ids; empty means every priced skin is a candidate. */
    @Setting("pool")
    private List<String> pool = new ArrayList<>();

    /** Skin ids that must never appear in the rotation. */
    @Setting("exclude")
    private List<String> exclude = new ArrayList<>();

    /** Whether skins without a price may appear. */
    @Setting("include-unpriced")
    private Boolean includeUnpriced = false;

    /** Optional price override applied to daily entries. */
    @Setting("price")
    private ShopPriceConfiguration price;

    /** Optional percentage discount applied to daily entries. */
    @Setting("discount")
    private Double discount;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public int getSlots() {
        return slots == null || slots < 1 ? 1 : Math.min(54, slots);
    }

    public String getRefresh() {
        return refresh == null || refresh.isBlank() ? "daily" : refresh.toLowerCase(java.util.Locale.ROOT).trim();
    }

    public String getResetTime() {
        return resetTime == null || resetTime.isBlank() ? "00:00" : resetTime.trim();
    }

    public String getZone() {
        return zone == null || zone.isBlank() ? "UTC" : zone.trim();
    }

    public String getRotationSalt() {
        return rotationSalt == null ? "" : rotationSalt;
    }

    public List<String> getPool() {
        return pool == null ? List.of() : List.copyOf(pool);
    }

    public List<String> getExclude() {
        return exclude == null ? List.of() : List.copyOf(exclude);
    }

    public boolean isIncludeUnpriced() {
        return includeUnpriced != null && includeUnpriced;
    }

    public ShopPriceConfiguration getPrice() {
        return price;
    }

    public double getDiscount() {
        if (discount == null || !Double.isFinite(discount)) {
            return 0;
        }
        return Math.max(0, Math.min(100, discount));
    }
}
