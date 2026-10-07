package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-game YAML editor for skin definitions, the ItemSkin GUI and Item Display preview transform. */
public final class SkinEditorManager implements Listener {
    private final HMCWrapsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SkinEditorManager(HMCWrapsPlugin plugin) { this.plugin = plugin; }

    public void openSkin(Player player, String skinId) {
        Path file = findSkinFile(skinId);
        if (file == null) { send(player, "<red>Skin o file non trovato.</red>"); return; }
        Map<String, Field> fields = new LinkedHashMap<>();
        fields.put("Nome", new Field(file, "display-name", Type.TEXT));
        fields.put("Materiale", new Field(file, "material", Type.TEXT));
        fields.put("Item model", new Field(file, "item-model", Type.TEXT));
        fields.put("Rarità", new Field(file, "rarity", Type.TEXT));
        fields.put("Categorie", new Field(file, "categories", Type.LIST));
        fields.put("Lore", new Field(file, "lore", Type.LORE));
        fields.put("Compatibilità materiali", new Field(file, "compatible-materials", Type.LIST));
        fields.put("Compatibilità item", new Field(file, "compatible-items", Type.LIST));
        fields.put("Prezzo", new Field(file, "price.amount", Type.NUMBER));
        fields.put("Valuta", new Field(file, "price.currency", Type.TEXT));
        open(player, "Editor skin: " + skinId, fields);
    }

    public void openGui(Player player) {
        Map<String, Field> fields = new LinkedHashMap<>(); Path file = HMCWraps.ITEMSKIN_GUI_PATH;
        fields.put("Titolo GUI", new Field(file, "gui.title", Type.TEXT));
        fields.put("Nome controllo oggetto", new Field(file, "gui.item-name", Type.TEXT));
        fields.put("Lore controllo oggetto", new Field(file, "gui.item-lore", Type.LORE));
        fields.put("Lore skin", new Field(file, "gui.skin-lore", Type.LORE));
        fields.put("Slot oggetto", new Field(file, "gui.item-slot", Type.NUMBER));
        open(player, "Editor GUI ItemSkin", fields);
    }

    public void openPreview(Player player) {
        Map<String, Field> fields = new LinkedHashMap<>(); Path file = HMCWraps.CONFIG_PATH;
        String root = "preview.item-display-transform.";
        fields.put("Offset X", new Field(file, root + "translation.x", Type.NUMBER));
        fields.put("Offset Y", new Field(file, root + "translation.y", Type.NUMBER));
        fields.put("Offset Z", new Field(file, root + "translation.z", Type.NUMBER));
        fields.put("Rotazione spada X", new Field(file, root + "sword-rotation.x", Type.NUMBER));
        fields.put("Rotazione spada Y", new Field(file, root + "sword-rotation.y", Type.NUMBER));
        fields.put("Rotazione spada Z", new Field(file, root + "sword-rotation.z", Type.NUMBER));
        open(player, "Editor preview Item Display", fields);
    }

    private void open(Player player, String title, Map<String, Field> fields) {
        Session session = new Session(fields);
        session.inventory = Bukkit.createInventory(session, 27, title.length() > 32 ? title.substring(0, 32) : title);
        int slot = 0;
        for (var entry : fields.entrySet()) {
            ItemStack icon = new ItemStack(Material.PAPER); ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName("§b" + entry.getKey());
            Object current = YamlConfiguration.loadConfiguration(entry.getValue().file().toFile()).get(entry.getValue().path());
            meta.setLore(java.util.List.of("§7Attuale: §f" + String.valueOf(current), "", "§eClicca e scrivi il nuovo valore in chat"));
            icon.setItemMeta(meta); session.bySlot.put(slot, entry.getValue()); session.inventory.setItem(slot++, icon);
        }
        player.openInventory(session.inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Session session) || !(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true); Field field = session.bySlot.get(event.getRawSlot()); if (field == null) return;
        pending.put(player.getUniqueId(), new Pending(field)); player.closeInventory();
        send(player, "<gray>Scrivi il nuovo valore. Liste: valori separati da virgola; lore: righe separate da |. Scrivi <white>cancel</white> per annullare.</gray>");
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending edit = pending.remove(event.getPlayer().getUniqueId()); if (edit == null) return;
        event.setCancelled(true); if (event.getMessage().equalsIgnoreCase("cancel")) return;
        plugin.getFoliaLib().getScheduler().runAtEntity(event.getPlayer(), ignored -> save(event.getPlayer(), edit.field(), event.getMessage()));
    }

    private void save(Player player, Field field, String input) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(field.file().toFile());
            Object value = switch (field.type()) {
                case NUMBER -> Double.parseDouble(input);
                case LIST -> java.util.Arrays.stream(input.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                case LORE -> java.util.Arrays.stream(input.split("\\|", -1)).map(String::trim).toList();
                case TEXT -> input;
            };
            yaml.set(field.path(), value); yaml.save(field.file().toFile());
            send(player, "<green>Valore salvato.</green> <gray>Usa <white>/itemskin reload</white> per applicarlo.</gray>");
        } catch (Exception exception) { send(player, "<red>Valore non valido o file non scrivibile.</red>"); }
    }

    private Path findSkinFile(String id) {
        try (var paths = Files.find(HMCWraps.SKINS_PATH, 8, (path, attr) -> attr.isRegularFile() && (path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")))) {
            return paths.filter(path -> id.equalsIgnoreCase(YamlConfiguration.loadConfiguration(path.toFile()).getString("id"))).findFirst().orElse(null);
        } catch (Exception exception) { return null; }
    }
    private void send(Player player, String text) { StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, text)); }
    private enum Type { TEXT, NUMBER, LIST, LORE }
    private record Field(Path file, String path, Type type) { }
    private record Pending(Field field) { }
    private static final class Session implements InventoryHolder {
        final Map<String, Field> fields; final Map<Integer, Field> bySlot = new LinkedHashMap<>(); Inventory inventory;
        Session(Map<String, Field> fields) { this.fields = fields; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
