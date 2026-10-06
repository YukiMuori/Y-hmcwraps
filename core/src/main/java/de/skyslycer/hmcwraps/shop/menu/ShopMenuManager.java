package de.skyslycer.hmcwraps.shop.menu;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.lang.LanguageManager;
import de.skyslycer.hmcwraps.serialization.shop.ShopSettings;
import de.skyslycer.hmcwraps.shop.Bundle;
import de.skyslycer.hmcwraps.shop.Coupon;
import de.skyslycer.hmcwraps.shop.EventShop;
import de.skyslycer.hmcwraps.shop.GiftRecord;
import de.skyslycer.hmcwraps.shop.GiftResult;
import de.skyslycer.hmcwraps.shop.PurchaseKind;
import de.skyslycer.hmcwraps.shop.PurchaseQuote;
import de.skyslycer.hmcwraps.shop.ShopChannel;
import de.skyslycer.hmcwraps.shop.ShopEntry;
import de.skyslycer.hmcwraps.shop.ShopEntryType;
import de.skyslycer.hmcwraps.shop.SkinProfile;
import de.skyslycer.hmcwraps.shop.TransactionResult;
import de.skyslycer.hmcwraps.shop.TransactionStatus;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The inventory UI of the shop.
 *
 * <p>Screens never calculate prices or ownership themselves: they display the catalog and forward the
 * player's intent to {@link de.skyslycer.hmcwraps.shop.ShopService}, which revalidates everything
 * server-side. Every screen is a plain Bukkit inventory (like the item skin menu), so no extra GUI
 * dependency is required and resource-pack items keep rendering.</p>
 */
public final class ShopMenuManager implements Listener {

    /** Slots available for content; the last row is reserved for navigation. */
    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;
    private static final java.util.regex.Pattern LEGACY_PATTERN = java.util.regex.Pattern
            .compile("(?i)[&\u00a7][0-9a-fk-or]|[&\u00a7]#[0-9a-f]{6}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final HMCWrapsPlugin plugin;
    private final Map<UUID, ShopSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> giftPrompts = new ConcurrentHashMap<>();

    public ShopMenuManager(@NotNull HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    /** The shop home screen with the featured entries and the section buttons. */
    public void openHome(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.HOME));
    }

