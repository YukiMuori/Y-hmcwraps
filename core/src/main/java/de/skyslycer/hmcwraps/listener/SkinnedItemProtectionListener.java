package de.skyslycer.hmcwraps.listener;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Set;

/** Prevents configured destructive operations while leaving dropping, storage and death behavior unchanged. */
public final class SkinnedItemProtectionListener implements Listener {
    private static final Set<InventoryType> DESTRUCTIVE = Set.of(
            InventoryType.CRAFTING, InventoryType.WORKBENCH, InventoryType.ANVIL,
            InventoryType.SMITHING, InventoryType.GRINDSTONE, InventoryType.STONECUTTER);

    private final HMCWrapsPlugin plugin;

    public SkinnedItemProtectionListener(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!DESTRUCTIVE.contains(event.getView().getTopInventory().getType())) return;
        if (isSkinned(event.getCursor()) || isSkinned(event.getCurrentItem())
                || event.getView().getTopInventory().getContents().length > 0
                && java.util.Arrays.stream(event.getView().getTopInventory().getContents()).anyMatch(this::isSkinned)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (DESTRUCTIVE.contains(event.getView().getTopInventory().getType()) && isSkinned(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (java.util.Arrays.stream(event.getInventory().getMatrix()).anyMatch(this::isSkinned)) event.getInventory().setResult(null);
    }

    @EventHandler public void onAnvil(PrepareAnvilEvent event) { if (contains(event.getInventory().getContents())) event.setResult(null); }
    @EventHandler public void onSmithing(PrepareSmithingEvent event) { if (contains(event.getInventory().getContents())) event.setResult(null); }
    @EventHandler public void onGrindstone(PrepareGrindstoneEvent event) { if (contains(event.getInventory().getContents())) event.setResult(null); }

    @EventHandler(ignoreCancelled = true)
    public void onFinalDurabilityDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!isSkinned(item) || !(item.getItemMeta() instanceof Damageable damageable)) return;
        int remaining = item.getType().getMaxDurability() - damageable.getDamage();
        if (remaining <= event.getDamage()) event.setCancelled(true);
    }

    private boolean contains(ItemStack[] items) {
        return java.util.Arrays.stream(items).anyMatch(this::isSkinned);
    }

    private boolean isSkinned(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        var wrap = plugin.getWrapper().getWrap(item);
        return wrap != null && plugin.getSkinCatalog().getSkins().stream()
                .anyMatch(skin -> skin.cosmetic().getUuid().equals(wrap.getUuid()));
    }
}
