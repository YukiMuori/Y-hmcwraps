package de.skyslycer.hmcwraps.database;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single-writer SQLite implementation of {@link Database}.
 *
 * <p>The plugin uses one connection guarded by a dedicated thread: SQLite serializes writes anyway and a
 * single connection removes {@code SQLITE_BUSY} races entirely. Every statement is executed off the
 * server thread and failures are reported through the returned stage while the connection stays usable
 * for transient errors.</p>
 */
public final class SqlDatabase implements Database {

    private static final String DRIVER = "org.sqlite.JDBC";
    private static final int BUSY_TIMEOUT_MS = 5_000;

    private final Path databasePath;
    private final Consumer<String> errorLogger;
    private final ExecutorService executor;
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile Connection connection;
    private volatile CompletableFuture<Boolean> initialization;
    private volatile int schemaVersion;

    public SqlDatabase(@NotNull Path databasePath, @NotNull Consumer<String> errorLogger) {
        this.databasePath = databasePath.toAbsolutePath();
        this.errorLogger = errorLogger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Y-HMCWraps-Database");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public @NotNull String id() {
        return "sqlite";
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public synchronized @NotNull CompletionStage<Boolean> initialize() {
        if (initialization != null) {
            return initialization;
        }
        initialization = CompletableFuture.supplyAsync(() -> {
            try {
                Path parent = databasePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Class.forName(DRIVER);
                connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA foreign_keys=ON");
                    statement.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
                }
                connection.setAutoCommit(true);
                applyMigrations(connection);
                ready.set(true);
                return true;
            } catch (Exception exception) {
                errorLogger.accept("Could not initialize the SQLite database: " + describe(exception));
                ready.set(false);
                return false;
            }
        }, executor);
        return initialization;
    }

    private void applyMigrations(Connection current) throws SQLException {
        try (Statement statement = current.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        }
        int installed = readSchemaVersion(current);
        for (DatabaseMigration migration : DatabaseMigrations.all()) {
            if (migration.version() <= installed) {
                continue;
            }
            boolean originalAutoCommit = current.getAutoCommit();
            current.setAutoCommit(false);
            try {
                try (Statement statement = current.createStatement()) {
                    for (String sql : migration.statements()) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement record = current.prepareStatement(
                        "INSERT OR REPLACE INTO schema_version(version, applied_at) VALUES(?,?)")) {
                    record.setInt(1, migration.version());
                    record.setLong(2, System.currentTimeMillis());
                    record.executeUpdate();
                }
                current.commit();
                installed = migration.version();
                errorLogger.accept("Applied database migration v" + migration.version() + " (" + migration.description() + ").");
            } catch (SQLException exception) {
                try {
                    current.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                current.setAutoCommit(originalAutoCommit);
            }
        }
        schemaVersion = installed;
    }

    private int readSchemaVersion(Connection current) throws SQLException {
        try (Statement statement = current.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    @Override
    public <T> @NotNull CompletionStage<T> read(@NotNull SqlAction<T> action) {
        return submit(action, false);
    }

    @Override
    public <T> @NotNull CompletionStage<T> write(@NotNull SqlAction<T> action) {
        return submit(action, false);
    }

    @Override
    public <T> @NotNull CompletionStage<T> transaction(@NotNull SqlAction<T> action) {
        return submit(action, true);
    }

    private <T> CompletionStage<T> submit(SqlAction<T> action, boolean useTransaction) {
        if (!ready.get()) {
            CompletableFuture<Boolean> pending = initialization;
            if (pending != null && !pending.isDone()) {
                return pending.thenCompose(initialized -> initialized
                        ? submit(action, useTransaction)
                        : CompletableFuture.failedFuture(new IllegalStateException("Database is not initialized")));
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Database is not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            Connection current = connection;
            if (current == null) {
                throw new java.util.concurrent.CompletionException(new SQLException("Database connection is unavailable"));
            }
            try {
                if (!useTransaction) {
                    return action.run(current);
                }
                boolean originalAutoCommit = current.getAutoCommit();
                current.setAutoCommit(false);
                try {
                    T result = action.run(current);
                    current.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    try {
                        current.rollback();
                    } catch (SQLException rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                    throw exception;
                } finally {
                    current.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException exception) {
                handleFailure(exception);
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, executor);
    }

    private void handleFailure(SQLException exception) {
        if (isConnectionFatal(exception)) {
            ready.set(false);
            errorLogger.accept("SQLite connection failed and was marked unusable: " + describe(exception));
        } else {
            errorLogger.accept("SQLite statement failed: " + describe(exception));
        }
    }

    private boolean isConnectionFatal(SQLException exception) {
        Connection current = connection;
        try {
            return current == null || current.isClosed();
        } catch (SQLException ignored) {
            return true;
        }
    }

    private static String describe(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    @Override
    public void close() {
        ready.set(false);
        try {
            executor.submit(() -> {
                Connection current = connection;
                connection = null;
                if (current != null) {
                    try {
                        current.close();
                    } catch (SQLException ignored) {
                        // The connection is going away anyway.
                    }
                }
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            errorLogger.accept("Timed out while closing the SQLite database: " + describe(exception));
        } finally {
            executor.shutdownNow();
        }
    }

    /** The configured database file, used for diagnostics. */
    public @NotNull Path path() {
        return databasePath;
    }

    /** A convenience accessor used by tests. */
    public @NotNull List<DatabaseMigration> migrations() {
        return DatabaseMigrations.all();
    }
}
