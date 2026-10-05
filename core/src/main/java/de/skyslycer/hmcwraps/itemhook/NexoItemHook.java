package de.skyslycer.hmcwraps.itemhook;

import com.nexomc.nexo.api.NexoItems;
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

}
