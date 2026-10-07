package de.skyslycer.hmcwraps.shop.menu;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.lang.LanguageManager;
import de.skyslycer.hmcwraps.util.StringUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import de.skyslycer.hmcwraps.util.YamlFileStore;
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
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chat driven editor for the shop definition files.
 *
 * <p>Every field of {@code shops.yml} and {@code coupons.yml} that administrators realistically change is
 * reachable without opening a text editor: a left click toggles a boolean, a right click asks for a new value
 * in chat (typing {@code cancel} aborts). Values are written through {@link YamlFileStore}, so a crash cannot
 * leave a half written file, and the shop is reloaded right after a successful save.</p>
 */
public final class ShopEditorManager implements Listener {

    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_RELOAD = 51;
    private static final int SLOT_CLOSE = 53;

    private final HMCWrapsPlugin plugin;
    private final Map<UUID, EditorSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, PendingEdit> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public ShopEditorManager(@NotNull HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Opens the editor for a player. */
    public void open(@NotNull Player player) {
        if (plugin.getShopRegistry() == null) {
            send(player, "shop.purchase.unavailable");
            return;
        }
        EditorSession existing = sessions.get(player.getUniqueId());
        EditorSession session = existing == null ? new EditorSession(player.getUniqueId()) : existing;
        sessions.put(player.getUniqueId(), session);
        render(player, session);
    }

    /** Closes every open editor and forgets pending prompts; used on shutdown and reload. */
    public void closeAll() {
        for (EditorSession session : List.copyOf(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && player.isOnline()) {
                plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> player.closeInventory());
            }
        }
        sessions.clear();
        pending.clear();
    }

    // ------------------------------------------------------------------ rendering

    private void render(Player player, EditorSession session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session) {
            return;
        }
        session.actions.clear();
        session.fields = fields();
        Inventory inventory = Bukkit.createInventory(session, 54,
                StringUtil.LEGACY_SERIALIZER.serialize(component(player, "shop.editor.title", Map.of())));
        session.inventory = inventory;

        int pages = Math.max(1, (session.fields.size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        session.page = Math.max(0, Math.min(session.page, pages - 1));
        int start = session.page * CONTENT_SLOTS;
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            int position = start + slot;
            if (position >= session.fields.size()) {
                break;
            }
            Field field = session.fields.get(position);
            inventory.setItem(slot, icon(player, field));
            session.actions.put(slot, click -> handle(player, session, field));
        }
        if (session.fields.isEmpty()) {
            inventory.setItem(22, simple(Material.GRAY_DYE,
                    localized(player, "shop.menu.no-entries", Map.of()), List.of()));
        }
        if (pages > 1) {
            if (session.page > 0) {
                inventory.setItem(SLOT_PREVIOUS, simple(Material.ARROW,
                        localized(player, "shop.menu.previous", Map.of()), List.of()));
                session.actions.put(SLOT_PREVIOUS, click -> {
                    session.page--;
                    render(player, session);
                });
            }
            inventory.setItem(SLOT_PAGE, simple(Material.PAPER, localized(player, "shop.menu.page",
                    Map.of("page", String.valueOf(session.page + 1), "pages", String.valueOf(pages))), List.of()));
            if (session.page + 1 < pages) {
                inventory.setItem(SLOT_NEXT, simple(Material.ARROW,
                        localized(player, "shop.menu.next", Map.of()), List.of()));
                session.actions.put(SLOT_NEXT, click -> {
                    session.page++;
                    render(player, session);
                });
            }
        }
        inventory.setItem(SLOT_RELOAD, simple(Material.REPEATER,
                localized(player, "shop.editor.reload", Map.of()), List.of()));
        session.actions.put(SLOT_RELOAD, click -> reload(player, session));
        inventory.setItem(SLOT_CLOSE, simple(Material.BARRIER,
                localized(player, "shop.menu.close", Map.of()), List.of()));
        session.actions.put(SLOT_CLOSE, click -> player.closeInventory());
        player.openInventory(inventory);
    }

