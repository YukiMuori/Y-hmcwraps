package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.skin.SkinPrice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * A configured shop listing. The entry only points at catalog objects ({@code referenceId}) and
 * optionally overrides their price; ownership, pricing and grants are always resolved server-side.
 *
 * @param type           whether the reference points at a skin or a bundle
 * @param referenceId    the normalized skin/bundle id
 * @param price          an optional price override for this listing
 * @param discount       an optional percentage discount (0-100) applied before coupons
 * @param permission     an optional permission required to see/buy the entry
 * @param availableFrom  optional start of the availability window
 * @param availableUntil optional end of the availability window
 */
public record ShopEntry(@NotNull ShopEntryType type, @NotNull String referenceId, @Nullable SkinPrice price,
                        double discount, @Nullable String permission, @Nullable Instant availableFrom,
                        @Nullable Instant availableUntil) {

    public ShopEntry {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(referenceId, "referenceId");
        referenceId = referenceId.toLowerCase(Locale.ROOT).trim();
        if (referenceId.isBlank()) {
            throw new IllegalArgumentException("Shop entry reference id must not be blank");
        }
        if (!Double.isFinite(discount) || discount < 0 || discount > 100) {
            throw new IllegalArgumentException("Shop entry discount must be between 0 and 100");
        }
        if (availableFrom != null && availableUntil != null && availableUntil.isBefore(availableFrom)) {
            throw new IllegalArgumentException("Shop entry availability window ends before it starts");
        }
    }

    /** Creates a skin listing without a price override. */
    public static @NotNull ShopEntry skin(@NotNull String skinId) {
        return new ShopEntry(ShopEntryType.SKIN, skinId, null, 0, null, null, null);
    }

    /** Creates a skin listing with a price override. */
    public static @NotNull ShopEntry skin(@NotNull String skinId, @Nullable SkinPrice price, double discount) {
        return new ShopEntry(ShopEntryType.SKIN, skinId, price, discount, null, null, null);
    }

    /** Creates a bundle listing without a price override. */
    public static @NotNull ShopEntry bundle(@NotNull String bundleId) {
        return new ShopEntry(ShopEntryType.BUNDLE, bundleId, null, 0, null, null, null);
    }

    /** Whether the entry's availability window covers the provided instant. */
    public boolean availableAt(@NotNull Instant instant) {
        if (availableFrom != null && instant.isBefore(availableFrom)) {
            return false;
        }
        return availableUntil == null || !instant.isAfter(availableUntil);
    }
}
