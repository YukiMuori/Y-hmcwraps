package de.skyslycer.hmcwraps.shop.config;

import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of the {@code events} section in {@code shops.yml}. Dates accept ISO-8601 instants
 * ({@code 2026-10-25T00:00:00Z}) or plain dates ({@code 2026-10-25}) which are interpreted in the shop zone.
 */
@ConfigSerializable
public class EventShopConfiguration {

    @Setting("name")
    private String name;

    @Setting("description")
    private List<String> description = new ArrayList<>();

    @Setting("icon")
    private SkinIconConfiguration icon = new SkinIconConfiguration();

    @Setting("start")
    private String start;

    @Setting("end")
    private String end;

    /** Entries as {@code skin:<id>}, {@code bundle:<id>} or a plain skin id. */
    @Setting("entries")
    private List<String> entries = new ArrayList<>();

    /** Fallback currency for entries without their own price. */
    @Setting("price")
    private ShopPriceConfiguration price;

    /** Extra percentage discount applied to every entry of this event. */
    @Setting("discount")
    private Double discount;

    @Setting("permission")
    private String permission;

    @Setting("enabled")
    private Boolean enabled = true;

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description == null ? List.of() : List.copyOf(description);
    }

    public SkinIconConfiguration getIcon() {
        return icon == null ? new SkinIconConfiguration() : icon;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
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

    public String getPermission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
