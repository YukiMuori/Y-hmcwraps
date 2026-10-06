package de.skyslycer.hmcwraps.itemhook;

import com.nexomc.nexo.api.NexoItems;
import de.skyslycer.hmcwraps.util.VersionUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class NexoItemHook extends ItemHook {

    @Override
    public String getPrefix() {
        return "nexo:";
    }

    @Nullable
    @Override
    public ItemStack get(String id) {
        try {
            var item = NexoItems.itemFromId(id);
            return item == null ? null : item.build();
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    @Override
    public String get(ItemStack stack) {
        if (stack == null) return null;
        try {
            String id = NexoItems.idFromItem(stack);
            return id == null || id.isBlank() ? null : getPrefix() + id;
        } catch (LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public int getModelId(String id) {
        // Nexo's modern resource-pack format addresses custom items through item_model.
        // Do not also copy its legacy CustomModelData onto a wrapped item.
        return VersionUtil.itemModelSupported() ? -1 : super.getModelId(id);
    }

    @Nullable
    @Override
    public NamespacedKey getItemModel(String id) {
        if (!VersionUtil.itemModelSupported() || id == null || id.isBlank()) return null;

        // Prefer the component explicitly configured by Nexo. When Nexo is still configured
        // to generate legacy CustomModelData, its canonical modern model remains nexo:<id>.
        var configured = super.getItemModel(id);
        return configured != null ? configured : new NamespacedKey("nexo", id);
    }

}
