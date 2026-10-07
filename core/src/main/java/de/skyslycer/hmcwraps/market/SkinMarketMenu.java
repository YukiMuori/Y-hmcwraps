package de.skyslycer.hmcwraps.market;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Paginated-by-cap market overview; listing IDs remain available through commands for cancellation. */
public final class SkinMarketMenu implements Listener {
    private final HMCWrapsPlugin plugin;
    private final SkinMarketService service;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SkinMarketMenu(HMCWrapsPlugin plugin, SkinMarketService service) { this.plugin = plugin; this.service = service; }

    public void open(Player player) {
        service.listings().whenComplete((listings, error) -> plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
            if (!player.isOnline()) return;
            Session session = new Session(player.getUniqueId());
            session.inventory = Bukkit.createInventory(session, 54, StringUtil.LEGACY_SERIALIZER.serialize(
                    StringUtil.parseComponent(player, "<gradient:#8FEAF9:#CDA4F9>ᴍᴇʀᴄᴀᴛᴏ sᴋɪɴ</gradient>")));
            if (error == null && listings != null) {
                int slot = 0;
                for (MarketListing listing : listings) {
                    if (slot >= 45) break;
                    var skin = plugin.getItemSkinManager().getSkin(listing.skinId()).orElse(null);
                    if (skin == null) continue;
                    ItemStack icon = skin.icon() == null ? new ItemStack(Material.PAPER) : skin.icon().clone();
                    ItemMeta meta = icon.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(StringUtil.LEGACY_SERIALIZER.serialize(plugin.getLanguageManager().parse(player, skin.displayName())));
                        meta.setLore(java.util.List.of(
                                legacy(player, "<gray>Venditore: <white>" + listing.sellerName() + "</white></gray>"),
                                legacy(player, "<gray>Prezzo: <#FFE89A>" + listing.amount() + " " + listing.currency() + "</#FFE89A></gray>"),
                                legacy(player, "<#FFE89A>➜ ᴄʟɪᴄᴄᴀ ᴘᴇʀ ᴀᴄǫᴜɪsᴛᴀʀᴇ</#FFE89A>")));
                        icon.setItemMeta(meta);
                    }
                    session.actions.put(slot, listing.id());
                    session.inventory.setItem(slot++, icon);
                }
            }
            sessions.put(player.getUniqueId(), session);
            player.openInventory(session.inventory);
        }));
    }

    private String legacy(Player player, String text) {
        return StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, text));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Session session)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID listing = session.actions.get(event.getRawSlot());
        if (listing == null) return;
        service.buy(player.getUniqueId(), listing).whenComplete((result, error) -> plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
            String key = result == SkinMarketService.Result.SUCCESS ? "messages.market-bought" : "messages.market-failed";
            StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, plugin.getLanguageManager().get(player, key)));
            open(player);
        }));
    }

    @EventHandler public void onClose(InventoryCloseEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    private static final class Session implements InventoryHolder {
        final UUID playerId; final Map<Integer, UUID> actions = new HashMap<>(); Inventory inventory;
        Session(UUID playerId) { this.playerId = playerId; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
