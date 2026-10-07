package de.skyslycer.hmcwraps.market;

import de.skyslycer.hmcwraps.database.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class MarketRepository {
    private final Database database;

    public MarketRepository(Database database) { this.database = database; }

    public CompletionStage<Boolean> create(MarketListing listing) {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO skin_market_listings(listing_id,seller_uuid,seller_name,skin_id,amount,currency,provider,status,created_at) VALUES(?,?,?,?,?,?,?,'ACTIVE',?)")) {
                statement.setString(1, listing.id().toString()); statement.setString(2, listing.sellerId().toString());
                statement.setString(3, listing.sellerName()); statement.setString(4, listing.skinId());
                statement.setDouble(5, listing.amount()); statement.setString(6, listing.currency());
                statement.setString(7, listing.provider()); statement.setLong(8, listing.createdAt());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletionStage<List<MarketListing>> active() {
        return database.read(connection -> {
            List<MarketListing> result = new ArrayList<>();
            try (var statement = connection.prepareStatement("SELECT * FROM skin_market_listings WHERE status='ACTIVE' ORDER BY created_at DESC"); var rows = statement.executeQuery()) {
                while (rows.next()) result.add(read(rows));
            }
            return List.copyOf(result);
        });
    }

    public CompletionStage<MarketListing> find(UUID id) {
        return database.read(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM skin_market_listings WHERE listing_id=? AND status='ACTIVE'")) {
                statement.setString(1, id.toString());
                try (var rows = statement.executeQuery()) { return rows.next() ? read(rows) : null; }
            }
        });
    }

    public CompletionStage<Boolean> complete(UUID id, UUID buyer) {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("UPDATE skin_market_listings SET status='SOLD',buyer_uuid=?,completed_at=? WHERE listing_id=? AND status='ACTIVE'")) {
                statement.setString(1, buyer.toString()); statement.setLong(2, System.currentTimeMillis()); statement.setString(3, id.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletionStage<Boolean> cancel(UUID id, UUID seller) {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("UPDATE skin_market_listings SET status='CANCELLED',completed_at=? WHERE listing_id=? AND seller_uuid=? AND status='ACTIVE'")) {
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, id.toString()); statement.setString(3, seller.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static MarketListing read(java.sql.ResultSet row) throws java.sql.SQLException {
        return new MarketListing(UUID.fromString(row.getString("listing_id")), UUID.fromString(row.getString("seller_uuid")),
                row.getString("seller_name"), row.getString("skin_id"), row.getDouble("amount"),
                row.getString("currency"), row.getString("provider"), row.getLong("created_at"));
    }
}
