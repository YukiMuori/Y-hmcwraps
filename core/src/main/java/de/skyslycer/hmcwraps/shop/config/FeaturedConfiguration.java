package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code featured} section of {@code shops.yml}.
 *
 * <p>Entries support both explicit selection ({@code skin:ruby_sword}, {@code bundle:crystal_bundle} or a
 * plain skin id) and automatic selection when {@code automatic} is enabled and no entries are listed.</p>
 */
@ConfigSerializable
public class FeaturedConfiguration {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("entries")
    private List<String> entries = new ArrayList<>();

    /** Optional price override applied to featured skin entries. */
    @Setting("price")
    private ShopPriceConfiguration price;

    /** Optional percentage discount applied to featured entries. */
    @Setting("discount")
    private Double discount;

    /** Fills the section automatically when no entry is configured. */
    @Setting("automatic")
    private Boolean automatic = false;

    @Setting("automatic-count")
    private Integer automaticCount = 3;

    /** Optional pool for the automatic selection; empty means every priced skin. */
    @Setting("automatic-pool")
    private List<String> automaticPool = new ArrayList<>();

    /** Recalculates the automatic selection on every daily rotation instead of every restart. */
    @Setting("automatic-rotate")
    private Boolean automaticRotate = true;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public List<String> getEntries() {
        return entries == null ? List.of() : List.copyOf(entries);
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

    public boolean isAutomatic() {
        return automatic != null && automatic;
    }

    public int getAutomaticCount() {
        return automaticCount == null || automaticCount < 1 ? 1 : Math.min(54, automaticCount);
    }

    public List<String> getAutomaticPool() {
        return automaticPool == null ? List.of() : List.copyOf(automaticPool);
    }

    public boolean isAutomaticRotate() {
        return automaticRotate == null || automaticRotate;
    }
}
