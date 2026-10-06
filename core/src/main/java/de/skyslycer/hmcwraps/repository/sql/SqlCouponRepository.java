package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.CouponRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link CouponRepository}. */
public final class SqlCouponRepository implements CouponRepository {

    private final Database database;

    public SqlCouponRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Reservation> reserve(@NotNull String code, @NotNull UUID playerId,
                                                        @NotNull String transactionId, double amount, double discount,
                                                        int maxUses, int maxUsesPerPlayer) {
        return database.transaction(connection -> {
            String normalized = normalizeCode(code);
            if (maxUses >= 0 && count(connection, "SELECT COUNT(*) FROM coupon_redemptions WHERE code=?",
                    normalized, null) >= maxUses) {
                return Reservation.EXHAUSTED;
            }
            if (maxUsesPerPlayer >= 0 && count(connection, "SELECT COUNT(*) FROM coupon_redemptions WHERE code=? AND player_uuid=?",
                    normalized, playerId.toString()) >= maxUsesPerPlayer) {
                return Reservation.ALREADY_USED;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO coupon_redemptions(code, player_uuid, transaction_id, amount, discount, redeemed_at) "
                            + "VALUES(?,?,?,?,?,?)")) {
                statement.setString(1, normalized);
                statement.setString(2, playerId.toString());
                statement.setString(3, transactionId);
                statement.setDouble(4, amount);
                statement.setDouble(5, discount);
                statement.setLong(6, System.currentTimeMillis());
                int inserted = statement.executeUpdate();
                return inserted > 0 ? Reservation.RESERVED : Reservation.ALREADY_USED;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> release(@NotNull String transactionId) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM coupon_redemptions WHERE transaction_id=?")) {
                statement.setString(1, transactionId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Integer> uses(@NotNull String code) {
        return database.read(connection -> count(connection,
                "SELECT COUNT(*) FROM coupon_redemptions WHERE code=?", normalizeCode(code), null));
    }

    @Override
    public @NotNull CompletionStage<Integer> usesByPlayer(@NotNull String code, @NotNull UUID playerId) {
        return database.read(connection -> count(connection,
                "SELECT COUNT(*) FROM coupon_redemptions WHERE code=? AND player_uuid=?",
                normalizeCode(code), playerId.toString()));
    }

    @Override
    public @NotNull CompletionStage<Integer> countByPlayer(@NotNull UUID playerId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM coupon_redemptions WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    private static int count(java.sql.Connection connection, String sql, String first, String second) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first);
            if (second != null) {
                statement.setString(2, second);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private static String normalizeCode(String code) {
        return code.toUpperCase(java.util.Locale.ROOT).trim();
    }
}
