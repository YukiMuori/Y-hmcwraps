package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.CollectionRewardRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link CollectionRewardRepository}. */
public final class SqlCollectionRewardRepository implements CollectionRewardRepository {

    private final Database database;

    public SqlCollectionRewardRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Set<String>> claimed(@NotNull UUID playerId, @NotNull String collectionId) {
        return database.read(connection -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT milestone_id FROM collection_rewards WHERE player_uuid=? AND collection_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(collectionId));
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
    public @NotNull CompletionStage<Set<String>> claimedMilestones(@NotNull UUID playerId) {
        return database.read(connection -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT collection_id || ':' || milestone_id FROM collection_rewards WHERE player_uuid=?")) {
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
    public @NotNull CompletionStage<Boolean> claim(@NotNull UUID playerId, @NotNull String collectionId,
                                                   @NotNull String milestoneId) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO collection_rewards(player_uuid, collection_id, milestone_id, claimed_at) VALUES(?,?,?,?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(collectionId));
                statement.setString(3, normalize(milestoneId));
                statement.setLong(4, System.currentTimeMillis());
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> release(@NotNull UUID playerId, @NotNull String collectionId,
                                                     @NotNull String milestoneId) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM collection_rewards WHERE player_uuid=? AND collection_id=? AND milestone_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(collectionId));
                statement.setString(3, normalize(milestoneId));
                return statement.executeUpdate() > 0;
            }
        });
    }

    private static String normalize(String input) {
        return input.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
