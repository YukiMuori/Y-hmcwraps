package de.skyslycer.hmcwraps.economy;

import de.skyslycer.hmcwraps.skin.EconomyProvider;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Provider registry; plugin detection and API calls live in provider adapters, not gameplay code. */
public final class EconomyManager {
    private final Map<String, EconomyProvider> providers = new ConcurrentHashMap<>();

    public void register(EconomyProvider provider) {
        java.util.Objects.requireNonNull(provider, "provider");
        String id = normalize(provider.id());
        if (id.isBlank()) throw new IllegalArgumentException("Economy provider id must not be blank");
        providers.put(id, provider);
    }
    public Optional<EconomyProvider> get(String id) { return Optional.ofNullable(providers.get(normalize(id))); }
    public Collection<EconomyProvider> providers() { return java.util.List.copyOf(providers.values()); }
    public void clear() { providers.clear(); }
    private static String normalize(String value) {
        // Configuration historically documented both excellent-economy and excellent_economy.
        // Treat separators identically for every provider so either spelling resolves reliably.
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replace('-', '_');
    }
}
