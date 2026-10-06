package de.skyslycer.hmcwraps.database;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A versioned schema migration. Migrations are applied in ascending version order exactly once and are
 * recorded in the {@code schema_version} table, so an existing installation is upgraded in place while
 * a fresh installation simply runs every migration.
 *
 * @param version    the schema version this migration installs
 * @param statements the statements to execute, in order
 * @param description a human readable description used for logging
 */
public record DatabaseMigration(int version, @NotNull List<String> statements, @NotNull String description) {

    public DatabaseMigration {
        statements = List.copyOf(statements);
    }
}
