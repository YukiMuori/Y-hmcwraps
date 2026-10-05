package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.lang.LanguageManager;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.skin.config.SkinMenuConfiguration;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.Component;
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
        skinManager.ownedSkinIds(player.getUniqueId()).whenComplete((owned, throwable) ->
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
                    if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) return;
                    Set<String> loadedOwned = owned == null ? Set.of() : owned;
                    if (throwable != null) {
                        plugin.getLogger().warning("Could not load owned skins for " + player.getUniqueId() + ": " + throwable.getMessage());
                        loadedOwned = Set.of();
                    }
                    render(player, session, loadedOwned);
                    if (session.refreshAfterLoading) {
                        session.refreshAfterLoading = false;
                        openSession(player, session);
                    }
                }));
    }

    private void render(Player player, MenuSession session, Set<String> owned) {
        SkinMenuConfiguration config = configuration;
        session.page = Math.max(0, session.page);
        session.slotToSkin.clear();
        session.slotToCategory.clear();
        session.inventory = Bukkit.createInventory(session, config.getSize(),
                StringUtil.LEGACY_SERIALIZER.serialize(plugin.getLanguageManager().parse(player, config.getTitle())));
        session.loading = false;

        ItemStack filler = plugin.getItemIconFactory().create(config.getFiller(), " ", player);
        for (int slot = 0; slot < config.getSize(); slot++) session.inventory.setItem(slot, filler.clone());

        ItemStack target = session.target.clone();
        int targetSlot = config.getItemSlot();
        if (isSlotValid(targetSlot, config.getSize())) session.inventory.setItem(targetSlot, target);

        List<ItemSkin> candidates = skinManager.getCompatibleSkins(session.target).stream()
                .filter(skin -> session.categoryId == null || skin.categoryIds().contains(session.categoryId))
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
            ItemStack icon = skinIcon(player, skin, owned);
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
        addButton(session, player, config.getSort(), plugin.getLanguageManager().get(player, "gui.sort"),
                List.of(localized(player, "gui.selected-sort", Placeholder.component("value", optionLabel(player, "sorting", session.sort)))), config.getSize());
        addButton(session, player, config.getFilter(), plugin.getLanguageManager().get(player, "gui.filter"),
                List.of(localized(player, "gui.selected-filter", Placeholder.component("value", optionLabel(player, "filters", session.filter)))), config.getSize());
        addCategoryButtons(session, player, config, candidates, config.getSize());

        player.openInventory(session.inventory);
    }

    private void addCategoryButtons(MenuSession session, Player player, SkinMenuConfiguration config,
                                    List<ItemSkin> candidates, int size) {
        Set<Integer> usedSlots = new HashSet<>();
        for (Map.Entry<String, Integer> entry : config.getCategorySlots().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            int slot = entry.getValue();
            if (!isSlotValid(slot, size)) continue;
            if (slot == config.getItemSlot() || slot == config.getPrevious().getSlot()
                    || slot == config.getNext().getSlot() || slot == config.getClose().getSlot()
                    || slot == config.getSort().getSlot() || slot == config.getFilter().getSlot()) {
                plugin.getLogger().warning("Skipping category button for '" + entry.getKey() + "' because its GUI slot overlaps a reserved slot.");
                continue;
            }
            String categoryId = normalize(entry.getKey());
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
                if (iconConfiguration == null || iconConfiguration.getName() == null || iconConfiguration.getName().isBlank()) {
                    meta.setDisplayName(StringUtil.LEGACY_SERIALIZER.serialize(plugin.getLanguageManager().parse(player, label)));
                }
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.category", Placeholder.component("value",
                        plugin.getLanguageManager().parse(player, label)))));
                meta.setLore(lore);
                icon.setItemMeta(meta);
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
        if (button == null || !isSlotValid(button.getSlot(), inventorySize)) return;
        ItemStack icon = plugin.getItemIconFactory().create(button.getItem(), fallbackName, player);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && !dynamicLore.isEmpty()) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            dynamicLore.stream().map(StringUtil.LEGACY_SERIALIZER::serialize).forEach(lore::add);
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        session.inventory.setItem(button.getSlot(), icon);
    }

    private ItemStack skinIcon(Player player, ItemSkin skin, Set<String> owned) {
        SkinIconConfiguration iconConfiguration = plugin.getSkinCatalog().skinIconConfiguration(skin.id());
        ItemStack icon = iconConfiguration == null ? skin.icon()
                : plugin.getItemIconFactory().create(iconConfiguration, skin.displayName(), player);
        if (icon == null) icon = new ItemStack(Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        if (iconConfiguration == null || iconConfiguration.getName() == null || iconConfiguration.getName().isBlank()) {
            meta.setDisplayName(StringUtil.LEGACY_SERIALIZER.serialize(plugin.getLanguageManager().parse(player, skin.displayName())));
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        ItemSkinRarity rarity = skinManager.rarity(skin.rarityId());
        if (rarity != null) {
            Component label = plugin.getLanguageManager().parse(player,
                    plugin.getLanguageManager().get(player, rarity.displayNameKey()));
            lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.rarity", Placeholder.component("value", label))));
        }
        SkinAccess access = skinManager.accessNow(player, skin, owned);
        lore.add(StringUtil.LEGACY_SERIALIZER.serialize(accessLine(player, access)));
        if (skin.price() != null) {
            lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.cost",
                    Placeholder.unparsed("amount", formatAmount(skin.price().amount())),
                    Placeholder.unparsed("currency", skin.price().currency()))));
        }
        lore.add(" ");
        lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.click-apply")));
        lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.click-preview")));
        if (access.state() == SkinAccess.State.PURCHASABLE) lore.add(StringUtil.LEGACY_SERIALIZER.serialize(localized(player, "gui.click-buy")));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
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
        if (slot == config.getClose().getSlot()) {
            player.closeInventory();
            return;
        }
        if (slot == config.getPrevious().getSlot()) {
            session.page = Math.max(0, session.page - 1);
            openSession(player, session);
            return;
        }
        if (slot == config.getNext().getSlot()) {
            session.page++;
            openSession(player, session);
            return;
        }
        if (slot == config.getSort().getSlot()) {
            session.sort = nextOption(session.sort, sortOptions);
            openSession(player, session);
            return;
        }
        if (slot == config.getFilter().getSlot()) {
            session.filter = nextOption(session.filter, filterOptions);
            session.page = 0;
            openSession(player, session);
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
                && !plugin.getPreviewManager().isPreviewing(player)) {
            sessions.remove(player.getUniqueId(), session);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        skinManager.invalidateOwnership(playerId);
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
            default -> "";
        };
        String configured = configuration.getClickActions().get(key);
        return (configured == null ? fallback : configured).toLowerCase(Locale.ROOT);
    }

    private List<Integer> validContentSlots(SkinMenuConfiguration config) {
        List<Integer> slots = config.getContentSlots().stream().filter(java.util.Objects::nonNull).distinct()
                .filter(slot -> isSlotValid(slot, config.getSize()))
                .filter(slot -> slot != config.getItemSlot())
                .filter(slot -> !isControlSlot(config, slot))
                .toList();
        if (!slots.isEmpty()) return slots;
        List<Integer> fallback = new ArrayList<>();
        for (int slot = 0; slot < config.getSize(); slot++) {
            if (slot != config.getItemSlot() && !isControlSlot(config, slot)) fallback.add(slot);
        }
        return fallback;
    }

    private boolean isControlSlot(SkinMenuConfiguration config, int slot) {
        if (slot == config.getPrevious().getSlot() || slot == config.getNext().getSlot()
                || slot == config.getClose().getSlot() || slot == config.getSort().getSlot()
                || slot == config.getFilter().getSlot()) return true;
        return config.getCategorySlots().containsValue(slot);
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
    private static final class MenuSession implements InventoryHolder {
        private final UUID playerId;
        private ItemStack target;
        private final int sourceSlot;
        private String categoryId;
        private String sort;
        private String filter;
        private final boolean descending;
        private int page;
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
