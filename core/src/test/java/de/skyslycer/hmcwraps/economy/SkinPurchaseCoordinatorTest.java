package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import de.skyslycer.hmcwraps.skin.PurchaseResult;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.skin.StorageProvider;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPurchaseCoordinatorTest {
    private static final SkinPrice PRICE = new SkinPrice("test", "coins", 25D);

    @Test
    void successfulPurchaseUsesCustomCurrencyAndGrantsOwnershipOnce() {
        FakeStorage storage = new FakeStorage();
        FakeEconomy economy = new FakeEconomy();
        SkinPurchaseCoordinator coordinator = coordinator(storage);
        UUID player = UUID.randomUUID();

        PurchaseResult result = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy).toCompletableFuture().join();

        assertEquals(PurchaseResult.Status.SUCCESS, result.status());
        assertTrue(storage.has(player, "ruby_sword"));
        assertEquals(1, economy.withdraws.get());
        assertEquals("coins", economy.lastCurrency);
        assertEquals(0, economy.deposits.get());
    }

    @Test
    void alreadyOwnedSkinIsNeverChargedAgain() {
        FakeStorage storage = new FakeStorage();
        FakeEconomy economy = new FakeEconomy();
        SkinPurchaseCoordinator coordinator = coordinator(storage);
        UUID player = UUID.randomUUID();
        storage.unlockSkin(player, "ruby_sword").toCompletableFuture().join();

        PurchaseResult result = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy).toCompletableFuture().join();

        assertEquals(PurchaseResult.Status.ALREADY_OWNED, result.status());
        assertEquals(0, economy.withdraws.get());
    }

    @Test
    void insufficientFundsAndFailedWithdrawalNeverGrantOwnership() {
        FakeStorage storage = new FakeStorage();
        FakeEconomy economy = new FakeEconomy();
        SkinPurchaseCoordinator coordinator = coordinator(storage);
        UUID player = UUID.randomUUID();

        economy.balance = 10D;
        PurchaseResult insufficient = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy).toCompletableFuture().join();
        assertEquals(PurchaseResult.Status.INSUFFICIENT_FUNDS, insufficient.status());
        assertEquals(0, economy.withdraws.get());

        economy.balance = 100D;
        economy.withdrawSuccess = false;
        PurchaseResult failed = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy).toCompletableFuture().join();
        assertEquals(PurchaseResult.Status.PAYMENT_FAILED, failed.status());
        assertFalse(storage.has(player, "ruby_sword"));
        assertEquals(0, economy.deposits.get());
    }

    @Test
    void storageFailureAfterChargeAttemptsRefund() {
        FakeStorage storage = new FakeStorage();
        storage.purchaseSuccess = false;
        FakeEconomy economy = new FakeEconomy();
        SkinPurchaseCoordinator coordinator = coordinator(storage);

        PurchaseResult result = coordinator.purchase(UUID.randomUUID(), "ruby_sword", PRICE, economy, economy)
                .toCompletableFuture().join();

        assertEquals(PurchaseResult.Status.STORAGE_FAILED, result.status());
        assertEquals(1, economy.withdraws.get());
        assertEquals(1, economy.deposits.get());
    }

    @Test
    void repeatedClickWhileTransactionIsPendingIsRejected() {
        FakeStorage storage = new FakeStorage();
        FakeEconomy economy = new FakeEconomy();
        CompletableFuture<Double> delayedBalance = new CompletableFuture<>();
        economy.balanceStage = delayedBalance;
        SkinPurchaseCoordinator coordinator = coordinator(storage);
        UUID player = UUID.randomUUID();

        CompletionStage<PurchaseResult> first = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy);
        PurchaseResult second = coordinator.purchase(player, "ruby_sword", PRICE, economy, economy).toCompletableFuture().join();
        assertEquals(PurchaseResult.Status.BUSY, second.status());

        delayedBalance.complete(100D);
        assertEquals(PurchaseResult.Status.SUCCESS, first.toCompletableFuture().join().status());
        assertEquals(1, economy.withdraws.get());
    }

    private static SkinPurchaseCoordinator coordinator(FakeStorage storage) {
        return new SkinPurchaseCoordinator(storage, ignored -> { });
    }

    private static final class FakeStorage implements StorageProvider {
        private final ConcurrentHashMap<UUID, Set<String>> owned = new ConcurrentHashMap<>();
        private volatile boolean purchaseSuccess = true;

        @Override public String id() { return "test"; }
        @Override public CompletionStage<Boolean> initialize() { return CompletableFuture.completedFuture(true); }
        @Override public CompletionStage<Boolean> hasSkin(UUID playerId, String skinId) {
            return CompletableFuture.completedFuture(has(playerId, skinId));
        }
        @Override public CompletionStage<Boolean> unlockSkin(UUID playerId, String skinId) {
            return add(playerId, skinId);
        }
        @Override public CompletionStage<Boolean> unlockPurchasedSkin(UUID playerId, String skinId) {
            return purchaseSuccess ? add(playerId, skinId) : CompletableFuture.completedFuture(false);
        }
        @Override public CompletionStage<Set<String>> getOwnedSkinIds(UUID playerId) {
            return CompletableFuture.completedFuture(owned.getOrDefault(playerId, Set.of()));
        }
        @Override public void close() { }
        private boolean has(UUID playerId, String skinId) {
            return owned.getOrDefault(playerId, Set.of()).contains(skinId.toLowerCase(java.util.Locale.ROOT));
        }
        private CompletionStage<Boolean> add(UUID playerId, String skinId) {
            owned.compute(playerId, (uuid, values) -> {
                java.util.HashSet<String> next = new java.util.HashSet<>(values == null ? Set.of() : values);
                next.add(skinId.toLowerCase(java.util.Locale.ROOT));
                return Set.copyOf(next);
            });
            return CompletableFuture.completedFuture(true);
        }
    }

    private static final class FakeEconomy implements EconomyProvider, SkinPurchaseCoordinator.EconomyDispatcher {
        private final AtomicInteger withdraws = new AtomicInteger();
        private final AtomicInteger deposits = new AtomicInteger();
        private volatile double balance = 100D;
        private volatile boolean withdrawSuccess = true;
        private volatile String lastCurrency;
        private volatile CompletableFuture<Double> balanceStage;

        @Override public String id() { return "test"; }
        @Override public boolean isAvailable() { return true; }
        @Override public boolean supportsCurrency(String currency) { return "coins".equals(currency); }
        @Override public CompletionStage<Double> balance(UUID playerId, String currency) {
            return CompletableFuture.completedFuture(balance);
        }
        @Override public CompletionStage<Boolean> withdraw(UUID playerId, String currency, double amount) {
            lastCurrency = currency;
            withdraws.incrementAndGet();
            return CompletableFuture.completedFuture(withdrawSuccess);
        }
        @Override public CompletionStage<Boolean> deposit(UUID playerId, String currency, double amount) {
            deposits.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }
        @Override public CompletionStage<Double> balance(UUID playerId, EconomyProvider provider, String currency) {
            lastCurrency = currency;
            CompletableFuture<Double> delayed = balanceStage;
            return delayed == null ? balance(playerId, currency) : delayed;
        }
        @Override public CompletionStage<Boolean> withdraw(UUID playerId, EconomyProvider provider, String currency, double amount) {
            return withdraw(playerId, currency, amount);
        }
        @Override public CompletionStage<Boolean> deposit(UUID playerId, EconomyProvider provider, String currency, double amount) {
            return deposit(playerId, currency, amount);
        }
    }
}
