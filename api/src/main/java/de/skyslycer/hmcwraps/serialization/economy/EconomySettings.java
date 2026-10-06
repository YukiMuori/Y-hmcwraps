package de.skyslycer.hmcwraps.serialization.economy;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The {@code economy} section of {@code config.yml}. */
@ConfigSerializable
public class EconomySettings {

    /** The value that enables automatic provider detection. */
    public static final String AUTO = "auto";

    @Setting("provider")
    private String provider = AUTO;

    @Setting("priority")
    private List<String> priority = new ArrayList<>(List.of("excellent_economy", "vault"));

    @Setting("default-currency")
    private String defaultCurrency = "coins";

    /** The configured provider id ({@code auto}, {@code vault}, ...). */
    public String getProvider() {
        return provider == null || provider.isBlank() ? AUTO : provider.toLowerCase(Locale.ROOT).trim();
    }

    /** The detection order used when {@link #getProvider()} is {@code auto}. */
    public List<String> getPriority() {
        if (priority == null || priority.isEmpty()) {
            return List.of("excellent_economy", "vault");
        }
        List<String> result = new ArrayList<>(priority.size());
        for (String entry : priority) {
            if (entry != null && !entry.isBlank()) {
                result.add(entry.toLowerCase(Locale.ROOT).trim());
            }
        }
        return List.copyOf(result);
    }

    /** The currency used when a price does not declare one. */
    public String getDefaultCurrency() {
        return defaultCurrency == null || defaultCurrency.isBlank() ? "coins" : defaultCurrency.trim();
    }

    /** Whether the provider should be detected automatically. */
    public boolean isAutomatic() {
        return getProvider().equals(AUTO);
    }
}
