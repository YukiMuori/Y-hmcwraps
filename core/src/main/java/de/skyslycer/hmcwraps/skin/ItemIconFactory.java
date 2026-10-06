package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.skin.config.SkinIconConfiguration;
import de.skyslycer.hmcwraps.util.VersionUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Converts configuration-only icon descriptions to Bukkit items through the existing optional hook accessor. */
public final class ItemIconFactory {
    private final HMCWrapsPlugin plugin;

    public ItemIconFactory(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(SkinIconConfiguration config, @Nullable String fallbackName) {
        return create(config, fallbackName, null);
    }

    /**
     * Resolves a textual reference such as {@code DIAMOND}, {@code material:DIAMOND} or {@code nexo:item_id}
     * into an item. Used by collection rewards and the shop editor.
     */
    public ItemStack createFromReference(@Nullable String reference, @Nullable String name) {
        if (reference == null || reference.isBlank()) {
            return new ItemStack(Material.PAPER);
        }
        String value = reference.trim();
        String type = "material";
        if (value.contains(":")) {
            String[] parts = value.split(":", 2);
            String candidate = parts[0].toLowerCase(Locale.ROOT);
            if (candidate.equals("material") || candidate.equals("vanilla") || candidate.equals("head")
                    || candidate.equals("nexo") || candidate.equals("itemsadder") || candidate.equals("oraxen")
                    || candidate.equals("craftengine") || candidate.equals("mythic")
                    || candidate.equals("executableitems") || candidate.equals("mmoitems")
                    || candidate.equals("custom")) {
                type = candidate;
                value = parts[1];
            }
        }
        return create(new SkinIconConfiguration(type, value, name, List.of()), name, null);
    }

    public ItemStack create(SkinIconConfiguration config, @Nullable String fallbackName, @Nullable Player player) {
        if (config == null) return new ItemStack(Material.PAPER);
        String type = config.getType().toLowerCase(Locale.ROOT);
        String id = config.getId();
        String hookId = hookId(type, id);
        ItemStack base = plugin.getHookAccessor().getItemFromHook(hookId);
        if (base == null && (type.equals("material") || type.equals("vanilla") || type.equals("head"))) {
            Material material = Material.matchMaterial(id);
            if (material != null) base = new ItemStack(material);
        }
        if (base == null && type.equals("head")) base = new ItemStack(Material.PLAYER_HEAD);
        if (base == null || base.getType().isAir()) {
            plugin.getLogger().warning("Could not resolve GUI item '" + type + ":" + id + "'; using BARRIER.");
            base = new ItemStack(Material.BARRIER);
        }

        ItemBuilder builder = ItemBuilder.from(base);
        String configuredName = config.getName() == null || config.getName().isBlank() ? fallbackName : config.getName();
        if (configuredName != null && !configuredName.isBlank()) {
            builder.name(plugin.getLanguageManager().parse(player, configuredName));
        }
        List<String> lore = config.getLore();
        if (lore != null && !lore.isEmpty()) {
            builder.lore(lore.stream().map(line -> plugin.getLanguageManager().parse(player, line)).toList());
        }
        if (config.getModelId() != null && config.getModelId() >= 0) {
            builder.model(config.getModelId());
        }
        if (Boolean.TRUE.equals(config.getGlow())) builder.glow();
        ItemStack result = builder.build();
        var meta = result.getItemMeta();
        if (meta == null) return result;

        if (VersionUtil.itemModelSupported() && config.getItemModel() != null && !config.getItemModel().isBlank()) {
            NamespacedKey model = NamespacedKey.fromString(config.getItemModel());
            if (model == null) plugin.getLogger().warning("Invalid GUI item model key '" + config.getItemModel() + "'.");
            else meta.setItemModel(model);
        }
        if (VersionUtil.hasTooltipStyle() && config.getTooltipStyle() != null && !config.getTooltipStyle().isBlank()) {
            NamespacedKey tooltipStyle = NamespacedKey.fromString(config.getTooltipStyle());
            if (tooltipStyle == null) plugin.getLogger().warning("Invalid tooltip style key '" + config.getTooltipStyle() + "'.");
            else meta.setTooltipStyle(tooltipStyle);
        }
        if (VersionUtil.hasDataComponents() && config.getGlintOverride() != null) {
            meta.setEnchantmentGlintOverride(config.getGlintOverride());
        }
        if (VersionUtil.hasDataComponents() && Boolean.TRUE.equals(config.getHideTooltip())) {
            meta.setHideTooltip(true);
        }
        if (result.getType() == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
            if (config.getSkullOwner() != null && !config.getSkullOwner().isBlank()) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(config.getSkullOwner()));
            }
            result.setItemMeta(skullMeta);
            if (config.getSkullTexture() != null && !config.getSkullTexture().isBlank()) {
                result = ItemBuilder.skull(result).texture(config.getSkullTexture(), UUID.randomUUID()).build();
            }
        } else {
            result.setItemMeta(meta);
        }
        return result;
    }

    private String hookId(String type, String id) {
        return switch (type) {
            case "nexo", "itemsadder", "oraxen", "craftengine", "mythic", "executableitems", "mmoitems" ->
                    id.contains(":") ? id : type + ":" + id;
            case "material", "vanilla", "head" -> id;
            case "custom" -> id;
            default -> {
                plugin.getLogger().warning("Unknown GUI item provider type '" + type + "'.");
                yield id;
            }
        };
    }
}
