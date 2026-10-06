package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Bridges player lifecycle events into the shop services.
 *
 * <p>Profiles and the selected coupon are preloaded asynchronously while the player is still in the
 * login screen, gifts that were paid for while the recipient was offline are delivered on join, and
 * per-player caches are dropped on quit so a long uptime cannot grow them without bound.</p>
 */
public final class ShopListener implements Listener {

    private final HMCWrapsPlugin plugin;

    public ShopListener(@NotNull HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (plugin.getProfileService() != null) {
            plugin.getProfileService().preload(player.getUniqueId());
        }
        if (plugin.getCouponService() != null) {
            plugin.getCouponService().preload(player.getUniqueId());
        }
        if (plugin.getGiftService() != null) {
            plugin.getGiftService().notifyPending(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getCouponService() != null) {
            plugin.getCouponService().forget(event.getPlayer().getUniqueId());
        }
    }
}
