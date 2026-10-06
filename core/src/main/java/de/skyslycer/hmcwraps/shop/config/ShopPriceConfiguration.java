package de.skyslycer.hmcwraps.shop.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/** A configured price: provider, currency and amount. */
@ConfigSerializable
public class ShopPriceConfiguration {

    @Setting("provider")
    private String provider = "auto";

    @Setting("currency")
    private String currency;

    @Setting("amount")
    private Double amount;

    /** A price defined only by currency (used as an event/featured fallback). */
    @Setting("affix")
    private Boolean affix;

    public String getProvider() {
        return provider == null || provider.isBlank() ? "auto" : provider;
    }

    public String getCurrency() {
        return currency;
    }

    public Double getAmount() {
        return amount;
    }

    public Boolean getAffix() {
        return affix;
    }
}
