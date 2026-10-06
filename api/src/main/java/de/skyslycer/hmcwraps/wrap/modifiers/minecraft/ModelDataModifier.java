package de.skyslycer.hmcwraps.wrap.modifiers.minecraft;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.wrap.modifiers.WrapModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;

public class ModelDataModifier implements WrapModifier {

    private final NamespacedKey originalModelIdKey;

    private final HMCWraps plugin;

    public ModelDataModifier(HMCWraps plugin) {
        this.plugin = plugin;
        this.originalModelIdKey = new NamespacedKey(plugin, "original-model-id");
    }

    @Override
    public void wrap(@Nullable Wrap wrap, @Nullable Wrap currentWrap, ItemStack item, Player player) {
        boolean itemModelWrap = usesItemModel(wrap);
        boolean currentItemModelWrap = usesItemModel(currentWrap);

        // Modern item-model wraps are independent from CustomModelData. In particular, applying
        // a Nexo skin must only set minecraft:item_model and must leave the target's existing
        // CustomModelData component exactly as it was.
        if (itemModelWrap) {
            // When replacing a legacy model-data wrap, first put back the target's original data.
            if (currentWrap != null && !currentItemModelWrap) {
                restoreOriginalModelData(item);
            }
            return;
        }

        // Removing an item-model wrap also leaves CustomModelData alone: it was never changed.
        if (wrap == null && currentItemModelWrap) {
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().remove(originalModelIdKey);
            item.setItemMeta(meta);
            return;
        }

        var originalModelId = getOriginalModelId(item);
        Integer currentModelId = currentModelData(item);
        var meta = item.getItemMeta();
        var newModelId = wrap == null ? originalModelId : wrap.getModelId();
        meta.setCustomModelData(newModelId == -1 ? null : newModelId);
        if (wrap == null) {
            meta.getPersistentDataContainer().remove(originalModelIdKey);
        }
        item.setItemMeta(meta);

        // A model-data wrap applied after an item-model wrap starts preserving here because the
        // item-model path intentionally did not create model-data preservation state.
        if (wrap != null && (currentWrap == null || currentItemModelWrap)) {
            setOriginalModelId(item, currentModelId);
        }
    }

    private boolean usesItemModel(@Nullable Wrap wrap) {
        return wrap != null && de.skyslycer.hmcwraps.util.VersionUtil.itemModelSupported()
                && wrap.getItemModel() != null;
    }

    private Integer currentModelData(ItemStack item) {
        var meta = item.getItemMeta();
        return meta.hasCustomModelData() ? meta.getCustomModelData() : null;
    }

    private void restoreOriginalModelData(ItemStack item) {
        var meta = item.getItemMeta();
        int original = getOriginalModelId(item);
        meta.setCustomModelData(original == -1 ? null : original);
        meta.getPersistentDataContainer().remove(originalModelIdKey);
        item.setItemMeta(meta);
    }

    private void setOriginalModelId(ItemStack item, Integer modelData) {
        var meta = item.getItemMeta();
        if (modelData != null) {
            meta.getPersistentDataContainer().set(originalModelIdKey, PersistentDataType.INTEGER, modelData);
        } else {
            meta.getPersistentDataContainer().remove(originalModelIdKey);
        }
        item.setItemMeta(meta);
    }

    /**
     * Get the original model id of the item.
     *
     * @param item The item
     * @return The original model id
     */
    public Integer getOriginalModelId(ItemStack item) {
        var meta = item.getItemMeta();
        var modelData = -1;
        var modelDataSettings = plugin.getConfiguration().getPreservation().getModelId();
        if (modelDataSettings.isOriginalEnabled()) {
            var data = meta.getPersistentDataContainer().get(originalModelIdKey, PersistentDataType.INTEGER);
            if (data != null) {
                modelData = data;
            }
        } else if (modelDataSettings.isDefaultEnabled()) {
            var map = modelDataSettings.getDefaults();
            if (map.containsKey(item.getType().toString())) {
                modelData = map.get(item.getType().toString());
            }
            for (String key : map.keySet()) {
                if (plugin.getCollectionHelper().getMaterials(key).contains(item.getType())) {
                    modelData = map.get(key);
                }
            }
        }
        return modelData;
    }

    /**
     * Get the real model id of the item. If the item is wrapped, the original model id will be returned.
     * If it isn't wrapped, the current model id will be returned.
     *
     * @param item The item
     * @return The real model id
     */
    public int getRealModelId(ItemStack item) {
        var modelData = -1;
        if (plugin.getWrapper().getWrap(item) != null) {
            modelData = getOriginalModelId(item);
        } else if (item.getItemMeta().hasCustomModelData()) {
            try { // Added to prevent error with racking datapack
                modelData = item.getItemMeta().getCustomModelData();
            } catch (Exception ignored) { }
        }
        return modelData;
    }

}
