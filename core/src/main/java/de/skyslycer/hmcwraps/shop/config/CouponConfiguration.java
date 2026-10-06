package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/** One entry of the {@code coupons} section in {@code coupons.yml}. */
@ConfigSerializable
public class CouponConfiguration {

    @Setting("type")
    private String type = "percentage";

    @Setting("value")
    private Double value;

    /** Global usage limit; {@code -1} for unlimited. */
    @Setting("max-uses")
    private Integer maxUses = -1;

    /** Per player usage limit; {@code -1} for unlimited. */
    @Setting("max-uses-per-player")
    private Integer maxUsesPerPlayer = 1;

    /** ISO-8601 instant or date; anything else disables the expiry check. */
    @Setting("expires")
    private String expires;

    @Setting("min-spend")
    private Double minSpend;

    @Setting("active")
    private Boolean active = true;

    /** Optional display name for menus and messages. */
    @Setting("display-name")
    private String displayName;

    @Setting("skins")
    private List<String> skins = new ArrayList<>();

    @Setting("bundles")
    private List<String> bundles = new ArrayList<>();

    @Setting("categories")
    private List<String> categories = new ArrayList<>();

    /** {@code daily}, {@code featured}, {@code bundle}, {@code event:<id>}, {@code all}. */
    @Setting("channels")
    private List<String> channels = new ArrayList<>();

    public String getType() {
        return type == null || type.isBlank() ? "percentage" : type;
    }

    public Double getValue() {
        return value;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public Integer getMaxUsesPerPlayer() {
        return maxUsesPerPlayer;
    }

    public String getExpires() {
        return expires;
    }

    public Double getMinSpend() {
        return minSpend;
    }

    public boolean isActive() {
        return active == null || active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSkins() {
        return skins == null ? List.of() : List.copyOf(skins);
    }

    public List<String> getBundles() {
        return bundles == null ? List.of() : List.copyOf(bundles);
    }

    public List<String> getCategories() {
        return categories == null ? List.of() : List.copyOf(categories);
    }

    public List<String> getChannels() {
        return channels == null ? List.of() : List.copyOf(channels);
    }
}