    private void reload(Player player, EditorSession session) {
        if (plugin.getShopService() == null) {
            return;
        }
        plugin.getShopService().reload().whenComplete((valid, error) -> runAtEntity(player, () -> {
            send(player, error != null || !Boolean.TRUE.equals(valid) ? "shop.menu.reload-failed" : "shop.menu.reload");
            render(player, session);
        }));
    }

    private ItemStack icon(Player player, Field field) {
        Map<String, String> values = field.arguments();
        List<String> lore = new ArrayList<>();
        lore.add(localized(player, "shop.editor.current", with(values, "value", String.valueOf(read(field)))));
        lore.add(localized(player, field.type() == FieldType.BOOLEAN ? "shop.editor.toggle-hint"
                : "shop.editor.edit-hint", values));
        return simple(field.material(), localized(player, field.labelKey(), values), lore);
    }

    private void handle(Player player, EditorSession session, Field field) {
        if (field.type() == FieldType.BOOLEAN) {
            write(player, session, field, String.valueOf(!Boolean.TRUE.equals(read(field))));
            return;
        }
        pending.put(player.getUniqueId(), new PendingEdit(session, field));
        send(player, "shop.editor.prompt", with(field.arguments(), "value", String.valueOf(read(field))));
        player.closeInventory();
    }

    // ------------------------------------------------------------------ fields

