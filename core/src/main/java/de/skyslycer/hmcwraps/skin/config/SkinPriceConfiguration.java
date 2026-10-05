package de.skyslycer.hmcwraps.skin.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class SkinPriceConfiguration {
    private String provider = "";
    private String currency;
    private Double amount;

    public String getProvider() { return provider == null ? "" : provider; }
    public String getCurrency() { return currency == null ? "" : currency; }
    public Double getAmount() { return amount; }
    public boolean isConfigured() { return !getProvider().isBlank() || !getCurrency().isBlank() || amount != null; }
}
