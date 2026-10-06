package de.skyslycer.hmcwraps.skin;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinOwnershipServiceTest {
    @Test
    void loadsOneOwnedSetAndRefreshesCacheAfterGrant() {
        InMemoryProvider storage = new InMemoryProvider();
        SkinOwnershipService ownership = new SkinOwnershipService(storage);
        UUID player = UUID.randomUUID();

        assertTrue(ownership.getOwnedSkinIds(player).toCompletableFuture().join().isEmpty());
        assertTrue(ownership.getOwnedSkinIds(player).toCompletableFuture().join().isEmpty());
        assertEquals(1, storage.reads.get(), "cached ownership should avoid duplicate storage reads");

        assertTrue(ownership.unlockSkin(player, "ruby_sword").toCompletableFuture().join());
        assertTrue(ownership.isCachedOwned(player, "RUBY_SWORD"));
        assertTrue(ownership.hasSkin(player, "ruby_sword").toCompletableFuture().join());
    }

    @Test
    void failedReadIsEvictedAndCanBeRetried() {
        InMemoryProvider storage = new InMemoryProvider();
        SkinOwnershipService ownership = new SkinOwnershipService(storage);
        UUID player = UUID.randomUUID();
        storage.failReads = true;

        org.junit.jupiter.api.Assertions.assertThrows(CompletionException.class,
                () -> ownership.getOwnedSkinIds(player).toCompletableFuture().join());
        assertTrue(ownership.cachedOwnedSkinIds(player).isEmpty());

        storage.failReads = false;
        assertTrue(ownership.getOwnedSkinIds(player).toCompletableFuture().join().isEmpty());
        assertEquals(2, storage.reads.get(), "a request immediately after the failed stage must start a new storage read");
    }

    @Test
    void favoriteUpdatesRefreshTheirPlayerCache() {
        InMemoryProvider storage = new InMemoryProvider();
        SkinOwnershipService ownership = new SkinOwnershipService(storage);
        UUID player = UUID.randomUUID();

        assertTrue(ownership.getFavoriteSkinIds(player).toCompletableFuture().join().isEmpty());
        assertTrue(ownership.setFavorite(player, "ruby_sword", true).toCompletableFuture().join());
        assertTrue(ownership.cachedFavoriteSkinIds(player).contains("ruby_sword"));
        assertTrue(ownership.setFavorite(player, "ruby_sword", false).toCompletableFuture().join());
        assertTrue(ownership.cachedFavoriteSkinIds(player).isEmpty());
    }

    @Test
    void purchasedUnlockUsesPurchaseAuditPath() {
        InMemoryProvider storage = new InMemoryProvider();
        SkinOwnershipService ownership = new SkinOwnershipService(storage);
        UUID player = UUID.randomUUID();

        assertTrue(ownership.unlockPurchasedSkin(player, "ruby_sword").toCompletableFuture().join());
        assertEquals(1, storage.purchases.get());
        assertTrue(ownership.isCachedOwned(player, "ruby_sword"));
    }

    private static final class InMemoryProvider implements StorageProvider {
        private final ConcurrentHashMap<UUID, Set<String>> values = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Set<String>> favorites = new ConcurrentHashMap<>();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger purchases = new AtomicInteger();
        private volatile boolean failReads;

        @Override public String id() { return "test"; }
        @Override public CompletionStage<Boolean> initialize() { return CompletableFuture.completedFuture(true); }
        @Override public CompletionStage<Boolean> hasSkin(UUID playerId, String skinId) {
            return CompletableFuture.completedFuture(values.getOrDefault(playerId, Set.of()).contains(normalize(skinId)));
        }
        @Override public CompletionStage<Boolean> unlockSkin(UUID playerId, String skinId) {
            return unlock(playerId, skinId);
        }
        @Override public CompletionStage<Boolean> unlockPurchasedSkin(UUID playerId, String skinId) {
            purchases.incrementAndGet();
            return unlock(playerId, skinId);
        }
        @Override public CompletionStage<Set<String>> getOwnedSkinIds(UUID playerId) {
            reads.incrementAndGet();
            return failReads ? CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"))
                    : CompletableFuture.completedFuture(values.getOrDefault(playerId, Set.of()));
        }
        @Override public CompletionStage<Set<String>> getFavoriteSkinIds(UUID playerId) {
            return CompletableFuture.completedFuture(favorites.getOrDefault(playerId, Set.of()));
        }
        @Override public CompletionStage<Boolean> setSkinFavorite(UUID playerId, String skinId, boolean favorite) {
            favorites.compute(playerId, (uuid, current) -> {
                java.util.HashSet<String> next = new java.util.HashSet<>(current == null ? Set.of() : current);
                if (favorite) next.add(normalize(skinId));
                else next.remove(normalize(skinId));
                return Set.copyOf(next);
            });
            return CompletableFuture.completedFuture(true);
        }
        @Override public void close() { }

        private CompletionStage<Boolean> unlock(UUID playerId, String skinId) {
            values.compute(playerId, (uuid, current) -> {
                java.util.HashSet<String> next = new java.util.HashSet<>(current == null ? Set.of() : current);
                next.add(normalize(skinId));
                return Set.copyOf(next);
            });
            return CompletableFuture.completedFuture(true);
        }
        private static String normalize(String id) { return id.toLowerCase(Locale.ROOT); }
    }
}
