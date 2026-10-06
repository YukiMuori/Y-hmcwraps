package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.ShopRotationState;
import de.skyslycer.hmcwraps.repository.ShopStateRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link ShopStateRepository}. Entry ids are stored as a comma separated list. */
public final class SqlShopStateRepository implements ShopStateRepository {

    private static final String SEPARATOR = "\u0001";

    private final Database database;

    public SqlShopStateRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Optional<ShopRotationState>> find(@NotNull String shopId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cycle_key, entries, created_at, expires_at FROM shop_state WHERE shop_id=?")) {
                statement.setString(1, shopId.toLowerCase(java.util.Locale.ROOT));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    List<String> entries = Arrays.stream(result.getString("entries").split(SEPARATOR))
                            .filter(value -> !value.isBlank()).toList();
                    return Optional.of(new ShopRotationState(shopId, result.getString("cycle_key"), entries,
                            result.getLong("created_at"), result.getLong("expires_at")));
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> save(@NotNull ShopRotationState state) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO shop_state(shop_id, cycle_key, entries, created_at, expires_at) VALUES(?,?,?,?,?)")) {
                statement.setString(1, state.shopId().toLowerCase(java.util.Locale.ROOT));
                statement.setString(2, state.cycleKey());
                statement.setString(3, String.join(SEPARATOR, state.entries()));
                statement.setLong(4, state.createdAt());
                statement.setLong(5, state.expiresAt());
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> clear(@NotNull String shopId) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shop_state WHERE shop_id=?")) {
                statement.setString(1, shopId.toLowerCase(java.util.Locale.ROOT));
                statement.executeUpdate();
            }
            return true;
        });
    }
}
