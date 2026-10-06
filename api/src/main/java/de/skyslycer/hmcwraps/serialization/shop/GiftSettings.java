package de.skyslycer.hmcwraps.serialization.shop;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/** The {@code gifts} section of {@code config.yml}. */
@ConfigSerializable
public class GiftSettings {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("allow-offline")
    private Boolean allowOffline = true;

    @Setting("cooldown-seconds")
    private Integer cooldownSeconds = 30;

    @Setting("message-max-length")
    private Integer messageMaxLength = 64;

    @Setting("notify-on-join")
    private Boolean notifyOnJoin = true;

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isAllowOffline() {
        return allowOffline == null || allowOffline;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds == null || cooldownSeconds < 0 ? 0 : cooldownSeconds;
    }

    /** The maximum message length; {@code 0} disables gift messages completely. */
    public int getMessageMaxLength() {
        return messageMaxLength == null || messageMaxLength < 0 ? 64 : messageMaxLength;
    }

    public boolean isNotifyOnJoin() {
        return notifyOnJoin == null || notifyOnJoin;
    }
}
