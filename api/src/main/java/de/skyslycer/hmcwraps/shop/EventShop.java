package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.skin.SkinPrice;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A timed shop such as a holiday event. After the end instant the entries become unbuyable while
 * the ownership granted during the event stays permanent.
 */
public final class EventShop {
    private final String id;
    private final String displayName;
    private final Instant start;
    private final Instant end;
    private final List<ShopEntry> entries;
    private final SkinPrice fallbackPrice;
    private final String permission;
    private final ItemStack icon;

    public EventShop(@NotNull String id, @NotNull String displayName, @NotNull Instant start, @NotNull Instant end,
                     @NotNull List<ShopEntry> entries, @Nullable SkinPrice fallbackPrice, @Nullable String permission,
                     @Nullable ItemStack icon) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("Event shop id must not be blank");
        }
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Event shop '" + this.id + "' ends before or when it starts");
        }
        this.entries = List.copyOf(entries);
        this.fallbackPrice = fallbackPrice;
        this.permission = permission == null || permission.isBlank() ? null : permission;
        this.icon = icon == null ? null : icon.clone();
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public @NotNull Instant start() {
        return start;
    }

    public @NotNull Instant end() {
        return end;
    }

    public @NotNull List<ShopEntry> entries() {
        return entries;
    }

    /** The currency used for entries that do not declare their own price. */
    public @Nullable SkinPrice fallbackPrice() {
        return fallbackPrice;
    }

    public @Nullable String permission() {
        return permission;
    }

    public @Nullable ItemStack icon() {
        return icon == null ? null : icon.clone();
    }

    /** Whether the event is running at the supplied instant. */
    public boolean activeAt(@NotNull Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }

    /** Whether the event already ended. */
    public boolean endedAt(@NotNull Instant instant) {
        return !instant.isBefore(end);
    }
}
