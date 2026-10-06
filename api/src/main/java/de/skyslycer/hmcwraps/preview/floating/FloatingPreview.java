package de.skyslycer.hmcwraps.preview.floating;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.preview.Preview;
import de.skyslycer.hmcwraps.serialization.preview.PreviewEntityType;
import de.skyslycer.hmcwraps.util.VersionUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class FloatingPreview implements Preview {

    private final int entityId;
    private final Player player;
    private final ItemStack item;
    private final Consumer<Player> onClose;
    private final HMCWraps plugin;
    private final boolean upsideDown;
    private final PreviewEntityType entityType;
    private WrappedTask task;
    private WrappedTask cancelTask;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public FloatingPreview(Player player, ItemStack item, boolean upsideDown, Consumer<Player> onClose, HMCWraps plugin) {
        this.player = player;
        this.entityId = VersionUtil.getNextEntityId(player.getWorld());
        this.item = item;
        this.upsideDown = item.getType().toString().contains("_HELMET") ? !upsideDown : upsideDown;
        this.onClose = onClose;
        this.plugin = plugin;
        this.entityType = resolveEntityType(plugin.getConfiguration().getPreview().getEntityType(), item);
    }

    public void preview() {
        player.closeInventory();

        VersionUtil.sendSpawnPacket(player, entityId, upsideDown, entityType.name());
        if (entityType == PreviewEntityType.ARMOR_STAND) {
            VersionUtil.sendMetadataPacket(player, entityId, upsideDown);
            VersionUtil.sendEquipPacket(player, entityId, item);
        } else if (entityType == PreviewEntityType.ITEM_DISPLAY) {
            VersionUtil.sendItemDisplayMetadataPacket(player, entityId, item);
        } else {
            VersionUtil.sendEquipPacket(player, entityId, item, equipmentSlot(item));
        }
        VersionUtil.sendTeleportPacket(player, entityId, upsideDown);

        task = plugin.getFoliaLib().getScheduler().runTimerAsync(new RotateRunnable(player, entityId, plugin), 0, 1);
        if (cancelled.get()) task.cancel();

        cancelTask = plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> plugin.getPreviewManager().remove(player.getUniqueId(), true),
                        plugin.getConfiguration().getPreview().getDuration() * 20L);
        if (cancelled.get()) cancelTask.cancel();
    }

    private static PreviewEntityType resolveEntityType(PreviewEntityType configured, ItemStack item) {
        if (configured != PreviewEntityType.AUTO) {
            return configured;
        }
        if (isArmor(item) && mannequinSupported()) {
            return PreviewEntityType.MANNEQUIN;
        }
        return VersionUtil.hasDataComponents() ? PreviewEntityType.ITEM_DISPLAY : PreviewEntityType.ARMOR_STAND;
    }

    private static boolean mannequinSupported() {
        try {
            org.bukkit.entity.EntityType.valueOf("MANNEQUIN");
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isArmor(ItemStack item) {
        var type = item.getType().toString();
        return type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") || type.endsWith("_LEGGINGS")
                || type.endsWith("_BOOTS") || type.equals("ELYTRA");
    }

    private static String equipmentSlot(ItemStack item) {
        var type = item.getType().toString();
        if (type.endsWith("_HELMET")) return "HEAD";
        if (type.endsWith("_CHESTPLATE") || type.equals("ELYTRA")) return "CHEST";
        if (type.endsWith("_LEGGINGS")) return "LEGS";
        if (type.endsWith("_BOOTS")) return "FEET";
        return "MAINHAND";
    }

    public void cancel(boolean open) {
        // Reload, timeout and sneak can race each other. Cancellation must be null-safe and run
        // once, including when preview() only managed to create one of its scheduled tasks.
        if (!cancelled.compareAndSet(false, true)) return;
        if (task != null) {
            task.cancel();
        }
        if (cancelTask != null) {
            cancelTask.cancel();
        }
        if (open && onClose != null) {
            onClose.accept(player);
        }
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            VersionUtil.sendDestroyPacket(player, entityId);
            if (plugin.getConfiguration().getPreview().getSneakCancel().isActionBar()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(" "));
            }
        }, 1L);
    }

}
