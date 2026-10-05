package de.skyslycer.hmcwraps.skin;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Public facade for catalog lookup, application, preview, ownership, purchasing and menus. */
public interface ItemSkinManager {
    @NotNull Collection<ItemSkin> getSkins();
    @NotNull Collection<ItemSkinRarity> getRarities();
    @NotNull Collection<ItemSkinCategory> getCategories();
    @NotNull Optional<ItemSkin> getSkin(@NotNull String id);
    @NotNull List<ItemSkin> getCompatibleSkins(@NotNull ItemStack item);
    @NotNull CompletionStage<SkinAccess> getAccess(@NotNull Player player, @NotNull ItemSkin skin);
    @NotNull CompletionStage<PurchaseResult> purchase(@NotNull Player player, @NotNull ItemSkin skin);
    @NotNull CompletionStage<Boolean> unlockSkin(@NotNull Player player, @NotNull ItemSkin skin);
    /** Register an optional/custom economy provider under its stable provider id. */
    void registerEconomyProvider(@NotNull EconomyProvider provider);
    /** Register a custom-item compatibility adapter such as a resource-pack or MMO item provider. */
    void registerCompatibilityProvider(@NotNull CompatibilityProvider provider);
    /** Returns the current persistent ownership provider (SQLite in the default implementation). */
    @NotNull StorageProvider getStorageProvider();
    @NotNull ItemStack applySkin(@NotNull Player player, @NotNull ItemStack item, @NotNull ItemSkin skin);
    @NotNull ItemStack removeSkin(@NotNull Player player, @NotNull ItemStack item);
    void preview(@NotNull Player player, @NotNull ItemStack item, @NotNull ItemSkin skin);
    void openMenu(@NotNull Player player, @NotNull ItemStack item);
    void openMenu(@NotNull Player player, @NotNull ItemStack item, @Nullable String categoryId);
}
