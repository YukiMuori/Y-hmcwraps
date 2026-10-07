package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent physical skin showcases backed by an Item Display and an invisible Interaction hitbox. */
public final class SkinDisplayManager implements Listener {
    private final HMCWrapsPlugin plugin;
    private final NamespacedKey skinKey;
    private final NamespacedKey displayKey;
    private final File file;

    public SkinDisplayManager(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
        this.skinKey = new NamespacedKey(plugin, "skin_display_skin");
        this.displayKey = new NamespacedKey(plugin, "skin_display_id");
        this.file = new File(plugin.getDataFolder(), "skin-displays.yml");
    }

    public UUID create(Player creator, ItemSkin skin) {
        Location location = creator.getEyeLocation().add(creator.getEyeLocation().getDirection().multiply(3));
        UUID id = UUID.randomUUID();
        spawn(id, skin, location);
        save(id, skin.id(), location);
        return id;
    }

    public boolean removeNearest(Player player, double radius) {
        Interaction nearest = player.getWorld().getNearbyEntitiesByType(Interaction.class, player.getLocation(), radius).stream()
                .filter(entity -> entity.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING))
                .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
        if (nearest == null) return false;
        String id = nearest.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
        removeEntities(id);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("displays." + id, null);
        try { yaml.save(file); } catch (Exception exception) { plugin.logSevere("Could not save skin-displays.yml.", exception); }
        return true;
    }

    public int count() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("displays");
        return section == null ? 0 : section.getKeys(false).size();
    }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("displays");
        if (section == null) return;
        for (String rawId : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String skinId = section.getString(rawId + ".skin");
                ItemSkin skin = plugin.getItemSkinManager().getSkin(skinId == null ? "" : skinId).orElse(null);
                Location location = section.getLocation(rawId + ".location");
                if (skin == null || location == null || location.getWorld() == null) continue;
                boolean exists = Bukkit.getWorlds().stream().flatMap(world -> world.getEntitiesByClass(Interaction.class).stream())
                        .anyMatch(entity -> rawId.equals(entity.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING)));
                if (!exists) spawn(id, skin, location);
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void spawn(UUID id, ItemSkin skin, Location location) {
        Material material = plugin.getCollectionHelper().getMaterial(skin.cosmetic());
        ItemStack item = plugin.getWrapper().setWrap(skin.cosmetic(), new ItemStack(material), false, null);
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1.5f, 1.5f, 1.5f), new AxisAngle4f()));
            entity.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, id.toString());
            entity.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin.id());
        });
        location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(2.0f);
            entity.setInteractionHeight(2.5f);
            entity.setResponsive(true);
            entity.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, id.toString());
            entity.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin.id());
        });
    }

    private void save(UUID id, String skinId, Location location) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "displays." + id;
        yaml.set(path + ".skin", skinId);
        yaml.set(path + ".location", location);
        try { yaml.save(file); } catch (Exception exception) { plugin.logSevere("Could not save skin-displays.yml.", exception); }
    }

    private void removeEntities(String id) {
        Bukkit.getWorlds().forEach(world -> new ArrayList<>(world.getEntities()).stream()
                .filter(entity -> id.equals(entity.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING)))
                .forEach(org.bukkit.entity.Entity::remove));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        String skinId = event.getRightClicked().getPersistentDataContainer().get(skinKey, PersistentDataType.STRING);
        if (skinId == null) return;
        event.setCancelled(true);
        ItemSkin skin = plugin.getItemSkinManager().getSkin(skinId).orElse(null);
        if (skin == null) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held != null && !held.getType().isAir() && plugin.getItemSkinManager().getCompatibleSkins(held).stream()
                .anyMatch(candidate -> candidate.id().equals(skin.id()))) {
            plugin.getPreviewManager().createHandTrial(event.getPlayer(), skin.cosmetic(), held, 15);
        } else if (plugin.getShopMenuManager() != null) {
            plugin.getShopMenuManager().openHome(event.getPlayer());
        }
    }
}
