package de.skyslycer.hmcwraps.collection;

import de.skyslycer.hmcwraps.skin.ItemSkinCollection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Skin collection progress and milestone reward claiming. */
public interface CollectionService {

    /** Whether collections are enabled in configuration. */
    boolean isEnabled();

    /** All configured collections. */
    @NotNull Collection<ItemSkinCollection> getCollections();

    /** A player's progress inside one collection. */
    @NotNull CompletionStage<CollectionProgress> getProgress(@NotNull UUID playerId, @NotNull String collectionId);

    /** A player's progress inside every collection. */
    @NotNull CompletionStage<Map<String, CollectionProgress>> getProgress(@NotNull UUID playerId);

    /** How many collections the player fully completed. */
    @NotNull CompletionStage<Integer> getCompletedCollections(@NotNull UUID playerId);

    /**
     * Claims a milestone's rewards exactly once. The claim is recorded in storage before the rewards
     * are granted, so a crash or a double click can never duplicate a reward.
     *
     * @param player       the claiming player (rewards are granted to an online player)
     * @param collectionId the collection id
     * @param milestoneId  the milestone id
     * @return a human readable outcome key used for localization
     */
    @NotNull CompletionStage<ClaimResult> claim(@NotNull Player player, @NotNull String collectionId, @NotNull String milestoneId);

    /** Claims every currently claimable milestone of a collection. */
    @NotNull CompletionStage<List<ClaimResult>> claimAll(@NotNull Player player, @NotNull String collectionId);

    /** Sends a completion notification when a purchase completed a collection. */
    void announceCompletion(@NotNull Player player, @NotNull String collectionId);

    /** Reward claim outcomes. */
    enum ClaimResult {
        /** The rewards were granted. */
        CLAIMED,
        /** The milestone was already claimed. */
        ALREADY_CLAIMED,
        /** The player does not own enough skins yet. */
        NOT_REACHED,
        /** The collection or milestone is unknown or disabled. */
        UNKNOWN,
        /** Another plugin cancelled the claim event; the milestone stays claimable. */
        CANCELLED,
        /** Storage was unavailable; nothing was granted. */
        STORAGE_ERROR
    }
}
