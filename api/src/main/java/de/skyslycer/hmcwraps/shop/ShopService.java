package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.collection.CollectionProgress;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.ItemSkinCollection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * The integrated skin shop. All mutating operations are server-authoritative, asynchronous and
 * transaction safe; GUIs only display quotes and forward the player's intent.
 */
public interface ShopService {

    /** Whether the shop is enabled in configuration. */
    boolean isEnabled();

    /** The skins currently offered by the daily rotation, in display order. */
    @NotNull List<ItemSkin> getDailySkins();

    /** The featured entries (skins and bundles), in display order. */
    @NotNull List<ShopEntry> getFeaturedEntries();

    /** All configured bundles. */
    @NotNull Collection<Bundle> getBundles();

    /** The bundles currently featured. */
    @NotNull List<Bundle> getFeaturedBundles();

    /** All configured event shops, including running and future ones. */
    @NotNull Collection<EventShop> getEventShops();

    /** The event shops running right now. */
    @NotNull List<EventShop> getActiveEventShops();

    /** Looks a bundle up by id. */
    @NotNull Optional<Bundle> getBundle(@NotNull String bundleId);

    /** The instant at which the daily shop rotates next. */
    @NotNull Instant getDailyRefreshInstant();

    /** The time remaining until the next daily rotation. */
    default @NotNull Duration getDailyTimeRemaining() {
        Duration remaining = Duration.between(Instant.now(), getDailyRefreshInstant());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /** The time remaining until an event shop ends. */
    @NotNull Duration getEventTimeRemaining(@NotNull String eventId);

    /**
     * Recomputes the daily rotation. Called by the scheduler when the rotation period elapsed and by
     * administrators; implementations must persist the result so a restart cannot reroll the shop.
     *
     * @param force whether the current rotation should be replaced even when it is still valid
     * @return whether a new rotation is active afterwards
     */
    @NotNull CompletionStage<Boolean> refreshDailyShop(boolean force);

    /** Quotes a skin purchase without charging anything. */
    @NotNull CompletionStage<PurchaseQuote> quoteSkin(@NotNull Player player, @NotNull String skinId, @Nullable String couponCode);

    /** Quotes a bundle purchase without charging anything (the full bundle option). */
    @NotNull CompletionStage<PurchaseQuote> quoteBundle(@NotNull Player player, @NotNull String bundleId, @Nullable String couponCode);

    /**
     * The bundle screen numbers: total value, bundle price, missing value and saving. Ownership is read
     * from storage, so the numbers are identical on every server and after a restart.
     */
    @NotNull CompletionStage<BundleQuote> quoteBundleOverview(@NotNull Player player, @NotNull String bundleId);

    /** Quotes only the missing skins of a bundle (dynamic pricing). */
    @NotNull CompletionStage<PurchaseQuote> quoteMissingBundleSkins(@NotNull Player player, @NotNull String bundleId,
                                                                   @Nullable String couponCode);

    /** Quotes an arbitrary shop entry. */
    @NotNull CompletionStage<PurchaseQuote> quoteEntry(@NotNull Player player, @NotNull ShopEntry entry, @Nullable String couponCode);

    /**
     * Buys a skin. The price, discounts, coupons and ownership are revalidated server-side.
     *
     * @param player     the buyer
     * @param skinId     the skin id
     * @param couponCode an optional coupon code
     * @param channel    the shop channel the purchase originates from, used for coupon targeting
     */
    @NotNull CompletionStage<TransactionResult> purchaseSkin(@NotNull Player player, @NotNull String skinId,
                                                             @Nullable String couponCode, @NotNull ShopChannel channel);

    /**
     * Buys a bundle.
     *
     * @param player       the buyer
     * @param bundleId     the bundle id
     * @param couponCode   an optional coupon code
     * @param channel      the shop channel the purchase originates from
     * @param missingOnly  whether only the missing skins should be granted and charged
     */
    @NotNull CompletionStage<TransactionResult> purchaseBundle(@NotNull Player player, @NotNull String bundleId,
                                                               @Nullable String couponCode, @NotNull ShopChannel channel,
                                                               boolean missingOnly);

    /** Collection progress for a player, used by the shop's collection screens and placeholders. */
    @NotNull CompletionStage<CollectionProgress> getCollectionProgress(@NotNull Player player, @NotNull String collectionId);

    /** All configured skin collections the shop can display. */
    @NotNull Collection<ItemSkinCollection> getCollections();

    /**
     * Reloads the shop definition files ({@code shops.yml} and {@code coupons.yml}).
     *
     * @return whether the files were valid
     */
    @NotNull CompletionStage<Boolean> reload();
}
