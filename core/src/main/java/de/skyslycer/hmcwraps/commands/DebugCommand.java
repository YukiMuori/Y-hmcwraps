package de.skyslycer.hmcwraps.commands;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.commands.annotation.LogFiles;
import de.skyslycer.hmcwraps.commands.annotation.NoHelp;
import de.skyslycer.hmcwraps.commands.annotation.PluginFiles;
import de.skyslycer.hmcwraps.debug.DebugCreator;
import de.skyslycer.hmcwraps.messages.Messages;
import de.skyslycer.hmcwraps.serialization.debug.Debuggable;
import de.skyslycer.hmcwraps.serialization.wrap.Wrap;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.*;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.nio.file.Files;
import java.nio.file.Path;

@NoHelp
@Command("wraps")
public class DebugCommand {

    public static final String DEBUG_PERMISSION = "hmcwraps.debug";

    private final HMCWrapsPlugin plugin;

    public DebugCommand(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isUpload(String upload) {
        return upload == null || !upload.equalsIgnoreCase("-noupload");
    }

    @Subcommand("debug info")
    @Description("Debugs plugin and server information.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugInformation(CommandSender sender, @Suggest("-noupload") @Optional String upload) {
        uploadAndSend(sender, DebugCreator.createDebugInformation(plugin), isUpload(upload));
    }

    @Subcommand("debug config")
    @Description("Debugs plugin configuration.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugConfig(CommandSender sender, @Suggest("-noupload") @Optional String upload) {
        uploadAndSend(sender, DebugCreator.createDebugConfig(plugin), isUpload(upload));
    }

    @Subcommand("debug wraps")
    @Description("Debugs wraps and collections.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugWraps(CommandSender sender, @Suggest("-noupload") @Optional String upload) {
        uploadAndSend(sender, DebugCreator.createDebugWraps(plugin), isUpload(upload));
    }

    @Subcommand("debug wrap")
    @Description("Debugs one wrap.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugWrap(CommandSender sender, Wrap wrap, @Suggest("-noupload") @Optional String upload) {
        uploadAndSend(sender, DebugCreator.createDebugWrap(plugin, wrap), isUpload(upload));
    }

    @Subcommand("debug player")
    @Description("Debugs a player.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugPlayer(CommandSender sender, @Default("@s") Player player, @Suggest("-noupload") @Optional String upload) {
        uploadAndSend(sender, DebugCreator.createDebugPlayer(plugin, player), isUpload(upload));
    }

    @Subcommand("debug item")
    @Description("Debugs the item the player is currently holding.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugItem(CommandSender sender, @Default("@s") Player player, @Suggest("-noupload") @Optional String upload) {
        var item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            plugin.getMessageHandler().send(player, Messages.NO_ITEM);
            return;
        }
        uploadAndSend(sender, DebugCreator.createDebugItemData(plugin, item), isUpload(upload));
    }

    @Subcommand("debug log")
    @Description("Uploads a server log.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugLog(CommandSender sender, @LogFiles @Default("latest.log") @Optional String log) {
        var basePath = Path.of("logs").toAbsolutePath();
        var path = basePath.resolve(log).normalize();
        if (!checkFile(sender, path, basePath)) return;
        handleLink(sender, DebugCreator.uploadLog(path).orElse(null), "log");
    }

    @Subcommand("debug upload")
    @Description("Uploads a configuration file.")
    @CommandPermission(DEBUG_PERMISSION)
    public void onDebugUpload(CommandSender sender, @PluginFiles String file) {
        var basePath = HMCWrapsPlugin.PLUGIN_PATH.toAbsolutePath();
        var path = basePath.resolve(file).normalize();
        if (!checkFile(sender, path, basePath)) return;
        try {
            var contents = Files.readString(path);
            var type = "plain";
            if (path.toString().endsWith(".yml") || path.toString().endsWith(".yaml")) {
                type = "yaml";
            }
            handleLink(sender, DebugCreator.upload(contents, type).orElse(null), path.getFileName().toString());
        } catch (Exception exception) {
            sendLocalized(sender, "debug.file-upload-failed");
            plugin.logSevere("Failed to upload file " + path + "!", exception);
        }
    }

    private void uploadAndSend(CommandSender sender, Debuggable debuggable, boolean upload) {
        plugin.getLogger().info("Debug information (" + debuggable.getClass().getSimpleName() + "): \n" + DebugCreator.debugToJson(debuggable));
        sendLocalized(sender, "debug.info-printed",
                Placeholder.unparsed("type", debuggable.getClass().getSimpleName()));
        if (upload) {
            plugin.getFoliaLib().getScheduler().runAsync((ignored) -> {
                var link = DebugCreator.upload(DebugCreator.debugToJson(debuggable), "json");
                handleLink(sender, link.orElse(null), debuggable.getClass().getSimpleName());
            });
        }
    }

    private void handleLink(CommandSender sender, String link, String type) {
        if (link != null && !link.equals("Too large")) {
            var prefix = StringUtil.parseComponent(sender,
                    plugin.getLanguageManager().get(sender instanceof Player player ? player : null, "debug.upload-success"),
                    Placeholder.unparsed("type", type));
            var hover = StringUtil.parseComponent(sender,
                    plugin.getLanguageManager().get(sender instanceof Player player ? player : null, "debug.click-to-open"));
            StringUtil.sendComponent(sender, prefix.append(Component.text(link).color(NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.openUrl(link)).hoverEvent(HoverEvent.showText(hover))));
        } else {
            sendLocalized(sender, "debug.upload-failed");
        }
    }

    private void sendLocalized(CommandSender sender, String key, TagResolver... resolvers) {
        String text = plugin.getLanguageManager().get(sender instanceof Player player ? player : null, key);
        StringUtil.send(sender, text, resolvers);
    }

    private boolean checkFile(CommandSender sender, Path path, Path basePath) {
        if (!path.toAbsolutePath().startsWith(basePath) || Files.notExists(path)) {
            sendLocalized(sender, "debug.file-missing");
            return false;
        }
        if (Files.isDirectory(path)) {
            sendLocalized(sender, "debug.file-directory");
            return false;
        }
        return true;
    }

}
