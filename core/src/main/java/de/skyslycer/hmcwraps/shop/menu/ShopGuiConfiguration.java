package de.skyslycer.hmcwraps.shop.menu;

import com.bgsoftware.common.config.CommentedConfiguration;
import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.nio.file.Files;

/** Static shop inventory titles, names and lore kept outside the language message catalog. */
final class ShopGuiConfiguration {

    private final HMCWrapsPlugin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();

    ShopGuiConfiguration(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    boolean load() {
        try {
            Files.createDirectories(HMCWraps.PLUGIN_PATH);
            if (Files.notExists(HMCWraps.SHOP_GUI_PATH)) {
                try (InputStream resource = plugin.getResource("shop-gui.yml")) {
                    if (resource == null) return false;
                    Files.copy(resource, HMCWraps.SHOP_GUI_PATH);
                }
            } else {
                try (InputStream resource = plugin.getResource("shop-gui.yml")) {
                    if (resource != null) {
                        CommentedConfiguration existing = CommentedConfiguration.loadConfiguration(HMCWraps.SHOP_GUI_PATH.toFile());
                        if (!existing.hasFailed()) existing.syncWithConfig(HMCWraps.SHOP_GUI_PATH.toFile(), resource);
                    }
                }
            }
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(HMCWraps.SHOP_GUI_PATH.toFile());
            yaml = loaded;
            return true;
        } catch (Exception exception) {
            plugin.logSevere("Could not load shop-gui.yml; language defaults will be used.", exception);
            return false;
        }
    }

    String text(String key, String fallback) {
        String configured = yaml.getString(key);
        return configured == null ? fallback : configured;
    }
}
