package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Additional contract implemented by storage providers that can grant several ownerships as one
 * operation and undo them afterwards. The shop refuses paid purchases when the active provider does not
 * implement this contract, so money can never be withdrawn without a way to grant or refund.
 */
public interface TransactionalStorage extends StorageProvider {

    /**
     * Grants ownership of every supplied skin as a single atomic operation.
     *
     * @param playerId the receiving player
     * @param skinIds  the skins to grant
     * @param source   the audit source ({@code purchase}, {@code gift}, {@code reward}, ...)
     * @return the skins that were newly granted; an empty set means the player already owned all of them.
     *         The stage completes exceptionally when nothing could be granted.
     */
    @NotNull CompletionStage<Set<String>> grantAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds, @NotNull String source);

    /**
     * Removes ownership of the supplied skins. Used to compensate a failed transaction, therefore it is
     * best-effort and never fails the caller's operation.
     *
     * @return whether every ownership row could be removed
     */
    @NotNull CompletionStage<Boolean> revokeAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds);

    /** Whether grants and revocations are implemented as fail-closed operations. */
    default boolean supportsTransactions() {
        return true;
    }
}
