package de.skyslycer.hmcwraps.serialization;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class LanguageSettings {
    @Setting("default")
    private String defaultLanguage = "en";
    @Setting("player-locale")
    private boolean usePlayerLocale = false;

    public String getDefaultLanguage() { return defaultLanguage == null || defaultLanguage.isBlank() ? "en" : defaultLanguage; }
    public boolean isUsePlayerLocale() { return usePlayerLocale; }
}
