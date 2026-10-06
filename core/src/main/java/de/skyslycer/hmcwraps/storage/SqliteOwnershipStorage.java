package de.skyslycer.hmcwraps.storage;

import de.skyslycer.hmcwraps.HMCWraps;
import de.skyslycer.hmcwraps.skin.StorageProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Single-writer SQLite backend; all JDBC operations stay off the server thread. */
public final class SqliteOwnershipStorage implements StorageProvider {
    private final Path databasePath;
    private final Consumer<String> errorLogger;
    private final ExecutorService executor;
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile Connection connection;
    private volatile CompletableFuture<Boolean> initialization;

    public SqliteOwnershipStorage(HMCWraps plugin) {
        this(HMCWraps.PLUGIN_PATH.resolve("skins.db"), message -> plugin.getLogger().severe(message));
    }

    SqliteOwnershipStorage(Path databasePath, Consumer<String> errorLogger) {
        this.databasePath = databasePath.toAbsolutePath();
        this.errorLogger = errorLogger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Y-HMCWraps-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public String id() { return "sqlite"; }
    @Override public boolean isReady() { return ready.get(); }
    @Override public boolean supportsSkinTransfers() { return true; }

    @Override
    public synchronized CompletionStage<Boolean> initialize() {
        if (initialization != null) return initialization;
        initialization = CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(databasePath.getParent());
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA foreign_keys=ON");
                    statement.execute("CREATE TABLE IF NOT EXISTS skin_ownership ("
                            + "player_uuid TEXT NOT NULL, skin_id TEXT NOT NULL, unlocked_at INTEGER NOT NULL, source TEXT NOT NULL, "
                            + "PRIMARY KEY(player_uuid, skin_id))");
                    statement.execute("CREATE TABLE IF NOT EXISTS skin_favorites ("
                            + "player_uuid TEXT NOT NULL, skin_id TEXT NOT NULL, created_at INTEGER NOT NULL, "
                            + "PRIMARY KEY(player_uuid, skin_id))");
                }
                ready.set(true);
                return true;
            } catch (Exception exception) {
                errorLogger.accept("Could not initialize SQLite skin storage: " + exception.getMessage());
                ready.set(false);
                return false;
            }
        }, executor);
        return initialization;
    }

    @Override
    public CompletionStage<Boolean> hasSkin(UUID playerId, String skinId) {
        return submit(() -> {
            try (PreparedStatement statement = requireConnection().prepareStatement(
                    "SELECT 1 FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                try (ResultSet result = statement.executeQuery()) { return result.next(); }
            }
        });
    }

    @Override
    public CompletionStage<Boolean> unlockSkin(UUID playerId, String skinId) {
        return submit(() -> {
            try (PreparedStatement statement = requireConnection().prepareStatement(
                    "INSERT OR IGNORE INTO skin_ownership(player_uuid, skin_id, unlocked_at, source) VALUES(?,?,?,?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, "grant");
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public CompletionStage<Boolean> unlockPurchasedSkin(UUID playerId, String skinId) {
        return submit(() -> {
            try (PreparedStatement statement = requireConnection().prepareStatement(
                    "INSERT OR IGNORE INTO skin_ownership(player_uuid, skin_id, unlocked_at, source) VALUES(?,?,?,?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, "purchase");
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public CompletionStage<Set<String>> getOwnedSkinIds(UUID playerId) {
        return submit(() -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = requireConnection().prepareStatement(
                    "SELECT skin_id FROM skin_ownership WHERE player_uuid=? ORDER BY unlocked_at DESC")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) values.add(result.getString(1));
                }
            }
            return Set.copyOf(values);
        });
    }

    @Override
    public CompletionStage<Set<String>> getFavoriteSkinIds(UUID playerId) {
        return submit(() -> {
            Set<String> values = new LinkedHashSet<>();
            try (PreparedStatement statement = requireConnection().prepareStatement(
                    "SELECT skin_id FROM skin_favorites WHERE player_uuid=? ORDER BY created_at DESC")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) values.add(result.getString(1));
                }
            }
            return Set.copyOf(values);
        });
    }

    @Override
    public CompletionStage<Boolean> setSkinFavorite(UUID playerId, String skinId, boolean favorite) {
        return submit(() -> {
            String sql = favorite
                    ? "INSERT OR IGNORE INTO skin_favorites(player_uuid, skin_id, created_at) VALUES(?,?,?)"
                    : "DELETE FROM skin_favorites WHERE player_uuid=? AND skin_id=?";
            try (PreparedStatement statement = requireConnection().prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, normalize(skinId));
                if (favorite) statement.setLong(3, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return true;
        });
    }

    @Override
    public CompletionStage<Boolean> transferSkin(UUID fromPlayer, UUID toPlayer, String skinId) {
        if (fromPlayer.equals(toPlayer)) return CompletableFuture.completedFuture(false);
        return submit(() -> {
            Connection current = requireConnection();
            boolean originalAutoCommit = current.getAutoCommit();
            current.setAutoCommit(false);
            try {
                String normalizedSkinId = normalize(skinId);
                try (PreparedStatement exists = current.prepareStatement(
                        "SELECT 1 FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                    exists.setString(1, fromPlayer.toString());
                    exists.setString(2, normalizedSkinId);
                    try (ResultSet result = exists.executeQuery()) {
                        if (!result.next()) {
                            current.rollback();
                            return false;
                        }
                    }
                    exists.setString(1, toPlayer.toString());
                    try (ResultSet result = exists.executeQuery()) {
                        if (result.next()) {
                            current.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement insert = current.prepareStatement(
                        "INSERT INTO skin_ownership(player_uuid, skin_id, unlocked_at, source) VALUES(?,?,?,?)")) {
                    insert.setString(1, toPlayer.toString());
                    insert.setString(2, normalizedSkinId);
                    insert.setLong(3, System.currentTimeMillis());
                    insert.setString(4, "trade");
                    insert.executeUpdate();
                }
                try (PreparedStatement delete = current.prepareStatement(
                        "DELETE FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
                    delete.setString(1, fromPlayer.toString());
                    delete.setString(2, normalizedSkinId);
                    if (delete.executeUpdate() != 1) {
                        current.rollback();
                        return false;
                    }
                }
                current.commit();
                return true;
            } catch (SQLException exception) {
                try { current.rollback(); } catch (SQLException rollbackException) { exception.addSuppressed(rollbackException); }
                throw exception;
            } finally {
                current.setAutoCommit(originalAutoCommit);
            }
        });
    }

    private <T> CompletionStage<T> submit(SqlSupplier<T> supplier) {
        if (!ready.get()) {
            CompletableFuture<Boolean> pendingInitialization = initialization;
            if (pendingInitialization != null && !pendingInitialization.isDone()) {
                return pendingInitialization.thenCompose(initialized -> initialized
                        ? submit(supplier)
                        : CompletableFuture.failedFuture(new IllegalStateException("SQLite storage is not initialized")));
            }
            return CompletableFuture.failedFuture(new IllegalStateException("SQLite storage is not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (SQLException exception) {
                ready.set(false);
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, executor);
    }

    private Connection requireConnection() throws SQLException {
        Connection current = connection;
        if (!ready.get() || current == null || current.isClosed()) throw new SQLException("SQLite connection is unavailable");
        return current;
    }

    @Override
    public void close() {
        ready.set(false);
        try {
            executor.submit(() -> {
                ready.set(false);
                Connection current = connection;
                connection = null;
                if (current != null) try { current.close(); } catch (SQLException ignored) { }
            }).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception exception) {
            errorLogger.accept("Timed out while closing SQLite skin storage: " + exception.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private static String normalize(String skinId) { return skinId.toLowerCase(java.util.Locale.ROOT); }
    @FunctionalInterface private interface SqlSupplier<T> { T get() throws SQLException; }
}
