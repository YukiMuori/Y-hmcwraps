package de.skyslycer.hmcwraps.lang;

import com.bgsoftware.common.config.CommentedConfiguration;
import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cached locale catalog with MiniMessage-aware reusable <lang:...> and <glyph:...> tags. */
public final class LanguageManager implements LanguageService {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Set<String> BUILT_IN_LOCALES = Set.of("en", "it");

    private final HMCWrapsPlugin plugin;
    private final Map<String, YamlConfiguration> locales = new ConcurrentHashMap<>();
    private volatile String defaultLocale = "en";
    private volatile boolean usePlayerLocale;

    public LanguageManager(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean load() {
        try {
            Files.createDirectories(HMCWraps.LANG_PATH);
            for (String locale : BUILT_IN_LOCALES) {
                Path path = HMCWraps.LANG_PATH.resolve(locale + ".yml");
                if (Files.notExists(path)) {
                    try (InputStream resource = plugin.getResource("lang/" + locale + ".yml")) {
                        if (resource != null) Files.copy(resource, path);
                    }
                    // Migrate old per-server English overrides when the new lang folder did not exist yet.
                    if (locale.equals("en") && Files.exists(HMCWraps.MESSAGES_PATH)) {
                        CommentedConfiguration created = CommentedConfiguration.loadConfiguration(path.toFile());
                        if (!created.hasFailed() && migrateLegacyProperties(created, true)) created.save(path.toFile());
                    }
                } else {
                    // Keep existing translations editable while adding newly introduced plugin keys.
                    // syncWithConfig only supplies missing paths; it does not replace server values.
                    try (InputStream resource = plugin.getResource("lang/" + locale + ".yml")) {
                        if (resource != null) {
                            CommentedConfiguration existing = CommentedConfiguration.loadConfiguration(path.toFile());
                            if (existing.hasFailed()) {
                                plugin.getLogger().warning("Could not update " + path.getFileName()
                                        + " because the existing language file is invalid; leaving it untouched.");
                            } else {
                                boolean migratedLegacyMessages = locale.equals("en") && migrateLegacyProperties(existing, false);
                                existing.syncWithConfig(path.toFile(), resource);
                                if (migratedLegacyMessages) existing.save(path.toFile());
                            }
                        }
                    }
                }
            }
            Map<String, YamlConfiguration> loaded = new HashMap<>();
            try (var files = Files.list(HMCWraps.LANG_PATH)) {
                for (Path path : files.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".yml") || file.toString().endsWith(".yaml")).toList()) {
                    String locale = stripExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(path.toFile());
                    loaded.put(locale, yaml);
                }
            }
            if (loaded.isEmpty()) {
                plugin.logSevere("No language files were found in " + HMCWraps.LANG_PATH);
                return false;
            }
            locales.clear();
            locales.putAll(loaded);
            if (plugin.getConfiguration() != null && plugin.getConfiguration().getLanguage() != null) {
                defaultLocale = normalize(plugin.getConfiguration().getLanguage().getDefaultLanguage());
                usePlayerLocale = plugin.getConfiguration().getLanguage().isUsePlayerLocale();
            }
            if (!locales.containsKey(defaultLocale)) {
                plugin.getLogger().warning("Language '" + defaultLocale + "' is not loaded; falling back to English or the first available locale.");
                defaultLocale = locales.containsKey("en") ? "en" : locales.keySet().stream().sorted().findFirst().orElse("en");
            }
            return true;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException exception) {
            plugin.logSevere("Could not load language files.", exception);
            return false;
        }
    }

    private boolean migrateLegacyProperties(CommentedConfiguration language, boolean overrideDefaults) throws IOException {
        if (Files.notExists(HMCWraps.MESSAGES_PATH)) return false;

        Properties defaults = new Properties();
        try (InputStream resource = plugin.getResource("messages.properties")) {
            if (resource == null) return false;
            defaults.load(resource);
        }
        Properties existingMessages = new Properties();
        try (InputStream input = Files.newInputStream(HMCWraps.MESSAGES_PATH)) {
            existingMessages.load(input);
        }

        boolean migrated = false;
        for (String key : existingMessages.stringPropertyNames()) {
            String value = existingMessages.getProperty(key);
            if (!Objects.equals(value, defaults.getProperty(key))
                    && (overrideDefaults || !language.contains("legacy." + key))) {
                language.set("legacy." + key, value);
                migrated = true;
            }
        }
        return migrated;
    }

    @Override
    public String get(Player player, String key) {
        if (key == null || key.isBlank()) return "";
        YamlConfiguration selected = locale(player);
        String value = selected == null ? null : selected.getString(key);
        if (value == null) {
            YamlConfiguration fallback = locales.get(defaultLocale);
            value = fallback == null ? null : fallback.getString(key);
        }
        return value == null ? key : value;
    }

    @Override
    public Component parse(Player player, String text, TagResolver... extraResolvers) {
        if (text == null || text.isEmpty()) return Component.empty();
        Set<String> resolving = new HashSet<>();
        TagResolver languageTags = TagResolver.builder()
                .resolver(TagResolver.resolver("lang", (queue, context) -> referenceTag(player, queue, resolving, "")))
                .resolver(TagResolver.resolver("glyph", (queue, context) -> referenceTag(player, queue, resolving, "glyphs.")))
                .build();
        TagResolver[] combined = new TagResolver[extraResolvers.length + 1];
        combined[0] = languageTags;
        System.arraycopy(extraResolvers, 0, combined, 1, extraResolvers.length);
        return Component.text().decoration(TextDecoration.ITALIC, false)
                .append(MINI_MESSAGE.deserialize(text, TagResolver.resolver(combined)))
                .build();
    }

    private Tag referenceTag(Player player, ArgumentQueue queue,
                             Set<String> resolving, String prefix) {
        String key = prefix + queue.popOr("A language tag requires a key").value();
        if (!resolving.add(key)) return Tag.inserting(Component.text(key));
        try {
            String value = get(player, key);
            if (value.equals(key)) return Tag.inserting(Component.text(key));
            // Resolve references one level at a time, with a recursion guard for bad translations.
            return Tag.inserting(MINI_MESSAGE.deserialize(value, TagResolver.resolver(
                    "lang", (arguments, context) -> referenceTag(player, arguments, resolving, "")),
                    TagResolver.resolver("glyph", (arguments, context) -> referenceTag(player, arguments, resolving, "glyphs."))));
        } finally {
            resolving.remove(key);
        }
    }

    public String getDefaultLocale() { return defaultLocale; }

    /** Returns the locale selected for a sender, respecting the configured client-locale option. */
    public String getSelectedLocale(Player player) {
        if (usePlayerLocale && player != null) {
            String requested = player.getLocale();
            if (requested != null) {
                String key = normalize(requested.replace('_', '-').split("-")[0]);
                if (locales.containsKey(key)) return key;
            }
        }
        return defaultLocale;
    }

    private YamlConfiguration locale(Player player) {
        return locales.get(getSelectedLocale(player));
    }

    private static String normalize(String locale) {
        return locale == null || locale.isBlank() ? "en" : locale.toLowerCase(Locale.ROOT).replace('_', '-').split("-")[0];
    }

    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? name : name.substring(0, index);
    }
}