    public void openDaily(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.DAILY));
    }

    public void openBundles(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.BUNDLES));
    }

    public void openEvents(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.EVENTS));
    }

    public void openCoupons(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.COUPONS));
    }

    public void openGifts(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.GIFTS));
    }

    public void openProfile(@NotNull Player player) {
        open(player, ShopSession.list(player, Screen.PROFILE));
    }

    public void openBundle(@NotNull Player player, @NotNull String bundleId) {
        open(player, ShopSession.detail(player, Screen.BUNDLE, bundleId));
    }

    public void openEvent(@NotNull Player player, @NotNull String eventId) {
        open(player, ShopSession.detail(player, Screen.EVENT, eventId));
    }

    /** Confirmation screen for a gift created by {@code /itemskin gift <player> <skin>}. */
    public void openGiftConfirm(@NotNull Player player, @NotNull PurchaseKind kind, @NotNull String targetId,
                               @NotNull UUID recipientId, @NotNull String recipientName) {
        open(player, ShopSession.gift(player, kind, targetId, recipientId, recipientName));
    }

    /** Closes every shop screen; used on reload and shutdown. */
    public void closeAll() {
        for (ShopSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.isOnline()) {
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> player.closeInventory());
            }
        }
        sessions.clear();
        giftPrompts.clear();
    }

    // ------------------------------------------------------------------ open and render

    private void open(Player player, ShopSession session) {
        var shop = plugin.getShopService();
        if (shop == null || !shop.isEnabled()) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        sessions.put(player.getUniqueId(), session);
        load(player, session, false, false);
    }

    /**
     * Loads the ownership snapshot and renders afterwards.
     *
     * @param profile whether the screen additionally needs the profile statistics
     * @param gifts   whether the screen additionally needs the pending gifts
     */
    private void load(Player player, ShopSession session, boolean profile, boolean gifts) {
        SkinOwnershipService ownership = plugin.getSkinOwnership();
        CompletionStage<Set<String>> owned = ownership == null ? AsyncUtil.completed(Set.of())
                : ownership.getOwnedSkinIds(player.getUniqueId());
        CompletionStage<Set<String>> favorites = ownership == null ? AsyncUtil.completed(Set.of())
                : ownership.getFavoriteSkinIds(player.getUniqueId());
        owned.thenCombine(favorites, Snapshot::new)
                .thenCompose(snapshot -> {
                    session.owned = snapshot.owned();
                    session.favorites = snapshot.favorites();
                    if (profile && plugin.getProfileService() != null) {
                        return plugin.getProfileService().getProfile(player.getUniqueId())
                                .thenApply(loaded -> {
                                    session.profile = loaded;
                                    return snapshot;
                                });
                    }
                    return AsyncUtil.completed(snapshot);
                })
                .thenCompose(snapshot -> {
                    if (gifts && plugin.getGiftService() != null) {
                        return plugin.getGiftService().pendingNotifications(player.getUniqueId())
                                .thenApply(loaded -> {
                                    session.gifts = loaded;
                                    return snapshot;
                                });
                    }
                    return AsyncUtil.completed(snapshot);
                })
                .whenComplete((snapshot, error) -> runAtEntity(player, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Could not load the shop screen of " + player.getName() + ": "
                                + AsyncUtil.describe(error));
                    }
                    render(player, session);
                }));
    }

    private void render(Player player, ShopSession session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
            return;
        }
        session.actions.clear();
        int size = session.screen.detail() ? 27 : 54;
        Inventory inventory = Bukkit.createInventory(session, size,
                StringUtil.LEGACY_SERIALIZER.serialize(title(player, session)));
        session.inventory = inventory;
        switch (session.screen) {
            case HOME -> renderHome(player, session);
            case DAILY -> renderDaily(player, session);
            case BUNDLES -> renderBundles(player, session);
            case BUNDLE -> renderBundle(player, session);
            case EVENTS -> renderEvents(player, session);
            case EVENT -> renderEvent(player, session);
            case COUPONS -> renderCoupons(player, session);
            case GIFTS -> renderGifts(player, session);
            case PROFILE -> renderProfile(player, session);
            case GIFT_CONFIRM -> renderGiftConfirm(player, session);
        }
        player.openInventory(inventory);
    }

    /** Re-renders the screen a player currently has open, if any. */
    private void refresh(Player player) {
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        load(player, session, session.screen == Screen.PROFILE, session.screen == Screen.GIFTS);
    }

    private Component title(Player player, ShopSession session) {
        Component base = localized(player, "shop.menu.title");
        if (session.screen == Screen.HOME) {
            return base;
        }
        return base.append(StringUtil.MINI_MESSAGE.deserialize(" <dark_gray>\u00bb "))
                .append(localized(player, "shop.menu." + session.screen.key()));
    }

    // ------------------------------------------------------------------ screens

    private void renderHome(Player player, ShopSession session) {
        List<Entry> entries = new ArrayList<>();
        for (ShopEntry entry : plugin.getShopService().getFeaturedEntries()) {
            if (!visible(player, entry)) {
                continue;
            }
            entries.add(entryItem(player, session, entry, ShopChannel.FEATURED));
        }
        if (entries.size() > CONTENT_SLOTS) {
            entries = entries.subList(0, CONTENT_SLOTS);
        }
        paginate(player, session, entries, null);
        button(player, session, SLOT_BACK + 0, Material.CLOCK, "shop.menu.daily", click -> open(player, ShopSession.list(player, Screen.DAILY)));
        button(player, session, SLOT_BACK + 1, Material.SHULKER_BOX, "shop.menu.bundles", click -> open(player, ShopSession.list(player, Screen.BUNDLES)));
        button(player, session, SLOT_BACK + 2, Material.NETHER_STAR, "shop.menu.events", click -> open(player, ShopSession.list(player, Screen.EVENTS)));
        button(player, session, SLOT_BACK + 3, Material.PAPER, "shop.menu.coupons", click -> open(player, ShopSession.list(player, Screen.COUPONS)));
        button(player, session, SLOT_BACK + 4, Material.CHEST, "shop.menu.gifts", click -> open(player, ShopSession.list(player, Screen.GIFTS)));
        button(player, session, SLOT_BACK + 5, Material.PLAYER_HEAD, "shop.menu.profile", click -> open(player, ShopSession.list(player, Screen.PROFILE)));
        closeButton(player, session);
    }

    private void renderDaily(Player player, ShopSession session) {
        var shop = plugin.getShopService();
        List<Entry> entries = new ArrayList<>();
        for (ItemSkin skin : shop.getDailySkins()) {
            entries.add(skinItem(player, session, skin, ShopChannel.DAILY, null));
        }
        paginate(player, session, entries, "shop.menu.time-remaining", timeUntil(shop.getDailyRefreshInstant()));
        backButton(player, session);
        closeButton(player, session);
    }

    private void renderBundles(Player player, ShopSession session) {
        List<Entry> entries = new ArrayList<>();
        for (Bundle bundle : plugin.getShopService().getBundles()) {
            entries.add(bundleItem(player, session, bundle, null));
        }
        paginate(player, session, entries, null);
        backButton(player, session);
        closeButton(player, session);
    }

    private void renderBundle(Player player, ShopSession session) {
        Bundle bundle = plugin.getShopService().getBundle(session.referenceId).orElse(null);
        if (bundle == null) {
            send(player, "shop.purchase.unavailable");
            player.closeInventory();
            return;
        }
        plugin.getShopService().quoteBundleOverview(player, bundle.id()).whenComplete((quote, error) -> {
            if (error != null || quote == null) {
                return;
            }
            runAtEntity(player, () -> {
                if (sessions.get(player.getUniqueId()) != session) {
                    return;
                }
                TagResolver values = resolvers(
                        Placeholder.unparsed("bundle", bundle.displayName()),
                        Placeholder.unparsed("owned", String.valueOf(quote.ownedCount())),
                        Placeholder.unparsed("total", String.valueOf(quote.totalCount())),
                        Placeholder.unparsed("count", String.valueOf(quote.missingCount())),
                        Placeholder.unparsed("amount", formatAmount(quote.dynamicPrice())),
                        Placeholder.unparsed("saving", formatAmount(quote.saving())));
                session.inventory.setItem(4, item(Material.SHULKER_BOX, bundle.displayName(),
                        quote.complete() ? List.of(localizedText(player, "shop.bundle.complete", values))
                                : List.of(localizedText(player, "shop.bundle.owned-partially", values),
                                localizedText(player, "shop.bundle.full-price", values),
                                localizedText(player, "shop.bundle.dynamic-price", values))));
                if (!quote.complete()) {
                    session.inventory.setItem(11, item(Material.GOLD_INGOT, localizedText(player, "shop.bundle.buy-full",
                                    resolvers(Placeholder.unparsed("amount", formatAmount(quote.bundlePrice())))),
                            List.of(localizedText(player, "shop.menu.buy", values))));
                    session.actions.put(11, click -> buyBundle(player, bundle.id(), false));
                    if (bundle.allowsMissingOnlyPurchase() && quote.ownedCount() > 0) {
                        session.inventory.setItem(15, item(Material.LIME_DYE, localizedText(player, "shop.bundle.buy-missing",
                                        values),
                                List.of(localizedText(player, "shop.menu.buy", values))));
                        session.actions.put(15, click -> buyBundle(player, bundle.id(), true));
                    }
                }
                session.actions.put(18, click -> open(player, ShopSession.list(player, Screen.BUNDLES)));
            });
        });
        button(player, session, 18, Material.BARRIER, "shop.menu.back",
                click -> open(player, ShopSession.list(player, Screen.BUNDLES)));
        closeButton(player, session);
        session.inventory.setItem(4, item(Material.SHULKER_BOX, bundle.displayName(), List.of(
                localizedText(player, "shop.bundle.owned-partially", baseResolvers()))));
    }

    private void renderEvents(Player player, ShopSession session) {
        Instant now = Instant.now();
        List<Entry> entries = new ArrayList<>();
        for (EventShop event : plugin.getShopService().getEventShops()) {
            boolean active = event.activeAt(now);
            boolean ended = event.endedAt(now);
            List<String> lore = new ArrayList<>();
            lore.add(localizedText(player, active ? "shop.menu.event-remaining"
                    : ended ? "shop.menu.event-closed" : "shop.menu.event-soon",
                    resolvers(Placeholder.unparsed("time", timeUntil(active
                            ? plugin.getShopService().getEventTimeRemaining(event.id())
                            : Duration.between(now, event.start()))))));
            ItemStack icon = event.icon() == null ? new ItemStack(Material.NETHER_STAR) : event.icon().clone();
            Consumer<ClickType> action = active
                    ? click -> open(player, ShopSession.detail(player, Screen.EVENT, event.id())) : null;
            entries.add(new Entry(named(icon, event.displayName(), lore), action));
        }
        paginate(player, session, entries, null);
        backButton(player, session);
        closeButton(player, session);
    }

    private void renderEvent(Player player, ShopSession session) {
        EventShop event = plugin.getShopService().getEventShops().stream()
                .filter(candidate -> candidate.id().equals(session.referenceId)).findFirst().orElse(null);
        if (event == null || !event.activeAt(Instant.now())) {
            send(player, "shop.purchase.unavailable");
            open(player, ShopSession.list(player, Screen.EVENTS));
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (ShopEntry entry : event.entries()) {
            if (!visible(player, entry)) {
                continue;
            }
            entries.add(entryItem(player, session, entry, ShopChannel.EVENT));
        }
        paginate(player, session, entries, "shop.menu.event-remaining",
                timeUntil(plugin.getShopService().getEventTimeRemaining(event.id())));
        backButton(player, session);
        closeButton(player, session);
    }

    private void renderCoupons(Player player, ShopSession session) {
        var coupons = plugin.getCouponService();
        String selected = coupons == null ? null : coupons.cachedSelectedCoupon(player.getUniqueId());
        List<Entry> entries = new ArrayList<>();
        if (coupons != null) {
            for (Coupon coupon : coupons.getCoupons().stream()
                    .sorted(Comparator.comparing(Coupon::code)).toList()) {
                List<String> lore = new ArrayList<>();
                lore.add(localizedText(player, "shop.menu.balance",
                        resolvers(Placeholder.unparsed("amount", discountText(coupon)))));
                if (coupon.minSpend() > 0) {
                    lore.add(localizedText(player, "shop.coupon.min-spend",
                            resolvers(Placeholder.unparsed("amount", formatAmount(coupon.minSpend())))));
                }
                if (coupon.expiresAt() != null) {
                    lore.add(localizedText(player, "shop.menu.time-remaining",
                            resolvers(Placeholder.unparsed("time", timeUntil(Duration.between(Instant.now(),
                                    coupon.expiresAt()))))));
                }
                lore.add(localizedText(player, coupon.code().equals(selected) ? "shop.coupon.selected"
                        : "shop.menu.select", resolvers(Placeholder.unparsed("code", coupon.code()))));
                String name = coupon.code().equals(selected) ? "<green>" + coupon.code() : "<gray>" + coupon.code();
                entries.add(new Entry(item(Material.PAPER, name, lore), click -> {
                    if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                        coupons.setSelectedCoupon(player.getUniqueId(), null);
                        send(player, "shop.coupon.cleared");
                    } else {
                        coupons.setSelectedCoupon(player.getUniqueId(), coupon.code());
                        send(player, "shop.coupon.selected", Placeholder.unparsed("code", coupon.code()));
                    }
                    refresh(player);
                }));
            }
        }
        paginate(player, session, entries, null);
        button(player, session, SLOT_BACK, Material.BARRIER, "shop.menu.clear-coupon",
                click -> {
                    if (coupons != null) {
                        coupons.setSelectedCoupon(player.getUniqueId(), null);
                    }
                    send(player, "shop.coupon.cleared");
                    refresh(player);
                });
        button(player, session, SLOT_BACK + 3, Material.CHEST, "shop.menu.title",
                click -> open(player, ShopSession.list(player, Screen.HOME)));
        closeButton(player, session);
    }

    private void renderGifts(Player player, ShopSession session) {
        List<GiftRecord> gifts = session.gifts == null ? List.of() : session.gifts;
        List<Entry> entries = new ArrayList<>();
        for (GiftRecord gift : gifts) {
            String sender = gift.senderName() == null ? gift.senderId().toString() : gift.senderName();
            String target = gift.targetId();
            List<String> lore = List.of(localizedText(player, "shop.gift.pending", resolvers(
                    Placeholder.unparsed("player", sender),
                    Placeholder.unparsed("target", target),
                    Placeholder.unparsed("message", gift.message() == null ? "" : gift.message()))));
            entries.add(new Entry(item(Material.CHEST, sender + " » " + target, lore), null));
        }
        if (entries.isEmpty()) {
            entries.add(new Entry(item(Material.GRAY_DYE, localizedText(player, "shop.gift.none", baseResolvers()),
                    List.of()), null));
        }
        paginate(player, session, entries, null);
        button(player, session, SLOT_BACK, Material.BARRIER, "shop.menu.mark-read", click -> {
            if (plugin.getGiftService() != null && !gifts.isEmpty()) {
                plugin.getGiftService().markNotified(player.getUniqueId(),
                        gifts.stream().map(GiftRecord::giftId).toList());
                send(player, "shop.menu.mark-read-done");
            }
            refresh(player);
        });
        button(player, session, SLOT_BACK + 3, Material.CHEST, "shop.menu.title",
                click -> open(player, ShopSession.list(player, Screen.HOME)));
        closeButton(player, session);
    }

    private void renderProfile(Player player, ShopSession session) {
        SkinProfile profile = session.profile;
        TagResolver values = baseResolvers();
        session.inventory.setItem(4, item(Material.PLAYER_HEAD, player.getName(), profile == null
                ? List.of(localizedText(player, "shop.purchase.error", values))
                : List.of(
                localizedText(player, "shop.menu.stat-owned", resolvers(with("count", String.valueOf(profile.ownedSkins())))),
                localizedText(player, "shop.menu.stat-favorites", resolvers(with("count", String.valueOf(profile.favoriteSkins())))),
                localizedText(player, "shop.menu.stat-collections", resolvers(with("count", String.valueOf(profile.collectionsCompleted())))),
                localizedText(player, "shop.menu.stat-purchases", resolvers(with("count", String.valueOf(profile.purchasedSkins())))),
                localizedText(player, "shop.menu.stat-gifts-sent", resolvers(with("count", String.valueOf(profile.giftedSkins())))),
                localizedText(player, "shop.menu.stat-gifts-received", resolvers(with("count", String.valueOf(profile.receivedSkins())))),
                localizedText(player, "shop.menu.stat-coupons", resolvers(with("count", String.valueOf(profile.couponsRedeemed())))),
                localizedText(player, "shop.menu.stat-xp", resolvers(with("amount", formatAmount(profile.collectionXp())))),
                localizedText(player, "shop.menu.stat-first-purchase", resolvers(with("date",
                        profile.firstPurchaseAt() == null ? "-" : DATE_FORMAT.format(Instant.ofEpochMilli(profile.firstPurchaseAt()))))))));
        button(player, session, SLOT_BACK, Material.CHEST, "shop.menu.history", click -> {
        });
        button(player, session, SLOT_BACK + 3, Material.CHEST, "shop.menu.title",
                click -> open(player, ShopSession.list(player, Screen.HOME)));
        closeButton(player, session);
        var repository = plugin.getPurchaseRepository();
        if (repository == null) {
            return;
        }
        repository.history(player.getUniqueId(), historyLimit()).whenComplete((records, error) -> runAtEntity(player, () -> {
            if (error != null || records == null || sessions.get(player.getUniqueId()) != session) {
                return;
            }
            int slot = 18;
            for (var record : records) {
                if (slot >= SLOT_BACK || slot >= 26) {
                    break;
                }
                session.inventory.setItem(slot++, item(Material.BOOK, record.targetId(), List.of(
                        localizedText(player, "shop.menu.balance",
                                resolvers(Placeholder.unparsed("amount", formatAmount(record.amount())))),
                        localizedText(player, "shop.menu.history", baseResolvers()))));
            }
        }));
    }

    private void renderGiftConfirm(Player player, ShopSession session) {
        TagResolver values = resolvers(
                Placeholder.unparsed("player", session.recipientName),
                Placeholder.unparsed("target", session.referenceId));
        session.inventory.setItem(4, item(Material.NAME_TAG, session.recipientName, List.of(
                localizedText(player, "shop.menu.gift", values))));
        session.inventory.setItem(11, item(Material.LIME_DYE, localizedText(player, "shop.menu.confirm", values),
                List.of(localizedText(player, "shop.gift.sent", values))));
        session.actions.put(11, click -> confirmGift(player, session));
        session.inventory.setItem(15, item(Material.RED_DYE, localizedText(player, "shop.menu.cancel", values),
                List.of(localizedText(player, "shop.menu.back", values))));
        session.actions.put(15, click -> player.closeInventory());
        var shop = plugin.getShopService();
        if (shop == null) {
            return;
        }
        CompletionStage<GiftQuote> quote = session.kind == PurchaseKind.SKIN
                ? shop.quoteSkin(player, session.referenceId, null).thenApply(result ->
                        new GiftQuote(result.originalAmount(), result.finalAmount()))
                : shop.quoteBundle(player, session.referenceId, null).thenApply(result ->
                        new GiftQuote(result.originalAmount(), result.finalAmount()));
        quote.whenComplete((result, error) -> runAtEntity(player, () -> {
            if (error != null || result == null || sessions.get(player.getUniqueId()) != session) {
                return;
            }
            session.inventory.setItem(13, item(Material.GOLD_INGOT, formatAmount(result.price()), List.of(
                    localizedText(player, "shop.bundle.full-price", resolvers(
                            Placeholder.unparsed("bundle", session.referenceId),
                            Placeholder.unparsed("amount", formatAmount(result.price())),
                            Placeholder.unparsed("saving", formatAmount(Math.max(0, result.value() - result.price()))))))));
        }));
    }

    // ------------------------------------------------------------------ items

    private Entry entryItem(Player player, ShopSession session, ShopEntry entry, ShopChannel channel) {
        if (entry.type() == ShopEntryType.BUNDLE) {
            Bundle bundle = plugin.getShopService().getBundle(entry.referenceId()).orElse(null);
            return bundle == null ? new Entry(new ItemStack(Material.BARRIER), null) : bundleItem(player, session, bundle, entry);
        }
        ItemSkin skin = plugin.getSkinCatalog().skinMap().get(entry.referenceId());
        return skin == null ? new Entry(new ItemStack(Material.BARRIER), null)
                : skinItem(player, session, skin, channel, entry);
    }

    private Entry skinItem(Player player, ShopSession session, ItemSkin skin, ShopChannel channel, @Nullable ShopEntry entry) {
        boolean owned = session.owned.contains(skin.id());
        List<String> lore = new ArrayList<>();
        lore.add(localizedText(player, owned ? "shop.menu.owned" : "shop.menu.locked", baseResolvers()));
        if (skin.price() != null) {
            lore.add(localizedText(player, "shop.menu.balance",
                    resolvers(Placeholder.unparsed("amount", formatAmount(skin.price().amount()) + " " + skin.price().currency()))));
        }
        if (!owned) {
            lore.add(localizedText(player, "shop.menu.buy", baseResolvers()));
        }
        lore.add(localizedText(player, "shop.menu.preview", baseResolvers()));
        lore.add(localizedText(player, "shop.menu.gift", baseResolvers()));
        ItemStack icon = icon(player, skin);
        return new Entry(named(icon, skin.displayName(), lore), click -> {
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT) {
                preview(player, skin);
            } else if (click == ClickType.SHIFT_RIGHT) {
                giftPrompts.put(player.getUniqueId(), skin.id());
                send(player, "shop.menu.gift-prompt");
                player.closeInventory();
            } else if (!owned) {
                buySkin(player, skin.id(), channel);
            }
        });
    }

    private Entry bundleItem(Player player, ShopSession session, Bundle bundle, @Nullable ShopEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add(localizedText(player, "shop.menu.buy", baseResolvers()));
        ItemStack icon = bundle.icon() == null ? new ItemStack(Material.SHULKER_BOX) : bundle.icon().clone();
        return new Entry(named(icon, bundle.displayName(), lore),
                click -> open(player, ShopSession.detail(player, Screen.BUNDLE, bundle.id())));
    }

    private ItemStack icon(Player player, ItemSkin skin) {
        SkinIconConfiguration configuration = plugin.getSkinCatalog().skinIconConfiguration(skin.id());
        if (configuration != null) {
            ItemStack created = plugin.getItemIconFactory().create(configuration, skin.displayName(), player);
            if (created != null && !created.getType().isAir()) {
                return created.clone();
            }
        }
        ItemStack fallback = skin.icon();
        return fallback == null ? new ItemStack(Material.PAPER) : fallback.clone();
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        return named(stack, name, lore);
    }

    private ItemStack named(ItemStack stack, String name, List<String> lore) {
        return ItemBuilder.from(stack)
                .name(StringUtil.parseComponent(renderable(name)))
                .lore(lore.stream().map(line -> StringUtil.parseComponent(renderable(line))).toList())
                .build();
    }

    /** Converts configured legacy colour codes, but leaves plain MiniMessage text alone. */
    private static String renderable(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY_PATTERN.matcher(text).find() ? StringUtil.legacyToMiniMessage(text) : text;
    }

    // ------------------------------------------------------------------ actions

    private void buySkin(Player player, String skinId, ShopChannel channel) {
        var shop = plugin.getShopService();
        if (shop == null) {
            return;
        }
        finish(player, shop.purchaseSkin(player, skinId, selectedCoupon(player), channel), skinId, false, channel);
    }

    private void buyBundle(Player player, String bundleId, boolean missingOnly) {
        var shop = plugin.getShopService();
        if (shop == null) {
            return;
        }
        finish(player, shop.purchaseBundle(player, bundleId, selectedCoupon(player), ShopChannel.BUNDLE, missingOnly),
                bundleId, true, ShopChannel.BUNDLE);
    }

    private void finish(Player player, CompletionStage<TransactionResult> stage, String target, boolean bundle,
                        ShopChannel channel) {
        stage.whenComplete((result, error) -> runAtEntity(player, () -> {
            if (error != null || result == null) {
                send(player, "shop.purchase.error");
                return;
            }
            feedback(player, result, target, bundle);
            refresh(player);
        }));
    }

    private void confirmGift(Player player, ShopSession session) {
        var gifts = plugin.getGiftService();
        if (gifts == null) {
            return;
        }
        CompletionStage<GiftResult> stage = session.kind == PurchaseKind.SKIN
                ? gifts.giftSkin(player, session.recipientId, session.recipientName, session.referenceId, null)
                : gifts.giftBundle(player, session.recipientId, session.recipientName, session.referenceId, null);
        stage.whenComplete((result, error) -> runAtEntity(player, () -> {
            if (error != null || result == null) {
                send(player, "shop.gift.failed");
            } else if (result.successful()) {
                send(player, "shop.gift.sent", resolvers(
                        Placeholder.unparsed("target", session.referenceId),
                        Placeholder.unparsed("player", session.recipientName),
                        Placeholder.unparsed("amount", formatAmount(result.charged()))));
            } else {
                send(player, giftFailureKey(result.status()), resolvers(
                        Placeholder.unparsed("target", session.referenceId),
                        Placeholder.unparsed("player", session.recipientName),
                        Placeholder.unparsed("amount", formatAmount(result.charged()))));
            }
            player.closeInventory();
        }));
    }

    private void preview(Player player, ItemSkin skin) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(player, "messages.no-item");
            return;
        }
        plugin.getItemSkinManager().preview(player, hand, skin);
    }

    private void feedback(Player player, TransactionResult result, String target, boolean bundle) {
        TagResolver values = resolvers(
                Placeholder.unparsed("target", target),
                Placeholder.unparsed("bundle", target),
                Placeholder.unparsed("amount", formatAmount(result.charged())),
                Placeholder.unparsed("count", formatAmount(result.charged())));
        switch (result.status()) {
            case SUCCESS -> send(player, bundle ? "shop.purchase.bundle-success" : "shop.purchase.success", values);
            case ALREADY_OWNED -> send(player, "messages.already-owned", resolvers(Placeholder.unparsed("skin", target)));
            case INSUFFICIENT_FUNDS -> send(player, "shop.purchase.insufficient", values);
            case PAYMENT_FAILED -> send(player, "shop.purchase.failed", values);
            case STORAGE_FAILED -> send(player, "shop.purchase.storage", values);
            case BUSY -> send(player, "shop.purchase.busy", values);
            case INVALID -> send(player, "shop.purchase.invalid", values);
            case UNAVAILABLE -> send(player, "shop.purchase.unavailable", values);
            case PERMISSION_DENIED -> send(player, "shop.purchase.permission", values);
            case PROVIDER_UNAVAILABLE -> send(player, "shop.purchase.provider", values);
            case CANCELLED -> send(player, "shop.purchase.cancelled", values);
            case COUPON_REJECTED -> send(player, "shop.coupon.not-applicable", values);
            case PENDING, ERROR -> send(player, "shop.purchase.error", values);
        }
    }

    private static String giftFailureKey(TransactionStatus status) {
        return switch (status) {
            case INSUFFICIENT_FUNDS -> "shop.purchase.insufficient";
            case ALREADY_OWNED -> "shop.gift.already-owned";
            case UNAVAILABLE -> "shop.purchase.unavailable";
            case PERMISSION_DENIED -> "shop.purchase.permission";
            case BUSY -> "shop.purchase.busy";
            case CANCELLED -> "shop.purchase.cancelled";
            case PROVIDER_UNAVAILABLE -> "shop.purchase.provider";
            default -> "shop.gift.failed";
        };
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopSession session)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || !event.getClickedInventory().equals(session.inventory)) {
            return;
        }
        if (sessions.get(player.getUniqueId()) != session) {
            return;
        }
        Consumer<ClickType> action = session.actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event.getClick());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopSession) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopSession session)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (sessions.get(player.getUniqueId()) == session) {
            sessions.remove(player.getUniqueId());
        }
    }

    /** Resolves the {@code /itemskin gift <player> <skin>} prompt started from a shop screen. */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String targetId = giftPrompts.remove(player.getUniqueId());
        if (targetId == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage().trim();
        if (input.equalsIgnoreCase("cancel")) {
            return;
        }
        Player online = Bukkit.getPlayerExact(input);
        UUID recipientId = null;
        String recipientName = input;
        if (online != null) {
            recipientId = online.getUniqueId();
            recipientName = online.getName();
        } else {
            try {
                var offline = Bukkit.getOfflinePlayer(input);
                if (offline != null && offline.hasPlayedBefore()) {
                    recipientId = offline.getUniqueId();
                }
            } catch (RuntimeException ignored) {
                // Invalid names are reported below like unknown players.
            }
        }
        UUID resolved = recipientId;
        String resolvedName = recipientName;
        runAtEntity(player, () -> {
            if (resolved == null) {
                send(player, "shop.gift.offline");
                return;
            }
            if (resolved.equals(player.getUniqueId())) {
                send(player, "shop.gift.self");
                return;
            }
            if (online == null && (plugin.getGiftService() == null || !plugin.getGiftService().allowsOffline())) {
                send(player, "shop.gift.offline");
                return;
            }
            openGiftConfirm(player, PurchaseKind.SKIN, targetId, resolved, resolvedName);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        giftPrompts.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ helpers

    private void paginate(Player player, ShopSession session, List<Entry> entries, @Nullable String footerKey,
                          @Nullable String footerValue) {
        int pages = Math.max(1, (entries.size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        session.page = Math.max(0, Math.min(session.page, pages - 1));
        int start = session.page * CONTENT_SLOTS;
        for (int index = 0; index < CONTENT_SLOTS; index++) {
            int position = start + index;
            if (position >= entries.size()) {
                break;
            }
            Entry entry = entries.get(position);
            session.inventory.setItem(index, entry.item());
            if (entry.action() != null) {
                session.actions.put(index, entry.action());
            }
        }
        if (entries.isEmpty()) {
            session.inventory.setItem(22, item(Material.GRAY_DYE,
                    localizedText(player, "shop.menu.no-entries", baseResolvers()), List.of()));
        }
        if (footerKey != null) {
            session.inventory.setItem(SLOT_PAGE, item(Material.CLOCK,
                    localizedText(player, footerKey, resolvers(Placeholder.unparsed("time",
                            footerValue == null ? "-" : footerValue))), List.of()));
        } else if (pages > 1) {
            session.inventory.setItem(SLOT_PAGE, item(Material.PAPER,
                    localizedText(player, "shop.menu.page", resolvers(
                            Placeholder.unparsed("page", String.valueOf(session.page + 1)),
                            Placeholder.unparsed("pages", String.valueOf(pages)))), List.of()));
        }
        if (pages > 1) {
            if (session.page > 0) {
                button(player, session, SLOT_PREVIOUS, Material.ARROW, "shop.menu.previous", click -> {
                    session.page--;
                    render(player, session);
                });
            }
            if (session.page + 1 < pages) {
                button(player, session, SLOT_NEXT, Material.ARROW, "shop.menu.next", click -> {
                    session.page++;
                    render(player, session);
                });
            }
        }
    }

    private void paginate(Player player, ShopSession session, List<Entry> entries, @Nullable String footerKey) {
        paginate(player, session, entries, footerKey, null);
    }

    private void button(Player player, ShopSession session, int slot, Material material, String key,
                        Consumer<ClickType> action) {
        session.inventory.setItem(slot, item(material, localizedText(player, key, baseResolvers()), List.of()));
        session.actions.put(slot, action);
    }

    private void backButton(Player player, ShopSession session) {
        button(player, session, SLOT_BACK, Material.BARRIER, "shop.menu.back",
                click -> open(player, ShopSession.list(player, Screen.HOME)));
    }

    private void closeButton(Player player, ShopSession session) {
        button(player, session, session.inventory.getSize() - 1, Material.BARRIER, "shop.menu.close",
                click -> player.closeInventory());
    }

    private boolean visible(Player player, ShopEntry entry) {
        if (!entry.availableAt(Instant.now())) {
            return false;
        }
        return entry.permission() == null || entry.permission().isBlank() || player.hasPermission(entry.permission());
    }

    private String selectedCoupon(Player player) {
        var coupons = plugin.getCouponService();
        return coupons == null ? null : coupons.cachedSelectedCoupon(player.getUniqueId());
    }

    private int historyLimit() {
        ShopSettings settings = plugin.getConfiguration() == null ? null : plugin.getConfiguration().getShop();
        return settings == null ? 10 : settings.getHistoryLimit();
    }

    private String timeUntil(Instant instant) {
        return timeUntil(Duration.between(Instant.now(), instant));
    }

    private static String timeUntil(Duration duration) {
        Duration remaining = duration.isNegative() ? Duration.ZERO : duration;
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        long seconds = remaining.toSecondsPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private static String discountText(Coupon coupon) {
        return switch (coupon.type()) {
            case PERCENTAGE -> coupon.value() + "%";
            case FIXED -> formatAmount(coupon.value());
        };
    }

    private static String formatAmount(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        StringUtil.sendComponent(player, localized(player, key, resolvers));
    }

    private Component localized(Player player, String key, TagResolver... resolvers) {
        LanguageManager language = plugin.getLanguageManager();
        return language.parse(player, language.get(player, key), merge(resolvers));
    }

    private String localizedText(Player player, String key, TagResolver resolvers) {
        LanguageManager language = plugin.getLanguageManager();
        return StringUtil.MINI_MESSAGE.serialize(language.parse(player, language.get(player, key), merge(resolvers)));
    }

    private static TagResolver baseResolvers() {
        return TagResolver.resolver(
                Placeholder.unparsed("amount", ""), Placeholder.unparsed("currency", ""),
                Placeholder.unparsed("target", ""), Placeholder.unparsed("bundle", ""),
                Placeholder.unparsed("count", ""), Placeholder.unparsed("time", ""),
                Placeholder.unparsed("code", ""), Placeholder.unparsed("discount", ""),
                Placeholder.unparsed("player", ""), Placeholder.unparsed("message", ""),
                Placeholder.unparsed("page", "1"), Placeholder.unparsed("pages", "1"),
                Placeholder.unparsed("skin", ""), Placeholder.unparsed("collection", ""),
                Placeholder.unparsed("milestone", ""), Placeholder.unparsed("owned", ""),
                Placeholder.unparsed("total", ""), Placeholder.unparsed("percentage", ""),
                Placeholder.unparsed("saving", ""), Placeholder.unparsed("date", ""));
    }

    private static TagResolver resolvers(TagResolver... resolvers) {
        return merge(resolvers);
    }

    private static TagResolver with(String key, String value) {
        return merge(Placeholder.unparsed(key, value));
    }

    /** Combines the screen specific placeholders with the defaults every message may reference. */
    private static TagResolver merge(TagResolver... resolvers) {
        List<TagResolver> all = new ArrayList<>(resolvers.length + 1);
        all.addAll(List.of(resolvers));
        all.add(baseResolvers());
        return TagResolver.resolver(all);
    }

    private void runAtEntity(Player player, Runnable action) {
        if (player.isOnline()) {
            plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> action.run());
        }
    }

    private record Snapshot(Set<String> owned, Set<String> favorites) {
    }

    private record GiftQuote(double value, double price) {
    }

    private record Entry(ItemStack item, @Nullable Consumer<ClickType> action) {
    }

    private enum Screen {
        HOME("title"),
        DAILY("daily"),
        BUNDLES("bundles"),
        BUNDLE("bundles"),
        EVENTS("events"),
        EVENT("events"),
        COUPONS("coupons"),
        GIFTS("gifts"),
        PROFILE("profile"),
        GIFT_CONFIRM("gifts");

        private final String key;

        Screen(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }

        private boolean detail() {
            return this == BUNDLE || this == GIFT_CONFIRM;
        }
    }

    private static final class ShopSession implements InventoryHolder {
        private final UUID playerId;
        private final Screen screen;
        private final String referenceId;
        private final UUID recipientId;
        private final String recipientName;
        private final PurchaseKind kind;
        private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        private Set<String> owned = Set.of();
        private Set<String> favorites = Set.of();
        private List<GiftRecord> gifts = List.of();
        private SkinProfile profile;
        private int page;
        private Inventory inventory;

        private ShopSession(Player player, Screen screen, @Nullable String referenceId, @Nullable PurchaseKind kind,
                            @Nullable UUID recipientId, @Nullable String recipientName) {
            this.playerId = player.getUniqueId();
            this.screen = screen;
            this.referenceId = referenceId;
            this.kind = kind;
            this.recipientId = recipientId;
            this.recipientName = recipientName;
        }

        private static ShopSession list(Player player, Screen screen) {
            return new ShopSession(player, screen, null, null, null, null);
        }

        private static ShopSession detail(Player player, Screen screen, String referenceId) {
            return new ShopSession(player, screen, referenceId, null, null, null);
        }

        private static ShopSession gift(Player player, PurchaseKind kind, String targetId, UUID recipientId,
                                        String recipientName) {
            return new ShopSession(player, Screen.GIFT_CONFIRM, targetId, kind, recipientId, recipientName);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
