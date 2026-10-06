package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.FavoriteRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link FavoriteRepository}. */
public final class SqlFavoriteRepository implements FavoriteRepository {

    private final Database database;

    public SqlFavoriteRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Set<String>> favorites(@NotNull UUID playerId) {
        return database.read(connection -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT skin_id FROM skin_favorites WHERE player_uuid=? ORDER BY created_at DESC, skin_id")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        values.add(result.getString(1));
                    }
                }
            }
            return Collections.unmodifiableSet(values);
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> isFavorite(@NotNull UUID playerId, @NotNull String skinId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM skin_favorites WHERE player_uuid=? AND skin_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> setFavorite(@NotNull UUID playerId, @NotNull String skinId, boolean favorite) {
        return database.write(connection -> {
            String sql = favorite
                    ? "INSERT OR IGNORE INTO skin_favorites(player_uuid, skin_id, created_at) VALUES(?,?,?)"
                    : "DELETE FROM skin_favorites WHERE player_uuid=? AND skin_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                if (favorite) {
                    statement.setLong(3, System.currentTimeMillis());
                }
                statement.executeUpdate();
            }
            return true;
        });
    }

    private static String normalize(String skinId) {
        return skinId.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
