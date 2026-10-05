package de.skyslycer.hmcwraps.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tolerant parser for the version string reported by the server implementation.
 * <p>
 * Server forks may use version strings whose components are not all numbers, for example {@code 26.2.build.123-alpha}.
 * Parsing must never fail, as the result is read while the plugin is enabling.
 */
final class VersionParser {

    /**
     * Matches up to three leading dot-separated numeric components, optionally prefixed with a {@code v}.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private VersionParser() {
    }

    /**
     * Parse the version string into a major, minor and patch number.
     * Components which are missing or not numeric are parsed as {@code 0}.
     *
     * @param version The version string, for example {@code 1.21.4-R0.1-SNAPSHOT}
     * @return The parsed version as an array containing the major, minor and patch number, in that order
     */
    static int[] parse(String version) {
        int[] result = new int[3];
        if (version == null) {
            return result;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return result;
        }
        for (int i = 1; i <= result.length; i++) {
            result[i - 1] = number(matcher.group(i));
        }
        return result;
    }

    private static int number(String component) {
        if (component == null) {
            return 0;
        }
        try {
            return Integer.parseInt(component);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

}
