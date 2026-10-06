package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.skin.SkinPrice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;


/**
 * Pure pricing rules for skins, bundles and discounts.
 *
 * <p>All values are calculated on the server from configuration and from the buyer's real ownership, so a
 * manipulated menu cannot influence what is charged. Every method here is side effect free and can be
 * unit tested without a running server.</p>
 */
public final class PricingService {

    private PricingService() {
    }

    /** Applies a percentage discount to an amount. */
    public static double applyPercentage(double amount, double percentage) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0;
        }
        if (!Double.isFinite(percentage) || percentage <= 0) {
            return round(amount);
        }
        return round(Math.max(0, amount * (1 - Math.min(100, percentage) / 100D)));
    }

    /** Rounds to two decimals, matching what economy providers store. */
    public static double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    /**
     * Builds the price function used by the transaction engine for a single skin: the amount does not
     * depend on how many skins are missing.
     */
    public static @NotNull Function<Set<String>, Double> skinPriceFunction(double price, double listingDiscount) {
        double finalPrice = applyPercentage(price, listingDiscount);
        return missing -> finalPrice;
    }

    /**
     * Builds the price function for a bundle.
     *
     * @param bundlePrice    the configured bundle price (already discounted by the bundle's own discount)
     * @param missingOnly    whether the buyer chose the "only the missing skins" option
     * @param individualPrices the individual skin prices, aligned with the bundle's skin ids ({@code null} entries count as zero)
     * @param bundleSkinIds  the bundle's skin ids in the same order as {@code individualPrices}
     */
    public static @NotNull Function<Set<String>, Double> bundlePriceFunction(double bundlePrice, boolean missingOnly,
                                                                            @NotNull List<Double> individualPrices,
                                                                            @NotNull List<String> bundleSkinIds) {
        List<Double> prices = new ArrayList<>(individualPrices);
        List<String> ids = new ArrayList<>(bundleSkinIds);
        return missing -> {
            if (!missingOnly) {
                return bundlePrice;
            }
            double missingValue = 0;
            for (int index = 0; index < ids.size() && index < prices.size(); index++) {
                if (missing.contains(ids.get(index))) {
                    Double price = prices.get(index);
                    if (price != null && Double.isFinite(price) && price > 0) {
                        missingValue += price;
                    }
                }
            }
            if (missingValue <= 0) {
                return bundlePrice;
            }
            // The dynamic price never exceeds the bundle price: buying the remaining skins is never
            // more expensive than buying the complete bundle.
            return round(Math.min(bundlePrice, missingValue));
        };
    }

    /**
     * Calculates the numbers shown in the bundle screen.
     *
     * @param bundlePrice      the configured bundle price
     * @param bundleDiscount   the bundle's percentage discount
     * @param skinIds          the bundle's skin ids
     * @param individualPrices the individual skin prices, aligned with {@code skinIds}
     * @param owned            the skins the player already owns
     */
    public static @NotNull BundleQuote quoteBundle(double bundlePrice, double bundleDiscount, @NotNull List<String> skinIds,
                                                   @NotNull List<Double> individualPrices, @NotNull Collection<String> owned) {
        double totalValue = 0;
        double missingValue = 0;
        int ownedCount = 0;
        for (int index = 0; index < skinIds.size(); index++) {
            String skinId = skinIds.get(index);
            Double price = index < individualPrices.size() ? individualPrices.get(index) : null;
            double value = price == null || !Double.isFinite(price) ? 0 : price;
            totalValue += value;
            if (owned.contains(skinId)) {
                ownedCount++;
            } else {
                missingValue += value;
            }
        }
        double discountedBundle = applyPercentage(bundlePrice, bundleDiscount);
        double dynamicPrice = missingValue <= 0 ? discountedBundle : round(Math.min(discountedBundle, missingValue));
        double saving = round(Math.max(0, totalValue - discountedBundle));
        return new BundleQuote(round(totalValue), discountedBundle, round(missingValue), dynamicPrice, saving,
                ownedCount, skinIds.size());
    }

    /** Formats an amount without trailing zeros, for menus and messages. */
    public static @NotNull String format(double amount) {
        return new java.math.BigDecimal(round(amount)).stripTrailingZeros().toPlainString();
    }

    /** A skin's price or {@code null} when the skin is free. */
    public static @Nullable Double priceOf(SkinPrice price) {
        return price == null ? null : price.amount();
    }
}
