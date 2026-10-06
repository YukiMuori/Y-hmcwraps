package de.skyslycer.hmcwraps.lang;

import de.skyslycer.hmcwraps.messages.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LanguageFilesTest {

    @Test
    void builtInLocalesContainEveryLegacyMessage() throws IOException {
        for (String locale : new String[]{"en", "it"}) {
            String resourcePath = "lang/" + locale + ".yml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                assertNotNull(stream, "Missing language resource " + resourcePath);
                YamlConfiguration language = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));

                for (Messages message : Messages.values()) {
                    String key = "legacy." + message.getKey();
                    String value = language.getString(key);
                    assertNotNull(value, locale + " is missing " + key);
                    assertFalse(value.isBlank(), locale + " has an empty translation for " + key);
                }
            }
        }
    }

    @Test
    void builtInLocalesContainTheWholePluginCatalog() throws IOException {
        for (String locale : new String[]{"en", "it"}) {
            String resourcePath = "lang/" + locale + ".yml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                assertNotNull(stream, "Missing language resource " + resourcePath);
                YamlConfiguration language = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));

                for (String key : new String[]{
                        "gui.title",
                        "messages.applied",
                        "messages.purchase-success",
                        "debug.file-upload-failed",
                        "debug.test.passed",
                        "command-descriptions.wraps.reload",
                        "command-descriptions.wraps.validate",
                        "validation.summary-success",
                        "validation.summary-failed",
                        "validation.messages.invalid-yaml",
                        "validation.messages.unknown-rarity",
                        "updates.available"
                }) {
                    String value = language.getString(key);
                    assertNotNull(value, locale + " is missing " + key);
                    assertFalse(value.isBlank(), locale + " has an empty translation for " + key);
                }
            }
        }
    }

}
