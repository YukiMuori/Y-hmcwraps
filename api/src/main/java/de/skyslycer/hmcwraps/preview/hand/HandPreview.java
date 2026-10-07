package de.skyslycer.hmcwraps.preview.hand;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.messages.Messages;
import de.skyslycer.hmcwraps.preview.Preview;
import de.skyslycer.hmcwraps.util.StringUtil;
import de.skyslycer.hmcwraps.util.VersionUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class HandPreview implements Preview {

    private final Player player;
    private final ItemStack item;
    private final Consumer<Player> onClose;
    private final HMCWraps plugin;
    private final int durationSeconds;
    private WrappedTask task;
    private WrappedTask cancelTask;
    private ItemStack oldItem;
    private ItemStack oldOffHandItem;

    public HandPreview(Player player, ItemStack item, Consumer<Player> onClose, HMCWraps plugin) {
        this(player, item, onClose, plugin, plugin.getConfiguration().getPreview().getDuration());
    }

    public HandPreview(Player player, ItemStack item, Consumer<Player> onClose, HMCWraps plugin, int durationSeconds) {
        this.player = player;
        this.item = item;
        this.onClose = onClose;
        this.plugin = plugin;
        this.durationSeconds = Math.max(1, durationSeconds);
    }

    public void preview() {
        player.closeInventory();

        oldItem = player.getInventory().getItemInMainHand().clone();
        oldOffHandItem = player.getInventory().getItemInOffHand().clone();
        plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> {
            // HAND previews are purely client-side. Hide the real off-hand item while the
            // preview model occupies the main hand, otherwise an off-hand sword appears twice.
            sendFakeItem(EquipmentSlot.OFF_HAND, new ItemStack(org.bukkit.Material.AIR));
            sendFakeItem(EquipmentSlot.HAND, item);
        }, 1L);

        task = plugin.getFoliaLib().getScheduler().runTimerAsync(() -> {
            if (plugin.getConfiguration().getPreview().getSneakCancel().isActionBar() && plugin.getConfiguration().getPreview().getSneakCancel().isEnabled()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, StringUtil.parse(player, plugin.getMessageHandler().get(player, Messages.PREVIEW_BAR)));
            }
        }, 3, 1);
        cancelTask = plugin.getFoliaLib().getScheduler().runAtEntityLater(player, () -> plugin.getPreviewManager().remove(player.getUniqueId(), true),
                        durationSeconds * 20L);
    }

    public void cancel(boolean open) {
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
            sendFakeItem(EquipmentSlot.HAND, oldItem);
            sendFakeItem(EquipmentSlot.OFF_HAND, oldOffHandItem);
            if (plugin.getConfiguration().getPreview().getSneakCancel().isActionBar()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(" "));
            }
        }, 1L);
    }

    private void sendFakeItem(EquipmentSlot slot, ItemStack item) {
        player.sendEquipmentChange(player, slot, item);
    }

}
