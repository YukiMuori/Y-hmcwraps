package de.skyslycer.hmcwraps.commands;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.commands.annotation.SkinIds;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/** Player-facing entry point for the independent v2 item-skin browser. */
@Command("itemskin")
public final class ItemSkinCommand {
    private final HMCWrapsPlugin plugin;

    public ItemSkinCommand(HMCWrapsPlugin plugin) { this.plugin = plugin; }

    @Command("itemskin")
    @Description("Open the item-skin catalog for the item in your main hand.")
    public void onItemSkin(Player player) { open(player); }

    @Subcommand("open")
    @Description("Open the item-skin catalog for the item in your main hand.")
    public void onOpen(Player player) { open(player); }

    @Subcommand("remove")
    @Description("Remove the current v2 skin from the item in your main hand.")
    public void onRemove(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemStack updated = plugin.getItemSkinManager().removeSkin(player, item);
        if (updated.equals(item)) {
            send(player, "messages.no-skin-to-remove");
            return;
        }
        player.getInventory().setItemInMainHand(updated);
        send(player, "messages.removed");
    }

    @Subcommand("preview")
    @Description("Preview a configured skin on the item in your main hand.")
    public void onPreview(Player player, @SkinIds String skinId) {
        ItemSkin skin = plugin.getItemSkinManager().getSkin(skinId).orElse(null);
        if (skin == null) {
            send(player, "messages.unknown-skin", Placeholder.unparsed("skin", skinId));
            return;
        }
        if (!skin.previewEnabled()) {
            send(player, "messages.preview-disabled");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            send(player, "messages.no-item");
            return;
        }
        if (!plugin.getItemSkinManager().getCompatibleSkins(item).stream().anyMatch(candidate -> candidate.id().equals(skin.id()))) {
            send(player, "messages.compatibility-error");
            return;
        }
        plugin.getItemSkinManager().preview(player, item, skin);
    }

    @Subcommand("trade")
    @Description("Offer an owned skin to another player; both players must confirm.")
    public void onTrade(Player sender, Player recipient, @SkinIds String skinId) {
        ItemSkin skin = plugin.getItemSkinManager().getSkin(skinId).orElse(null);
        if (skin == null) {
            send(sender, "messages.unknown-skin", Placeholder.unparsed("skin", skinId));
            return;
        }
        plugin.getSkinTradeManager().offer(sender, recipient, skin);
    }

    @Subcommand("trade confirm")
    @Description("Confirm your active skin trade offer.")
    public void onTradeConfirm(Player player) {
        plugin.getSkinTradeManager().confirm(player);
    }

    @Subcommand("trade cancel")
    @Description("Cancel your active skin trade offer.")
    public void onTradeCancel(Player player) {
        plugin.getSkinTradeManager().cancel(player);
    }

    @Subcommand("give")
    @Description("Grant a skin permanently to a player without charging them.")
    @CommandPermission("hmcwraps.commands.itemskin.give")
    public void onGive(CommandSender sender, @SkinIds String skinId, String playerName) {
        ItemSkin skin = plugin.getItemSkinManager().getSkin(skinId).orElse(null);
        if (skin == null) {
            send(sender, "messages.unknown-skin", Placeholder.unparsed("skin", skinId));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            send(sender, "messages.admin-player-not-found", Placeholder.unparsed("player", playerName));
            return;
        }
        if (plugin.getSkinOwnership() == null || !plugin.getSkinOwnership().isReady()) {
            send(sender, "messages.storage-unavailable");
            return;
        }
        plugin.getSkinOwnership().hasSkin(target.getUniqueId(), skin.id()).thenCompose(owned -> {
            if (owned) return java.util.concurrent.CompletableFuture.completedFuture(GiveResult.ALREADY_OWNED);
            return plugin.getSkinOwnership().unlockSkin(target.getUniqueId(), skin.id())
                    .thenApply(success -> Boolean.TRUE.equals(success) ? GiveResult.SUCCESS : GiveResult.FAILED);
        }).whenComplete((result, error) -> sync(sender, () -> {
            if (error != null || result == GiveResult.FAILED) {
                send(sender, "messages.admin-give-failed", Placeholder.unparsed("player", target.getName() == null ? playerName : target.getName()),
                        Placeholder.component("skin", plugin.getLanguageManager().parse(sender instanceof Player player ? player : null, skin.displayName())));
            } else if (result == GiveResult.ALREADY_OWNED) {
                send(sender, "messages.admin-give-already-owned", Placeholder.unparsed("player", target.getName() == null ? playerName : target.getName()),
                        Placeholder.component("skin", plugin.getLanguageManager().parse(sender instanceof Player player ? player : null, skin.displayName())));
            } else {
                send(sender, "messages.admin-give-success", Placeholder.unparsed("player", target.getName() == null ? playerName : target.getName()),
                        Placeholder.component("skin", plugin.getLanguageManager().parse(sender instanceof Player player ? player : null, skin.displayName())));
            }
        }));
    }

