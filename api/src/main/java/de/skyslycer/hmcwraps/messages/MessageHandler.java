package de.skyslycer.hmcwraps.messages;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Single;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;

public interface MessageHandler {

    /**
     * Load messages from a path.
     *
     * @param path The path to load from
     * @return If it was successful
     */
    boolean load(Path path);

    /**
     * Get a message based on its key.
     *
     * @param key The message key
     * @return The message
     */
    String get(Messages key);

    /**
     * Get a message for a sender, allowing the sender's client locale to be used when configured.
     * Existing implementations remain compatible and may fall back to {@link #get(Messages)}.
     *
     * @param sender the receiver
     * @param key the message key
     * @return the localized message
     */
    default String get(CommandSender sender, Messages key) {
        return get(key);
    }

    /**
     * Try to update the given .properties file by adding missing messages, which are present in the internal .properties file
     *
     * @param path The file to update
     */
    void update(Path path);

    /**
     * Send a message to a sender with replacing placeholders.
     *
     * @param sender       The receiver
     * @param key          The message key
     * @param placeholders The placeholders to replace
     */
    void send(CommandSender sender, Messages key, Single... placeholders);

}
