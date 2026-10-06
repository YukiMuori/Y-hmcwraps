package de.skyslycer.hmcwraps.shop;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Gifting: a sender pays for a skin or bundle and another player (online or offline) receives the
 * ownership. Gifts are transactional — the recipient is only granted after the payment succeeded and
 * the sender is refunded when the grants cannot be stored.
 */
public interface GiftService {

    /** Whether gifting is enabled in configuration. */
    boolean isEnabled();

    /** Whether offline recipients are allowed. */
    boolean allowsOffline();

    /** The remaining cooldown for a sender in milliseconds, or zero when the sender may gift. */
    long getCooldownRemaining(@NotNull UUID senderId);

    /**
     * Gifts a skin to another player.
     *
     * @param sender        the paying player
     * @param recipientId   the recipient uuid
     * @param recipientName the recipient name (used for offline delivery and notifications)
     * @param skinId        the skin to gift
     * @param message       an optional message shown to the recipient
     */
    @NotNull CompletionStage<GiftResult> giftSkin(@NotNull Player sender, @NotNull UUID recipientId,
                                                  @NotNull String recipientName, @NotNull String skinId,
                                                  @Nullable String message);

    /** Gifts a bundle to another player. */
    @NotNull CompletionStage<GiftResult> giftBundle(@NotNull Player sender, @NotNull UUID recipientId,
                                                    @NotNull String recipientName, @NotNull String bundleId,
                                                    @Nullable String message);

    /** Gift records the recipient was not notified about yet. */
    @NotNull CompletionStage<List<GiftRecord>> pendingNotifications(@NotNull UUID recipientId);

    /** Marks every open notification of a recipient as delivered. */
    @NotNull CompletionStage<Boolean> markNotified(@NotNull UUID recipientId, @NotNull List<String> giftIds);

    /** Sends a pending gift notification to a joining player on the main thread. */
    void notifyPending(@NotNull Player player);
}
