package de.skyslycer.hmcwraps.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous SQL boundary used by every repository. Implementations must never block the calling
 * thread: all work happens on a dedicated executor and results are handed back through
 * {@link CompletionStage}s so callers can hop back onto the server thread when Bukkit APIs are needed.
 *
 * <p>The interface is deliberately dialect neutral: {@link SqlDatabase} implements it for SQLite today,
 * while a MySQL/MariaDB implementation can be added without touching services or repositories.</p>
 */
public interface Database extends AutoCloseable {

    /** The stable id of this database implementation ({@code sqlite}, {@code mariadb}, ...). */
    @NotNull String id();

    /** Whether the database finished initialization and can serve statements. */
    boolean isReady();

    /**
     * Opens the connection pool/connection and applies pending migrations.
     *
     * @return whether the database is usable afterwards
     */
    @NotNull CompletionStage<Boolean> initialize();

    /** Runs a read statement off the main thread. */
    <T> @NotNull CompletionStage<T> read(@NotNull SqlAction<T> action);

    /** Runs a single write statement off the main thread. */
    <T> @NotNull CompletionStage<T> write(@NotNull SqlAction<T> action);

    /**
     * Runs a statement inside an explicit transaction. Any thrown {@link SQLException} rolls the
     * transaction back; the returned stage then completes exceptionally.
     */
    <T> @NotNull CompletionStage<T> transaction(@NotNull SqlAction<T> action);

    /** The schema version currently installed, or {@code 0} when it is not known yet. */
    int schemaVersion();

    @Override
    void close();

    /** A unit of work executed with a JDBC connection. */
    @FunctionalInterface
    interface SqlAction<T> {
        T run(@NotNull Connection connection) throws SQLException;
    }
}
