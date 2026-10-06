package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.PurchaseRepository;
import de.skyslycer.hmcwraps.shop.PurchaseKind;
import de.skyslycer.hmcwraps.shop.PurchaseRecord;
import de.skyslycer.hmcwraps.shop.TransactionStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link PurchaseRepository}. */
public final class SqlPurchaseRepository implements PurchaseRepository {

    private static final String COLUMNS = "transaction_id, player_uuid, kind, target_id, amount, currency, provider, "
            + "status, created_at, completed_at, detail, coupon_code, recipient_uuid";

    private final Database database;

    public SqlPurchaseRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Boolean> begin(@NotNull PurchaseRecord record) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO transactions(" + COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, record.transactionId());
                statement.setString(2, record.playerId().toString());
                statement.setString(3, record.kind().id());
                statement.setString(4, record.targetId());
                statement.setDouble(5, record.amount());
                statement.setString(6, record.currency());
                statement.setString(7, record.provider());
                statement.setString(8, record.status().name());
                statement.setLong(9, record.createdAt());
                if (record.completedAt() == null) {
                    statement.setNull(10, java.sql.Types.INTEGER);
                } else {
                    statement.setLong(10, record.completedAt());
                }
                statement.setString(11, record.detail());
                statement.setString(12, record.couponCode());
                statement.setString(13, record.recipientId() == null ? null : record.recipientId().toString());
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public @NotNull CompletionStage<Optional<PurchaseRecord>> find(@NotNull String transactionId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM transactions WHERE transaction_id=?")) {
                statement.setString(1, transactionId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(map(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> complete(@NotNull String transactionId, @NotNull TransactionStatus status,
                                                     @Nullable String detail) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE transactions SET status=?, completed_at=?, detail=? WHERE transaction_id=?")) {
                statement.setString(1, status.name());
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, detail);
                statement.setString(4, transactionId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<List<PurchaseRecord>> history(@NotNull UUID playerId, int limit) {
        return database.read(connection -> {
            List<PurchaseRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM transactions WHERE player_uuid=? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, Math.max(1, limit));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        records.add(map(result));
                    }
                }
            }
            return List.copyOf(records);
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> hasCompleted(@NotNull UUID playerId, @NotNull PurchaseKind kind,
                                                          @NotNull String targetId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM transactions WHERE player_uuid=? AND kind=? AND target_id=? AND status IN (?,?) LIMIT 1")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, kind.id());
                statement.setString(3, targetId.toLowerCase(java.util.Locale.ROOT).trim());
                statement.setString(4, TransactionStatus.SUCCESS.name());
                statement.setString(5, TransactionStatus.ALREADY_OWNED.name());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Integer> countCompleted(@NotNull UUID playerId, @NotNull PurchaseKind... kinds) {
        return database.read(connection -> {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM transactions WHERE player_uuid=? AND status IN (?,?)");
            if (kinds.length > 0) {
                sql.append(" AND kind IN (");
                sql.append("?,".repeat(kinds.length));
                sql.setLength(sql.length() - 1);
                sql.append(')');
            }
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                statement.setString(index++, playerId.toString());
                statement.setString(index++, TransactionStatus.SUCCESS.name());
                statement.setString(index++, TransactionStatus.ALREADY_OWNED.name());
                for (PurchaseKind kind : kinds) {
                    statement.setString(index++, kind.id());
                }
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<Optional<Long>> firstCompletedAt(@NotNull UUID playerId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT MIN(created_at) FROM transactions WHERE player_uuid=? AND status IN (?,?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, TransactionStatus.SUCCESS.name());
                statement.setString(3, TransactionStatus.ALREADY_OWNED.name());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    long value = result.getLong(1);
                    return result.wasNull() ? Optional.empty() : Optional.of(value);
                }
            }
        });
    }

    @Override
    public @NotNull CompletionStage<List<PurchaseRecord>> pendingOlderThan(long timestamp) {
        return database.read(connection -> {
            List<PurchaseRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM transactions WHERE status=? AND created_at < ? ORDER BY created_at ASC LIMIT 100")) {
                statement.setString(1, TransactionStatus.PENDING.name());
                statement.setLong(2, timestamp);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        records.add(map(result));
                    }
                }
            }
            return List.copyOf(records);
        });
    }

    private PurchaseRecord map(ResultSet result) throws SQLException {
        long completed = result.getLong("completed_at");
        boolean completedNull = result.wasNull();
        String recipient = result.getString("recipient_uuid");
        return new PurchaseRecord(
                result.getString("transaction_id"),
                UUID.fromString(result.getString("player_uuid")),
                PurchaseKind.fromId(result.getString("kind")),
                result.getString("target_id"),
                result.getDouble("amount"),
                result.getString("currency"),
                result.getString("provider"),
                parseStatus(result.getString("status")),
                result.getLong("created_at"),
                completedNull ? null : completed,
                result.getString("detail"),
                result.getString("coupon_code"),
                recipient == null ? null : UUID.fromString(recipient)
        );
    }

    private static TransactionStatus parseStatus(String value) {
        if (value == null) {
            return TransactionStatus.ERROR;
        }
        try {
            return TransactionStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return TransactionStatus.ERROR;
        }
    }
}
