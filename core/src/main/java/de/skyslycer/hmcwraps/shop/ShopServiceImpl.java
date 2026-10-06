package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.collection.CollectionProgress;
import de.skyslycer.hmcwraps.collection.CollectionService;
import de.skyslycer.hmcwraps.compat.Scheduler;
import de.skyslycer.hmcwraps.economy.EconomyService;
import de.skyslycer.hmcwraps.economy.PurchaseTransactionService;
import de.skyslycer.hmcwraps.events.BundlePurchaseEvent;
import de.skyslycer.hmcwraps.events.ShopRefreshEvent;
import de.skyslycer.hmcwraps.events.SkinPurchaseEvent;
import de.skyslycer.hmcwraps.skin.CompatibilityRegistry;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.ItemSkinCollection;
import de.skyslycer.hmcwraps.skin.SkinCatalog;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.skin.TransactionalStorage;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * The integrated shop.
 *
 * <p>It ties together the catalog, ownership, bundles, the daily rotation, event shops, coupons and the
 * transaction engine. Every mutation is validated against storage, every price is recalculated on the
 * server, and GUI state is never trusted.</p>
 */
public final class ShopServiceImpl implements ShopService {

    private final HMCWrapsPlugin plugin;
    private final ShopRegistry registry;
    private final SkinCatalog catalog;
    private final SkinOwnershipService ownership;
    private final TransactionalStorage storage;
    private final PurchaseTransactionService transactions;
    private final CouponServiceImpl coupons;
    private final EconomyService economy;
    private final DailyShopService daily;
    private final CollectionService collections;
    private final Scheduler scheduler;
    private final java.util.function.Consumer<String> debug;
    private final java.util.Set<String> activeEventShops = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean eventShopsSeeded;
    private volatile String activeCycleKey;

    public ShopServiceImpl(@NotNull HMCWrapsPlugin plugin, @NotNull ShopRegistry registry, @NotNull SkinCatalog catalog,
                           @NotNull SkinOwnershipService ownership, @NotNull TransactionalStorage storage,
                           @NotNull PurchaseTransactionService transactions, @NotNull CouponServiceImpl coupons,
                           @NotNull EconomyService economy, @NotNull DailyShopService daily,
                           @NotNull CollectionService collections, @NotNull Scheduler scheduler) {
        this.plugin = plugin;
        this.registry = registry;
        this.catalog = catalog;
        this.ownership = ownership;
        this.storage = storage;
        this.transactions = transactions;
        this.coupons = coupons;
        this.economy = economy;
        this.daily = daily;
        this.collections = collections;
        this.scheduler = scheduler;
        this.debug = message -> {
            if (plugin.getConfiguration() != null && plugin.getConfiguration().isDebug()) {
                plugin.getLogger().info("[debug] " + message);
            }
        };
    }

    /** The transaction engine, so gifts and other paid flows reuse the very same path. */
    public @NotNull PurchaseTransactionService transactions() {
        return transactions;
    }

    /**
     * Detects event shop openings and closings and announces them exactly once.
     *
     * <p>The first call only seeds the state: events that were already running before the plugin
     * started are not announced again after a restart. Called by the shop scheduler every minute.</p>
     */
    public void checkEventShops() {
        Instant now = Instant.now();
        Set<String> current = new LinkedHashSet<>();
        List<EventShop> started = new ArrayList<>();
        List<EventShop> ended = new ArrayList<>();
        for (EventShop event : registry.events()) {
            if (event.activeAt(now)) {
                current.add(event.id());
                if (eventShopsSeeded && !activeEventShops.contains(event.id())) {
                    started.add(event);
                }
            } else if (eventShopsSeeded && activeEventShops.contains(event.id()) && event.endedAt(now)) {
                ended.add(event);
            }
        }
        activeEventShops.clear();
        activeEventShops.addAll(current);
        if (!eventShopsSeeded) {
            eventShopsSeeded = true;
            return;
        }
        for (EventShop event : started) {
            announce(event, "started", plugin.getDiscordWebhook());
        }
        for (EventShop event : ended) {
            announce(event, "ended", plugin.getDiscordWebhook());
        }
    }

