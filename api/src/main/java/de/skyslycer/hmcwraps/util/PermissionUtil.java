package de.skyslycer.hmcwraps.util;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class PermissionUtil {

    /**
     * Check if a player has the permission to use a wrap on an item based on the configuration.
     *
     * @param plugin The plugin
     * @param wrap   The wrap
     * @param item   The item stack
     * @param player The player
     * @return If the player has permission to use the wrap on the item
     */
    public static boolean hasPermission(HMCWraps plugin, Wrap wrap, ItemStack item, Player player) {
        var wrapper = plugin.getWrapper();
        if (wrapper.isPhysical(item) && plugin.getConfiguration().getPermissions().isCheckPermissionPhysical() && !wrap.hasPermission(player)
                && !wrapper.isOwningPlayer(item, player)) {
            return false;
        }
        return wrapper.isPhysical(item) || !plugin.getConfiguration().getPermissions().isCheckPermissionVirtual() || wrap.hasPermission(player)
                || wrapper.isOwningPlayer(item, player);
    }

    /**
     * Check if the player has permission for the wrap on the item.
     *
     * @param plugin The plugin
     * @param item   The item
     * @param player The player
     * @return If the player doesn't have the required permission, it returns the item but unwrapped, while it returns null when the player has
     * permission
     */
    @Nullable
    public static ItemStack hasPermission(HMCWraps plugin, ItemStack item, Player player) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        var wrap = plugin.getWrapper().getWrap(item);
        if (wrap == null) {
            return null;
        }
        if (!hasPermission(plugin, wrap, item, player)) {
            return plugin.getWrapper().removeWrap(item, player);
        }
        return null;
    }

    /**
     * Loops through an inventory and unwraps items the player does not have access to.
     *
     * @param plugin The plugin
     * @param player The player
     * @param inventory The inventory
     */
    public static void loopThroughInventory(HMCWraps plugin, Player player, Inventory inventory) {
        for (int i = 0; i < inventory.getContents().length - 1; i++) {
            var item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            var updatedItem = check(plugin, player, item);
            if (updatedItem.equals(item)) {
                continue;
            }
            inventory.setItem(i, updatedItem);
        }
    }

    /**
     * Applies permission checks to a wrapped item.
     *
     * @param plugin The plugin
     * @param player The player
     * @param item   The item
     * @return The item to set
     */
    public static ItemStack check(HMCWraps plugin, Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        var newItem = PermissionUtil.hasPermission(plugin, item, player);
        if (newItem != null) {
            return newItem;
        }
        return item;
    }

}