    @Subcommand("editor")
    @Description("Edit the shop definition files (shops.yml and coupons.yml) in game.")
    @CommandPermission("hmcwraps.commands.itemskin.editor")
    public void onEditor(Player player) {
        if (plugin.getShopEditorManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopEditorManager().open(player);
    }

    @Subcommand("shop")
    @Description("Open the skin shop with the daily offers, bundles and events.")
    public void onShop(Player player) {
        if (plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openHome(player);
    }

    @Subcommand("bundles")
    @Description("Open the bundle overview of the skin shop.")
    public void onBundles(Player player) {
        if (plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openBundles(player);
    }

    @Subcommand("events")
    @Description("Open the running and upcoming event shops.")
    public void onEvents(Player player) {
        if (plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openEvents(player);
    }

    @Subcommand("coupons")
    @Description("Open the coupon overview and pick the coupon used for your next purchase.")
    public void onCoupons(Player player) {
        if (plugin.getCouponService() == null || plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openCoupons(player);
    }

    @Subcommand("profile")
    @Description("Show your skin statistics, gifts and recent purchases.")
    public void onProfile(Player player) {
        if (plugin.getProfileService() == null || plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openProfile(player);
    }

    @Subcommand("gifts")
    @Description("Show the gifts you have not been notified about yet.")
    public void onGifts(Player player) {
        if (plugin.getGiftService() == null || plugin.getShopMenuManager() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getShopMenuManager().openGifts(player);
    }

    @Subcommand("gift")
    @Description("Gift an owned or purchasable skin to another player; you pay the price.")
    public void onGift(Player sender, String recipientName, @SkinIds String skinId) {
        if (plugin.getGiftService() == null || plugin.getShopMenuManager() == null) {
            send(sender, "shop.purchase.unavailable");
            return;
        }
        if (!plugin.getGiftService().isEnabled()) {
            send(sender, "shop.gift.disabled");
            return;
        }
        if (plugin.getItemSkinManager().getSkin(skinId).isEmpty()) {
            send(sender, "messages.unknown-skin", Placeholder.unparsed("skin", skinId));
            return;
        }
        Player online = org.bukkit.Bukkit.getPlayerExact(recipientName);
        if (online != null) {
            if (online.getUniqueId().equals(sender.getUniqueId())) {
                send(sender, "shop.gift.self");
                return;
            }
            plugin.getShopMenuManager().openGiftConfirm(sender, de.skyslycer.hmcwraps.shop.PurchaseKind.SKIN, skinId,
                    online.getUniqueId(), online.getName());
            return;
        }
        if (!plugin.getGiftService().allowsOffline()) {
            send(sender, "shop.gift.offline");
            return;
        }
        org.bukkit.OfflinePlayer offline;
        try {
            offline = org.bukkit.Bukkit.getOfflinePlayer(recipientName);
        } catch (RuntimeException exception) {
            send(sender, "shop.gift.offline");
            return;
        }
        if (offline == null || !offline.hasPlayedBefore()) {
            send(sender, "shop.gift.offline");
            return;
        }
        plugin.getShopMenuManager().openGiftConfirm(sender, de.skyslycer.hmcwraps.shop.PurchaseKind.SKIN, skinId,
                offline.getUniqueId(), recipientName);
    }

    @Subcommand("coupon")
    @Description("Apply a coupon code to your next purchase.")
    public void onCoupon(Player player, String code) {
        if (plugin.getCouponService() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        plugin.getCouponService().setSelectedCoupon(player.getUniqueId(), code);
        send(player, "shop.coupon.selected", Placeholder.unparsed("code", code.toUpperCase(java.util.Locale.ROOT)));
    }

    private void open(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            send(player, "messages.no-item");
            return;
        }
        if (plugin.getItemSkinManager().getCompatibleSkins(item).isEmpty()) {
            send(player, "messages.no-compatible-skins");
        }
        plugin.getItemSkinManager().openMenu(player, item);
    }

    private void sync(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> task.run());
        } else {
            plugin.getFoliaLib().getScheduler().runNextTick(ignored -> task.run());
        }
    }

    private void send(CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        Player player = sender instanceof Player online ? online : null;
        String value = plugin.getLanguageManager().get(player, key);
        StringUtil.sendComponent(sender, plugin.getLanguageManager().parse(player, value, resolvers));
    }

    private enum GiveResult {
        SUCCESS,
        ALREADY_OWNED,
        FAILED
    }
}
