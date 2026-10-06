package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.PlayerRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link PlayerRepository}. */
public final class SqlPlayerRepository implements PlayerRepository {

    private final Database database;

    public SqlPlayerRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Optional<String>> selectedCoupon(@NotNull UUID playerId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT selected_coupon FROM player_settings WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    String value = result.getString(1);
                    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> setSelectedCoupon(@NotNull UUID playerId, String code) {
        return database.write(connection -> {
            if (code == null || code.isBlank()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_settings WHERE player_uuid=?")) {
                    statement.setString(1, playerId.toString());
                    statement.executeUpdate();
                }
                return true;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO player_settings(player_uuid, selected_coupon, updated_at) VALUES(?,?,?) "
                            + "ON CONFLICT(player_uuid) DO UPDATE SET selected_coupon=excluded.selected_coupon, "
                            + "updated_at=excluded.updated_at")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, code.toUpperCase(java.util.Locale.ROOT).trim());
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public @NotNull CompletionStage<Double> addCollectionXp(@NotNull UUID playerId, double amount) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO profile_stats(player_uuid, collection_xp, updated_at) VALUES(?,?,?) "
                            + "ON CONFLICT(player_uuid) DO UPDATE SET collection_xp=profile_stats.collection_xp + excluded.collection_xp, "
                            + "updated_at=excluded.updated_at")) {
                statement.setString(1, playerId.toString());
                statement.setDouble(2, amount);
                statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT collection_xp FROM profile_stats WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getDouble(1) : 0D;
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Double> collectionXp(@NotNull UUID playerId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT collection_xp FROM profile_stats WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getDouble(1) : 0D;
                }
            }
        });
    }
}
