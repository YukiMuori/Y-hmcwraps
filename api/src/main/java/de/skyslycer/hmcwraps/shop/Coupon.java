package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable promotional code definition. Usage counters live in storage, never in this record.
 *
 * @param code             the uppercase code players type
 * @param type             percentage or fixed discount
 * @param value            percentage (0-100) or fixed amount
 * @param maxUses          global redemptions allowed; {@code -1} means unlimited
 * @param maxUsesPerPlayer per-player redemptions allowed; {@code -1} means unlimited
 * @param expiresAt        optional expiry instant
 * @param minSpend         minimum order value required
 * @param skins            applicable skin ids; empty means all skins
 * @param bundles          applicable bundle ids; empty means all bundles
 * @param categories       applicable skin categories; empty means all categories
 * @param channels         applicable shop channels ({@code daily}, {@code featured}, {@code event:&lt;id&gt;}, {@code bundle}, {@code direct}); empty means all
 * @param active           whether the coupon can currently be redeemed
 */
public record Coupon(@NotNull String code, @NotNull CouponType type, double value, int maxUses, int maxUsesPerPlayer,
                     @Nullable Instant expiresAt, double minSpend, @NotNull Set<String> skins,
                     @NotNull Set<String> bundles, @NotNull Set<String> categories, @NotNull Set<String> channels,
                     boolean active) {

    public Coupon {
        Objects.requireNonNull(code, "code");
        code = code.toUpperCase(Locale.ROOT).trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank");
        }
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("Coupon value must be a positive finite number");
        }
        if (type == CouponType.PERCENTAGE && value > 100) {
            throw new IllegalArgumentException("Coupon percentage must not exceed 100");
        }
        Objects.requireNonNull(skins, "skins");
        Objects.requireNonNull(bundles, "bundles");
        Objects.requireNonNull(categories, "categories");
        Objects.requireNonNull(channels, "channels");
        skins = normalize(skins);
        bundles = normalize(bundles);
        categories = normalize(categories);
        channels = normalize(channels);
        if (!Double.isFinite(minSpend) || minSpend < 0) {
            throw new IllegalArgumentException("Coupon minimum spend must be a non-negative finite number");
        }
    }

    private static Set<String> normalize(Set<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Whether the coupon expired at the provided instant. */
    public boolean expiredAt(@NotNull Instant instant) {
        return expiresAt != null && !instant.isBefore(expiresAt);
    }

    /**
     * Calculates the discounted price for an order.
     *
     * @param amount the original price
     * @return the payable amount, never below zero and never above the original amount
     */
    public double apply(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0;
        }
        double discounted = switch (type) {
            case PERCENTAGE -> amount * (1 - Math.min(100, value) / 100D);
            case FIXED -> amount - value;
        };
        double rounded = Math.max(0, Math.min(amount, discounted));
        // Economy providers work with doubles; round to two decimals to avoid floating point noise in receipts.
        return Math.round(rounded * 100D) / 100D;
    }

    /** Whether this coupon applies to at least one of the supplied targets. */
    public boolean appliesToTarget(@NotNull Set<String> targetSkinIds, @NotNull Set<String> targetBundleIds,
                                   @NotNull Set<String> targetCategoryIds, @NotNull String channelId) {
        if (!skins.isEmpty() && targetSkinIds.stream().noneMatch(skins::contains)) {
            return false;
        }
        if (!bundles.isEmpty() && targetBundleIds.stream().noneMatch(bundles::contains)) {
            return false;
        }
        if (!categories.isEmpty() && targetCategoryIds.stream().noneMatch(categories::contains)) {
            return false;
        }
        if (!channels.isEmpty() && !channels.contains(channelId.toLowerCase(Locale.ROOT)) && !channels.contains("all")) {
            return false;
        }
        return true;
    }
}
