package de.skyslycer.hmcwraps.updater;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ContinuousUpdateChecker {

    private static final int SPIGOT_ID = 107099;
    private static final int POLYMART_ID = 3216;

    private final HMCWrapsPlugin plugin;

    private PluginUpdater updater;
    private long lastResultTime = 0;
    private CheckResult lastResult;
    private WrappedTask task;

    public ContinuousUpdateChecker(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        resetUpdater();
        if (task != null) {
            task.cancel();
        }
        task = plugin.getFoliaLib().getScheduler().runTimerAsync(() -> {
            var result = updater.check(plugin);
            lastResultTime = System.currentTimeMillis();
            lastResult = result;
            if (result != null && !result.latest()) {
                plugin.getLogger().warning(String.format(
                        """
                                
                                ++++++++++++++++++++++++++++++
                                + There is a new update for HMCWraps available!
                                + Please download it as soon as possible for possible fixes and new features.
                                + Current version: %s | Latest version: %s
                                + SpigotMC: %s
                                + Polymart: %s
                                ++++++++++++++++++++++++++++++""",
                        plugin.getDescription().getVersion(), result.version(),
                        String.format(PluginPlatform.SPIGOT_MC.url(), SPIGOT_ID), String.format(PluginPlatform.POLYMART.url(), POLYMART_ID)));
                Bukkit.getOnlinePlayers().forEach(player -> checkPlayer(player, result));
            }
        }, 20, StringUtil.shortTimeToSeconds(plugin.getConfiguration().getUpdater().getFrequency(),
                60 * 5, 60 * 60 * 3) * 20); // 5 minutes minimum, 3 hours default
    }

    private void resetUpdater() {
        if (plugin.getConfiguration().getUpdater().getPlatform() == PluginPlatform.SPIGOT_MC) {
            updater = new SpigotPluginUpdater(SPIGOT_ID);
        } else if (plugin.getConfiguration().getUpdater().getPlatform() == PluginPlatform.POLYMART) {
            updater = new PolymartPluginUpdater(POLYMART_ID);
        }
    }

    public void checkPlayer(Player player) {
        checkPlayer(player, null);
    }

    private void checkPlayer(Player player, CheckResult defaultResult) {
        if ((!player.isOp() && !player.hasPermission("*")) || !plugin.getConfiguration().getUpdater().isEnabled()) {
            return;
        }
        CheckResult result;
        if (defaultResult != null) {
            result = defaultResult;
        } else {
            result = getLatest();
        }
        if (result == null || result.latest()) {
            return;
        }
        String message = plugin.getLanguageManager().get(player, "updates.available")
                .replace("{current}", plugin.getDescription().getVersion())
                .replace("{latest}", result.version())
                .replace("{spigot_url}", String.format(PluginPlatform.SPIGOT_MC.url(), SPIGOT_ID))
                .replace("{polymart_url}", String.format(PluginPlatform.POLYMART.url(), POLYMART_ID));
        var component = StringUtil.parseComponent(player, message);
        StringUtil.sendComponent(player, component);
    }

    public CheckResult getLatest() {
        if (System.currentTimeMillis() - lastResultTime < 1000 * 60 * 10 && lastResult != null) {
            return lastResult;
        } else {
            return updater.check(plugin);
        }
    }

}
