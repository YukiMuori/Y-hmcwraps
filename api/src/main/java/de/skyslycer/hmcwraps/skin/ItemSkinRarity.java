package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/**
 * A server-configured cosmetic rarity. Presentation is resolved by the language system;
 * no color or display text is hard-coded here.
 */
public record ItemSkinRarity(@NotNull String id, int priority, @NotNull String displayNameKey) {
    public ItemSkinRarity {
        id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey");
        if (id.isBlank()) throw new IllegalArgumentException("Rarity id must not be blank");
    }
}
