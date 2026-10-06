package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.OwnershipRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link OwnershipRepository} with a single atomic grant/transfer path. */
public final class SqlOwnershipRepository implements OwnershipRepository {

    private final Database database;

    public SqlOwnershipRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Boolean> has(@NotNull UUID playerId, @NotNull String skinId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Set<String>> owned(@NotNull UUID playerId) {
        return database.read(connection -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT skin_id FROM skin_ownership WHERE player_uuid=? ORDER BY unlocked_at DESC, skin_id")) {
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
    public @NotNull CompletionStage<Boolean> grant(@NotNull UUID playerId, @NotNull String skinId, @NotNull String source) {
        return database.write(connection -> insert(connection, playerId, normalize(skinId), source));
    }

    @Override
    public @NotNull CompletionStage<Set<String>> grantAll(@NotNull UUID playerId, @NotNull Collection<String> skinIds,
                                                          @NotNull String source) {
        return database.transaction(connection -> {
            Set<String> granted = new LinkedHashSet<>();
            for (String skinId : skinIds) {
                String normalized = normalize(skinId);
                if (insert(connection, playerId, normalized, source)) {
                    granted.add(normalized);
                }
            }
            return Collections.unmodifiableSet(granted);
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> revoke(@NotNull UUID playerId, @NotNull String skinId) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> transfer(@NotNull UUID fromPlayer, @NotNull UUID toPlayer, @NotNull String skinId) {
        if (fromPlayer.equals(toPlayer)) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return database.transaction(connection -> {
            String normalized = normalize(skinId);
            if (!owns(connection, fromPlayer, normalized) || owns(connection, toPlayer, normalized)) {
                return false;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO skin_ownership(player_uuid, skin_id, unlocked_at, source) VALUES(?,?,?,?)")) {
                insert.setString(1, toPlayer.toString());
                insert.setString(2, normalized);
                insert.setLong(3, System.currentTimeMillis());
                insert.setString(4, "trade");
                insert.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                delete.setString(1, fromPlayer.toString());
                delete.setString(2, normalized);
                if (delete.executeUpdate() != 1) {
                    throw new SQLException("Ownership row disappeared during the transfer");
                }
            }
            return true;
        });
    }

    private boolean insert(Connection connection, UUID playerId, String skinId, String source) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO skin_ownership(player_uuid, skin_id, unlocked_at, source) VALUES(?,?,?,?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, skinId);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, source);
            return statement.executeUpdate() > 0;
        }
    }

    private boolean owns(Connection connection, UUID playerId, String skinId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, skinId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String normalize(String skinId) {
        return skinId.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
