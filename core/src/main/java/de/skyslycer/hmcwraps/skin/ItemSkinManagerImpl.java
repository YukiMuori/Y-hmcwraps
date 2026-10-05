package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.economy.EconomyManager;
import de.skyslycer.hmcwraps.economy.SkinPurchaseCoordinator;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Core implementation for catalog, ownership, safe purchases and the legacy-wrapper adapter. */
public final class ItemSkinManagerImpl implements ItemSkinManager {
    private final HMCWrapsPlugin plugin;
    private final SkinCatalog catalog;
    private final CompatibilityRegistry compatibility;
    private final SkinOwnershipService ownership;
    private final EconomyManager economyManager;
    private final SkinPurchaseCoordinator purchaseCoordinator;
    private final SkinMenuManager menuManager;

    public ItemSkinManagerImpl(HMCWrapsPlugin plugin, SkinCatalog catalog,
                               CompatibilityRegistry compatibility, SkinOwnershipService ownership,
                               EconomyManager economyManager) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.compatibility = compatibility;
        this.ownership = ownership;
        this.economyManager = economyManager;
        this.purchaseCoordinator = new SkinPurchaseCoordinator(ownership.storageProvider(), plugin.getLogger()::warning);
        this.menuManager = new SkinMenuManager(plugin, this);
    }

    public boolean load() { return menuManager.load(); }
    public SkinMenuManager menuManager() { return menuManager; }

    @Override public @NotNull Collection<ItemSkin> getSkins() { return catalog.getSkins(); }
    @Override public @NotNull Collection<ItemSkinRarity> getRarities() {
        return catalog.rarityMap().values().stream().sorted(Comparator.comparingInt(ItemSkinRarity::priority).reversed()).toList();
    }
    @Override public @NotNull Collection<ItemSkinCategory> getCategories() {
        return catalog.categoryMap().values().stream().sorted(Comparator.comparingInt(ItemSkinCategory::priority)).toList();
    }
    @Override public @NotNull Optional<ItemSkin> getSkin(@NotNull String id) { return Optional.ofNullable(catalog.skinMap().get(normalize(id))); }

    @Override
    public @NotNull java.util.List<ItemSkin> getCompatibleSkins(@NotNull ItemStack item) {
        if (item.getType().isAir()) return java.util.List.of();
        Material effectiveMaterial = compatibility.getEffectiveMaterial(item);
        Set<String> itemIds = compatibility.getItemIds(item);
        return catalog.getSkins().stream().filter(skin -> isCompatible(item, skin, effectiveMaterial, itemIds)).toList();
    }

    public boolean isCompatible(ItemStack item, ItemSkin skin) {
        if (item == null || item.getType().isAir()) return false;
        return isCompatible(item, skin, compatibility.getEffectiveMaterial(item), compatibility.getItemIds(item));
    }

    private boolean isCompatible(ItemStack item, ItemSkin skin, Material effectiveMaterial, Set<String> itemIds) {
        boolean materialMatch = skin.compatibleMaterials().contains(effectiveMaterial);
        boolean itemMatch = compatibility.matches(itemIds, skin.compatibleItems());
        if (!materialMatch && !itemMatch) return false;
        try { return plugin.getWrapper().isValid(item, skin.cosmetic()); }
        catch (RuntimeException exception) {
            plugin.getLogger().warning("Compatibility check failed for skin '" + skin.id() + "': " + exception.getMessage());
            return false;
        }
    }

    @Override
    public @NotNull CompletionStage<SkinAccess> getAccess(@NotNull Player player, @NotNull ItemSkin skin) {
        if (!isRegistered(skin)) return CompletableFuture.completedFuture(new SkinAccess(SkinAccess.State.LOCKED, "unknown skin"));
        if (!hasPermission(player, skin)) return CompletableFuture.completedFuture(new SkinAccess(SkinAccess.State.PERMISSION, skin.permission()));
        if (skin.price() == null) return CompletableFuture.completedFuture(new SkinAccess(SkinAccess.State.FREE, null));
        SkinPrice price = skin.price();
        EconomyProvider provider = economyManager.get(price.provider()).orElse(null);
        boolean providerAvailable = providerSupports(provider, price.currency());
        // The SQLite provider queues reads while initialization is in progress. Let async API callers
        // await that result instead of incorrectly reporting a transient startup lock.
        return ownership.getOwnedSkinIds(player.getUniqueId()).thenApply(owned -> {
                    if (!ownership.isReady()) return new SkinAccess(SkinAccess.State.LOCKED, "ownership storage unavailable");
                    return accessFromSnapshot(skin, owned, providerAvailable);
                })
                .exceptionally(error -> new SkinAccess(SkinAccess.State.LOCKED, "ownership storage unavailable"));
    }

    SkinAccess accessNow(Player player, ItemSkin skin, Set<String> owned) {
        if (!hasPermission(player, skin)) return new SkinAccess(SkinAccess.State.PERMISSION, skin.permission());
        if (skin.price() == null) return new SkinAccess(SkinAccess.State.FREE, null);
        if (!ownership.isReady()) return new SkinAccess(SkinAccess.State.LOCKED, "ownership storage unavailable");
        SkinPrice price = skin.price();
        EconomyProvider provider = economyManager.get(price.provider()).orElse(null);
        boolean providerAvailable = providerSupports(provider, price.currency());
        return accessFromSnapshot(skin, owned, providerAvailable);
    }

    private SkinAccess accessFromSnapshot(ItemSkin skin, Set<String> owned, boolean providerAvailable) {
        if (skin.price() == null) return new SkinAccess(SkinAccess.State.FREE, null);
        if (owned != null && owned.contains(normalize(skin.id()))) return new SkinAccess(SkinAccess.State.OWNED, null);
        if (!providerAvailable) return new SkinAccess(SkinAccess.State.LOCKED, "provider unavailable: " + skin.price().provider());
        return new SkinAccess(SkinAccess.State.PURCHASABLE, null);
    }

    private boolean providerSupports(EconomyProvider provider, String currency) {
        if (provider == null) return false;
        try {
            return provider.isAvailable() && provider.supportsCurrency(currency);
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("Economy provider '" + provider.id() + "' failed its availability check: " + exception.getMessage());
            return false;
        }
    }

    private boolean hasPermission(Player player, ItemSkin skin) {
        return (skin.permission() == null || player.hasPermission(skin.permission())) && skin.cosmetic().hasPermission(player);
    }

    @Override
    public @NotNull CompletionStage<PurchaseResult> purchase(@NotNull Player player, @NotNull ItemSkin skin) {
        if (!isRegistered(skin)) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.LOCKED, "unknown skin"));
        if (!hasPermission(player, skin)) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.PERMISSION_DENIED, skin.permission()));
        SkinPrice price = skin.price();
        if (price == null || price.amount() <= 0) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.LOCKED, "skin is free or has no price"));
        EconomyProvider provider = economyManager.get(price.provider()).orElse(null);
        if (!providerSupports(provider, price.currency())) {
            return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.PROVIDER_UNAVAILABLE, price.provider()));
        }
        UUID playerId = player.getUniqueId();
        SkinPurchaseCoordinator.EconomyDispatcher dispatcher = new SkinPurchaseCoordinator.EconomyDispatcher() {
            @Override public CompletionStage<Double> balance(UUID id, EconomyProvider economy, String currency) {
                return callOnPlayer(player, () -> economy.balance(id, currency));
            }
            @Override public CompletionStage<Boolean> withdraw(UUID id, EconomyProvider economy, String currency, double amount) {
                return callOnPlayer(player, () -> economy.withdraw(id, currency, amount));
            }
            @Override public CompletionStage<Boolean> deposit(UUID id, EconomyProvider economy, String currency, double amount) {
                return callOnPlayer(player, () -> economy.deposit(id, currency, amount));
            }
        };
        return purchaseCoordinator.purchase(playerId, skin.id(), price, provider, dispatcher).thenCompose(result -> {
            if (result.status() != PurchaseResult.Status.SUCCESS && result.status() != PurchaseResult.Status.ALREADY_OWNED) {
                return CompletableFuture.completedFuture(result);
            }
            ownership.invalidate(playerId);
            return ownership.getOwnedSkinIds(playerId).handle((ignored, error) -> {
                if (error != null) plugin.getLogger().warning("Purchase completed but the ownership cache refresh failed for " + playerId + ".");
                return result;
            });
        });
    }

    private <T> CompletableFuture<T> callOnPlayer(Player player, Supplier<CompletionStage<T>> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
            if (!player.isOnline()) {
                result.completeExceptionally(new IllegalStateException("Player is no longer online"));
                return;
            }
            try {
                CompletionStage<T> operation = supplier.get();
                if (operation == null) result.completeExceptionally(new IllegalStateException("Economy provider returned no operation"));
                else operation.whenComplete((value, error) -> {
                    if (error != null) result.completeExceptionally(error);
                    else result.complete(value);
                });
            } catch (Throwable throwable) { result.completeExceptionally(throwable); }
        });
        return result;
    }

    @Override
    public @NotNull CompletionStage<Boolean> unlockSkin(@NotNull Player player, @NotNull ItemSkin skin) {
        if (!isRegistered(skin)) return CompletableFuture.completedFuture(false);
        return ownership.unlockSkin(player.getUniqueId(), skin.id());
    }

    @Override public void registerEconomyProvider(@NotNull EconomyProvider provider) { economyManager.register(provider); }
    @Override public void registerCompatibilityProvider(@NotNull CompatibilityProvider provider) { compatibility.register(provider); }
    @Override public @NotNull StorageProvider getStorageProvider() { return ownership.storageProvider(); }

    @Override
    public @NotNull ItemStack applySkin(@NotNull Player player, @NotNull ItemStack item, @NotNull ItemSkin skin) {
        if (item.getType().isAir() || !isRegistered(skin) || !isCompatible(item, skin) || !hasPermission(player, skin)) return item.clone();
        if (skin.price() != null && !ownership.isCachedOwned(player.getUniqueId(), skin.id())) return item.clone();
        Wrap current = plugin.getWrapper().getWrap(item);
        if (current != null && current.getUuid().equals(skin.cosmetic().getUuid())) return item.clone();
        ItemStack wrapped = plugin.getWrapper().setWrap(skin.cosmetic(), item, false, player);
        Wrap applied = plugin.getWrapper().getWrap(wrapped);
        if (applied == null || !applied.getUuid().equals(skin.cosmetic().getUuid())) return item.clone();
        return plugin.getWrapper().setOwningPlayer(wrapped, player.getUniqueId());
    }

    @Override
    public @NotNull ItemStack removeSkin(@NotNull Player player, @NotNull ItemStack item) {
        if (item.getType().isAir()) return item.clone();
        Wrap current = plugin.getWrapper().getWrap(item);
        if (current == null || catalog.getSkins().stream().noneMatch(skin -> skin.cosmetic().getUuid().equals(current.getUuid()))) return item.clone();
        return plugin.getWrapper().removeWrap(item, player);
    }

    @Override
    public void preview(@NotNull Player player, @NotNull ItemStack item, @NotNull ItemSkin skin) {
        if (!skin.previewEnabled() || !isCompatible(item, skin)) return;
        plugin.getFoliaLib().getScheduler().runAtEntity(player,
                ignored -> plugin.getPreviewManager().create(player, null, skin.cosmetic(), item.clone()));
    }

    @Override public void openMenu(@NotNull Player player, @NotNull ItemStack item) { openMenu(player, item, null); }
    @Override public void openMenu(@NotNull Player player, @NotNull ItemStack item, @Nullable String categoryId) {
        plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> menuManager.open(player, item, categoryId));
    }

    @NotNull CompletionStage<Set<String>> ownedSkinIds(UUID playerId) { return ownership.getOwnedSkinIds(playerId); }
    Set<String> cachedOwnedSkinIds(UUID playerId) { return ownership.cachedOwnedSkinIds(playerId); }
    void invalidateOwnership(UUID playerId) { ownership.invalidate(playerId); }
    @Nullable ItemSkinRarity rarity(String id) { return catalog.rarityMap().get(normalize(id)); }
    @Nullable ItemSkinCategory category(String id) { return catalog.categoryMap().get(normalize(id)); }

    private boolean isRegistered(ItemSkin skin) {
        return skin != null && catalog.skinMap().get(normalize(skin.id())) == skin;
    }

    private static String normalize(String id) { return id == null ? "" : id.toLowerCase(Locale.ROOT).trim(); }
}
