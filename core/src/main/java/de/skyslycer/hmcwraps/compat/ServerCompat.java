package de.skyslycer.hmcwraps.compat;

import de.skyslycer.hmcwraps.util.VersionUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Central, isolated knowledge about the running server. Everything version specific that is not item
 * data lives here so future Minecraft releases only require edits in one place.
 */
public final class ServerCompat {

    private static final MinecraftVersion VERSION = MinecraftVersion.parse(Bukkit.getBukkitVersion());
    private static final String RAW_VERSION = Bukkit.getBukkitVersion();
    private static final String SERVER_NAME = Bukkit.getName();

    private ServerCompat() {
    }

    /** The parsed Minecraft version of the running server. */
    public static @NotNull MinecraftVersion version() {
        return VERSION;
    }

    /** The raw version string reported by the server implementation. */
    public static @NotNull String rawVersion() {
        return RAW_VERSION;
    }

    /** The server implementation name, for example {@code Paper}. */
    public static @NotNull String serverName() {
        return SERVER_NAME;
    }

    /** Whether item model components ({@code minecraft:item_model}) are available (1.21.4+). */
    public static boolean supportsItemModel() {
        return VersionUtil.itemModelSupported();
    }

    /** Whether tooltip style components are available (1.21.2+). */
    public static boolean supportsTooltipStyle() {
        return VersionUtil.hasTooltipStyle();
    }

    /** Whether data components replaced legacy NBT access (1.20.5+). */
    public static boolean supportsDataComponents() {
        return VersionUtil.hasDataComponents();
    }

    /** Whether the running server supports the classic 1.20.4 feature set the plugin requires. */
    public static boolean isSupported() {
        return VersionUtil.isSupported();
    }

    /** A short description used in logs and diagnostics. */
    public static @NotNull String describe() {
        return SERVER_NAME + " " + RAW_VERSION + " (parsed " + VERSION + ")";
    }
}
