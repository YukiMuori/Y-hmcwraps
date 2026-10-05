package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import de.skyslycer.hmcwraps.skin.PurchaseResult;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.skin.StorageProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Serializes per-player purchases and compensates a successful charge if ownership cannot be stored. */
public final class SkinPurchaseCoordinator {
    private final StorageProvider storage;
    private final Consumer<String> warningLogger;
    private final java.util.Set<PurchaseKey> inFlight = ConcurrentHashMap.newKeySet();

    public SkinPurchaseCoordinator(StorageProvider storage, Consumer<String> warningLogger) {
        this.storage = storage;
        this.warningLogger = warningLogger;
    }

    public CompletionStage<PurchaseResult> purchase(UUID playerId, String skinId, SkinPrice price,
                                                     EconomyProvider provider, EconomyDispatcher dispatcher) {
        PurchaseKey key = new PurchaseKey(playerId, skinId.toLowerCase(java.util.Locale.ROOT));
        if (!inFlight.add(key)) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.BUSY, null));
        CompletableFuture<PurchaseResult> result = safe(() -> storage.hasSkin(playerId, skinId)).handle((owned, error) -> {
            if (error != null) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.STORAGE_FAILED, skinId));
            if (Boolean.TRUE.equals(owned)) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.ALREADY_OWNED, null));
            return charge(playerId, skinId, price, provider, dispatcher).toCompletableFuture();
        }).thenCompose(stage -> stage).toCompletableFuture().exceptionally(error -> {
            warningLogger.accept("Skin purchase transaction failed for " + playerId + " / " + skinId + ": " + rootCause(error).getMessage());
            return new PurchaseResult(PurchaseResult.Status.PROVIDER_UNAVAILABLE, provider.id());
        }).whenComplete((ignored, error) -> inFlight.remove(key));
        return result;
    }

    private CompletionStage<PurchaseResult> charge(UUID playerId, String skinId, SkinPrice price,
                                                    EconomyProvider provider, EconomyDispatcher dispatcher) {
        return safe(() -> dispatcher.balance(playerId, provider, price.currency())).handle((balance, error) -> {
            if (error != null) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.PROVIDER_UNAVAILABLE, provider.id()));
            if (balance == null || !Double.isFinite(balance) || balance < price.amount()) {
                return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.INSUFFICIENT_FUNDS, price.currency()));
            }
            return safe(() -> dispatcher.withdraw(playerId, provider, price.currency(), price.amount())).handle((withdrawn, withdrawError) -> {
                if (withdrawError != null) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.PROVIDER_UNAVAILABLE, provider.id()));
                if (!Boolean.TRUE.equals(withdrawn)) return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.PAYMENT_FAILED, provider.id()));
                return persistOrRefund(playerId, skinId, price, provider, dispatcher);
            }).thenCompose(stage -> stage).toCompletableFuture();
        }).thenCompose(stage -> stage);
    }

    private CompletionStage<PurchaseResult> persistOrRefund(UUID playerId, String skinId, SkinPrice price,
                                                             EconomyProvider provider, EconomyDispatcher dispatcher) {
        return safe(() -> storage.unlockPurchasedSkin(playerId, skinId)).handle((unlocked, error) -> {
            if (error == null && Boolean.TRUE.equals(unlocked)) {
                return CompletableFuture.completedFuture(new PurchaseResult(PurchaseResult.Status.SUCCESS, null));
            }
            return safe(() -> dispatcher.deposit(playerId, provider, price.currency(), price.amount())).handle((refunded, refundError) -> {
                if (refundError != null || !Boolean.TRUE.equals(refunded)) {
                    warningLogger.accept("Payment refund failed after skin ownership storage failed for " + playerId + " / " + skinId);
                }
                return new PurchaseResult(PurchaseResult.Status.STORAGE_FAILED, skinId);
            }).toCompletableFuture();
        }).thenCompose(stage -> stage);
    }

    public interface EconomyDispatcher {
        CompletionStage<Double> balance(UUID playerId, EconomyProvider provider, String currency);
        CompletionStage<Boolean> withdraw(UUID playerId, EconomyProvider provider, String currency, double amount);
        CompletionStage<Boolean> deposit(UUID playerId, EconomyProvider provider, String currency, double amount);
    }

    private static <T> CompletionStage<T> safe(Supplier<CompletionStage<T>> supplier) {
        try {
            CompletionStage<T> result = supplier.get();
            return result == null ? CompletableFuture.failedFuture(new IllegalStateException("Operation returned no completion stage")) : result;
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current != current.getCause()) current = current.getCause();
        return current;
    }

    private record PurchaseKey(UUID playerId, String skinId) { }
}
