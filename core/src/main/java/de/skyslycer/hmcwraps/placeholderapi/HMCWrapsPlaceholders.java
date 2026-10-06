package de.skyslycer.hmcwraps.placeholderapi;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.messages.Messages;
import de.skyslycer.hmcwraps.util.ColorUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public class HMCWrapsPlaceholders extends PlaceholderExpansion {

    private final HMCWrapsPlugin plugin;

    public HMCWrapsPlaceholders(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "hmcwraps";
    }

    @Override
    public String getAuthor() {
        return "Skyslycer";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        var skinManager = plugin.getItemSkinManager();
        String normalizedIdentifier = identifier.toLowerCase(Locale.ROOT);
        if (normalizedIdentifier.equals("skins_total")) {
            return String.valueOf(skinManager.getSkins().size());
        }
        if (player != null && normalizedIdentifier.startsWith("skin_owned_")) {
            String skinId = identifier.substring("skin_owned_".length());
            if (skinManager.getSkin(skinId).isEmpty()) return null;
            skinManager.preloadOwnership(player.getUniqueId());
            return String.valueOf(skinManager.cachedOwnedSkinIds(player.getUniqueId()).contains(skinId.toLowerCase(Locale.ROOT)));
        }
        if (player != null && normalizedIdentifier.equals("skins_owned")) {
            skinManager.preloadOwnership(player.getUniqueId());
            return String.valueOf(countConfigured(skinManager.cachedOwnedSkinIds(player.getUniqueId())));
        }
        if (player != null && normalizedIdentifier.equals("skins_owned_percentage")) {
            skinManager.preloadOwnership(player.getUniqueId());
            int total = skinManager.getSkins().size();
            if (total == 0) return "0";
            return String.valueOf((int) Math.floor(countConfigured(skinManager.cachedOwnedSkinIds(player.getUniqueId())) * 100.0 / total));
        }
        if (player != null && normalizedIdentifier.equals("mainhand_skin")) {
            var mainHandWrap = plugin.getWrapper().getWrap(player.getInventory().getItemInMainHand());
            if (mainHandWrap == null) return "";
            return skinManager.getSkins().stream()
                    .filter(skin -> skin.cosmetic().getUuid().equals(mainHandWrap.getUuid()))
                    .map(skin -> skin.id()).findFirst().orElse("");
        }
        if (player != null && normalizedIdentifier.equals("mainhand_compatible")) {
            return String.valueOf(skinManager.getCompatibleSkins(player.getInventory().getItemInMainHand()).size());
        }
        if (identifier.equals("mainhand") && player != null) {
            var wrap = plugin.getWrapper().getWrap(player.getInventory().getItemInMainHand());
            if (wrap == null) {
                return null;
            }
            return wrap.getUuid();
        } else if (identifier.equals("mainhand_itemmodel") && player != null) {
            var meta = player.getInventory().getItemInMainHand().getItemMeta();
            if (meta == null || !meta.hasItemModel()) return null;
            return meta.getItemModel().toString();
        } else if (identifier.equals("filter") && player != null) {
            if (plugin.getFilterStorage().get(player)) {
                return StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.INVENTORY_FILTER_ACTIVE)));
            } else {
                return StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.INVENTORY_FILTER_INACTIVE)));
            }
        } else if (identifier.equals("iswrapped") && player != null) {
            var wrap = plugin.getWrapper().getWrap(player.getInventory().getItemInMainHand());
            return PlainTextComponentSerializer.plainText().serialize(StringUtil.parseComponent(player,
                    plugin.getMessageHandler().get(player, wrap == null ? Messages.PLACEHOLDER_NOT_EQUIPPED : Messages.PLACEHOLDER_EQUIPPED)));
        } else if (identifier.split("_").length >= 2) {
            var action = identifier.substring(0, identifier.indexOf("_"));
            var wrapUuid = identifier.substring(identifier.indexOf("_") + 1);
            var wrap = plugin.getWrapsLoader().getWraps().get(wrapUuid);
            switch (action) {
                case "equipped" -> { // Check if the specified wrap is the one equipped on the item the player is wrapping in the virtual inventory
                    if (player == null) {
                        return null;
                    }
                    var equipped = plugin.getWrapGui().get(player.getUniqueId());
                    return wrapUuid.equals(equipped) ?
                            StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.PLACEHOLDER_EQUIPPED)))
                            : StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.PLACEHOLDER_NOT_EQUIPPED)));
                }
                case "modelid" -> {
                    if (wrap == null) {
                        return invalidWrap(player);
                    }
                    return String.valueOf(wrap.getModelId() >= 0 ? wrap.getModelId() : "None");
                }
                case "color" -> {
                    if (wrap == null || wrap.getColor() == null) {
                        return invalidWrap(player);
                    }
                    return ColorUtil.colorToHex(wrap.getColor());
                }
                case "type" -> {
                    return plugin.getWrapsLoader().getTypeWraps().entrySet().stream().filter(it -> it.getValue().contains(wrapUuid))
                            .findFirst().map(Map.Entry::getKey).orElse(null);
                }
                case "hasperm" -> {
                    if (wrap == null || player == null) {
                        return invalidWrap(player);
                    }
                    return wrap.hasPermission(player) ?
                            StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.PLACEHOLDER_HAS_PERMISSION)))
                            : StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.PLACEHOLDER_NO_PERMISSION)));
                }
            }
        }
        return null;
    }

    private int countConfigured(java.util.Set<String> skinIds) {
        return (int) plugin.getItemSkinManager().getSkins().stream()
                .filter(skin -> skinIds.contains(skin.id().toLowerCase(Locale.ROOT))).count();
    }

    private String invalidWrap(Player player) {
        return StringUtil.LEGACY_SERIALIZER.serialize(StringUtil.parseComponent(player, plugin.getMessageHandler().get(player, Messages.PLACEHOLDER_INVALID_WRAP)));
    }

}
