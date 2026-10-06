package de.skyslycer.hmcwraps.util;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small helper around Configurate for the shop files: it copies missing defaults from the jar, loads the
 * node tree and saves changes atomically so a crash during an editor save cannot destroy the file.
 */
public final class YamlFileStore {

    private final Path path;
    private final String resourceName;
    private final Supplier<InputStream> resourceSupplier;
    private final YamlConfigurationLoader loader;

    public YamlFileStore(@NotNull Path path, @NotNull String resourceName, @NotNull Supplier<InputStream> resourceSupplier) {
        this.path = path;
        this.resourceName = resourceName;
        this.resourceSupplier = resourceSupplier;
        this.loader = YamlConfigurationLoader.builder()
                .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .path(path)
                .build();
    }

    /** The file this store manages. */
    public @NotNull Path path() {
        return path;
    }

    /** Creates the file from the bundled default when it does not exist yet. */
    public void createIfAbsent() throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream resource = resourceSupplier.get()) {
            if (resource == null) {
                Files.createFile(path);
                return;
            }
            Files.copy(resource, path);
        }
    }

    /** Loads the node tree, creating the file first when needed. */
    public @NotNull ConfigurationNode load() throws IOException {
        createIfAbsent();
        return loader.load();
    }

    /** Applies a mutation to the node tree and saves it atomically. */
    public void update(@NotNull Consumer<ConfigurationNode> mutation) throws IOException {
        ConfigurationNode root = load();
        mutation.accept(root);
        save(root);
    }

    /** Saves a node tree atomically: the new content is written next to the file and then moved over it. */
    public void save(@NotNull ConfigurationNode root) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        YamlConfigurationLoader temporaryLoader = YamlConfigurationLoader.builder()
                .defaultOptions(ConfigurationOptions.defaults().implicitInitialization(false))
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .path(temporary)
                .build();
        temporaryLoader.save(root);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The bundled resource name, used in log messages. */
    public @NotNull String resourceName() {
        return resourceName;
    }
}
