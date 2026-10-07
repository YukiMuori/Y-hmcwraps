package de.skyslycer.hmcwraps.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationValidatorTest {
    @TempDir
    Path dataFolder;

    @BeforeEach
    void createValidBaseline() throws IOException {
        Files.createDirectories(dataFolder.resolve("wraps"));
        Files.createDirectories(dataFolder.resolve("collections"));
        Files.createDirectories(dataFolder.resolve("skins"));
        Files.createDirectories(dataFolder.resolve("lang"));
        write("config.yml", """
                language:
                  default: en
                legacy-wraps:
                  enabled: true
                items: {}
                collections: {}
                """);
        write("rarities.yml", """
                enabled: true
                rarities:
                  common:
                    priority: 1
                    display-name-key: rarities.common
                """);
        write("categories.yml", """
                enabled: true
                categories:
                  swords:
                    priority: 1
                    display-name-key: categories.swords
                  tools:
                    priority: 2
                    display-name-key: categories.tools
                  armor:
                    priority: 3
                    display-name-key: categories.armor
                """);
        write("skin-collections.yml", """
                enabled: true
                collections: {}
                """);
        write("itemskin-gui.yml", """
                gui:
                  size: 27
                  item-slot: 4
                  content-slots: [10, 11]
                  previous:
                    slot: 18
                  next:
                    slot: 26
                  close:
                    slot: 22
                sorting:
                  options: [rarity, name, price]
                filters:
                  options: [all, owned, unowned, purchasable, free]
                """);
        write("skins/sample.yml", """
                enabled: true
                id: sample
                display-name: '<red>Sample skin'
                rarity: common
                categories: [swords]
                compatibility:
                  materials: [DIAMOND_SWORD]
                  items: []
                """);
        copyResource("lang/en.yml", "lang/en.yml");
        copyResource("lang/it.yml", "lang/it.yml");
    }

    @Test
    void acceptsAValidReadOnlyConfiguration() {
        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertTrue(report.isValid(), () -> report.issues().toString());
        assertTrue(report.filesChecked() >= 7);
    }

    @Test
    void acceptsCompactItemModelSkinFiles() throws IOException {
        write("skins/sample.yml", """
                enabled: true
                id: amethyst_greatblade
                display-name: '<gradient:#8FEAF9:#CDA4F9>Amethyst Greatblade</gradient>'
                material: DIAMOND_SWORD
                item-model: nexo:amethyst_greatblade
                lore: ['<gray>Example skin']
                rarity: common
                categories: [swords]
                compatible-materials: [DIAMOND_SWORD, NETHERITE_SWORD]
                """);

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertTrue(report.isValid(), () -> report.issues().toString());
    }

    @Test
    void acceptsThemedCollectionsAndSkinReferences() throws IOException {
        write("skin-collections.yml", """
                enabled: true
                collections:
                  angelico:
                    priority: 10
                    display-name-key: collections.angelico
                    categories: [swords, tools, armor]
                """);
        write("skins/sample.yml", """
                enabled: true
                id: sample
                display-name: '<red>Sample skin'
                rarity: common
                collection: angelico
                categories: [swords]
                compatibility:
                  materials: [DIAMOND_SWORD]
                  items: []
                """);

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertTrue(report.isValid(), () -> report.issues().toString());
    }

    @Test
    void detectsDuplicateSkinIdsCaseInsensitively() throws IOException {
        write("skins/duplicate.yml", """
                enabled: true
                id: SAMPLE
                display-name: '<red>Another sample'
                rarity: common
                categories: [swords]
                compatibility:
                  materials: [DIAMOND_SWORD]
                  items: []
                cosmetic:
                  uuid: another-cosmetic-id
                """);

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertTrue(report.issues().stream().anyMatch(issue -> issue.key().equals("duplicate-skin-id")));
        assertTrue(report.errorCount() > 0);
    }

    @Test
    void reportsInvalidMaterialsAndMissingCompatibility() throws IOException {
        write("skins/sample.yml", """
                enabled: true
                id: sample
                display-name: '<red>Sample skin'
                rarity: common
                categories: [swords]
                compatibility:
                  materials: [NOT_A_REAL_MATERIAL]
                  items: []
                """);

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();
        Set<String> issueKeys = report.issues().stream().map(ConfigurationValidator.Issue::key).collect(Collectors.toSet());

        assertTrue(issueKeys.contains("unknown-skin-material"));
        assertTrue(issueKeys.contains("missing-compatibility"));
    }

    @Test
    void reportsGuiSlotsOutsideTheConfiguredInventory() throws IOException {
        write("itemskin-gui.yml", """
                gui:
                  size: 27
                  item-slot: 4
                  content-slots: [10, 31]
                """);

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.key().equals("invalid-gui-slot")));
    }

    @Test
    void reportsMalformedYamlWithItsFilePath() throws IOException {
        write("wraps/broken.yml", "items: [this is not valid YAML\n");

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertTrue(report.issues().stream().anyMatch(issue -> issue.key().equals("invalid-yaml")
                && issue.path().equals("wraps/broken.yml")));
    }

    @Test
    void skipsLegacyFilesWhenClassicWrapsAreDisabled() throws IOException {
        write("config.yml", """
                language:
                  default: en
                legacy-wraps:
                  enabled: false
                """);
        write("wraps/broken.yml", "items: [this is not valid YAML\n");

        ConfigurationValidator.Report report = new ConfigurationValidator(dataFolder).validate();

        assertFalse(report.issues().stream().anyMatch(issue -> issue.path().equals("wraps/broken.yml")));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = dataFolder.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void copyResource(String resourcePath, String relativePath) throws IOException {
        Path destination = dataFolder.resolve(relativePath);
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IOException("Missing test resource " + resourcePath);
            Files.copy(stream, destination);
        }
    }
}
