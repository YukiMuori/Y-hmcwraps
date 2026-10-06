package de.skyslycer.hmcwraps.serialization.discord;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code discord} section of {@code config.yml}. Discord support is entirely optional: the webhook
 * is called asynchronously and any failure is logged once and then ignored.
 */
@ConfigSerializable
public class DiscordSettings {

    @Setting("enabled")
    private Boolean enabled = false;

    @Setting("webhook-url")
    private String webhookUrl = "";

    @Setting("username")
    private String username = "HMCWraps";

    @Setting("avatar-url")
    private String avatarUrl = "";

    @Setting("server-name")
    private String serverName = "";

    @Setting("events")
    private Events events = new Events();

    @Setting("messages")
    private Map<String, String> messages = new LinkedHashMap<>();

    /** Whether the webhook is configured and turned on. */
    public boolean isEnabled() {
        return enabled != null && enabled && !getWebhookUrl().isBlank();
    }

    public String getWebhookUrl() {
        return webhookUrl == null ? "" : webhookUrl.trim();
    }

    public String getUsername() {
        return username == null || username.isBlank() ? "HMCWraps" : username;
    }

    public String getAvatarUrl() {
        return avatarUrl == null ? "" : avatarUrl.trim();
    }

    public String getServerName() {
        return serverName == null ? "" : serverName.trim();
    }

    public Events getEvents() {
        return events == null ? new Events() : events;
    }

    /** The overridden message of an event, or {@code null} when the built-in default should be used. */
    public String message(String event) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(event.toLowerCase(Locale.ROOT));
    }

    /** Per event switches. */
    @ConfigSerializable
    public static class Events {

        @Setting("purchase")
        private Boolean purchase = true;

        @Setting("bundle-purchase")
        private Boolean bundlePurchase = true;

        @Setting("gift")
        private Boolean gift = true;

        @Setting("collection-complete")
        private Boolean collectionComplete = true;

        @Setting("collection-reward")
        private Boolean collectionReward = false;

        @Setting("coupon")
        private Boolean coupon = false;

        @Setting("shop-refresh")
        private Boolean shopRefresh = false;

        @Setting("event-start")
        private Boolean eventStart = true;

        @Setting("event-end")
        private Boolean eventEnd = true;

        public boolean isPurchase() {
            return purchase == null || purchase;
        }

        public boolean isBundlePurchase() {
            return bundlePurchase == null || bundlePurchase;
        }

        public boolean isGift() {
            return gift == null || gift;
        }

        public boolean isCollectionComplete() {
            return collectionComplete == null || collectionComplete;
        }

        public boolean isCollectionReward() {
            return collectionReward != null && collectionReward;
        }

        public boolean isCoupon() {
            return coupon != null && coupon;
        }

        public boolean isShopRefresh() {
            return shopRefresh != null && shopRefresh;
        }

        public boolean isEventStart() {
            return eventStart == null || eventStart;
        }

        public boolean isEventEnd() {
            return eventEnd == null || eventEnd;
        }
    }
}
