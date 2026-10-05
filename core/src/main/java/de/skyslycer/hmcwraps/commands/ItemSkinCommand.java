package de.skyslycer.hmcwraps.commands;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.commands.annotation.SkinIds;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;

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

    private void send(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        String value = plugin.getLanguageManager().get(player, key);
        StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, value, resolvers));
    }
}
