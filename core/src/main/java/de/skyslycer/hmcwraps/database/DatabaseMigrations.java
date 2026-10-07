package de.skyslycer.hmcwraps.database;

import java.util.List;

/**
 * The complete, ordered schema history of the plugin database.
 *
 * <p><strong>Never modify a released migration.</strong> Add a new version instead; existing servers
 * only run migrations above their recorded {@code schema_version}.</p>
 */
public final class DatabaseMigrations {

    private DatabaseMigrations() {
    }

    /** The latest schema version shipped with this build. */
    public static final int LATEST_VERSION = 8;

    private static final List<DatabaseMigration> MIGRATIONS = List.of(
            new DatabaseMigration(1, List.of(
                    "CREATE TABLE IF NOT EXISTS skin_ownership ("
                            + "player_uuid TEXT NOT NULL, skin_id TEXT NOT NULL, unlocked_at INTEGER NOT NULL, "
                            + "source TEXT NOT NULL, PRIMARY KEY(player_uuid, skin_id))",
                    "CREATE TABLE IF NOT EXISTS skin_favorites ("
                            + "player_uuid TEXT NOT NULL, skin_id TEXT NOT NULL, created_at INTEGER NOT NULL, "
                            + "PRIMARY KEY(player_uuid, skin_id))"
            ), "baseline ownership and favorites tables"),
            new DatabaseMigration(2, List.of(
                    "CREATE TABLE IF NOT EXISTS transactions ("
                            + "transaction_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, kind TEXT NOT NULL, "
                            + "target_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, provider TEXT NOT NULL, "
                            + "status TEXT NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER, detail TEXT, "
                            + "coupon_code TEXT, recipient_uuid TEXT)",
                    "CREATE INDEX IF NOT EXISTS idx_transactions_player ON transactions(player_uuid, created_at DESC)",
                    "CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status)",
                    "CREATE TABLE IF NOT EXISTS coupon_redemptions ("
                            + "code TEXT NOT NULL, player_uuid TEXT NOT NULL, transaction_id TEXT NOT NULL, "
                            + "amount REAL NOT NULL, discount REAL NOT NULL, redeemed_at INTEGER NOT NULL, "
                            + "PRIMARY KEY(code, transaction_id))",
                    "CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_player ON coupon_redemptions(code, player_uuid)"
            ), "transaction audit trail and coupon redemptions"),
            new DatabaseMigration(3, List.of(
                    "CREATE TABLE IF NOT EXISTS shop_state ("
                            + "shop_id TEXT PRIMARY KEY, cycle_key TEXT NOT NULL, entries TEXT NOT NULL, "
                            + "created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)"
            ), "persisted shop rotations"),
            new DatabaseMigration(4, List.of(
                    "CREATE TABLE IF NOT EXISTS collection_rewards ("
                            + "player_uuid TEXT NOT NULL, collection_id TEXT NOT NULL, milestone_id TEXT NOT NULL, "
                            + "claimed_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, collection_id, milestone_id))",
                    "CREATE INDEX IF NOT EXISTS idx_collection_rewards_player ON collection_rewards(player_uuid)"
            ), "claimed collection milestones"),
            new DatabaseMigration(5, List.of(
                    "CREATE TABLE IF NOT EXISTS gifts ("
                            + "gift_id TEXT PRIMARY KEY, sender_uuid TEXT NOT NULL, sender_name TEXT, "
                            + "recipient_uuid TEXT NOT NULL, recipient_name TEXT, kind TEXT NOT NULL, target_id TEXT NOT NULL, "
                            + "amount REAL NOT NULL, currency TEXT, provider TEXT, message TEXT, status TEXT NOT NULL, "
                            + "created_at INTEGER NOT NULL, delivered_at INTEGER, notified INTEGER NOT NULL DEFAULT 0)",
                    "CREATE INDEX IF NOT EXISTS idx_gifts_recipient ON gifts(recipient_uuid, notified)",
                    "CREATE INDEX IF NOT EXISTS idx_gifts_sender ON gifts(sender_uuid, created_at DESC)"
            ), "gift audit trail and delivery notifications"),
            new DatabaseMigration(6, List.of(
                    "CREATE TABLE IF NOT EXISTS player_settings ("
                            + "player_uuid TEXT PRIMARY KEY, selected_coupon TEXT, updated_at INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS profile_stats ("
                            + "player_uuid TEXT PRIMARY KEY, collection_xp REAL NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)"
            ), "player shop preferences and profile counters"),
            new DatabaseMigration(7, List.of(
                    "DROP TABLE IF EXISTS skin_favorites"
            ), "remove the discontinued favorites feature"),
            new DatabaseMigration(8, List.of(
                    "CREATE TABLE IF NOT EXISTS skin_market_listings ("
                            + "listing_id TEXT PRIMARY KEY, seller_uuid TEXT NOT NULL, seller_name TEXT NOT NULL, "
                            + "skin_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, provider TEXT NOT NULL, "
                            + "status TEXT NOT NULL, created_at INTEGER NOT NULL, buyer_uuid TEXT, completed_at INTEGER)",
                    "CREATE INDEX IF NOT EXISTS idx_skin_market_status ON skin_market_listings(status, created_at DESC)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_skin_market_seller_skin_active ON skin_market_listings(seller_uuid, skin_id) WHERE status = 'ACTIVE'"
            ), "persistent player skin market listings")
    );

    /** All migrations in ascending order. */
    public static List<DatabaseMigration> all() {
        return MIGRATIONS;
    }
}
