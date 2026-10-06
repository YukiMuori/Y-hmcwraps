package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Economy facade. It resolves the provider to use for a currency according to the
 * {@code economy.provider} configuration and never throws: unavailable providers are reported as an
 * empty result so the shop can disable itself gracefully.
 */
public interface EconomyService {

    /** A human readable description of the currently active provider, for placeholders and logs. */
    @NotNull String describeActiveProvider();

    /** Whether at least one provider is currently available. */
    boolean isAvailable();

    /**
     * Resolves the provider responsible for a currency.
     *
     * @param preferredProvider a provider id from configuration ({@code auto} for automatic detection)
     * @param currency          the currency id
     * @return the provider able to handle the currency, if any
     */
    @NotNull Optional<EconomyProvider> providerFor(@Nullable String preferredProvider, @NotNull String currency);

    /** Convenience check for {@link #providerFor(String, String)}. */
    default boolean supports(@Nullable String preferredProvider, @NotNull String currency) {
        return providerFor(preferredProvider, currency).isPresent();
    }

    /** The configured provider selection mode ({@code auto}, a provider id, ...). */
    @NotNull String getConfiguredProvider();

    /** All registered providers. */
    @NotNull Collection<EconomyProvider> providers();

    /** Registers (or replaces) a provider. */
    void register(@NotNull EconomyProvider provider);

    /** Async balance lookup through the resolved provider. */
    @NotNull CompletionStage<Double> balance(@NotNull UUID playerId, @NotNull String currency, @Nullable String preferredProvider);

    /** Async withdrawal through the resolved provider. */
    @NotNull CompletionStage<Boolean> withdraw(@NotNull UUID playerId, @NotNull String currency, double amount, @Nullable String preferredProvider);

    /** Async deposit through the resolved provider. */
    @NotNull CompletionStage<Boolean> deposit(@NotNull UUID playerId, @NotNull String currency, double amount, @Nullable String preferredProvider);
}
