package de.skyslycer.hmcwraps.shop.config;

import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/** One entry of the {@code bundles} section in {@code shops.yml}. */
@ConfigSerializable
public class BundleConfiguration {

    @Setting("name")
    private String name;

    @Setting("icon")
    private SkinIconConfiguration icon = new SkinIconConfiguration();

    @Setting("skins")
    private List<String> skins = new ArrayList<>();

    @Setting("price")
    private ShopPriceConfiguration price;

    /** Extra percentage discount on top of the price. */
    @Setting("discount")
    private Double discount;

    /** {@code full}, {@code missing-only} or {@code both}. */
    @Setting("purchase-mode")
    private String purchaseMode = "both";

    @Setting("permission")
    private String permission;

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("featured")
    private Boolean featured = false;

    public String getName() {
        return name;
    }

    public SkinIconConfiguration getIcon() {
        return icon == null ? new SkinIconConfiguration() : icon;
    }

    public List<String> getSkins() {
        return skins == null ? List.of() : List.copyOf(skins);
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

    public String getPurchaseMode() {
        return purchaseMode == null || purchaseMode.isBlank() ? "both" : purchaseMode;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isFeatured() {
        return featured != null && featured;
    }
}
