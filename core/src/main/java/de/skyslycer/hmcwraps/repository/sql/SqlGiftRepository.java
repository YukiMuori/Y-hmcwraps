package de.skyslycer.hmcwraps.repository.sql;

import de.skyslycer.hmcwraps.database.Database;
import de.skyslycer.hmcwraps.repository.GiftRepository;
import de.skyslycer.hmcwraps.shop.GiftRecord;
import de.skyslycer.hmcwraps.shop.GiftStatus;
import de.skyslycer.hmcwraps.shop.PurchaseKind;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL implementation of {@link GiftRepository}. */
public final class SqlGiftRepository implements GiftRepository {

    private static final String COLUMNS = "gift_id, sender_uuid, sender_name, recipient_uuid, recipient_name, kind, "
            + "target_id, amount, currency, provider, message, created_at, delivered_at, notified";

    private final Database database;

    public SqlGiftRepository(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletionStage<Boolean> insert(@NotNull GiftRecord record) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO gifts(" + COLUMNS + ", status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, record.giftId());
                statement.setString(2, record.senderId().toString());
                statement.setString(3, record.senderName());
                statement.setString(4, record.recipientId().toString());
                statement.setString(5, record.recipientName());
                statement.setString(6, record.kind().id());
                statement.setString(7, record.targetId());
                statement.setDouble(8, record.amount());
                statement.setString(9, record.currency());
                statement.setString(10, record.provider());
                statement.setString(11, record.message());
                statement.setLong(12, record.createdAt());
                if (record.deliveredAt() == null) {
                    statement.setNull(13, java.sql.Types.INTEGER);
                } else {
                    statement.setLong(13, record.deliveredAt());
                }
                statement.setInt(14, record.notified() ? 1 : 0);
                statement.setString(15, record.deliveredAt() == null ? GiftStatus.PENDING.id() : GiftStatus.DELIVERED.id());
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public @NotNull CompletionStage<Boolean> updateStatus(@NotNull String giftId, @NotNull GiftStatus status, boolean notified) {
        return database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gifts SET status=?, notified=?, delivered_at=COALESCE(delivered_at, ?) WHERE gift_id=?")) {
                statement.setString(1, status.id());
                statement.setInt(2, notified ? 1 : 0);
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, giftId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public @NotNull CompletionStage<List<GiftRecord>> pendingNotifications(@NotNull UUID recipientId) {
        return database.read(connection -> {
            List<GiftRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM gifts WHERE recipient_uuid=? AND notified=0 ORDER BY created_at ASC LIMIT 50")) {
                statement.setString(1, recipientId.toString());
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
    public @NotNull CompletionStage<Boolean> markNotified(@NotNull UUID recipientId, @NotNull List<String> giftIds) {
        if (giftIds.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }
        return database.write(connection -> {
            StringBuilder sql = new StringBuilder("UPDATE gifts SET notified=1 WHERE recipient_uuid=? AND gift_id IN (");
            sql.append("?,".repeat(giftIds.size()));
            sql.setLength(sql.length() - 1);
            sql.append(')');
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.setString(1, recipientId.toString());
                int index = 2;
                for (String giftId : giftIds) {
                    statement.setString(index++, giftId);
                }
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public @NotNull CompletionStage<Integer> countSent(@NotNull UUID senderId) {
        return count("SELECT COUNT(*) FROM gifts WHERE sender_uuid=?", senderId);
    }

    @Override
    public @NotNull CompletionStage<Integer> countReceived(@NotNull UUID recipientId) {
        return count("SELECT COUNT(*) FROM gifts WHERE recipient_uuid=?", recipientId);
    }

    @Override
    public @NotNull CompletionStage<List<GiftRecord>> history(@NotNull UUID recipientId, int limit) {
        return database.read(connection -> {
            List<GiftRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM gifts WHERE recipient_uuid=? ORDER BY created_at DESC LIMIT ?")) {
                statement.setString(1, recipientId.toString());
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

    private java.util.concurrent.CompletionStage<Integer> count(String sql, UUID playerId) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    private GiftRecord map(ResultSet result) throws SQLException {
        long delivered = result.getLong("delivered_at");
        boolean deliveredNull = result.wasNull();
        return new GiftRecord(
                result.getString("gift_id"),
                UUID.fromString(result.getString("sender_uuid")),
                result.getString("sender_name"),
                UUID.fromString(result.getString("recipient_uuid")),
                result.getString("recipient_name"),
                PurchaseKind.fromId(result.getString("kind")),
                result.getString("target_id"),
                result.getDouble("amount"),
                result.getString("currency") == null ? "" : result.getString("currency"),
                result.getString("provider") == null ? "" : result.getString("provider"),
                result.getString("message"),
                result.getLong("created_at"),
                deliveredNull ? null : delivered,
                result.getInt("notified") == 1
        );
    }
}
