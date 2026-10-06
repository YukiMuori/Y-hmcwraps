package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.collection.CollectionService;
import de.skyslycer.hmcwraps.repository.CouponRepository;
import de.skyslycer.hmcwraps.repository.GiftRepository;
import de.skyslycer.hmcwraps.repository.PlayerRepository;
import de.skyslycer.hmcwraps.repository.PurchaseRepository;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Aggregated skin statistics.
 *
 * <p>Nothing is duplicated: the counts are read from the existing ownership, transaction, gift and coupon
 * tables with a single batch of queries. The profile is cached per player and invalidated whenever a
 * transaction completes.</p>
 */
public final class ProfileServiceImpl implements ProfileService {

    private final SkinOwnershipService ownership;
    private final PurchaseRepository purchases;
    private final GiftRepository gifts;
    private final CouponRepository coupons;
    private final PlayerRepository players;
    private final CollectionService collections;
    private final Predicate<String> knownSkin;
    private final Consumer<String> logger;
    private final ConcurrentHashMap<UUID, SkinProfile> cache = new ConcurrentHashMap<>();

    public ProfileServiceImpl(@NotNull SkinOwnershipService ownership, @NotNull PurchaseRepository purchases,
                              @NotNull GiftRepository gifts, @NotNull CouponRepository coupons,
                              @NotNull PlayerRepository players, @NotNull CollectionService collections,
                              @NotNull Predicate<String> knownSkin, @NotNull Consumer<String> logger) {
        this.ownership = ownership;
        this.purchases = purchases;
        this.gifts = gifts;
        this.coupons = coupons;
        this.players = players;
        this.collections = collections;
        this.knownSkin = knownSkin;
        this.logger = logger;
    }

    @Override
    public @NotNull CompletionStage<SkinProfile> getProfile(@NotNull UUID playerId) {
        SkinProfile cached = cache.get(playerId);
        if (cached != null) {
            return AsyncUtil.completed(cached);
        }
        return load(playerId).exceptionally(error -> {
            logger.accept("Could not load the skin profile of " + playerId + ": " + AsyncUtil.describe(error));
            return new SkinProfile(playerId, 0, 0, 0, 0, 0, 0, 0, 0, null);
        });
    }

    private CompletionStage<SkinProfile> load(UUID playerId) {
        CompletionStage<Set<String>> owned = AsyncUtil.safe(() -> ownership.getOwnedSkinIds(playerId));
        CompletionStage<Set<String>> favorites = AsyncUtil.safe(() -> ownership.getFavoriteSkinIds(playerId));
        CompletionStage<Integer> purchased = AsyncUtil.safe(() -> purchases.countCompleted(playerId,
                de.skyslycer.hmcwraps.shop.PurchaseKind.SKIN, de.skyslycer.hmcwraps.shop.PurchaseKind.BUNDLE));
        CompletionStage<Integer> gifted = AsyncUtil.safe(() -> gifts.countSent(playerId));
        CompletionStage<Integer> received = AsyncUtil.safe(() -> gifts.countReceived(playerId));
        CompletionStage<Integer> redeemed = AsyncUtil.safe(() -> coupons.countByPlayer(playerId));
        CompletionStage<Integer> completed = AsyncUtil.safe(() -> collections.getCompletedCollections(playerId));
        CompletionStage<Double> experience = AsyncUtil.safe(() -> players.collectionXp(playerId));
        CompletionStage<OptionalLong> firstPurchase = AsyncUtil.safe(() -> purchases.firstCompletedAt(playerId))
                .thenApply(optional -> optional == null || optional.isEmpty()
                        ? OptionalLong.empty() : OptionalLong.of(optional.get()));

        List<CompletionStage<?>> stages = List.of(owned, favorites, purchased, gifted, received, redeemed, completed,
                experience, firstPurchase);
        CompletableFuture<?>[] futures = stages.stream()
                .map(stage -> stage.toCompletableFuture().exceptionally(error -> null))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenApply(ignored -> {
            SkinProfile profile = new SkinProfile(playerId,
                    countKnown(join(owned, Set.of())),
                    join(favorites, Set.of()).size(),
                    join(completed, 0),
                    join(purchased, 0),
                    join(gifted, 0),
                    join(received, 0),
                    join(redeemed, 0),
                    join(experience, 0D),
                    firstPurchaseAt(firstPurchase));
            cache.put(playerId, profile);
            return profile;
        });
    }

    private int countKnown(Set<String> owned) {
        if (owned.isEmpty()) {
            return 0;
        }
        return (int) owned.stream().filter(knownSkin).count();
    }

    private static @Nullable Long firstPurchaseAt(CompletionStage<OptionalLong> stage) {
        OptionalLong value = join(stage, OptionalLong.empty());
        return value.isPresent() ? value.getAsLong() : null;
    }

    private static <T> T join(CompletionStage<T> stage, T fallback) {
        try {
            T value = stage.toCompletableFuture().join();
            return value == null ? fallback : value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    @Override
    public @Nullable SkinProfile cachedProfile(@NotNull UUID playerId) {
        return cache.get(playerId);
    }

    @Override
    public void preload(@NotNull UUID playerId) {
        getProfile(playerId);
    }

    @Override
    public void invalidate(@NotNull UUID playerId) {
        cache.remove(playerId);
    }

    @Override
    public @NotNull CompletionStage<Boolean> addCollectionXp(@NotNull UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount == 0) {
            return AsyncUtil.completed(true);
        }
        invalidate(playerId);
        return AsyncUtil.safe(() -> players.addCollectionXp(playerId, amount))
                .thenApply(value -> true)
                .exceptionally(error -> {
                    logger.accept("Could not store collection XP for " + playerId + ": " + AsyncUtil.describe(error));
                    return false;
                });
    }

    @Override
    public @NotNull CompletionStage<Double> getCollectionXp(@NotNull UUID playerId) {
        return AsyncUtil.safe(() -> players.collectionXp(playerId)).exceptionally(error -> 0D);
    }
}
