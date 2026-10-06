package de.skyslycer.hmcwraps.skin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Ownership cache over the persistent provider. GUI rendering loads one set, not one query per icon. */
public final class SkinOwnershipService implements OwnershipManager {
    private final StorageProvider storage;
    private final ConcurrentHashMap<UUID, CompletableFuture<Set<String>>> playerCache = new ConcurrentHashMap<>();

    public SkinOwnershipService(StorageProvider storage) { this.storage = storage; }

    public StorageProvider storageProvider() { return storage; }
    public boolean isReady() { return storage.isReady(); }

    @Override
    public CompletionStage<Boolean> hasSkin(UUID playerId, String skinId) {
        return owned(playerId).thenApply(ids -> ids.contains(normalize(skinId)));
    }

    @Override
    public CompletionStage<Boolean> unlockSkin(UUID playerId, String skinId) {
        return unlock(playerId, skinId, false);
    }

    public CompletionStage<Boolean> unlockPurchasedSkin(UUID playerId, String skinId) {
        return unlock(playerId, skinId, true);
    }

    private CompletionStage<Boolean> unlock(UUID playerId, String skinId, boolean purchased) {
        CompletionStage<Boolean> operation = safe(() -> purchased
                ? storage.unlockPurchasedSkin(playerId, skinId) : storage.unlockSkin(playerId, skinId));
        return operation.thenCompose(success -> {
            if (!success) return CompletableFuture.completedFuture(false);
            invalidate(playerId);
            return safe(() -> storage.getOwnedSkinIds(playerId)).handle((ids, error) -> {
                if (error == null && ids != null) playerCache.put(playerId, CompletableFuture.completedFuture(normalize(ids)));
                return true;
            });
        });
    }

    @Override
    public CompletionStage<Set<String>> getOwnedSkinIds(UUID playerId) { return owned(playerId); }

    public CompletionStage<Boolean> transferSkin(UUID fromPlayer, UUID toPlayer, String skinId) {
        if (fromPlayer.equals(toPlayer)) return CompletableFuture.completedFuture(false);
        return safe(() -> storage.transferSkin(fromPlayer, toPlayer, skinId)).thenApply(success -> {
            if (Boolean.TRUE.equals(success)) {
                invalidate(fromPlayer);
                invalidate(toPlayer);
                return true;
            }
            return false;
        });
    }

    public void preload(UUID playerId) {
        owned(playerId);
    }

    public void invalidate(UUID playerId) {
        playerCache.remove(playerId);
    }

    public Set<String> cachedOwnedSkinIds(UUID playerId) {
        CompletableFuture<Set<String>> cached = playerCache.get(playerId);
        if (cached == null || !cached.isDone() || cached.isCompletedExceptionally()) return Set.of();
        return cached.getNow(Set.of());
    }

    public boolean isCachedOwned(UUID playerId, String skinId) {
        CompletableFuture<Set<String>> cached = playerCache.get(playerId);
        return cached != null && cached.isDone() && !cached.isCompletedExceptionally()
                && cached.getNow(Set.of()).contains(normalize(skinId));
    }

    private CompletableFuture<Set<String>> owned(UUID playerId) {
        CompletableFuture<Set<String>> existing = playerCache.get(playerId);
        if (existing != null) return existing;
        CompletableFuture<Set<String>> placeholder = new CompletableFuture<>();
        existing = playerCache.putIfAbsent(playerId, placeholder);
        if (existing != null) return existing;
        safe(() -> storage.getOwnedSkinIds(playerId)).whenComplete((ids, error) -> {
            if (error != null) {
                playerCache.remove(playerId, placeholder);
                placeholder.completeExceptionally(error);
            } else {
                placeholder.complete(normalize(ids));
            }
        });
        return placeholder;
    }

    private static <T> CompletionStage<T> safe(Supplier<CompletionStage<T>> supplier) {
        try {
            CompletionStage<T> result = supplier.get();
            return result == null ? CompletableFuture.failedFuture(new IllegalStateException("Storage provider returned no completion stage")) : result;
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private static String normalize(String id) { return id.toLowerCase(java.util.Locale.ROOT); }
    private static Set<String> normalize(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return ids.stream().filter(java.util.Objects::nonNull).map(SkinOwnershipService::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
