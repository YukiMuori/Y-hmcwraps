package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Optional currency backend. A failed withdrawal must never be reported as successful. */
public interface EconomyProvider {
    @NotNull String id();
    boolean isAvailable();
    @NotNull CompletionStage<Double> balance(@NotNull UUID playerId, @NotNull String currency);
    @NotNull CompletionStage<Boolean> withdraw(@NotNull UUID playerId, @NotNull String currency, double amount);
    @NotNull CompletionStage<Boolean> deposit(@NotNull UUID playerId, @NotNull String currency, double amount);
    default boolean supportsCurrency(@NotNull String currency) { return isAvailable(); }
}
