package de.skyslycer.hmcwraps.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persistent, aggregated skin statistics for one player. Counts are computed from the existing
 * ownership and transaction tables instead of duplicating state.
 */
public record SkinProfile(@NotNull UUID playerId, int ownedSkins, int collectionsCompleted,
                          int purchasedSkins, int giftedSkins, int receivedSkins, int couponsRedeemed,
                          double collectionXp, @Nullable Long firstPurchaseAt) {
}
