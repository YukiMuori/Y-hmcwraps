package de.skyslycer.hmcwraps.compat;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tolerant Minecraft version value object. It understands both the classic ({@code 1.21.4}) and the
 * new ({@code 26.2}) schemes and never fails to parse, because forks such as Leaf report strings like
 * {@code 26.2.build.123-alpha}.
 *
 * <p>This class is pure and server independent so the compatibility layer can be unit tested without a
 * running server.</p>
 */
public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {

    private static final Pattern PATTERN = Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    /** The version unknown servers fall back to. */
    public static final MinecraftVersion UNKNOWN = new MinecraftVersion(0, 0, 0);

    public static @NotNull MinecraftVersion parse(String version) {
        if (version == null) {
            return UNKNOWN;
        }
        Matcher matcher = PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return UNKNOWN;
        }
        return new MinecraftVersion(number(matcher.group(1)), number(matcher.group(2)), number(matcher.group(3)));
    }

    /**
     * Whether this version is at least the supplied version.
     *
     * <p>The classic {@code 1.x} scheme sorts before the new {@code 26.x} scheme: comparing
     * {@code 1.21.4} with {@code 26.2} first compares the major component, which handles the change
     * from {@code 1.21} to {@code 26.x} correctly.</p>
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new MinecraftVersion(major, minor, patch)) >= 0;
    }

    /** Whether this version is at least the supplied version. */
    public boolean isAtLeast(@NotNull MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    /** Whether this is the classic pre-1.21 style version. */
    public boolean isLegacyScheme() {
        return major == 1;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static int number(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
