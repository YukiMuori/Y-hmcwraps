package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.LinkedHashMap;
import java.util.Map;

/** The {@code coupons.yml} file. */
@ConfigSerializable
public class CouponFile {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("coupons")
    private Map<String, CouponConfiguration> coupons = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public Map<String, CouponConfiguration> getCoupons() {
        return coupons == null ? Map.of() : Map.copyOf(coupons);
    }
}
