package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyManagerTest {
    @Test
    void customProvidersAreRegisteredCaseInsensitivelyAndCanBeReplaced() {
        EconomyManager manager = new EconomyManager();
        TestProvider first = new TestProvider("custom_coins", "coins");
        manager.register(first);
        assertSame(first, manager.get("CUSTOM_COINS").orElseThrow());
        assertTrue(first.supportsCurrency("coins"));

        TestProvider replacement = new TestProvider("custom_coins", "tokens");
        manager.register(replacement);
        assertSame(replacement, manager.get("custom_coins").orElseThrow());
        assertEquals(1, manager.providers().size());
    }

    private record TestProvider(String id, String currency) implements EconomyProvider {
        @Override public boolean isAvailable() { return true; }
        @Override public boolean supportsCurrency(String requested) { return currency.equals(requested); }
        @Override public CompletionStage<Double> balance(UUID playerId, String requested) {
            return CompletableFuture.completedFuture(100D);
        }
        @Override public CompletionStage<Boolean> withdraw(UUID playerId, String requested, double amount) {
            return CompletableFuture.completedFuture(supportsCurrency(requested));
        }
        @Override public CompletionStage<Boolean> deposit(UUID playerId, String requested, double amount) {
            return CompletableFuture.completedFuture(supportsCurrency(requested));
        }
    }
}