    private void announce(EventShop event, String phase, @Nullable de.skyslycer.hmcwraps.discord.DiscordWebhook webhook) {
        String key = "shop.event." + phase;
        if (webhook != null && phase.equals("started")) {
            webhook.eventStart(event);
        } else if (webhook != null) {
            webhook.eventEnd(event);
        }
        String message = plugin.getLanguageManager().get(null, key);
        if (message == null || message.equals(key)) {
            debug.accept("Event shop '" + event.id() + "' " + phase + " but the message " + key + " is missing.");
            return;
        }
        TagResolver values = TagResolver.resolver(
                Placeholder.unparsed("event", event.displayName()),
                Placeholder.unparsed("id", event.id()));
        Component component = plugin.getLanguageManager().parse(null, message, values);
        scheduler.runGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.isOnline()) {
                    StringUtil.sendComponent(online, component);
                }
            }
        });
        debug.accept("Event shop '" + event.id() + "' " + phase + "; announced to the server.");
    }

    /** The daily rotation service, used by the countdown placeholder and the scheduler. */
    public @NotNull DailyShopService daily() {
        return daily;
    }

    public @NotNull ShopRegistry registry() {
        return registry;
    }

    public @NotNull EconomyService economy() {
        return economy;
    }

    @Override
    public boolean isEnabled() {
        return registry.isEnabled();
    }

    @Override
    public @NotNull List<ItemSkin> getDailySkins() {
        DailyShopService.Rotation rotation = daily.current();
        if (rotation == null) {
            return List.of();
        }
        List<ItemSkin> result = new ArrayList<>();
        for (String skinId : rotation.entries()) {
            ItemSkin skin = catalog.skinMap().get(skinId);
            if (skin != null) {
                result.add(skin);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public @NotNull List<ShopEntry> getFeaturedEntries() {
        return registry.featured();
    }

    @Override
    public @NotNull Collection<Bundle> getBundles() {
        return registry.bundles().values();
    }

    @Override
    public @NotNull List<Bundle> getFeaturedBundles() {
        return registry.featured().stream()
                .filter(entry -> entry.type() == ShopEntryType.BUNDLE)
                .map(entry -> registry.bundle(entry.referenceId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public @NotNull Collection<EventShop> getEventShops() {
        return registry.events();
    }

    @Override
    public @NotNull List<EventShop> getActiveEventShops() {
        Instant now = Instant.now();
        return registry.events().stream().filter(event -> event.activeAt(now)).toList();
    }

    @Override
    public @NotNull Optional<Bundle> getBundle(@NotNull String bundleId) {
        return registry.bundle(bundleId);
    }

    @Override
    public @NotNull Instant getDailyRefreshInstant() {
        Instant refresh = daily.nextRefresh();
        return refresh == null ? Instant.now() : refresh;
    }

    @Override
    public @NotNull Duration getEventTimeRemaining(@NotNull String eventId) {
        String normalized = eventId.toLowerCase(Locale.ROOT).trim();
        Instant now = Instant.now();
        return registry.events().stream()
                .filter(event -> event.id().equals(normalized))
                .findFirst()
                .map(event -> {
                    Duration remaining = Duration.between(now, event.end());
                    return remaining.isNegative() ? Duration.ZERO : remaining;
                })
                .orElse(Duration.ZERO);
    }

    @Override
    public @NotNull CompletionStage<Boolean> refreshDailyShop(boolean force) {
        return daily.ensureCurrent(registry.dailyConfiguration(), registry.dailyPool(), force)
                .thenCompose(rotation -> {
                    if (rotation == null) {
                        activeCycleKey = null;
                        return AsyncUtil.completed(false);
                    }
                    DailyShopService.Rotation active = rotation;
                    boolean changed = force || !active.cycleKey().equals(activeCycleKey);
                    activeCycleKey = active.cycleKey();
                    if (!changed) {
                        return AsyncUtil.completed(false);
                    }
                    // A new cycle also re-reads the definition files, so automatic featured entries and
                    // manual edits take effect without a restart.
                    return reload().thenApply(ignored -> {
                        scheduler.runGlobal(() -> Bukkit.getPluginManager().callEvent(new ShopRefreshEvent("daily",
                                active.cycleKey(), active.entries(), Instant.ofEpochMilli(active.expiresAt()), force)));
                        plugin.getLogger().info("Daily shop refreshed (" + active.cycleKey() + ", "
                                + active.entries().size() + " entries" + (force ? ", forced" : "") + ").");
                        plugin.getDiscordWebhook().shopRefresh("daily", active.entries().size());
                        return true;
                    });
                });
    }

    @Override
    public @NotNull CompletionStage<PurchaseQuote> quoteSkin(@NotNull Player player, @NotNull String skinId,
                                                             @Nullable String couponCode) {
        return quoteEntry(player, ShopEntry.skin(skinId), couponCode);
    }

    @Override
    public @NotNull CompletionStage<PurchaseQuote> quoteBundle(@NotNull Player player, @NotNull String bundleId,
                                                               @Nullable String couponCode) {
        Bundle bundle = registry.bundle(bundleId).orElse(null);
        if (bundle == null) {
            return AsyncUtil.completed(invalidQuote(bundleId));
        }
        return quote(player, ShopEntry.bundle(bundleId), bundle, false, couponCode, ShopChannel.BUNDLE, "");
    }

    @Override
    public @NotNull CompletionStage<PurchaseQuote> quoteMissingBundleSkins(@NotNull Player player, @NotNull String bundleId,
                                                                          @Nullable String couponCode) {
        Bundle bundle = registry.bundle(bundleId).orElse(null);
        if (bundle == null) {
            return AsyncUtil.completed(invalidQuote(bundleId));
        }
        return quote(player, ShopEntry.bundle(bundleId), bundle, true, couponCode, ShopChannel.BUNDLE, "");
    }

    @Override
    public @NotNull CompletionStage<BundleQuote> quoteBundleOverview(@NotNull Player player, @NotNull String bundleId) {
        Bundle bundle = registry.bundle(bundleId).orElse(null);
        if (bundle == null) {
            return AsyncUtil.completed(new BundleQuote(0, 0, 0, 0, 0, 0, 0));
        }
        return ownedSkins(player.getUniqueId()).thenApply(owned ->
                PricingService.quoteBundle(bundle.price().amount(), bundle.discount(), bundle.skinIds(),
                        individualPrices(bundle), owned));
    }

    @Override
    public @NotNull CompletionStage<PurchaseQuote> quoteEntry(@NotNull Player player, @NotNull ShopEntry entry,
                                                              @Nullable String couponCode) {
        if (entry.type() == ShopEntryType.BUNDLE) {
            Bundle bundle = registry.bundle(entry.referenceId()).orElse(null);
            if (bundle == null) {
                return AsyncUtil.completed(invalidQuote(entry.referenceId()));
            }
            boolean missingOnly = bundle.purchaseMode() == BundlePurchaseMode.MISSING_ONLY;
            return quote(player, entry, bundle, missingOnly, couponCode, ShopChannel.BUNDLE, "");
        }
        ItemSkin skin = catalog.skinMap().get(entry.referenceId());
        if (skin == null) {
            return AsyncUtil.completed(invalidQuote(entry.referenceId()));
        }
        return quote(player, entry, null, false, couponCode, ShopChannel.DIRECT, "");
    }

    private PurchaseQuote invalidQuote(String targetId) {
        return new PurchaseQuote(targetId, ShopEntryType.SKIN, new SkinPrice("auto", "none", 0), 0, 0, 0, null,
                List.of(), List.of(), true);
    }

    private CompletionStage<PurchaseQuote> quote(Player player, ShopEntry entry, @Nullable Bundle bundle,
                                                 boolean missingOnly, @Nullable String couponCode, ShopChannel channel,
                                                 String sectionId) {
        SkinPrice price = resolvePrice(entry, bundle);
        if (price == null) {
            return AsyncUtil.completed(invalidQuote(entry.referenceId()));
        }
        List<String> skinIds = grantSkinIds(entry, bundle);
        if (skinIds.isEmpty()) {
            return AsyncUtil.completed(invalidQuote(entry.referenceId()));
        }
        String effectiveCoupon = couponCode == null || couponCode.isBlank()
                ? coupons.cachedSelectedCoupon(player.getUniqueId()) : couponCode;
        return ownedSkins(player.getUniqueId()).thenCompose(owned -> {
            List<String> missing = skinIds.stream().filter(skinId -> !owned.contains(skinId)).toList();
            List<String> alreadyOwned = skinIds.stream().filter(owned::contains).toList();
            double original;
            double base;
            if (bundle == null) {
                original = price.amount();
                base = PricingService.applyPercentage(original, entry.discount());
            } else {
                BundleQuote overview = PricingService.quoteBundle(price.amount(), bundle.discount(), bundle.skinIds(),
                        individualPrices(bundle), owned);
                original = overview.totalValue();
                base = missingOnly ? overview.dynamicPrice() : overview.bundlePrice();
            }
            if (missing.isEmpty()) {
                return AsyncUtil.completed(new PurchaseQuote(entry.referenceId(), entry.type(), price, original, 0, 0,
                        null, alreadyOwned, List.of(), true));
            }
            return evaluateCoupon(player, effectiveCoupon, channel, sectionId, entry, bundle, base)
                    .thenApply(discount -> {
                        double finalAmount = Math.max(0, PricingService.round(base - discount));
                        return new PurchaseQuote(entry.referenceId(), entry.type(), price, original,
                                PricingService.round(original - finalAmount), finalAmount,
                                discount > 0 ? effectiveCoupon : null, alreadyOwned, missing, false);
                    });
        });
    }

    private CompletionStage<Double> evaluateCoupon(Player player, @Nullable String couponCode, ShopChannel channel,
                                                   String sectionId, ShopEntry entry, @Nullable Bundle bundle, double base) {
        if (couponCode == null || couponCode.isBlank()) {
            return AsyncUtil.completed(0D);
        }
        List<String> skinIds = grantSkinIds(entry, bundle);
        List<String> bundleIds = bundle == null ? List.of() : List.of(bundle.id());
        Set<String> categories = new LinkedHashSet<>();
        for (String skinId : skinIds) {
            ItemSkin skin = catalog.skinMap().get(skinId);
            if (skin != null) {
                categories.addAll(skin.categoryIds());
            }
        }
        return coupons.evaluate(player.getUniqueId(), couponCode, channel.id(), sectionId, base, skinIds, bundleIds, categories)
                .thenApply(result -> result.usable() ? result.discount() : 0D);
    }

    @Override
    public @NotNull CompletionStage<TransactionResult> purchaseSkin(@NotNull Player player, @NotNull String skinId,
                                                                    @Nullable String couponCode, @NotNull ShopChannel channel) {
        ItemSkin skin = catalog.skinMap().get(normalize(skinId));
        if (!isEnabled() || skin == null) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.UNAVAILABLE, newId(), skinId));
        }
        if (skin.permission() != null && !player.hasPermission(skin.permission())) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.PERMISSION_DENIED, newId(), skin.permission()));
        }
        SkinPrice price = skin.price();
        if (price != null && price.amount() > 0) {
            var provider = economy.providerFor(price.provider(), price.currency()).orElse(null);
            if (provider == null) {
                return AsyncUtil.completed(TransactionResult.of(TransactionStatus.PROVIDER_UNAVAILABLE, newId(), price.provider()));
            }
        }
        ShopEntry entry = ShopEntry.skin(skin.id());
        String effectiveCoupon = effectiveCoupon(player, couponCode);
        return quote(player, entry, null, false, effectiveCoupon, channel, "")
                .thenCompose(quote -> {
                    if (quote.fullyOwned()) {
                        return AsyncUtil.completed(new TransactionResult(TransactionStatus.ALREADY_OWNED, newId(), 0, 0, skin.id()));
                    }
                    return callPurchaseEvent(player, quote, effectiveCoupon, channel, null).thenCompose(allowed -> {
                        if (!allowed) {
                            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.CANCELLED, newId(), "event"));
                        }
                        if (quote.finalAmount() <= 0 || price == null) {
                            return grantFree(player, skin.id(), quote, "purchase");
                        }
                        return transactions.execute(buildRequest(player, player.getUniqueId(), PurchaseKind.SKIN, skin.id(),
                                        List.of(skin.id()), price, quote, effectiveCoupon, channel,
                                        PricingService.skinPriceFunction(price.amount(), entry.discount()),
                                        "skin purchase"), dispatcher(player))
                                .thenCompose(result -> afterPurchase(player, skin.id(), result));
                    });
                });
    }

    @Override
    public @NotNull CompletionStage<TransactionResult> purchaseBundle(@NotNull Player player, @NotNull String bundleId,
                                                                      @Nullable String couponCode, @NotNull ShopChannel channel,
                                                                      boolean missingOnly) {
        Bundle bundle = registry.bundle(bundleId).orElse(null);
        if (!isEnabled() || bundle == null) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.UNAVAILABLE, newId(), bundleId));
        }
        if (missingOnly && !bundle.allowsMissingOnlyPurchase()) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.UNAVAILABLE, newId(), "missing-only disabled"));
        }
        if (!missingOnly && !bundle.allowsFullPurchase()) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.UNAVAILABLE, newId(), "full purchase disabled"));
        }
        if (bundle.permission() != null && !player.hasPermission(bundle.permission())) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.PERMISSION_DENIED, newId(), bundle.permission()));
        }
        SkinPrice price = bundle.price();
        var provider = economy.providerFor(price.provider(), price.currency()).orElse(null);
        if (provider == null) {
            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.PROVIDER_UNAVAILABLE, newId(), price.provider()));
        }
        List<Double> individualPrices = individualPrices(bundle);
        ShopEntry entry = ShopEntry.bundle(bundle.id());
        String effectiveCoupon = effectiveCoupon(player, couponCode);
        return quote(player, entry, bundle, missingOnly, effectiveCoupon, channel, "")
                .thenCompose(quote -> {
                    if (quote.fullyOwned()) {
                        return AsyncUtil.completed(new TransactionResult(TransactionStatus.ALREADY_OWNED, newId(), 0, 0, bundle.id()));
                    }
                    return callPurchaseEvent(player, quote, effectiveCoupon, channel, bundle).thenCompose(allowed -> {
                        if (!allowed) {
                            return AsyncUtil.completed(TransactionResult.of(TransactionStatus.CANCELLED, newId(), "event"));
                        }
                        if (quote.finalAmount() <= 0) {
                            return grantFree(player, bundle.skinIds(), quote, "purchase");
                        }
                        return transactions.execute(buildRequest(player, player.getUniqueId(), PurchaseKind.BUNDLE, bundle.id(),
                                        bundle.skinIds(), price, quote, effectiveCoupon, channel,
                                        PricingService.bundlePriceFunction(PricingService.applyPercentage(price.amount(), bundle.discount()),
                                                missingOnly, individualPrices, bundle.skinIds()),
                                        "bundle purchase"), dispatcher(player))
                                .thenCompose(result -> afterPurchase(player, bundle.id(), result));
                    });
                });
    }

    private CompletionStage<TransactionResult> grantFree(Player player, String skinId, PurchaseQuote quote, String source) {
        return grantFree(player, List.of(skinId), quote, source);
    }

    /** Grants skins that cost nothing (free skins or a 100% coupon) without touching the economy. */
    private CompletionStage<TransactionResult> grantFree(Player player, List<String> skinIds, PurchaseQuote quote, String source) {
        String transactionId = newId();
        return AsyncUtil.safe(() -> storage.grantAll(player.getUniqueId(), quote.missingSkinIds(), source))
                .handle((granted, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("Could not grant free skins for " + player.getUniqueId() + ": "
                                + AsyncUtil.describe(error));
                        return new TransactionResult(TransactionStatus.STORAGE_FAILED, transactionId, 0, 0, skinIds.toString());
                    }
                    ownership.invalidate(player.getUniqueId());
                    plugin.getProfileService().invalidate(player.getUniqueId());
                    announceCollections(player, quote.missingSkinIds());
                    return new TransactionResult(TransactionStatus.SUCCESS, transactionId, 0, 0, null);
                });
    }

    private CompletionStage<TransactionResult> afterPurchase(Player player, String targetId, TransactionResult result) {
        if (!result.successful()) {
            return AsyncUtil.completed(result);
        }
        ownership.invalidate(player.getUniqueId());
        plugin.getProfileService().invalidate(player.getUniqueId());
        List<String> granted = new ArrayList<>();
        ItemSkin skin = catalog.skinMap().get(normalize(targetId));
        if (skin != null) {
            granted.add(skin.id());
        } else {
            Bundle bundle = registry.bundle(targetId).orElse(null);
            if (bundle != null) {
                granted.addAll(bundle.skinIds());
            }
        }
        announceCollections(player, granted);
        plugin.getDiscordWebhook().purchase(player, targetId, result.charged(), null);
        debug.accept("Purchase " + targetId + " for " + player.getUniqueId() + " -> " + result.status()
                + " (" + result.charged() + ")");
        return AsyncUtil.completed(result);
    }

    private void announceCollections(Player player, List<String> grantedSkinIds) {
        if (grantedSkinIds.isEmpty()) {
            return;
        }
        Set<String> collections = new LinkedHashSet<>();
        for (String skinId : grantedSkinIds) {
            ItemSkin skin = catalog.skinMap().get(normalize(skinId));
            if (skin != null && skin.collectionId() != null) {
                collections.add(skin.collectionId());
            }
        }
        for (String collectionId : collections) {
            collections().announceCompletion(player, collectionId);
        }
    }

    private CollectionService collections() {
        return collections;
    }

    @Override
    public @NotNull CompletionStage<CollectionProgress> getCollectionProgress(@NotNull Player player,
                                                                              @NotNull String collectionId) {
        return collections.getProgress(player.getUniqueId(), collectionId);
    }

    @Override
    public @NotNull Collection<ItemSkinCollection> getCollections() {
        return catalog.collectionMap().values();
    }

    @Override
    public @NotNull CompletionStage<Boolean> reload() {
        boolean loaded = registry.load();
        if (!loaded) {
            return AsyncUtil.completed(false);
        }
        return daily.ensureCurrent(registry.dailyConfiguration(), registry.dailyPool(), false)
                .thenApply(rotation -> true);
    }

    @Nullable
    private String effectiveCoupon(Player player, @Nullable String couponCode) {
        if (couponCode != null && !couponCode.isBlank()) {
            return couponCode.toUpperCase(Locale.ROOT).trim();
        }
        return coupons.cachedSelectedCoupon(player.getUniqueId());
    }

    private PurchaseTransactionService.TransactionRequest buildRequest(Player player, UUID owner, PurchaseKind kind,
                                                                       String targetId, List<String> skinIds, SkinPrice price,
                                                                       PurchaseQuote quote, @Nullable String couponCode,
                                                                       ShopChannel channel,
                                                                       java.util.function.Function<Set<String>, Double> amountFor,
                                                                       String description) {
        return new PurchaseTransactionService.TransactionRequest(player.getUniqueId(), owner, kind, targetId, skinIds,
                amountFor, quote.discount(), quote.finalAmount(), couponCode,
                coupons.reservation(), price, economy.providerFor(price.provider(), price.currency()).orElseThrow(),
                null, kind.gift() ? "gift" : "purchase", description, newId(), dispatcher(player));
    }

    /** Runs economy calls on the player's thread, because most economy plugins are not thread safe. */
    public @NotNull PurchaseTransactionService.EconomyDispatcher dispatcher(@NotNull Player player) {
        return new PurchaseTransactionService.EconomyDispatcher() {
            @Override
            public CompletionStage<Double> balance(UUID playerId, de.skyslycer.hmcwraps.skin.EconomyProvider provider, String currency) {
                return onEntity(player, () -> provider.balance(playerId, currency));
            }

            @Override
            public CompletionStage<Boolean> withdraw(UUID playerId, de.skyslycer.hmcwraps.skin.EconomyProvider provider, String currency, double amount) {
                return onEntity(player, () -> provider.withdraw(playerId, currency, amount));
            }

            @Override
            public CompletionStage<Boolean> deposit(UUID playerId, de.skyslycer.hmcwraps.skin.EconomyProvider provider, String currency, double amount) {
                return onEntity(player, () -> provider.deposit(playerId, currency, amount));
            }
        };
    }

    /** Runs a stage producing supplier on the entity thread. */
    public <T> CompletionStage<T> onEntity(Player player, Supplier<CompletionStage<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        scheduler.runOnEntity(player, () -> {
            if (!player.isOnline()) {
                result.completeExceptionally(new IllegalStateException("Player is no longer online"));
                return;
            }
            try {
                CompletionStage<T> stage = operation.get();
                if (stage == null) {
                    result.completeExceptionally(new IllegalStateException("Economy provider returned no operation"));
                    return;
                }
                stage.whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private CompletionStage<Boolean> callPurchaseEvent(Player player, PurchaseQuote quote, @Nullable String couponCode,
                                                      ShopChannel channel, @Nullable Bundle bundle) {
        return callCancellable(player, () -> bundle == null
                ? new SkinPurchaseEvent(player, quote, couponCode == null ? "" : couponCode, channel)
                : new BundlePurchaseEvent(player, quote, couponCode == null ? "" : couponCode, channel,
                        bundle.purchaseMode() == BundlePurchaseMode.MISSING_ONLY));
    }

    /**
     * Calls a cancellable event on the player's thread without ever blocking the caller, so the shop is
     * safe on Folia too.
     */
    public <E extends Event & Cancellable> CompletionStage<Boolean> callCancellable(Player player, Supplier<E> supplier) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable call = () -> result.complete(callEvent(supplier));
        if (!scheduler.isFolia() && Bukkit.isPrimaryThread()) {
            call.run();
        } else {
            scheduler.runOnEntity(player, call);
        }
        return result;
    }

    private <E extends Event & Cancellable> boolean callEvent(Supplier<E> supplier) {
        try {
            E event = supplier.get();
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("A purchase event handler failed: " + AsyncUtil.describe(throwable));
            return true;
        }
    }

    private CompletionStage<Set<String>> ownedSkins(UUID playerId) {
        return AsyncUtil.safe(() -> ownership.getOwnedSkinIds(playerId)).thenApply(owned -> owned == null ? Set.of() : owned);
    }

    private @Nullable SkinPrice resolvePrice(ShopEntry entry, @Nullable Bundle bundle) {
        if (bundle != null) {
            return bundle.price();
        }
        if (entry.price() != null) {
            return entry.price();
        }
        ItemSkin skin = catalog.skinMap().get(entry.referenceId());
        return skin == null ? null : skin.price();
    }

    private List<String> grantSkinIds(ShopEntry entry, @Nullable Bundle bundle) {
        if (bundle != null) {
            return bundle.skinIds();
        }
        ItemSkin skin = catalog.skinMap().get(entry.referenceId());
        return skin == null ? List.of() : List.of(skin.id());
    }

    private List<Double> individualPrices(Bundle bundle) {
        return bundle.skinIds().stream().map(skinId -> {
            ItemSkin skin = catalog.skinMap().get(skinId);
            return skin == null || skin.price() == null ? null : skin.price().amount();
        }).toList();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }
}
