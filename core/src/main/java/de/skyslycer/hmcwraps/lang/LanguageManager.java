package de.skyslycer.hmcwraps.lang;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import net.kyori.adventure.text.Component;
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
        return MINI_MESSAGE.deserialize(text, TagResolver.resolver(combined));
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

    private YamlConfiguration locale(Player player) {
        if (usePlayerLocale && player != null) {
            String requested = player.getLocale();
            if (requested != null) {
                String key = normalize(requested.replace('_', '-').split("-")[0]);
                YamlConfiguration match = locales.get(key);
                if (match != null) return match;
            }
        }
        return locales.get(defaultLocale);
    }

    private static String normalize(String locale) {
        return locale == null || locale.isBlank() ? "en" : locale.toLowerCase(Locale.ROOT).replace('_', '-').split("-")[0];
    }

    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? name : name.substring(0, index);
    }
}
