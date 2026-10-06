package de.skyslycer.hmcwraps.discord;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.serialization.Config;
import de.skyslycer.hmcwraps.serialization.discord.DiscordSettings;
import de.skyslycer.hmcwraps.shop.EventShop;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Optional Discord webhook notifications.
 *
 * <p>Every call is asynchronous on a dedicated executor and every failure is logged once and then
 * ignored: a broken webhook URL, a rate limit or a Discord outage must never affect gameplay. The
 * messages themselves are configurable through {@code discord.messages} and each event can be turned
 * off individually in {@code discord.events}.</p>
 */
public final class DiscordWebhook {

    /** Fallback templates used when {@code discord.messages} does not define the key. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "purchase", "**%player%** bought `%target%` for **%amount%**%detail%.",
            "bundle-purchase", "**%player%** bought the bundle `%bundle%` for **%amount%**%detail%.",
            "gift", "**%sender%** gifted `%target%` to **%recipient%** for **%amount%**.",
            "collection-complete", "**%player%** completed the collection `%collection%` (%owned%/%total%).",
            "collection-reward", "**%player%** claimed `%milestone%` of the collection `%collection%`.",
            "coupon", "**%player%** redeemed the coupon `%code%` and saved **%discount%**.",
            "shop-refresh", "The shop `%shop%` refreshed with **%entries%** offers.",
            "event-start", "The event shop `%event%` is now open.",
            "event-end", "The event shop `%event%` closed.");

    private final HMCWrapsPlugin plugin;
    private final HttpClient client;
    private final ExecutorService executor;
    private final Map<String, Long> lastFailure = new ConcurrentHashMap<>();

    public DiscordWebhook(@NotNull HMCWrapsPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Y-HMCWraps-Discord");
            thread.setDaemon(true);
            return thread;
        });
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
    }

    /** Sends a purchase notification. The detail is appended to the message when present. */
    public void purchase(@NotNull Player player, @NotNull String targetId, double amount, @Nullable String detail) {
        Map<String, String> values = playerValues(player);
        values.put("target", targetId);
        values.put("amount", format(amount));
        values.put("detail", detail == null ? "" : " (" + detail + ")");
        send("purchase", settings -> settings.getEvents().isPurchase(), values);
    }

    /** Sends a bundle purchase notification. */
    public void bundlePurchase(@NotNull Player player, @NotNull String bundleId, double amount, boolean missingOnly) {
        Map<String, String> values = playerValues(player);
        values.put("bundle", bundleId);
        values.put("amount", format(amount));
        values.put("detail", missingOnly ? " (missing skins only)" : "");
        send("bundle-purchase", settings -> settings.getEvents().isBundlePurchase(), values);
    }

    /** Sends a gift notification. */
    public void gift(@Nullable String senderName, @Nullable String recipientName, @NotNull String targetId,
                     double amount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sender", senderName == null ? "?" : senderName);
        values.put("recipient", recipientName == null ? "?" : recipientName);
        values.put("target", targetId);
        values.put("amount", format(amount));
        send("gift", settings -> settings.getEvents().isGift(), values);
    }

    /** Sends a collection completion notification. */
    public void collectionComplete(@NotNull Player player, @NotNull String collectionId, int owned, int total) {
        Map<String, String> values = playerValues(player);
        values.put("collection", collectionId);
        values.put("owned", String.valueOf(owned));
        values.put("total", String.valueOf(total));
        send("collection-complete", settings -> settings.getEvents().isCollectionComplete(), values);
    }

    /** Sends a collection reward notification. */
    public void collectionReward(@NotNull Player player, @NotNull String collectionId, @NotNull String milestoneId) {
        Map<String, String> values = playerValues(player);
        values.put("collection", collectionId);
        values.put("milestone", milestoneId);
        send("collection-reward", settings -> settings.getEvents().isCollectionReward(), values);
    }

    /** Sends a coupon redemption notification. */
    public void coupon(@NotNull Player player, @NotNull String code, double discount) {
        Map<String, String> values = playerValues(player);
        values.put("code", code);
        values.put("discount", format(discount));
        send("coupon", settings -> settings.getEvents().isCoupon(), values);
    }

    /** Sends a shop refresh notification. */
    public void shopRefresh(@NotNull String shopId, int entries) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("shop", shopId);
        values.put("entries", String.valueOf(entries));
        send("shop-refresh", settings -> settings.getEvents().isShopRefresh(), values);
    }

    /** Sends an event shop opening notification. */
    public void eventStart(@NotNull EventShop event) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("event", event.id());
        send("event-start", settings -> settings.getEvents().isEventStart(), values);
    }

    /** Sends an event shop closing notification. */
    public void eventEnd(@NotNull EventShop event) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("event", event.id());
        send("event-end", settings -> settings.getEvents().isEventEnd(), values);
    }

    /** Releases the notification executor. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private Map<String, String> playerValues(Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        return values;
    }

    private void send(String event, java.util.function.Predicate<DiscordSettings> enabled,
                      Map<String, String> placeholders) {
        Config config = plugin.getConfiguration();
        if (config == null) {
            return;
        }
        DiscordSettings settings = config.getDiscord();
        if (!settings.isEnabled() || !enabled.test(settings)) {
            return;
        }
        String webhookUrl = settings.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        String override = settings.message(event);
        String template = override == null || override.isBlank() ? DEFAULTS.getOrDefault(event, event) : override;
        String content = apply(template, placeholders);
        String serverName = settings.getServerName();
        if (serverName != null && !serverName.isBlank() && template.equals(DEFAULTS.get(event))) {
            content = "[" + serverName + "] " + content;
        }
        String payload = payload(settings, content);
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        reportFailure(event, error);
                        return;
                    }
                    if (response.statusCode() >= 300) {
                        reportFailure(event, new IllegalStateException("HTTP " + response.statusCode()));
                    }
                });
    }

    /** Logs a failure once per event per hour instead of spamming the console on every purchase. */
    private void reportFailure(String event, Throwable error) {
        long now = System.currentTimeMillis();
        Long last = lastFailure.get(event);
        if (last != null && now - last < TimeUnit.HOURS.toMillis(1)) {
            return;
        }
        lastFailure.put(event, now);
        plugin.getLogger().warning("Could not send the Discord notification '" + event + "': " + error.getMessage());
    }

    private static String apply(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private static String payload(DiscordSettings settings, String content) {
        StringBuilder builder = new StringBuilder("{\"content\":\"").append(escape(content)).append('"');
        builder.append(",\"username\":\"").append(escape(settings.getUsername())).append('"');
        if (!settings.getAvatarUrl().isBlank()) {
            builder.append(",\"avatar_url\":\"").append(escape(settings.getAvatarUrl())).append('"');
        }
        return builder.append('}').toString();
    }

    private static String escape(String input) {
        StringBuilder builder = new StringBuilder(input.length() + 8);
        for (char character : input.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String format(double amount) {
        if (amount == Math.rint(amount) && !Double.isInfinite(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }
}
