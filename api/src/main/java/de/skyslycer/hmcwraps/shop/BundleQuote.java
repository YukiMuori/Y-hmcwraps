package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;

/**
 * The numbers displayed by the bundle screen.
 *
 * @param totalValue   the sum of the individual skin prices
 * @param bundlePrice  the configured bundle price (after the bundle discount)
 * @param missingValue the value of the skins the player does not own yet
 * @param dynamicPrice the price of only the missing skins, never higher than the bundle price
 * @param saving       how much the player saves compared to buying every skin separately
 * @param ownedCount   how many of the bundle's skins the player already owns
 * @param totalCount   how many skins the bundle contains
 */
public record BundleQuote(double totalValue, double bundlePrice, double missingValue, double dynamicPrice,
                          double saving, int ownedCount, int totalCount) {

    /** How many skins the player would still receive. */
    public int missingCount() {
        return Math.max(0, totalCount - ownedCount);
    }

    /** Whether the player already owns every skin of the bundle. */
    public boolean complete() {
        return totalCount > 0 && ownedCount >= totalCount;
    }

    /** The progress text used by the bundle screen, for example {@code 2/3}. */
    public @NotNull String progress() {
        return ownedCount + "/" + totalCount;
    }
}
