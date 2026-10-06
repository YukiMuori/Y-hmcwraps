package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.economy.EconomyManager;
import de.skyslycer.hmcwraps.serialization.economy.EconomySettings;
import de.skyslycer.hmcwraps.skin.EconomyProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Economy facade with automatic provider detection.
 *
 * <p>Resolution order with {@code economy.provider: auto}: the configured priority list first, then any
 * registered provider that supports the requested currency. When nothing matches, the shop reports the
 * provider as unavailable instead of charging the player or crashing.</p>
 */
public final class EconomyServiceImpl implements EconomyService {

    /** The configuration value that enables automatic detection. */
    public static final String AUTO = "auto";

    private final EconomyManager registry;
    private final Supplier<EconomySettings> settings;
    private final Consumer<String> warningLogger;

    public EconomyServiceImpl(@NotNull EconomyManager registry, @NotNull Supplier<EconomySettings> settings,
                              @NotNull Consumer<String> warningLogger) {
        this.registry = registry;
        this.settings = settings;
        this.warningLogger = warningLogger;
    }

    private EconomySettings currentSettings() {
        EconomySettings current = settings.get();
        return current == null ? new EconomySettings() : current;
    }

    @Override
    public @NotNull String getConfiguredProvider() {
        String provider = currentSettings().getProvider();
        return provider == null || provider.isBlank() ? AUTO : provider.toLowerCase(Locale.ROOT).trim();
    }

    @Override
    public boolean isAvailable() {
        return registry.providers().stream().anyMatch(this::safeAvailable);
    }

    @Override
    public @NotNull Collection<EconomyProvider> providers() {
        return registry.providers();
    }

    @Override
    public void register(@NotNull EconomyProvider provider) {
        registry.register(provider);
    }

    @Override
    public @NotNull String describeActiveProvider() {
        return providerFor(getConfiguredProvider(), currentSettings().getDefaultCurrency())
                .map(EconomyProvider::id)
                .orElse("unavailable");
    }

    @Override
    public @NotNull Optional<EconomyProvider> providerFor(@Nullable String preferredProvider, @NotNull String currency) {
        String configured = preferredProvider == null || preferredProvider.isBlank()
                ? getConfiguredProvider() : preferredProvider.toLowerCase(Locale.ROOT).trim();
        if (!configured.equals(AUTO) && !configured.equals("automatic")) {
            return registry.get(configured).filter(provider -> supports(provider, currency));
        }
        List<String> priority = currentSettings().getPriority();
        for (String id : priority) {
            Optional<EconomyProvider> candidate = registry.get(id);
            if (candidate.isPresent() && supports(candidate.get(), currency)) {
                return candidate;
            }
        }
        return registry.providers().stream()
                .filter(provider -> supports(provider, currency))
                .sorted(Comparator.comparing(EconomyProvider::id))
                .findFirst();
    }

    @Override
    public @NotNull CompletionStage<Double> balance(@NotNull UUID playerId, @NotNull String currency,
                                                    @Nullable String preferredProvider) {
        EconomyProvider provider = providerFor(preferredProvider, currency).orElse(null);
        if (provider == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "No economy provider is available for currency '" + currency + "'"));
        }
        return safe(provider, () -> provider.balance(playerId, currency));
    }

    @Override
    public @NotNull CompletionStage<Boolean> withdraw(@NotNull UUID playerId, @NotNull String currency, double amount,
                                                      @Nullable String preferredProvider) {
        EconomyProvider provider = providerFor(preferredProvider, currency).orElse(null);
        if (provider == null) {
            return CompletableFuture.completedFuture(false);
        }
        return safe(provider, () -> provider.withdraw(playerId, currency, amount));
    }

    @Override
    public @NotNull CompletionStage<Boolean> deposit(@NotNull UUID playerId, @NotNull String currency, double amount,
                                                     @Nullable String preferredProvider) {
        EconomyProvider provider = providerFor(preferredProvider, currency).orElse(null);
        if (provider == null) {
            return CompletableFuture.completedFuture(false);
        }
        return safe(provider, () -> provider.deposit(playerId, currency, amount));
    }

    private <T> CompletionStage<T> safe(EconomyProvider provider, java.util.function.Supplier<CompletionStage<T>> action) {
        try {
            CompletionStage<T> stage = action.get();
            if (stage == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Economy provider '" + provider.id() + "' returned no completion stage"));
            }
            return stage.exceptionally(error -> {
                warningLogger.accept("Economy provider '" + provider.id() + "' failed: " + de.skyslycer.hmcwraps.util.AsyncUtil.describe(error));
                throw new java.util.concurrent.CompletionException(error);
            });
        } catch (LinkageError | RuntimeException exception) {
            warningLogger.accept("Economy provider '" + provider.id() + "' is unusable: "
                    + de.skyslycer.hmcwraps.util.AsyncUtil.describe(exception));
            return CompletableFuture.failedFuture(exception);
        }
    }

    private boolean safeAvailable(EconomyProvider provider) {
        try {
            return provider.isAvailable();
        } catch (LinkageError | RuntimeException exception) {
            warningLogger.accept("Economy provider '" + provider.id() + "' failed its availability check: "
                    + de.skyslycer.hmcwraps.util.AsyncUtil.describe(exception));
            return false;
        }
    }

    private boolean supports(EconomyProvider provider, String currency) {
        if (!safeAvailable(provider)) {
            return false;
        }
        try {
            return provider.supportsCurrency(currency);
        } catch (LinkageError | RuntimeException exception) {
            warningLogger.accept("Economy provider '" + provider.id() + "' does not support currency '"
                    + currency + "': " + de.skyslycer.hmcwraps.util.AsyncUtil.describe(exception));
            return false;
        }
    }
}
