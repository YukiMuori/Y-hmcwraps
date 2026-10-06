package de.skyslycer.hmcwraps.shop;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Coupon lookup, evaluation and persistent redemption bookkeeping. */
public interface CouponService {

    /** Whether coupons are enabled in configuration. */
    boolean isEnabled();

    /** Looks a coupon up by its code; codes are case-insensitive. */
    @NotNull Optional<Coupon> getCoupon(@NotNull String code);

    /** All configured coupons. */
    @NotNull Collection<Coupon> getCoupons();

    /**
     * Evaluates a coupon for a specific order. Reads the persistent usage counters, so a coupon is
     * never accepted twice by two concurrent purchases.
     *
     * @param playerId    the buyer
     * @param code        the coupon code
     * @param channel     the {@link ShopChannel#id()} the order originates from
     * @param sectionId   the concrete section id ({@code event:halloween_2026}) or empty
     * @param amount      the order value before the discount
     * @param skinIds     the skins contained in the order
     * @param bundleIds   the bundles contained in the order
     * @param categoryIds the categories contained in the order
     */
    @NotNull CompletionStage<CouponResult> evaluate(@NotNull UUID playerId, @NotNull String code, @NotNull String channel,
                                                    @NotNull String sectionId, double amount, @NotNull Collection<String> skinIds,
                                                    @NotNull Collection<String> bundleIds, @NotNull Collection<String> categoryIds);

    /** Stores the coupon a player selected in the shop so it survives a relog. */
    @NotNull CompletionStage<Boolean> setSelectedCoupon(@NotNull UUID playerId, @Nullable String code);

    /** The coupon code the player currently selected, if any. */
    @NotNull CompletionStage<Optional<String>> getSelectedCoupon(@NotNull UUID playerId);

    /** The cached selected coupon code of an online player, for GUIs and placeholders. */
    @Nullable String cachedSelectedCoupon(@NotNull UUID playerId);

    /** Records a redemption. Called by the transaction service once a discounted purchase succeeded. */
    @NotNull CompletionStage<Boolean> recordRedemption(@NotNull String code, @NotNull UUID playerId,
                                                       @NotNull String transactionId, double amount, double discount);

    /** The total number of redemptions of a coupon. */
    @NotNull CompletionStage<Integer> getUses(@NotNull String code);

    /** How often a player already used a coupon. */
    @NotNull CompletionStage<Integer> getUses(@NotNull String code, @NotNull UUID playerId);

    /** Reloads {@code coupons.yml}. */
    @NotNull CompletionStage<Boolean> reload();

    /** Sends the coupon feedback message to a player (used by commands and menus). */
    void sendFeedback(@NotNull Player player, @NotNull CouponResult result);
}
