package de.skyslycer.hmcwraps.storage;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.FavoriteRepository;
import de.skyslycer.hmcwraps.repository.OwnershipRepository;
import de.skyslycer.hmcwraps.skin.StorageProvider;
import de.skyslycer.hmcwraps.skin.TransactionalStorage;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * The bundled storage provider. It exposes the repository backed SQL database through the public
 * {@link StorageProvider} contract and adds the atomic grant/revoke operations the shop requires.
 */
public final class SqlStorageProvider implements TransactionalStorage, StorageProvider {

    private final Database database;
    private final OwnershipRepository ownership;
    private final FavoriteRepository favorites;

    public SqlStorageProvider(@NotNull Database database, @NotNull OwnershipRepository ownership,
                              @NotNull FavoriteRepository favorites) {
        this.database = database;
        this.ownership = ownership;
        this.favorites = favorites;
    }

    @Override
    public @NotNull String id() {
        return "sqlite";
    }

    @Override
    public @NotNull CompletionStage<Boolean> initialize() {
        return database.initialize();
    }

    @Override
    public boolean isReady() {
        return database.isReady();
    }

    @Override
    public @NotNull CompletionStage<Boolean> hasSkin(@NotNull UUID playerId, @NotNull String skinId) {
        return ownership.has(playerId, skinId);
    }

    @Override
    public @NotNull CompletionStage<Boolean> unlockSkin(@NotNull UUID playerId, @NotNull String skinId) {
        return ownership.grant(playerId, skinId, "grant");
    }

    @Override
    public @NotNull CompletionStage<Boolean> unlockPurchasedSkin(@NotNull UUID playerId, @NotNull String skinId) {
        return ownership.grant(playerId, skinId, "purchase");
    }

    @Override
    public @NotNull CompletionStage<Set<String>> getOwnedSkinIds(@NotNull UUID playerId) {
        return ownership.owned(playerId);
    }

    @Override
    public @NotNull CompletionStage<Set<String>> getFavoriteSkinIds(@NotNull UUID playerId) {
        return favorites.favorites(playerId);
    }

    @Override
    public @NotNull CompletionStage<Boolean> setSkinFavorite(@NotNull UUID playerId, @NotNull String skinId, boolean favorite) {
        return favorites.setFavorite(playerId, skinId, favorite);
    }

    @Override
    public boolean supportsSkinTransfers() {
        return true;
    }

    @Override
    public @NotNull CompletionStage<Boolean> transferSkin(@NotNull UUID fromPlayer, @NotNull UUID toPlayer, @NotNull String skinId) {
        return ownership.transfer(fromPlayer, toPlayer, skinId);
    }

    @Override
    public @NotNull CompletionStage<Set<String>> grantAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds,
                                                          @NotNull String source) {
        List<String> requested = skinIds.stream().filter(skinId -> skinId != null && !skinId.isBlank())
                .map(skinId -> skinId.toLowerCase(Locale.ROOT).trim()).distinct().toList();
        if (requested.isEmpty()) {
            return AsyncUtil.completed(Set.of());
        }
        return AsyncUtil.safe(() -> ownership.owned(playerId)).thenCompose(owned -> {
            Set<String> known = owned == null ? Set.of() : owned;
            Set<String> missing = requested.stream().filter(skinId -> !known.contains(skinId))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (missing.isEmpty()) {
                return AsyncUtil.completed(Set.<String>of());
            }
            return AsyncUtil.safe(() -> ownership.grantAll(playerId, missing, source)).thenCompose(granted -> {
                if (!Boolean.TRUE.equals(granted)) {
                    // Fail closed: the caller must not treat a stored-less grant as a success.
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Ownership could not be stored"));
                }
                return AsyncUtil.completed(Set.copyOf(missing));
            });
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> revokeAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds) {
        return de.skyslycer.hmcwraps.util.AsyncUtil.allOf(skinIds.stream()
                .map(skinId -> ownership.revoke(playerId, skinId).exceptionally(error -> false))
                .toList()).thenApply(results -> results.stream().allMatch(Boolean::booleanValue));
    }

    @Override
    public void close() {
        database.close();
    }

    /** The underlying database, used for diagnostics. */
    public @NotNull Database database() {
        return database;
    }
}
