package de.skyslycer.hmcwraps.repository;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A persisted shop rotation.
 *
 * @param shopId    the shop section id ({@code daily}, ...)
 * @param cycleKey  the deterministic cycle id (for example {@code 2026-10-06})
 * @param entries   the entry ids of the rotation, in display order
 * @param createdAt the instant the rotation was created
 * @param expiresAt the instant the next rotation starts
 */
public record ShopRotationState(@NotNull String shopId, @NotNull String cycleKey, @NotNull List<String> entries,
                                long createdAt, long expiresAt) {

    public ShopRotationState {
        entries = List.copyOf(entries);
    }

    /** Whether the rotation is still valid at the supplied epoch millis. */
    public boolean validAt(long timestamp) {
        return timestamp < expiresAt;
    }
}
