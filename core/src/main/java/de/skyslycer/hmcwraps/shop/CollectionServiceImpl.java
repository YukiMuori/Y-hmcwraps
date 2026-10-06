package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.collection.CollectionMilestone;
import de.skyslycer.hmcwraps.collection.CollectionProgress;
import de.skyslycer.hmcwraps.collection.CollectionReward;
import de.skyslycer.hmcwraps.collection.CollectionRewardType;
import de.skyslycer.hmcwraps.collection.CollectionService;
import de.skyslycer.hmcwraps.collection.MilestoneState;
import de.skyslycer.hmcwraps.compat.Scheduler;
import de.skyslycer.hmcwraps.economy.EconomyService;
import de.skyslycer.hmcwraps.economy.EntityEconomyDispatcher;
import de.skyslycer.hmcwraps.events.CollectionCompleteEvent;
import de.skyslycer.hmcwraps.events.CollectionRewardClaimEvent;
import de.skyslycer.hmcwraps.repository.CollectionRewardRepository;
import de.skyslycer.hmcwraps.repository.PlayerRepository;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.ItemSkinCollection;
import de.skyslycer.hmcwraps.skin.SkinCatalog;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.skin.TransactionalStorage;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Collection progress and milestone rewards.
 *
 * <p>Progress is derived from the live ownership set, so it can never diverge from reality. Claiming is
 * idempotent: the claim is written to storage first and only then are the rewards granted, which makes a
 * double click, a crash or a concurrent purchase unable to duplicate a reward.</p>
 */
public final class CollectionServiceImpl implements CollectionService {

    /** Internal milestone id used to announce a completion exactly once. */
    private static final String ANNOUNCEMENT_MILESTONE = "__announcement";

    private final HMCWrapsPlugin plugin;
    private final SkinCatalog catalog;
    private final SkinOwnershipService ownership;
    private final TransactionalStorage storage;
    private final CollectionRewardRepository repository;
    private final ShopRegistry registry;
    private final EconomyService economy;
    private final Scheduler scheduler;
    private final PlayerRepository players;
    private final java.util.function.Consumer<UUID> profileInvalidator;