    private List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        YamlFileStore shops = plugin.getShopRegistry().shopsFile();
        YamlFileStore coupons = plugin.getShopRegistry().couponsFile();
        // Daily shop.
        fields.add(bool(shops, new Object[]{"daily-shop", "enabled"}, Material.CLOCK, "shop.editor.daily-enabled"));
        fields.add(number(shops, new Object[]{"daily-shop", "slots"}, Material.CHEST, "shop.editor.daily-slots", false));
        fields.add(text(shops, new Object[]{"daily-shop", "reset-time"}, Material.CLOCK, "shop.editor.daily-reset"));
        fields.add(text(shops, new Object[]{"daily-shop", "zone"}, Material.COMPASS, "shop.editor.daily-zone"));
        fields.add(number(shops, new Object[]{"daily-shop", "discount"}, Material.GOLD_INGOT, "shop.editor.daily-discount", true));
        fields.add(bool(shops, new Object[]{"daily-shop", "include-unpriced"}, Material.EMERALD, "shop.editor.daily-unpriced"));
        fields.add(number(shops, new Object[]{"daily-shop", "price", "amount"}, Material.SUNFLOWER, "shop.editor.daily-price", true));
        // Featured.
        fields.add(bool(shops, new Object[]{"featured", "enabled"}, Material.NETHER_STAR, "shop.editor.featured-enabled"));
        fields.add(bool(shops, new Object[]{"featured", "automatic"}, Material.BEACON, "shop.editor.featured-automatic"));
        fields.add(number(shops, new Object[]{"featured", "automatic-count"}, Material.BEACON, "shop.editor.featured-count", false));
        fields.add(bool(shops, new Object[]{"featured", "automatic-rotate"}, Material.BEACON, "shop.editor.featured-rotate"));
        fields.add(number(shops, new Object[]{"featured", "discount"}, Material.GOLD_INGOT, "shop.editor.featured-discount", true));
        fields.add(number(shops, new Object[]{"featured", "price", "amount"}, Material.SUNFLOWER, "shop.editor.featured-price", true));
        // Bundles.
        for (String bundleId : sectionIds(shops, "bundles")) {
            Object[] base = {"bundles", bundleId};
            Map<String, String> args = Map.of("bundle", bundleId, "id", bundleId);
            fields.add(bool(shops, append(base, "enabled"), Material.SHULKER_BOX, "shop.editor.bundle-enabled", args));
            fields.add(bool(shops, append(base, "featured"), Material.NETHER_STAR, "shop.editor.bundle-featured", args));
            fields.add(number(shops, append(base, "discount"), Material.GOLD_INGOT, "shop.editor.bundle-discount", true, args));
            fields.add(text(shops, append(base, "purchase-mode"), Material.LEVER, "shop.editor.bundle-mode", args));
            fields.add(number(shops, append(base, "price.amount"), Material.SUNFLOWER, "shop.editor.bundle-price", true, args));
        }
        // Event shops.
        for (String eventId : sectionIds(shops, "events")) {
            Object[] base = {"events", eventId};
            Map<String, String> args = Map.of("id", eventId, "bundle", eventId);
            fields.add(bool(shops, append(base, "enabled"), Material.NETHER_STAR, "shop.editor.event-enabled", args));
            fields.add(text(shops, append(base, "start"), Material.CLOCK, "shop.editor.event-start", args));
            fields.add(text(shops, append(base, "end"), Material.CLOCK, "shop.editor.event-end", args));
            fields.add(number(shops, append(base, "discount"), Material.GOLD_INGOT, "shop.editor.event-discount", true, args));
            fields.add(number(shops, append(base, "price.amount"), Material.SUNFLOWER, "shop.editor.event-price", true, args));
        }
        // Coupons.
        for (String code : sectionIds(coupons, "coupons")) {
            Object[] base = {"coupons", code};
            Map<String, String> args = Map.of("code", code, "id", code);
            fields.add(bool(coupons, append(base, "active"), Material.PAPER, "shop.editor.coupon-active", args));
            fields.add(number(coupons, append(base, "value"), Material.GOLD_INGOT, "shop.editor.coupon-value", true, args));
            fields.add(number(coupons, append(base, "max-uses"), Material.PAPER, "shop.editor.coupon-max-uses", false, args));
            fields.add(number(coupons, append(base, "max-uses-per-player"), Material.PLAYER_HEAD,
                    "shop.editor.coupon-max-per-player", false, args));
            fields.add(number(coupons, append(base, "min-spend"), Material.SUNFLOWER, "shop.editor.coupon-min-spend", true, args));
            fields.add(text(coupons, append(base, "expires"), Material.CLOCK, "shop.editor.coupon-expires", args));
        }
        return fields;
    }

    private static List<String> sectionIds(YamlFileStore store, String section) {
        ConfigurationNode root = node(store);
        if (root == null) {
            return List.of();
        }
        ConfigurationNode sectionNode = root.node(section);
        if (sectionNode.virtual()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object key : sectionNode.childrenMap().keySet()) {
            ids.add(String.valueOf(key));
        }
        return ids;
    }

    private static Object[] append(Object[] base, String tail) {
        String[] parts = tail.split("\\.");
        Object[] result = new Object[base.length + parts.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(parts, 0, result, base.length, parts.length);
        return result;
    }

    private Field bool(YamlFileStore store, Object[] path, Material material, String labelKey) {
        return bool(store, path, material, labelKey, Map.of());
    }

    private Field bool(YamlFileStore store, Object[] path, Material material, String labelKey, Map<String, String> arguments) {
        return new Field(store, path, FieldType.BOOLEAN, material, labelKey, arguments);
    }

    private Field number(YamlFileStore store, Object[] path, Material material, String labelKey, boolean decimal) {
        return number(store, path, material, labelKey, decimal, Map.of());
    }

    private Field number(YamlFileStore store, Object[] path, Material material, String labelKey, boolean decimal,
                         Map<String, String> arguments) {
        return new Field(store, path, decimal ? FieldType.DECIMAL : FieldType.INTEGER, material, labelKey, arguments);
    }

    private Field text(YamlFileStore store, Object[] path, Material material, String labelKey) {
        return text(store, path, material, labelKey, Map.of());
    }

    private Field text(YamlFileStore store, Object[] path, Material material, String labelKey, Map<String, String> arguments) {
        return new Field(store, path, FieldType.TEXT, material, labelKey, arguments);
    }

    private static @Nullable ConfigurationNode node(YamlFileStore store) {
        try {
            return store.load();
        } catch (Exception exception) {
            return null;
        }
    }

    private static @Nullable Object read(Field field) {
        ConfigurationNode root = node(field.store());
        return root == null ? null : root.node(field.path()).raw();
    }

    private void write(Player player, EditorSession session, Field field, String raw) {
        Object value;
        try {
            value = convert(field.type(), raw);
        } catch (IllegalArgumentException exception) {
            send(player, "shop.editor.invalid", with(field.arguments(), "reason", exception.getMessage()));
            render(player, session);
            return;
        }
        try {
            field.store().update(root -> root.node(field.path()).raw(value));
        } catch (Exception exception) {
            plugin.logSevere("Could not save the shop configuration.", exception);
            send(player, "shop.editor.save-failed");
            render(player, session);
            return;
        }
        send(player, "shop.editor.saved", with(field.arguments(), "value", String.valueOf(value)));
        if (plugin.getShopService() != null) {
            plugin.getShopService().reload();
        }
        render(player, session);
    }

    private static @Nullable Object convert(FieldType type, String raw) {
        String value = raw == null ? "" : raw.trim();
        return switch (type) {
            case BOOLEAN -> Boolean.parseBoolean(value);
            case INTEGER -> {
                try {
                    yield Integer.valueOf(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(value + " is not a whole number");
                }
            }
            case DECIMAL -> {
                try {
                    yield Double.valueOf(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(value + " is not a number");
                }
            }
            case TEXT -> value.isEmpty() || value.equalsIgnoreCase("none") ? null : value;
        };
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditorSession session)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || !event.getClickedInventory().equals(session.inventory)) {
            return;
        }
        Consumer<ClickType> action = session.actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EditorSession) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EditorSession
                && event.getPlayer() instanceof Player player) {
            sessions.remove(player.getUniqueId());
        }
    }

    /** Consumes the chat input of a pending edit. */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingEdit edit = pending.remove(player.getUniqueId());
        if (edit == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            runAtEntity(player, () -> open(player));
            return;
        }
        runAtEntity(player, () -> {
            EditorSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                session = new EditorSession(player.getUniqueId());
                sessions.put(player.getUniqueId(), session);
            }
            write(player, session, edit.field(), message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        pending.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ helpers

    private ItemStack simple(Material material, String name, List<String> lore) {
        return ItemBuilder.from(new ItemStack(material))
                .name(StringUtil.parseComponent(name))
                .lore(lore.stream().map(StringUtil::parseComponent).toList())
                .build();
    }

    private String localized(Player player, String key) {
        return localized(player, key, Map.of());
    }

    private String localized(Player player, String key, Map<String, String> values) {
        return StringUtil.MINI_MESSAGE.serialize(component(player, key, values));
    }

    private net.kyori.adventure.text.Component component(Player player, String key, Map<String, String> values) {
        LanguageManager language = plugin.getLanguageManager();
        ShopMenuManager menus = plugin.getShopMenuManager();
        String configured = menus == null ? language.get(player, key) : menus.configuredText(player, key);
        return language.parse(player, configured, placeholders(values));
    }

    private void send(Player player, String key) {
        send(player, key, Map.of());
    }

    private void send(Player player, String key, Map<String, String> values) {
        StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player,
                plugin.getLanguageManager().get(player, key), placeholders(values)));
    }

    private static TagResolver placeholders(Map<String, String> values) {
        List<TagResolver> resolvers = new ArrayList<>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), String.valueOf(entry.getValue())));
        }
        return TagResolver.resolver(resolvers);
    }

    private static Map<String, String> with(Map<String, String> values, String key, String value) {
        Map<String, String> result = new LinkedHashMap<>(values);
        result.put(key, value == null || value.equals("null") ? "-" : value);
        return result;
    }

    private void runAtEntity(Player player, Runnable action) {
        if (player.isOnline()) {
            plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> action.run());
        }
    }

    private enum FieldType {
        BOOLEAN, INTEGER, DECIMAL, TEXT
    }

    private record Field(YamlFileStore store, Object[] path, FieldType type, Material material, String labelKey,
                         Map<String, String> arguments) {
    }

    private record PendingEdit(EditorSession session, Field field) {
    }

    private static final class EditorSession implements InventoryHolder {
        private final UUID playerId;
        private final Map<Integer, Consumer<ClickType>> actions = new LinkedHashMap<>();
        private List<Field> fields = List.of();
        private int page;
        private Inventory inventory;

        private EditorSession(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
