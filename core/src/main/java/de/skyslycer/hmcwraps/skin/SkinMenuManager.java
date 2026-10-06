package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.lang.LanguageManager;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinMenuConfiguration;
import de.skyslycer.hmcwraps.util.StringUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Config-driven, resource-pack-friendly inventory UI for the v2 item-skin catalog. */
public final class SkinMenuManager implements Listener {
    private static final List<String> DEFAULT_SORT_OPTIONS = List.of("rarity", "name", "price");
    private static final List<String> DEFAULT_FILTER_OPTIONS = List.of("all", "owned", "unowned", "purchasable", "free");

    private final HMCWrapsPlugin plugin;
    private final ItemSkinManagerImpl skinManager;
    private final Map<UUID, MenuSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile SkinMenuConfiguration configuration = new SkinMenuConfiguration();
    private volatile List<String> sortOptions = DEFAULT_SORT_OPTIONS;
    private volatile List<String> filterOptions = DEFAULT_FILTER_OPTIONS;

    public SkinMenuManager(HMCWrapsPlugin plugin, ItemSkinManagerImpl skinManager) {
        this.plugin = plugin;
        this.skinManager = skinManager;
    }

    public boolean load() {
        try {
            ConfigurationNode root = YamlConfigurationLoader.builder()
                    .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
                    .path(HMCWraps.ITEMSKIN_GUI_PATH)
                    .build().load();
            SkinMenuConfiguration loaded = root.node("gui").get(SkinMenuConfiguration.class);
            if (loaded == null) throw new IOException("Missing gui section in itemskin-gui.yml");
            if (loaded.getSize() < 9 || loaded.getSize() > 54 || loaded.getSize() % 9 != 0) {
                plugin.getLogger().warning("itemskin-gui.yml gui.size must be a multiple of 9 between 9 and 54; using 54.");
                loaded = new SkinMenuConfiguration();
            }
            List<String> sorts = root.node("sorting", "options").getList(String.class);
            List<String> filters = root.node("filters", "options").getList(String.class);
            if (!loaded.isShowLocked()) {
                plugin.getLogger().warning("itemskin-gui.yml gui.show-locked=false is ignored: /itemskin must show owned and unowned compatible skins.");
            }
            configuration = loaded;
            sortOptions = validOptions(sorts, DEFAULT_SORT_OPTIONS);
            filterOptions = validOptions(filters, DEFAULT_FILTER_OPTIONS);
            return true;
        } catch (Exception exception) {
            plugin.logSevere("Could not load itemskin-gui.yml.", exception);
            return false;
        }
    }

