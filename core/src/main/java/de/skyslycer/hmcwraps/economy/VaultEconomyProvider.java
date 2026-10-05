package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Optional Vault bridge with no compile-time link to VaultAPI. Vault exposes one default currency. */
public final class VaultEconomyProvider implements EconomyProvider {
    private volatile Object economy;
    private volatile Plugin vaultPlugin;

    @Override public String id() { return "vault"; }
    @Override public boolean supportsCurrency(String currency) {
        if (currency == null || currency.isBlank()) return true;
        String normalized = currency.toLowerCase(Locale.ROOT);
        return normalized.equals("vault") || normalized.equals("money") || normalized.equals("default");
    }
    @Override public boolean isAvailable() { return getEconomy() != null; }

    @Override
    public CompletionStage<Double> balance(UUID playerId, String currency) {
        if (!supportsCurrency(currency)) return CompletableFuture.failedFuture(new IllegalArgumentException("Vault has one default currency"));
        try {
            Object provider = requireEconomy();
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            Object value = provider.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(provider, player);
            if (value instanceof Number number) return CompletableFuture.completedFuture(number.doubleValue());
            return CompletableFuture.failedFuture(new IllegalStateException("Vault returned an invalid balance"));
        } catch (Throwable throwable) { return CompletableFuture.failedFuture(unwrap(throwable)); }
    }

    @Override public CompletionStage<Boolean> withdraw(UUID playerId, String currency, double amount) { return transact("withdrawPlayer", playerId, currency, amount); }
    @Override public CompletionStage<Boolean> deposit(UUID playerId, String currency, double amount) { return transact("depositPlayer", playerId, currency, amount); }

    private CompletionStage<Boolean> transact(String methodName, UUID playerId, String currency, double amount) {
        if (!supportsCurrency(currency)) return CompletableFuture.completedFuture(false);
        try {
            Object provider = requireEconomy();
            Method method = provider.getClass().getMethod(methodName, OfflinePlayer.class, double.class);
            Object response = method.invoke(provider, Bukkit.getOfflinePlayer(playerId), amount);
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return CompletableFuture.completedFuture(Boolean.TRUE.equals(success));
        } catch (Throwable throwable) { return CompletableFuture.failedFuture(unwrap(throwable)); }
    }

    private Object requireEconomy() {
        Object value = getEconomy();
        if (value == null) throw new IllegalStateException("Vault economy service is not available");
        return value;
    }

    private Object getEconomy() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) { economy = null; vaultPlugin = null; return null; }
        try {
            Class<?> serviceType = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"}) RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration((Class) serviceType);
            if (registration == null) {
                economy = null;
                vaultPlugin = null;
                return null;
            }
            vaultPlugin = vault;
            economy = registration.getProvider();
            return economy;
        } catch (Throwable ignored) { return null; }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.lang.reflect.InvocationTargetException invocation && invocation.getCause() != null) return invocation.getCause();
        return throwable;
    }
}