    public CollectionServiceImpl(@NotNull HMCWrapsPlugin plugin, @NotNull SkinCatalog catalog,
                                @NotNull SkinOwnershipService ownership, @NotNull TransactionalStorage storage,
                                @NotNull CollectionRewardRepository repository, @NotNull ShopRegistry registry,
                                @NotNull EconomyService economy, @NotNull Scheduler scheduler,
                                @NotNull PlayerRepository players,
                                @NotNull java.util.function.Consumer<UUID> profileInvalidator) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.ownership = ownership;
        this.storage = storage;
        this.repository = repository;
        this.registry = registry;
        this.economy = economy;
        this.scheduler = scheduler;
        this.players = players;
        this.profileInvalidator = profileInvalidator;
    }

    @Override
    public boolean isEnabled() {
        return !catalog.collectionMap().isEmpty();
    }

    @Override
    public @NotNull Collection<ItemSkinCollection> getCollections() {
        return catalog.collectionMap().values();
    }

    @Override
    public @NotNull CompletionStage<CollectionProgress> getProgress(@NotNull UUID playerId, @NotNull String collectionId) {
        return ownedSkins(playerId).thenCompose(owned -> claimed(playerId, collectionId)
                .thenApply(claimed -> build(collectionId, owned, claimed)));
    }

    @Override
    public @NotNull CompletionStage<Map<String, CollectionProgress>> getProgress(@NotNull UUID playerId) {
        return ownedSkins(playerId).thenCompose(owned ->
                AsyncUtil.safe(() -> repository.claimedMilestones(playerId)).thenApply(claimed -> {
                    Map<String, CollectionProgress> result = new LinkedHashMap<>();
                    for (ItemSkinCollection collection : catalog.getCollections()) {
                        Set<String> collectionClaims = new LinkedHashSet<>();
                        for (String entry : claimed) {
                            int separator = entry.indexOf(':');
                            if (separator > 0 && entry.substring(0, separator).equals(collection.id())) {
                                collectionClaims.add(entry.substring(separator + 1));
                            }
                        }
                        result.put(collection.id(), build(collection.id(), owned, collectionClaims));
                    }
                    return Map.copyOf(result);
                }));
    }

    @Override
    public @NotNull CompletionStage<Integer> getCompletedCollections(@NotNull UUID playerId) {
        return getProgress(playerId).thenApply(progress ->
                (int) progress.values().stream().filter(CollectionProgress::complete).count());
    }

    private CollectionProgress build(String collectionId, Set<String> owned, Set<String> claimed) {
        String normalized = normalize(collectionId);
        List<ItemSkin> skins = skinsOf(normalized);
        int ownedCount = (int) skins.stream().filter(skin -> owned.contains(skin.id())).count();
        double percentage = skins.isEmpty() ? 0 : Math.round(ownedCount * 1000.0 / skins.size()) / 10.0;
        List<MilestoneState> milestones = milestones(normalized).stream()
                .map(milestone -> new MilestoneState(milestone, milestone.reached(ownedCount, skins.size()),
                        claimed.contains(milestone.id())))
                .toList();
        return new CollectionProgress(normalized, ownedCount, skins.size(), percentage, milestones);
    }

    @Override
    public @NotNull CompletionStage<ClaimResult> claim(@NotNull Player player, @NotNull String collectionId,
                                                       @NotNull String milestoneId) {
        String normalizedCollection = normalize(collectionId);
        CollectionMilestone milestone = milestone(normalizedCollection, milestoneId);
        if (milestone == null) {
            return AsyncUtil.completed(ClaimResult.UNKNOWN);
        }
        return getProgress(player.getUniqueId(), normalizedCollection).thenCompose(progress -> {
            MilestoneState state = progress.milestones().stream()
                    .filter(candidate -> candidate.milestone().id().equals(milestone.id()))
                    .findFirst().orElse(null);
            if (state == null) {
                return AsyncUtil.completed(ClaimResult.UNKNOWN);
            }
            if (state.claimed()) {
                return AsyncUtil.completed(ClaimResult.ALREADY_CLAIMED);
            }
            if (!state.reached()) {
                return AsyncUtil.completed(ClaimResult.NOT_REACHED);
            }
            return callCancellable(player, () -> new CollectionRewardClaimEvent(player, normalizedCollection,
                            milestone.id(), milestone.rewards()))
                    .thenCompose(allowed -> {
                        if (!allowed) {
                            return AsyncUtil.completed(ClaimResult.CANCELLED);
                        }
                        return AsyncUtil.safe(() -> repository.claim(player.getUniqueId(), normalizedCollection, milestone.id()))
                                .thenCompose(claimed -> {
                                    if (!claimed) {
                                        return AsyncUtil.completed(ClaimResult.ALREADY_CLAIMED);
                                    }
                                    return grant(player, milestone.rewards()).thenApply(success -> {
                                        if (!success) {
                                            // Nothing could be granted: release the claim so the player can retry.
                                            AsyncUtil.safe(() -> repository.release(player.getUniqueId(), normalizedCollection,
                                                    milestone.id()));
                                            return ClaimResult.STORAGE_ERROR;
                                        }
                                        plugin.getDiscordWebhook().collectionReward(player, normalizedCollection, milestone.id());
                                        return ClaimResult.CLAIMED;
                                    });
                                })
                                .exceptionally(error -> {
                                    plugin.getLogger().warning("Could not claim milestone " + milestone.id() + " of collection "
                                            + normalizedCollection + ": " + AsyncUtil.describe(error));
                                    return ClaimResult.STORAGE_ERROR;
                                });
                    });
        });
    }

    @Override
    public @NotNull CompletionStage<List<ClaimResult>> claimAll(@NotNull Player player, @NotNull String collectionId) {
        String normalized = normalize(collectionId);
        return getProgress(player.getUniqueId(), normalized).thenCompose(progress -> {
            List<CollectionMilestone> claimable = progress.milestones().stream()
                    .filter(MilestoneState::claimable).map(MilestoneState::milestone).toList();
            if (claimable.isEmpty()) {
                return AsyncUtil.completed(List.of());
            }
            CompletionStage<List<ClaimResult>> chain = AsyncUtil.completed(new ArrayList<ClaimResult>());
            for (CollectionMilestone milestone : claimable) {
                chain = chain.thenCompose(results -> claim(player, normalized, milestone.id()).thenApply(result -> {
                    results.add(result);
                    return results;
                }));
            }
            return chain;
        });
    }

    @Override
    public void announceCompletion(@NotNull Player player, @NotNull String collectionId) {
        String normalized = normalize(collectionId);
        getProgress(player.getUniqueId(), normalized).thenCompose(progress -> {
            if (!progress.complete()) {
                return AsyncUtil.completed(false);
            }
            return AsyncUtil.safe(() -> repository.claim(player.getUniqueId(), normalized, ANNOUNCEMENT_MILESTONE))
                    .thenApply(claimed -> {
                        if (!claimed) {
                            return false;
                        }
                        scheduler.runOnEntity(player, () -> {
                            if (!player.isOnline() || plugin.getConfiguration() == null
                                    || !plugin.getConfiguration().getShop().isCompletionNotifications()) {
                                return;
                            }
                            Bukkit.getPluginManager().callEvent(new CollectionCompleteEvent(player, normalized,
                                    progress.owned(), progress.total()));
                            String message = plugin.getLanguageManager().get(player, "shop.collection.completed");
                            ItemSkinCollection collection = catalog.collectionMap().get(normalized);
                            String name = collection == null ? normalized : collection.displayNameKey();
                            StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, message,
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("collection", normalized),
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("collection_name",
                                            plugin.getLanguageManager().parse(player, name)),
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("owned",
                                            String.valueOf(progress.owned())),
                                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("total",
                                            String.valueOf(progress.total()))));
                            profileInvalidator.accept(player.getUniqueId());
                            plugin.getDiscordWebhook().collectionComplete(player, normalized, progress.owned(), progress.total());
                        });
                        return true;
                    });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not announce the completion of collection " + normalized + ": "
                    + AsyncUtil.describe(error));
            return false;
        });
    }

    /** Grants every reward of a milestone sequentially; returns whether at least one succeeded. */
    private CompletionStage<Boolean> grant(Player player, List<CollectionReward> rewards) {
        List<CompletionStage<Boolean>> stages = new ArrayList<>();
        for (CollectionReward reward : rewards) {
            stages.add(apply(player, reward));
        }
        return AsyncUtil.allOf(stages).thenApply(results -> {
            int success = 0;
            for (int index = 0; index < results.size(); index++) {
                if (Boolean.TRUE.equals(results.get(index))) {
                    success++;
                } else {
                    plugin.getLogger().warning("Collection reward " + rewards.get(index).type().id()
                            + " could not be granted to " + player.getName() + ".");
                }
            }
            return success > 0 || rewards.isEmpty();
        });
    }

    private CompletionStage<Boolean> apply(Player player, CollectionReward reward) {
        return switch (reward.type()) {
            case MONEY -> grantMoney(player, reward);
            case SKIN -> grantSkins(player, List.of(reward.value() == null ? "" : normalize(reward.value())));
            case BUNDLE -> {
                Bundle bundle = reward.value() == null ? null : registry.bundle(reward.value()).orElse(null);
                yield bundle == null ? AsyncUtil.completed(false) : grantSkins(player, bundle.skinIds());
            }
            case COLLECTION_XP -> grantCollectionXp(player, reward);
            case PERMISSION -> AsyncUtil.completed(grantPermission(player, reward.value()));
            case COMMAND -> runOnEntity(player, () -> dispatchCommand(player, reward.value()));
            case ITEM -> runOnEntity(player, () -> giveItem(player, reward.value()));
            case MESSAGE -> runOnEntity(player, () -> {
                if (reward.value() != null) {
                    StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, reward.value()));
                }
                return true;
            });
        };
    }

    private CompletionStage<Boolean> grantCollectionXp(Player player, CollectionReward reward) {
        if (!Double.isFinite(reward.amount()) || reward.amount() <= 0) {
            return AsyncUtil.completed(false);
        }
        return AsyncUtil.safe(() -> players.addCollectionXp(player.getUniqueId(), reward.amount()))
                .thenApply(xp -> {
                    profileInvalidator.accept(player.getUniqueId());
                    return xp != null;
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not store collection experience for " + player.getName() + ": "
                            + AsyncUtil.describe(error));
                    return false;
                });
    }

    private CompletionStage<Boolean> grantMoney(Player player, CollectionReward reward) {
        if (reward.amount() <= 0) {
            return AsyncUtil.completed(false);
        }
        String currency = plugin.getConfiguration().getEconomy().getDefaultCurrency();
        var provider = economy.providerFor(null, currency).orElse(null);
        if (provider == null) {
            plugin.getLogger().warning("Collection money rewards are configured but no economy provider is available.");
            return AsyncUtil.completed(false);
        }
        return EntityEconomyDispatcher.onEntity(scheduler, player,
                () -> provider.deposit(player.getUniqueId(), currency, reward.amount()));
    }

    private CompletionStage<Boolean> grantSkins(Player player, List<String> skinIds) {
        List<String> valid = skinIds.stream().filter(skinId -> catalog.skinMap().containsKey(normalize(skinId))).toList();
        if (valid.isEmpty()) {
            return AsyncUtil.completed(false);
        }
        return AsyncUtil.safe(() -> storage.grantAll(player.getUniqueId(), valid, "collection-reward"))
                .thenApply(granted -> {
                    ownership.invalidate(player.getUniqueId());
                    profileInvalidator.accept(player.getUniqueId());
                    return true;
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not grant collection reward skins to " + player.getName() + ": "
                            + AsyncUtil.describe(error));
                    return false;
                });
    }

    private boolean grantPermission(Player player, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        String node = permission.replace("<player>", player.getName()).replace("%player%", player.getName()).trim();
        Object permissionService = vaultPermissions();
        if (permissionService != null) {
            try {
                Class<?> playerClass = Class.forName("org.bukkit.entity.Player");
                Object result = permissionService.getClass()
                        .getMethod("playerAdd", playerClass, String.class)
                        .invoke(permissionService, player, node);
                return !(result instanceof Boolean value) || value;
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Could not grant the permission " + node + " through Vault: "
                        + AsyncUtil.describe(throwable));
            }
        }
        player.addAttachment(plugin, node, true);
        plugin.getLogger().warning("Granted the permission " + node + " to " + player.getName()
                + " for this session only: install a Vault permissions plugin for a permanent reward.");
        return true;
    }

    private Object vaultPermissions() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                return null;
            }
            Class<?> serviceType = Class.forName("net.milkbowl.vault.permission.Permission");
            @SuppressWarnings({"rawtypes", "unchecked"})
            org.bukkit.plugin.RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration((Class) serviceType);
            return registration == null ? null : registration.getProvider();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private boolean dispatchCommand(Player player, String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String resolved = command.replace("<player>", player.getName()).replace("%player%", player.getName()).trim();
        if (resolved.startsWith("/")) {
            resolved = resolved.substring(1);
        }
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not run the collection reward command '" + resolved + "': "
                    + AsyncUtil.describe(throwable));
            return false;
        }
    }

    private boolean giveItem(Player player, String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        ItemStack item = plugin.getItemIconFactory().createFromReference(reference, null);
        if (item.getType().isAir()) {
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
        return true;
    }

    private <T> CompletionStage<T> runOnEntity(Player player, java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        scheduler.runOnEntity(player, () -> {
            try {
                if (!player.isOnline()) {
                    result.completeExceptionally(new IllegalStateException("The player went offline"));
                    return;
                }
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private <E extends org.bukkit.event.Event & org.bukkit.event.Cancellable> CompletionStage<Boolean> callCancellable(
            Player player, java.util.function.Supplier<E> supplier) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        scheduler.runOnEntity(player, () -> {
            try {
                result.complete(!Bukkit.getPluginManager().callEvent(supplier.get()).isCancelled());
            } catch (Throwable throwable) {
                plugin.getLogger().warning("A collection claim event handler failed: " + AsyncUtil.describe(throwable));
                result.complete(true);
            }
        });
        return result;
    }

    private CompletionStage<Set<String>> ownedSkins(UUID playerId) {
        return AsyncUtil.safe(() -> ownership.getOwnedSkinIds(playerId))
                .thenApply(owned -> owned == null ? Set.of() : owned)
                .exceptionally(error -> Set.of());
    }

    private CompletionStage<Set<String>> claimed(UUID playerId, String collectionId) {
        return AsyncUtil.safe(() -> repository.claimed(playerId, collectionId))
                .thenApply(claimed -> claimed == null ? Set.of() : claimed)
                .exceptionally(error -> Set.of());
    }

    private List<ItemSkin> skinsOf(String collectionId) {
        return catalog.getSkins().stream().filter(skin -> collectionId.equals(skin.collectionId())).toList();
    }

    private List<CollectionMilestone> milestones(String collectionId) {
        ItemSkinCollection collection = catalog.collectionMap().get(collectionId);
        return collection == null ? List.of() : collection.milestones();
    }

    private CollectionMilestone milestone(String collectionId, String milestoneId) {
        String normalized = normalize(milestoneId);
        return milestones(collectionId).stream()
                .filter(milestone -> milestone.id().equals(normalized))
                .findFirst().orElse(null);
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
