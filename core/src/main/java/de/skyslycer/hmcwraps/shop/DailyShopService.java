package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.repository.ShopRotationState;
import de.skyslycer.hmcwraps.repository.ShopStateRepository;
import de.skyslycer.hmcwraps.shop.config.DailyShopConfiguration;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * The daily shop rotation.
 *
 * <p>The rotation is <em>deterministic and persisted</em>: it is derived from the cycle key (the day or
 * week id) and the configured salt, and the result is stored in the database. A restart, reload or crash
 * can therefore never generate a different shop - the stored rotation stays active until its refresh
 * instant. Only when the cycle key changes is a new rotation computed and stored.</p>
 */
public final class DailyShopService {

    private final ShopStateRepository repository;
    private final Clock clock;
    private final Consumer<String> logger;
    private volatile Rotation current;

    public DailyShopService(@NotNull ShopStateRepository repository, @NotNull Clock clock, @NotNull Consumer<String> logger) {
        this.repository = repository;
        this.clock = clock;
        this.logger = logger;
    }

    /**
     * Makes sure a valid rotation is active, computing and persisting a new one when the cycle changed.
     *
     * @param configuration the daily shop configuration
     * @param pool          the candidate skin ids (already filtered by the registry)
     * @param force         whether a new rotation must be generated even when the current one is still valid
     * @return the active rotation; {@code null} when the daily shop is disabled
     */
    public @NotNull CompletionStage<Rotation> ensureCurrent(@NotNull DailyShopConfiguration configuration,
                                                            @NotNull List<String> pool, boolean force) {
        if (!configuration.isEnabled()) {
            current = null;
            return AsyncUtil.safe(() -> repository.clear("daily")).thenApply(ignored -> null);
        }
        ShopRotation.Interval interval = ShopRotation.Interval.fromId(configuration.getRefresh());
        ZoneId zone = ShopRotation.parseZone(configuration.getZone());
        LocalTime resetTime = ShopRotation.parseResetTime(configuration.getResetTime());
        Instant now = Instant.now(clock);
        String cycleKey = ShopRotation.cycleKey(now, zone, interval, resetTime);
        Instant expiresAt = ShopRotation.nextRefresh(now, zone, interval, resetTime);

        return AsyncUtil.safe(() -> repository.find("daily")).thenCompose(stored -> {
            Rotation existing = current;
            if (!force && existing != null && existing.cycleKey().equals(cycleKey)) {
                return AsyncUtil.completed(existing);
            }
            Rotation usable = stored.map(this::fromState)
                    .filter(state -> state.cycleKey().equals(cycleKey)
                            || (interval == ShopRotation.Interval.NEVER && !force))
                    .orElse(null);
            if (usable != null) {
                current = usable;
                return AsyncUtil.completed(usable);
            }
            return rotate(configuration, pool, cycleKey, expiresAt, now);
        }).exceptionally(error -> {
            logger.accept("Could not read the stored daily shop rotation (" + AsyncUtil.describe(error)
                    + "); using an in-memory rotation until the database is available.");
            return current != null ? current : fallback(configuration, pool, cycleKey, expiresAt, now);
        });
    }

    private CompletionStage<Rotation> rotate(DailyShopConfiguration configuration, List<String> pool, String cycleKey,
                                             Instant expiresAt, Instant now) {
        List<String> entries = ShopRotation.rotate(pool, configuration.getRotationSalt(), cycleKey, configuration.getSlots());
        Rotation rotation = new Rotation(cycleKey, entries, now.toEpochMilli(), expiresAt.toEpochMilli());
        current = rotation;
        return AsyncUtil.safe(() -> repository.save(new ShopRotationState("daily", cycleKey, entries,
                        rotation.createdAt(), rotation.expiresAt())))
                .thenApply(saved -> rotation)
                .exceptionally(error -> {
                    logger.accept("Could not persist the daily shop rotation (" + AsyncUtil.describe(error)
                            + "); it stays active for this session but will be recalculated after a restart.");
                    return rotation;
                });
    }

    private Rotation fallback(DailyShopConfiguration configuration, List<String> pool, String cycleKey,
                              Instant expiresAt, Instant now) {
        return new Rotation(cycleKey, ShopRotation.rotate(pool, configuration.getRotationSalt(), cycleKey, configuration.getSlots()),
                now.toEpochMilli(), expiresAt.toEpochMilli());
    }

    private Rotation fromState(ShopRotationState state) {
        return new Rotation(state.cycleKey(), state.entries(), state.createdAt(), state.expiresAt());
    }

    /** The rotation that is currently active, if the shop has been loaded already. */
    public @Nullable Rotation current() {
        return current;
    }

    /** The next refresh instant, or {@code null} when the shop is not loaded yet. */
    public @Nullable Instant nextRefresh() {
        Rotation rotation = current;
        return rotation == null ? null : Instant.ofEpochMilli(rotation.expiresAt());
    }

    /** Whether the stored rotation expired; used by the periodic check task. */
    public boolean expired() {
        Rotation rotation = current;
        return rotation != null && Instant.now(clock).toEpochMilli() >= rotation.expiresAt();
    }

    /** Drops the in-memory rotation, for example when the feature is disabled. */
    public void clear() {
        current = null;
    }

    /**
     * An active rotation.
     *
     * @param cycleKey  the deterministic cycle id
     * @param entries   the selected entry ids, in display order
     * @param createdAt when the rotation was created
     * @param expiresAt when the next rotation starts
     */
    public record Rotation(@NotNull String cycleKey, @NotNull List<String> entries, long createdAt, long expiresAt) {

        public Rotation {
            entries = List.copyOf(entries);
        }

        public boolean validAt(long timestamp) {
            return timestamp < expiresAt;
        }
    }
}
