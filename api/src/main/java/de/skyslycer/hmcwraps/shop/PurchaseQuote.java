package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.skin.SkinPrice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A server-side calculated purchase quote. Quotes are never trusted from the GUI: the shop
 * recalculates them from configuration and storage immediately before charging the player.
 *
 * @param targetId        the skin or bundle id being quoted
 * @param type            the quoted object type
 * @param price           the base price (after the listing/bundle discount, before coupons)
 * @param originalAmount  the amount before any discount
 * @param discount        the total discount in currency units
 * @param finalAmount     the amount the player would pay
 * @param couponCode      the coupon that was applied, if any
 * @param ownedSkinIds    the skins of the quote the player already owns
 * @param missingSkinIds  the skins of the quote the player would receive
 * @param fullyOwned      whether the player already owns everything in the quote
 */
public record PurchaseQuote(@NotNull String targetId, @NotNull ShopEntryType type, @NotNull SkinPrice price,
                            double originalAmount, double discount, double finalAmount, @Nullable String couponCode,
                            @NotNull List<String> ownedSkinIds, @NotNull List<String> missingSkinIds,
                            boolean fullyOwned) {

    public PurchaseQuote {
        ownedSkinIds = List.copyOf(ownedSkinIds);
        missingSkinIds = List.copyOf(missingSkinIds);
    }

    /** How many ownerships this purchase would grant. */
    public int grantCount() {
        return missingSkinIds.size();
    }

    /** Whether anything would actually be charged. */
    public boolean chargeable() {
        return !fullyOwned && finalAmount > 0;
    }

    /** Creates a copy of this quote with a different payable amount (used after coupon evaluation). */
    public @NotNull PurchaseQuote withAmounts(double originalAmount, double discount, double finalAmount, @Nullable String couponCode) {
        return new PurchaseQuote(targetId, type, price, originalAmount, discount, finalAmount, couponCode, ownedSkinIds, missingSkinIds, fullyOwned);
    }
}
