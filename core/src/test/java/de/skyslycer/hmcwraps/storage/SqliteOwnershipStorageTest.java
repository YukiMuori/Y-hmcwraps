package de.skyslycer.hmcwraps.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteOwnershipStorageTest {
    @TempDir Path directory;

    @Test
    void ownershipTransfersAtomically() throws Exception {
        Path database = directory.resolve("skin-trade.db");
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        SqliteOwnershipStorage storage = new SqliteOwnershipStorage(database, ignored -> { });
        try {
            assertTrue(storage.initialize().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertTrue(storage.unlockPurchasedSkin(sender, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));

            assertTrue(storage.transferSkin(sender, recipient, "RUBY_SWORD").toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(storage.hasSkin(sender, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertTrue(storage.hasSkin(recipient, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(storage.transferSkin(sender, recipient, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
        } finally {
            storage.close();
        }
    }

    @Test
    void ownershipPersistsAcrossStorageRestart() throws Exception {
        Path database = directory.resolve("skins.db");
        UUID player = UUID.randomUUID();
        SqliteOwnershipStorage first = new SqliteOwnershipStorage(database, ignored -> { });
        try {
            assertTrue(first.initialize().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertFalse(first.hasSkin(player, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertTrue(first.unlockPurchasedSkin(player, "Ruby_Sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertTrue(first.hasSkin(player, "ruby_sword").toCompletableFuture().get(10, TimeUnit.SECONDS));
        } finally {
            first.close();
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.prepareStatement("SELECT source FROM skin_ownership WHERE player_uuid=? AND skin_id=?")) {
            statement.setString(1, player.toString());
            statement.setString(2, "ruby_sword");
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                org.junit.jupiter.api.Assertions.assertEquals("purchase", result.getString("source"));
            }
        }

        SqliteOwnershipStorage restarted = new SqliteOwnershipStorage(database, ignored -> { });
        try {
            assertTrue(restarted.initialize().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertTrue(restarted.getOwnedSkinIds(player).toCompletableFuture().get(10, TimeUnit.SECONDS)
                    .containsAll(Set.of("ruby_sword")));
        } finally {
            restarted.close();
        }
    }
}
