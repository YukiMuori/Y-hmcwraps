package de.skyslycer.hmcwraps.skin;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Optional bridge between a third-party custom-item system and the generic skin catalog.
 * Return a stable namespaced item id such as {@code vendor:namespace:item}; skin compatibility
 * entries under {@code compatibility.items} are matched case-insensitively against this value.
 */
public interface CompatibilityProvider {
    @NotNull String id();
    boolean isAvailable();
    @Nullable String getItemId(@NotNull ItemStack item);
    default boolean matches(@NotNull ItemStack item, @NotNull String configuredId) {
        String actual = getItemId(item);
        return actual != null && actual.equalsIgnoreCase(configuredId);
    }
}
