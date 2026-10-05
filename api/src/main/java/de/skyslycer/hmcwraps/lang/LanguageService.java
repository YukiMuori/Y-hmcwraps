package de.skyslycer.hmcwraps.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Language lookup and MiniMessage parsing service, including the {@code <lang:...>} and {@code <glyph:...>} tags. */
public interface LanguageService {
    @NotNull String get(@Nullable Player player, @NotNull String key);
    @NotNull Component parse(@Nullable Player player, @NotNull String text, TagResolver... extraResolvers);
}