    public void closeAll() {
        for (MenuSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.isOnline()) {
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> player.closeInventory());
            }
        }
        sessions.clear();
    }

    public void open(Player player, ItemStack target, @Nullable String categoryId) {
        if (target == null || target.getType().isAir()) {
            send(player, "messages.no-item");
            return;
        }
        ItemStack clone = target.clone();
        int sourceSlot = findSourceSlot(player, clone);
        if (sourceSlot < 0) {
            send(player, "messages.item-not-in-inventory");
            return;
        }
        String category = categoryId == null || categoryId.isBlank() ? null : normalize(categoryId);
        player.closeInventory();
        MenuSession session = new MenuSession(player.getUniqueId(), clone, sourceSlot, category,
                normalizeOption(configuration.getDefaultSort(), sortOptions, "rarity"),
                normalizeOption("all", filterOptions, "all"),
                !configuration.getDefaultSortOrder().equalsIgnoreCase("ascending"));
        sessions.put(player.getUniqueId(), session);
        openSession(player, session);
    }

    private void openSession(Player player, MenuSession session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
        if (session.loading) {
            session.refreshAfterLoading = true;
            return;
        }
        session.loading = true;
        skinManager.ownedSkinIds(player.getUniqueId())
                .thenCombine(skinManager.favoriteSkinIds(player.getUniqueId()), OwnershipSnapshot::new)
                .whenComplete((snapshot, throwable) -> plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
                    if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
                    Set<String> loadedOwned = snapshot == null || snapshot.owned() == null ? Set.of() : snapshot.owned();
                    Set<String> loadedFavorites = snapshot == null || snapshot.favorites() == null ? Set.of() : snapshot.favorites();
                    if (throwable != null) {
                        plugin.getLogger().warning("Could not load skin state for " + player.getUniqueId() + ": " + throwable.getMessage());
                        loadedOwned = skinManager.cachedOwnedSkinIds(player.getUniqueId());
                        loadedFavorites = skinManager.cachedFavoriteSkinIds(player.getUniqueId());
                    }
                    render(player, session, loadedOwned, loadedFavorites);
                    if (session.refreshAfterLoading) {
                        session.refreshAfterLoading = false;
                        openSession(player, session);
                    }
                }));
    }

    private void render(Player player, MenuSession session, Set<String> owned, Set<String> favorites) {
        SkinMenuConfiguration config = configuration;
        session.page = Math.max(0, session.page);
        session.slotToSkin.clear();
        session.slotToCategory.clear();
        session.inventory = Bukkit.createInventory(session, config.getSize(),
                StringUtil.LEGACY_SERIALIZER.serialize(plugin.getLanguageManager().parse(player, config.getTitle())));
        session.loading = false;

        if (config.isFillerEnabled()) {
            ItemStack filler = plugin.getItemIconFactory().create(config.getFiller(), " ", player);
            for (int slot = 0; slot < config.getSize(); slot++) session.inventory.setItem(slot, filler.clone());
        }

        if (config.isItemEnabled() && isSlotValid(config.getItemSlot(), config.getSize())) {
            session.inventory.setItem(config.getItemSlot(), session.target.clone());
        }

        List<ItemSkin> candidates = skinManager.getCompatibleSkins(session.target).stream()
                .filter(skin -> session.collectionId == null || session.collectionId.equals(skin.collectionId()))
                .filter(skin -> session.categoryId == null || skin.categoryIds().contains(session.categoryId))
                .filter(skin -> session.searchQuery.isBlank() || matchesSearch(player, skin, session.searchQuery))
                .filter(skin -> !session.favoritesOnly || favorites.contains(normalize(skin.id())))
                .filter(skin -> matchesFilter(player, skin, owned, session.filter))
                .sorted(comparator(player, owned, session.sort, session.descending))
                .toList();

        List<Integer> contentSlots = validContentSlots(config);
        int pageSize = contentSlots.size();
        int maxPage = pageSize == 0 ? 0 : Math.max(0, (candidates.size() - 1) / pageSize);
        if (session.page > maxPage) session.page = maxPage;
        int start = session.page * pageSize;
        for (int index = 0; index < pageSize && start + index < candidates.size(); index++) {
            ItemSkin skin = candidates.get(start + index);
            int slot = contentSlots.get(index);
            ItemStack icon = skinIcon(player, skin, owned, favorites);
            session.inventory.setItem(slot, icon);
            session.slotToSkin.put(slot, skin.id());
        }

        addButton(session, player, config.getPrevious(), plugin.getLanguageManager().get(player, "gui.previous"),
                List.of(localized(player, "gui.page", Placeholder.unparsed("page", String.valueOf(session.page + 1)),
                        Placeholder.unparsed("pages", String.valueOf(maxPage + 1)))), config.getSize());
        addButton(session, player, config.getNext(), plugin.getLanguageManager().get(player, "gui.next"),
                List.of(localized(player, "gui.page", Placeholder.unparsed("page", String.valueOf(session.page + 1)),
                        Placeholder.unparsed("pages", String.valueOf(maxPage + 1)))), config.getSize());
        addButton(session, player, config.getClose(), plugin.getLanguageManager().get(player, "gui.close"), List.of(), config.getSize());
        addButton(session, player, config.getUnskin(), plugin.getLanguageManager().get(player, "gui.unskin"), List.of(), config.getSize());
        addButton(session, player, config.getSort(), plugin.getLanguageManager().get(player, "gui.sort"),
                List.of(localized(player, "gui.selected-sort", Placeholder.component("value", optionLabel(player, "sorting", session.sort)))), config.getSize());
        addButton(session, player, config.getFilter(), plugin.getLanguageManager().get(player, "gui.filter"),
                List.of(localized(player, "gui.selected-filter", Placeholder.component("value", optionLabel(player, "filters", session.filter)))), config.getSize());
        String searchDisplay = session.searchQuery.isBlank()
                ? plugin.getLanguageManager().get(player, "gui.search-empty") : session.searchQuery;
        addButton(session, player, config.getSearch(), plugin.getLanguageManager().get(player, "gui.search"),
                List.of(localized(player, "gui.search-current", Placeholder.unparsed("query", searchDisplay))), config.getSize());
        addButton(session, player, config.getFavorites(), plugin.getLanguageManager().get(player, "gui.favorites"),
                List.of(localized(player, session.favoritesOnly ? "gui.favorites-active" : "gui.favorites-inactive")), config.getSize());
        ItemSkinCollection selectedCollection = session.collectionId == null ? null : skinManager.collection(session.collectionId);
        String collectionLabel = selectedCollection == null
                ? plugin.getLanguageManager().get(player, "gui.collection-all")
                : plugin.getLanguageManager().get(player, selectedCollection.displayNameKey());
        addButton(session, player, config.getCollection(), plugin.getLanguageManager().get(player, "gui.collection"),
                List.of(localized(player, "gui.selected-collection", Placeholder.unparsed("value", collectionLabel))), config.getSize());
        addCategoryButtons(session, player, config, config.getSize());

        player.openInventory(session.inventory);
    }

    private void addCategoryButtons(MenuSession session, Player player, SkinMenuConfiguration config, int size) {
        Set<Integer> usedSlots = new HashSet<>();
        Set<String> availableCategoryIds = null;
        if (session.collectionId != null) {
            ItemSkinCollection selected = skinManager.collection(session.collectionId);
            Set<String> candidateCategoryIds = skinManager.getCompatibleSkins(session.target).stream()
                    .filter(skin -> session.collectionId.equals(skin.collectionId()))
                    .flatMap(skin -> skin.categoryIds().stream())
                    .collect(java.util.stream.Collectors.toSet());
            if (selected != null && !selected.categoryIds().isEmpty()) {
                availableCategoryIds = new HashSet<>(selected.categoryIds());
                availableCategoryIds.retainAll(candidateCategoryIds);
            } else {
                availableCategoryIds = candidateCategoryIds;
            }
        }
        for (Map.Entry<String, Integer> entry : config.getCategorySlots().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            int slot = entry.getValue();
            if (!isSlotValid(slot, size)) continue;
            if ((config.isItemEnabled() && slot == config.getItemSlot()) || isButtonControlSlot(config, slot)) {
                plugin.getLogger().warning("Skipping category button for '" + entry.getKey() + "' because its GUI slot overlaps a reserved slot.");
                continue;
            }
            String categoryId = normalize(entry.getKey());
            if (availableCategoryIds != null && !availableCategoryIds.contains(categoryId)) continue;
            ItemSkinCategory category = skinManager.category(categoryId);
            if (category == null) {
                plugin.getLogger().warning("GUI category slot refers to unknown category '" + categoryId + "'.");
                continue;
            }
            SkinIconConfiguration iconConfiguration = plugin.getSkinCatalog().categoryIconConfiguration(categoryId);
            ItemStack icon = iconConfiguration == null ? category.icon()
                    : plugin.getItemIconFactory().create(iconConfiguration, plugin.getLanguageManager().get(player, category.displayNameKey()), player);
            if (icon == null) icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String label = plugin.getLanguageManager().get(player, category.displayNameKey());
                ItemBuilder builder = ItemBuilder.from(icon);
                if (iconConfiguration == null || iconConfiguration.getName() == null || iconConfiguration.getName().isBlank()) {
                    builder.name(nonItalic(plugin.getLanguageManager().parse(player, label)));
                }
                List<Component> lore = componentLore(meta);
                lore.add(nonItalic(localized(player, "gui.category", Placeholder.component("value",
                        plugin.getLanguageManager().parse(player, label)))));
                icon = builder.lore(lore).build();
            }
            if (!usedSlots.add(slot)) {
                plugin.getLogger().warning("Skipping duplicate category GUI slot " + slot + ".");
                continue;
            }
            session.inventory.setItem(slot, icon);
            session.slotToCategory.put(slot, categoryId);
        }
    }

    private void addButton(MenuSession session, Player player, SkinMenuConfiguration.Button button, String fallbackName,
                           List<Component> dynamicLore, int inventorySize) {
        if (button == null || !button.isEnabled() || !isSlotValid(button.getSlot(), inventorySize)) return;
        ItemStack icon = plugin.getItemIconFactory().create(button.getItem(), fallbackName, player);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && !dynamicLore.isEmpty()) {
            List<Component> lore = componentLore(meta);
            dynamicLore.stream().map(this::nonItalic).forEach(lore::add);
            icon = ItemBuilder.from(icon).lore(lore).build();
        }
        session.inventory.setItem(button.getSlot(), icon);
    }

    private ItemStack skinIcon(Player player, ItemSkin skin, Set<String> owned, Set<String> favorites) {
        SkinIconConfiguration iconConfiguration = plugin.getSkinCatalog().skinIconConfiguration(skin.id());
        ItemStack icon = iconConfiguration == null ? skin.icon()
                : plugin.getItemIconFactory().create(iconConfiguration, skin.displayName(), player);
        if (icon == null) icon = new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        ItemBuilder builder = ItemBuilder.from(icon);
        if (iconConfiguration == null || iconConfiguration.getName() == null || iconConfiguration.getName().isBlank()) {
            builder.name(nonItalic(plugin.getLanguageManager().parse(player, skin.displayName())));
        }
        List<Component> lore = componentLore(meta);
        ItemSkinRarity rarity = skinManager.rarity(skin.rarityId());
        if (rarity != null) {
            Component label = plugin.getLanguageManager().parse(player,
                    plugin.getLanguageManager().get(player, rarity.displayNameKey()));
            lore.add(nonItalic(localized(player, "gui.rarity", Placeholder.component("value", label))));
        }
        SkinAccess access = skinManager.accessNow(player, skin, owned);
        lore.add(nonItalic(accessLine(player, access)));
        if (skin.price() != null) {
            lore.add(nonItalic(localized(player, "gui.cost",
                    Placeholder.unparsed("amount", formatAmount(skin.price().amount())),
                    Placeholder.unparsed("currency", skin.price().currency()))));
        }
        lore.add(Component.empty());
        lore.add(nonItalic(localized(player, "gui.click-apply")));
        lore.add(nonItalic(localized(player, "gui.click-preview")));
        if (access.state() == SkinAccess.State.PURCHASABLE) lore.add(nonItalic(localized(player, "gui.click-buy")));
        lore.add(nonItalic(localized(player,
                favorites.contains(normalize(skin.id())) ? "gui.favorite-on" : "gui.favorite-off")));
        lore.add(nonItalic(localized(player, "gui.click-favorite")));
        return builder.lore(lore).build();
    }

    private List<Component> componentLore(ItemMeta meta) {
        if (meta.getLore() == null) return new ArrayList<>();
        return meta.getLore().stream()
                .map(StringUtil.LEGACY_SERIALIZER::deserialize)
                .map(this::nonItalic)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private Component accessLine(Player player, SkinAccess access) {
        return switch (access.state()) {
            case FREE -> localized(player, "components.free");
            case OWNED -> localized(player, "components.owned");
            case PURCHASABLE -> localized(player, "components.purchasable");
            case PERMISSION -> localized(player, "components.permission");
            case LOCKED -> localized(player, "components.locked");
        };
    }

    private boolean matchesFilter(Player player, ItemSkin skin, Set<String> owned, String filter) {
        boolean hasSkin = owned.contains(normalize(skin.id()));
        return switch (filter) {
            case "owned" -> hasSkin;
            case "unowned" -> !hasSkin;
            case "free" -> skin.price() == null;
            case "purchasable" -> skinManager.accessNow(player, skin, owned).state() == SkinAccess.State.PURCHASABLE;
            default -> true;
        };
    }

    private boolean matchesSearch(Player player, ItemSkin skin, String query) {
        String normalizedQuery = normalize(query);
        if (normalize(skin.id()).contains(normalizedQuery)) return true;
        String name = PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().parse(player, skin.displayName()));
        if (normalize(name).contains(normalizedQuery)) return true;
        for (String categoryId : skin.categoryIds()) {
            if (normalize(categoryId).contains(normalizedQuery)) return true;
            ItemSkinCategory category = skinManager.category(categoryId);
            if (category != null && normalize(plugin.getLanguageManager().get(player, category.displayNameKey())).contains(normalizedQuery)) return true;
        }
        if (skin.collectionId() != null) {
            ItemSkinCollection collection = skinManager.collection(skin.collectionId());
            if (collection != null && (normalize(collection.id()).contains(normalizedQuery)
                    || normalize(plugin.getLanguageManager().get(player, collection.displayNameKey())).contains(normalizedQuery))) return true;
        }
        return false;
    }

    private Comparator<ItemSkin> comparator(Player player, Set<String> owned, String sort, boolean descending) {
        if (sort.equals("rarity") || !Set.of("name", "price", "owned", "category").contains(sort)) {
            return SkinMenuOrder.byRarity(skin -> {
                ItemSkinRarity rarity = skinManager.rarity(skin.rarityId());
                return rarity == null ? Integer.MIN_VALUE : rarity.priority();
            }, ItemSkin::id, descending);
        }
        Comparator<ItemSkin> comparator = switch (sort) {
            case "name" -> Comparator.comparing(skin -> PlainTextComponentSerializer.plainText()
                    .serialize(plugin.getLanguageManager().parse(player, skin.displayName())), String.CASE_INSENSITIVE_ORDER);
            case "price" -> Comparator.comparingDouble(skin -> skin.price() == null ? 0D : skin.price().amount());
            case "owned" -> Comparator.comparing(skin -> owned.contains(normalize(skin.id())));
            case "category" -> Comparator.comparing(skin -> skin.categoryIds().stream().sorted().findFirst().orElse(""));
            default -> throw new IllegalStateException("Unexpected sort option: " + sort);
        };
        if (descending) comparator = comparator.reversed();
        return comparator.thenComparing(ItemSkin::id, String.CASE_INSENSITIVE_ORDER);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) return;
        event.setCancelled(true);
        if (session.loading || event.getClickedInventory() != session.inventory) return;
        int slot = event.getSlot();
        SkinMenuConfiguration config = configuration;
        if (buttonAt(config.getClose(), slot)) {
            player.closeInventory();
            return;
        }
        if (buttonAt(config.getUnskin(), slot)) {
            unskin(player, session);
            return;
        }
        if (buttonAt(config.getPrevious(), slot)) {
            session.page = Math.max(0, session.page - 1);
            openSession(player, session);
            return;
        }
        if (buttonAt(config.getNext(), slot)) {
            session.page++;
            openSession(player, session);
            return;
        }
        if (buttonAt(config.getSort(), slot)) {
            session.sort = nextOption(session.sort, sortOptions);
            openSession(player, session);
            return;
        }
        if (buttonAt(config.getFilter(), slot)) {
            session.filter = nextOption(session.filter, filterOptions);
            session.page = 0;
            openSession(player, session);
            return;
        }
        if (buttonAt(config.getSearch(), slot)) {
            session.awaitingSearch = true;
            player.closeInventory();
            send(player, "messages.search-prompt");
            return;
        }
        if (buttonAt(config.getFavorites(), slot)) {
            session.favoritesOnly = !session.favoritesOnly;
            session.page = 0;
            openSession(player, session);
            return;
        }
        if (buttonAt(config.getCollection(), slot)) {
            cycleCollection(player, session);
            return;
        }
        if (session.slotToCategory.containsKey(slot)) {
            String selected = session.slotToCategory.get(slot);
            session.categoryId = selected.equals(session.categoryId) ? null : selected;
            session.page = 0;
            openSession(player, session);
            return;
        }
        String skinId = session.slotToSkin.get(slot);
        if (skinId == null) return;
        ItemSkin skin = skinManager.getSkin(skinId).orElse(null);
        if (skin == null) return;

        String action = clickAction(event.getClick());
        switch (action) {
            case "apply" -> apply(player, session, skin);
            case "preview" -> preview(player, session, skin);
            case "buy", "purchase" -> purchase(player, session, skin);
            case "favorite" -> toggleFavorite(player, session, skin);
            default -> { }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        MenuSession session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory
                && !session.awaitingSearch && !plugin.getPreviewManager().isPreviewing(player)) {
            sessions.remove(player.getUniqueId(), session);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.awaitingSearch) return;
        event.setCancelled(true);
        String message = event.getMessage().trim();
        plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
            if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
            session.awaitingSearch = false;
            if (message.equalsIgnoreCase("cancel")) {
                send(player, "messages.search-cancelled");
            } else {
                session.searchQuery = message.equalsIgnoreCase("clear") ? "" : message;
                session.page = 0;
                if (session.searchQuery.isBlank()) send(player, "messages.search-cleared");
                else send(player, "messages.search-set", Placeholder.unparsed("query", session.searchQuery));
            }
            openSession(player, session);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        skinManager.invalidateOwnership(playerId);
    }

    private void cycleCollection(Player player, MenuSession session) {
        List<String> available = skinManager.getCompatibleSkins(session.target).stream()
                .map(ItemSkin::collectionId).filter(java.util.Objects::nonNull).distinct()
                .filter(id -> skinManager.collection(id) != null)
                .sorted(Comparator.comparingInt((String id) -> skinManager.collection(id).priority())
                        .thenComparing(id -> id, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (available.isEmpty()) {
            send(player, "messages.no-collections");
            return;
        }
        int current = session.collectionId == null ? -1 : available.indexOf(session.collectionId);
        session.collectionId = current < 0 || current == available.size() - 1 ? null : available.get(current + 1);
        session.categoryId = null;
        session.page = 0;
        openSession(player, session);
    }

    private void toggleFavorite(Player player, MenuSession session, ItemSkin skin) {
        UUID playerId = player.getUniqueId();
        boolean favorite = skinManager.cachedFavoriteSkinIds(playerId).contains(normalize(skin.id()));
        skinManager.setFavorite(playerId, skin.id(), !favorite).whenComplete((success, error) ->
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
                    if (!player.isOnline()) return;
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        send(player, "messages.favorite-storage-error");
                    } else {
                        send(player, favorite ? "messages.favorite-removed" : "messages.favorite-added",
                                Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
                    }
                    openSession(player, session);
                }));
    }

    private void apply(Player player, MenuSession session, ItemSkin skin) {
        SkinAccess access = skinManager.accessNow(player, skin, skinManager.cachedOwnedSkinIds(player.getUniqueId()));
        if (access.state() != SkinAccess.State.FREE && access.state() != SkinAccess.State.OWNED) {
            send(player, access.state() == SkinAccess.State.PERMISSION ? "messages.permission-required" : "messages.locked",
                    Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
            return;
        }
        if (!skinManager.isCompatible(session.target, skin)) {
            send(player, "messages.compatibility-error");
            return;
        }
        ItemStack current = getCurrentItem(player, session);
        if (current == null) {
            send(player, "messages.item-changed");
            player.closeInventory();
            return;
        }
        ItemStack updated = skinManager.applySkin(player, current, skin);
        var appliedWrap = plugin.getWrapper().getWrap(updated);
        if (appliedWrap == null || !appliedWrap.getUuid().equals(skin.cosmetic().getUuid())) {
            send(player, "messages.apply-cancelled");
            return;
        }
        player.getInventory().setItem(session.sourceSlot, updated);
        session.target = updated.clone();
        send(player, "messages.applied", Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
        openSession(player, session);
    }

    private void unskin(Player player, MenuSession session) {
        ItemStack current = getCurrentItem(player, session);
        if (current == null) {
            send(player, "messages.item-changed");
            player.closeInventory();
            return;
        }
        ItemStack updated = skinManager.removeSkin(player, current);
        if (updated.isSimilar(current)) {
            send(player, "messages.no-skin-to-remove");
            return;
        }
        player.getInventory().setItem(session.sourceSlot, updated);
        session.target = updated.clone();
        send(player, "messages.removed");
        openSession(player, session);
    }

    private void preview(Player player, MenuSession session, ItemSkin skin) {
        if (!skin.previewEnabled()) {
            send(player, "messages.preview-disabled");
            return;
        }
        if (!skinManager.isCompatible(session.target, skin)) {
            send(player, "messages.compatibility-error");
            return;
        }
        send(player, "messages.preview-started", Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
        plugin.getPreviewManager().create(player, ignored -> openSession(player, session), skin.cosmetic(), session.target);
    }

    private void purchase(Player player, MenuSession session, ItemSkin skin) {
        skinManager.purchase(player, skin).whenComplete((result, throwable) ->
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
                    if (!player.isOnline()) return;
                    if (throwable != null) {
                        plugin.getLogger().warning("Skin purchase failed for " + player.getUniqueId() + ": " + throwable.getMessage());
                        send(player, "messages.purchase-failed");
                    } else {
                        switch (result.status()) {
                            case SUCCESS -> send(player, "messages.purchase-success",
                                    Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
                            case ALREADY_OWNED -> send(player, "messages.already-owned",
                                    Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
                            case INSUFFICIENT_FUNDS -> send(player, "messages.purchase-insufficient",
                                    Placeholder.unparsed("currency", skin.price() == null ? "" : skin.price().currency()));
                            case PERMISSION_DENIED, LOCKED -> send(player, "messages.locked",
                                    Placeholder.component("skin", plugin.getLanguageManager().parse(player, skin.displayName())));
                            case BUSY -> send(player, "messages.purchase-busy");
                            case PROVIDER_UNAVAILABLE -> send(player, "messages.purchase-provider-unavailable");
                            case STORAGE_FAILED -> send(player, "messages.purchase-storage-error");
                            default -> send(player, "messages.purchase-failed");
                        }
                    }
                    openSession(player, session);
                }));
    }

    private ItemStack getCurrentItem(Player player, MenuSession session) {
        if (session.sourceSlot < 0 || session.sourceSlot >= player.getInventory().getSize()) return null;
        ItemStack current = player.getInventory().getItem(session.sourceSlot);
        if (current == null || current.getType().isAir() || current.getAmount() != session.target.getAmount()
                || !current.isSimilar(session.target)) return null;
        return current.clone();
    }

    private String clickAction(ClickType click) {
        String key = switch (click) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case SHIFT_LEFT -> "shift-left";
            case SHIFT_RIGHT -> "shift-right";
            case MIDDLE -> "middle";
            default -> "";
        };
        if (key.isEmpty()) return "";
        String fallback = switch (key) {
            case "left" -> "apply";
            case "right" -> "preview";
            case "shift-left", "shift-right" -> "buy";
            case "middle" -> "favorite";
            default -> "";
        };
        String configured = configuration.getClickActions().get(key);
        return (configured == null ? fallback : configured).toLowerCase(Locale.ROOT);
    }

    private List<Integer> validContentSlots(SkinMenuConfiguration config) {
        List<Integer> slots = config.getContentSlots().stream().filter(java.util.Objects::nonNull).distinct()
                .filter(slot -> isSlotValid(slot, config.getSize()))
                .filter(slot -> !config.isItemEnabled() || slot != config.getItemSlot())
                .filter(slot -> !isControlSlot(config, slot))
                .toList();
        if (!slots.isEmpty()) return slots;
        List<Integer> fallback = new ArrayList<>();
        for (int slot = 0; slot < config.getSize(); slot++) {
            if ((!config.isItemEnabled() || slot != config.getItemSlot()) && !isControlSlot(config, slot)) fallback.add(slot);
        }
        return fallback;
    }

    private boolean isControlSlot(SkinMenuConfiguration config, int slot) {
        return isButtonControlSlot(config, slot) || config.getCategorySlots().containsValue(slot);
    }

    private boolean isButtonControlSlot(SkinMenuConfiguration config, int slot) {
        return buttonAt(config.getPrevious(), slot) || buttonAt(config.getNext(), slot)
                || buttonAt(config.getClose(), slot) || buttonAt(config.getSort(), slot)
                || buttonAt(config.getFilter(), slot) || buttonAt(config.getSearch(), slot)
                || buttonAt(config.getFavorites(), slot) || buttonAt(config.getCollection(), slot)
                || buttonAt(config.getUnskin(), slot);
    }

    private boolean buttonAt(SkinMenuConfiguration.Button button, int slot) {
        return button != null && button.isEnabled() && button.getSlot() == slot;
    }

    private int findSourceSlot(Player player, ItemStack target) {
        int hand = player.getInventory().getHeldItemSlot();
        if (matches(player.getInventory().getItem(hand), target)) return hand;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot == hand) continue;
            if (matches(player.getInventory().getItem(slot), target)) return slot;
        }
        return -1;
    }

    private boolean matches(@Nullable ItemStack left, ItemStack right) {
        return left != null && !left.getType().isAir() && left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private Component localized(Player player, String key, TagResolver... resolvers) {
        LanguageManager language = plugin.getLanguageManager();
        return language.parse(player, language.get(player, key), resolvers);
    }

    private Component optionLabel(Player player, String group, String option) {
        String key = group + "." + option;
        String value = plugin.getLanguageManager().get(player, key);
        return value.equals(key) ? Component.text(prettyOption(option)) : plugin.getLanguageManager().parse(player, value);
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        StringUtil.sendComponent(player, localized(player, key, resolvers));
    }

    private static List<String> validOptions(List<String> configured, List<String> defaults) {
        if (configured == null || configured.isEmpty()) return defaults;
        List<String> normalized = configured.stream().filter(value -> value != null && !value.isBlank())
                .map(SkinMenuManager::normalize).distinct().toList();
        return normalized.isEmpty() ? defaults : normalized;
    }

    private static String normalizeOption(String option, List<String> options, String fallback) {
        String value = normalize(option);
        return options.contains(value) ? value : options.contains(fallback) ? fallback : options.getFirst();
    }

    private static String nextOption(String current, List<String> options) {
        if (options.isEmpty()) return current;
        int index = options.indexOf(current);
        return options.get((index + 1 + options.size()) % options.size());
    }

    private static boolean isSlotValid(int slot, int size) { return slot >= 0 && slot < size; }
    private static String normalize(String input) { return input == null ? "" : input.toLowerCase(Locale.ROOT).trim(); }
    private static String prettyOption(String value) { return value.isEmpty() ? "all" : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }
    private static String formatAmount(double amount) {
        return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }
    private record OwnershipSnapshot(Set<String> owned, Set<String> favorites) { }

    private static final class MenuSession implements InventoryHolder {
        private final UUID playerId;
        private ItemStack target;
        private final int sourceSlot;
        private String categoryId;
        private String collectionId;
        private String searchQuery = "";
        private String sort;
        private String filter;
        private final boolean descending;
        private int page;
        private boolean favoritesOnly;
        private volatile boolean awaitingSearch;
        private boolean loading;
        private boolean refreshAfterLoading;
        private Inventory inventory;
        private final Map<Integer, String> slotToSkin = new HashMap<>();
        private final Map<Integer, String> slotToCategory = new HashMap<>();

        private MenuSession(UUID playerId, ItemStack target, int sourceSlot, String categoryId,
                            String sort, String filter, boolean descending) {
            this.playerId = playerId;
            this.target = target;
            this.sourceSlot = sourceSlot;
            this.categoryId = categoryId;
            this.sort = sort;
            this.filter = filter;
            this.descending = descending;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
