package de.skyslycer.hmcwraps.serialization;

import de.skyslycer.hmcwraps.serialization.discord.DiscordSettings;
import de.skyslycer.hmcwraps.serialization.economy.EconomySettings;
import de.skyslycer.hmcwraps.serialization.filter.FilterSettings;
import de.skyslycer.hmcwraps.serialization.shop.GiftSettings;
import de.skyslycer.hmcwraps.serialization.shop.ShopSettings;
import de.skyslycer.hmcwraps.serialization.globaldisable.GlobalDisable;
import de.skyslycer.hmcwraps.serialization.integration.PluginIntegrations;
import de.skyslycer.hmcwraps.serialization.inventory.Inventory;
import de.skyslycer.hmcwraps.serialization.item.PhysicalUnwrapper;
import de.skyslycer.hmcwraps.serialization.permission.PermissionSettings;
import de.skyslycer.hmcwraps.serialization.preservation.PreservationSettings;
import de.skyslycer.hmcwraps.serialization.preview.PreviewSettings;
import de.skyslycer.hmcwraps.serialization.updater.UpdaterSettings;
import de.skyslycer.hmcwraps.serialization.wrap.WrappableItem;
import de.skyslycer.hmcwraps.serialization.wrapping.WrappingSettings;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigSerializable
public class Config {

    private UpdaterSettings updater;
    private WrappingSettings wrapping;
    private PermissionSettings permissions;
    private PreviewSettings preview;
    private Toggleable legacyWraps = new Toggleable(false);
    private FilterSettings filter;
    private Inventory inventory;
    private PhysicalUnwrapper unwrapper;
    private PreservationSettings preservation;
    private GlobalDisable globalDisable;
    private Map<String, WrappableItem> items = new HashMap<>();
    private Map<String, List<String>> collections = new HashMap<>();
    private PluginIntegrations integrations;
    private LanguageSettings language = new LanguageSettings();
    private EconomySettings economy = new EconomySettings();
    private ShopSettings shop = new ShopSettings();
    private GiftSettings gifts = new GiftSettings();
    private DiscordSettings discord = new DiscordSettings();
    private Boolean debug = false;
    private Integer config = 1;

    public Config(UpdaterSettings updater, PermissionSettings permissions, PreviewSettings preview, Toggleable legacyWraps,
                  Inventory inventory, PhysicalUnwrapper unwrapper, PreservationSettings preservation, Map<String, WrappableItem> items,
                  Map<String, List<String>> collections, FilterSettings filter, WrappingSettings wrapping) {
        this.updater = updater;
        this.permissions = permissions;
        this.preview = preview;
        this.legacyWraps = legacyWraps;
        this.inventory = inventory;
        this.unwrapper = unwrapper;
        this.preservation = preservation;
        this.items = items;
        this.collections = collections;
        this.filter = filter;
        this.wrapping = wrapping;
    }

    public Config() {
    }

    public Inventory getInventory() {
        return inventory;
    }

    public WrappingSettings getWrapping() {
        return wrapping;
    }

    public PhysicalUnwrapper getUnwrapper() {
        return unwrapper;
    }

    public Map<String, WrappableItem> getItems() {
        return items;
    }

    public PreviewSettings getPreview() {
        return preview;
    }

    /** Whether the classic /wraps system and legacy wrap files are enabled. */
    public Toggleable getLegacyWraps() {
        return legacyWraps == null ? new Toggleable(false) : legacyWraps;
    }

    public UpdaterSettings getUpdater() {
        return updater;
    }

    public Map<String, List<String>> getCollections() {
        return collections;
    }

    public PermissionSettings getPermissions() {
        return permissions;
    }

    public PreservationSettings getPreservation() {
        return preservation;
    }

    public FilterSettings getFilter() {
        return filter;
    }

    public GlobalDisable getGlobalDisable() {
        return globalDisable;
    }

    public PluginIntegrations getPluginIntegrations() {
        return integrations;
    }

    public LanguageSettings getLanguage() {
        return language == null ? new LanguageSettings() : language;
    }

    public EconomySettings getEconomy() {
        return economy == null ? new EconomySettings() : economy;
    }

    public ShopSettings getShop() {
        return shop == null ? new ShopSettings() : shop;
    }

    public GiftSettings getGifts() {
        return gifts == null ? new GiftSettings() : gifts;
    }

    public DiscordSettings getDiscord() {
        return discord == null ? new DiscordSettings() : discord;
    }

    /** Whether verbose debug logging is enabled. */
    public boolean isDebug() {
        return debug != null && debug;
    }

}
