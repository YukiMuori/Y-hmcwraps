package de.skyslycer.hmcwraps.serialization.shop;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/** The {@code shop} section of {@code config.yml}. The definition files live in {@code shops.yml}. */
@ConfigSerializable
public class ShopSettings {

    @Setting("enabled")
    private Boolean enabled = true;

    @Setting("daily-enabled")
    private Boolean dailyEnabled = true;

    @Setting("collection-completion-notifications")
    private Boolean completionNotifications = true;

    @Setting("history-limit")
    private Integer historyLimit = 10;

    @Setting("reconcile-interrupted-transactions")
    private Boolean reconcileTransactions = true;

    /** Whether the shop is enabled at all. */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** Whether the rotating daily section is offered. */
    public boolean isDailyEnabled() {
        return dailyEnabled == null || dailyEnabled;
    }

    /** Whether completing a collection is announced to the player. */
    public boolean isCompletionNotifications() {
        return completionNotifications == null || completionNotifications;
    }

    /** How many entries the history and profile screens show. */
    public int getHistoryLimit() {
        return historyLimit == null || historyLimit < 1 ? 10 : Math.min(100, historyLimit);
    }

    /** Whether interrupted journal rows are reconciled on startup. */
    public boolean isReconcileTransactions() {
        return reconcileTransactions == null || reconcileTransactions;
    }
}
