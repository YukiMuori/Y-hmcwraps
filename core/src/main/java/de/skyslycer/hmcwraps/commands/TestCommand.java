package de.skyslycer.hmcwraps.commands;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.commands.annotation.NoHelp;
import de.skyslycer.hmcwraps.util.StringUtil;
import de.skyslycer.hmcwraps.util.VersionUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import revxrsal.commands.annotation.*;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

@NoHelp
@Command("wraps")
public class TestCommand {

    public static final String DEBUG_PERMISSION = "hmcwraps.debug";

    private final HMCWrapsPlugin plugin;

    public TestCommand(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    @Subcommand("test invalidcolors")
    @Description("Tests the invalid color codes in the plugin.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onTestInvalidColors(Player player) {
        var item = new ItemStack(Material.DIAMOND_SWORD);
        var meta = item.getItemMeta();
        meta.setLore(List.of("§zthere is a preceeding invalid color code"));
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        sendTest(player, "debug.test.invalid-color-received");
    }

    @Subcommand("test reflection")
    @Description("Tests the reflection version helper methods.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onTestReflection(CommandSender sender, @Default("@s") Player player) {
        sendTest(sender, "debug.test.started");
        sendTest(sender, "debug.test.attribute-section");
        try {
            VersionUtil.getOpenInventoryType(player);
            testResult(sender, "getOpenInventoryType", true);
        } catch (Exception e) {
            testResult(sender, "getOpenInventoryType", false);
            plugin.getLogger().log(Level.SEVERE, "getOpenInventoryType test failed:", e);
        }
        try {
            VersionUtil.getBottomInventory(player);
            testResult(sender, "getBottomInventory", true);
        } catch (Exception e) {
            testResult(sender, "getBottomInventory", false);
            plugin.getLogger().log(Level.SEVERE, "getBottomInventory test failed:", e);
        }
        try {
            VersionUtil.getTopInventory(player);
            testResult(sender, "getTopInventory", true);
        } catch (Exception e) {
            testResult(sender, "getTopInventory", false);
            plugin.getLogger().log(Level.SEVERE, "getTopInventory test failed:", e);
        }
        try {
            var testEvent = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            VersionUtil.getItemFromSlot(testEvent, 0);
            testResult(sender, "getItemFromSlot", true);
        } catch (Exception e) {
            testResult(sender, "getItemFromSlot", false);
            plugin.getLogger().log(Level.SEVERE, "getItemFromSlot test failed:", e);
        }
        try {
            var testEvent = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            VersionUtil.setItemInSlot(testEvent, 0, new ItemStack(Material.DIAMOND));
            testResult(sender, "setItemInSlot", true);
        } catch (Exception e) {
            testResult(sender, "setItemInSlot", false);
            plugin.getLogger().log(Level.SEVERE, "setItemInSlot test failed:", e);
        }
        try {
            var testStack = new ItemStack(Material.DIAMOND_CHESTPLATE);
            VersionUtil.Attribute.addAttributeModifier(testStack.getItemMeta(), EquipmentSlot.CHEST, VersionUtil.Attribute.ARMOR_TOUGHNESS, 1.0);
            testResult(sender, "addAttributeModifier", true);
        } catch (Exception e) {
            testResult(sender, "addAttributeModifier", false);
            plugin.getLogger().log(Level.SEVERE, "addAttributeModifier test failed:", e);
        }
        try {
            var testStack = new ItemStack(Material.DIAMOND_CHESTPLATE);
            VersionUtil.Attribute.removeAttributeModifier(testStack.getItemMeta(), VersionUtil.Attribute.ARMOR_TOUGHNESS);
            testResult(sender, "removeAttributeModifier", true);
        } catch (Exception e) {
            testResult(sender, "removeAttributeModifier", false);
            plugin.getLogger().log(Level.SEVERE, "removeAttributeModifier test failed:", e);
        }
        sendTest(sender, "debug.test.packet-section");
        var entityId = ThreadLocalRandom.current().nextInt();
        try {
            entityId = VersionUtil.getNextEntityId(player.getWorld());
            testResult(sender, "getNextEntityId", true);
        } catch (Exception e) {
            testResult(sender, "getNextEntityId", false);
            plugin.getLogger().log(Level.SEVERE, "getNextEntityId test failed:", e);
        }
        try {
            VersionUtil.sendSpawnPacket(player, entityId, false);
            testResult(sender, "sendSpawnPacket", true);
        } catch (Exception e) {
            testResult(sender, "sendSpawnPacket", false);
            plugin.getLogger().log(Level.SEVERE, "sendSpawnPacket test failed:", e);
        }
        try {
            VersionUtil.sendMetadataPacket(player, entityId, false);
            testResult(sender, "sendMetadataPacket", true);
        } catch (Exception e) {
            testResult(sender, "sendMetadataPacket", false);
            plugin.getLogger().log(Level.SEVERE, "sendMetadataPacket test failed:", e);
        }
        try {
            VersionUtil.sendTeleportPacket(player, entityId, false);
            testResult(sender, "sendTeleportPacket", true);
        } catch (Exception e) {
            testResult(sender, "sendTeleportPacket", false);
            plugin.getLogger().log(Level.SEVERE, "sendTeleportPacket test failed:", e);
        }
        try {
            VersionUtil.sendEquipPacket(player, entityId, new ItemStack(Material.DIAMOND_SWORD));
            testResult(sender, "sendEquipPacket", true);
        } catch (Exception e) {
            testResult(sender, "sendEquipPacket", false);
            plugin.getLogger().log(Level.SEVERE, "sendEquipPacket test failed:", e);
        }
        try {
            VersionUtil.sendRelativeMoveAndRotatePacket(player, entityId, 1, 0);
            testResult(sender, "sendRelativeMoveAndRotatePacket", true);
        } catch (Exception e) {
            testResult(sender, "sendRelativeMoveAndRotatePacket", false);
            plugin.getLogger().log(Level.SEVERE, "sendRelativeMoveAndRotatePacket test failed:", e);
        }
        int finalEntityId = entityId;
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            try {
                VersionUtil.sendDestroyPacket(player, finalEntityId);
                testResult(sender, "sendDestroyPacket", true);
            } catch (Exception e) {
                testResult(sender, "sendDestroyPacket", false);
                plugin.getLogger().log(Level.SEVERE, "sendDestroyPacket test failed:", e);
            }
            sendTest(sender, "debug.test.finished");
        }, 20L);
    }

    private void sendTest(CommandSender sender, String key) {
        String text = plugin.getLanguageManager().get(sender instanceof Player player ? player : null, key);
        StringUtil.send(sender, text);
    }

    private void testResult(CommandSender sender, String method, boolean passed) {
        String key = passed ? "debug.test.passed" : "debug.test.failed";
        String text = plugin.getLanguageManager().get(sender instanceof Player player ? player : null, key);
        StringUtil.send(sender, text, Placeholder.unparsed("method", method));
    }

}
