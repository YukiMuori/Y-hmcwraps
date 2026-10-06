package de.skyslycer.hmcwraps.repository;

import de.skyslycer.hmcwraps.shop.GiftRecord;
import de.skyslycer.hmcwraps.shop.GiftStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistent gift history and delivery notifications. */
public interface GiftRepository {

    /** Stores a new gift. */
    @NotNull CompletionStage<Boolean> insert(@NotNull GiftRecord record);

    /** Updates the delivery status of a gift. */
    @NotNull CompletionStage<Boolean> updateStatus(@NotNull String giftId, @NotNull GiftStatus status, boolean notified);

    /** Gifts a player was not notified about yet. */
    @NotNull CompletionStage<List<GiftRecord>> pendingNotifications(@NotNull UUID recipientId);

    /** Marks gift notifications as delivered. */
    @NotNull CompletionStage<Boolean> markNotified(@NotNull UUID recipientId, @NotNull List<String> giftIds);

    /** How many gifts a player sent. */
    @NotNull CompletionStage<Integer> countSent(@NotNull UUID senderId);

    /** How many gifts a player received. */
    @NotNull CompletionStage<Integer> countReceived(@NotNull UUID recipientId);

    /** The newest gifts a player received. */
    @NotNull CompletionStage<List<GiftRecord>> history(@NotNull UUID recipientId, int limit);
}
