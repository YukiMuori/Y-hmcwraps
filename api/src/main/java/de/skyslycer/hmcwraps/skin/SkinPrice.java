package de.skyslycer.hmcwraps.skin;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/** A configured price in an economy provider's currency. */
public record SkinPrice(@NotNull String provider, @NotNull String currency, double amount) {
    public SkinPrice {
        provider = Objects.requireNonNull(provider, "provider").toLowerCase(Locale.ROOT).trim();
        currency = Objects.requireNonNull(currency, "currency").trim();
        if (provider.isBlank() || currency.isBlank()) throw new IllegalArgumentException("Provider and currency must not be blank");
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Price must be a finite non-negative number");
    }
}
