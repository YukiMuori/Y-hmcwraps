package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.compat.Scheduler;
import de.skyslycer.hmcwraps.economy.EconomyService;
import de.skyslycer.hmcwraps.economy.EntityEconomyDispatcher;
import de.skyslycer.hmcwraps.economy.PurchaseTransactionService;
import de.skyslycer.hmcwraps.events.SkinGiftEvent;
import de.skyslycer.hmcwraps.repository.GiftRepository;
import de.skyslycer.hmcwraps.serialization.shop.GiftSettings;
import de.skyslycer.hmcwraps.skin.ItemSkin;
import de.skyslycer.hmcwraps.skin.SkinCatalog;
import de.skyslycer.hmcwraps.skin.SkinOwnershipService;
import de.skyslycer.hmcwraps.skin.SkinPrice;
import de.skyslycer.hmcwraps.util.AsyncUtil;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Gifting.
 *
 * <p>A gift is a paid transaction whose ownership goes to another player: the sender pays, the recipient
 * receives. It therefore uses the same {@link PurchaseTransactionService} as a normal purchase, with the
 * recipient as the ownership owner - so a crash, a double click or a failed grant can never duplicate
 * ownership or lose money. Recipients who were offline are notified the next time they join.</p>
 */
public final class GiftServiceImpl implements GiftService {

    private final HMCWrapsPlugin plugin;
    private final ShopRegistry registry;
    private final SkinCatalog catalog;
    private final SkinOwnershipService ownership;
    private final EconomyService economy;
    private final PurchaseTransactionService transactions;
    private final GiftRepository gifts;
    private final Scheduler scheduler;
    private final Supplier<GiftSettings> settings;
    private final Consumer<String> logger;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public GiftServiceImpl(@NotNull HMCWrapsPlugin plugin, @NotNull ShopRegistry registry, @NotNull SkinCatalog catalog,
                           @NotNull SkinOwnershipService ownership, @NotNull EconomyService economy,
                           @NotNull PurchaseTransactionService transactions, @NotNull GiftRepository gifts,
                           @NotNull Scheduler scheduler, @NotNull Supplier<GiftSettings> settings,
                           @NotNull Consumer<String> logger) {
        this.plugin = plugin;
        this.registry = registry;
        this.catalog = catalog;
        this.ownership = ownership;
        this.economy = economy;
        this.transactions = transactions;
        this.gifts = gifts;
        this.scheduler = scheduler;
        this.settings = settings;
        this.logger = logger;
    }

    private GiftSettings currentSettings() {
        GiftSettings current = settings.get();
        return current == null ? new GiftSettings() : current;
    }

    @Override
    public boolean isEnabled() {
        return currentSettings().isEnabled();
    }

    @Override
    public boolean allowsOffline() {
        return currentSettings().isAllowOffline();
    }

    @Override
    public long getCooldownRemaining(@NotNull UUID senderId) {
        Long until = cooldowns.get(senderId);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(senderId);
            return 0;
        }
        return remaining;
    }

    @Override
    public @NotNull CompletionStage<GiftResult> giftSkin(@NotNull Player sender, @NotNull UUID recipientId,
                                                         @NotNull String recipientName, @NotNull String skinId,
                                                         @Nullable String message) {
        ItemSkin skin = catalog.skinMap().get(normalize(skinId));
        if (skin == null || skin.price() == null || skin.price().amount() <= 0) {
            return AsyncUtil.completed(failure(TransactionStatus.UNAVAILABLE, "unknown or unpriced skin"));
        }
        return gift(sender, recipientId, recipientName, PurchaseKind.GIFT_SKIN, skin.id(), List.of(skin.id()),
                skin.price(), message);
    }

    @Override
    public @NotNull CompletionStage<GiftResult> giftBundle(@NotNull Player sender, @NotNull UUID recipientId,
                                                          @NotNull String recipientName, @NotNull String bundleId,
                                                          @Nullable String message) {
        Bundle bundle = registry.bundle(bundleId).orElse(null);
        if (bundle == null) {
            return AsyncUtil.completed(failure(TransactionStatus.UNAVAILABLE, "unknown bundle"));
        }
        return gift(sender, recipientId, recipientName, PurchaseKind.GIFT_BUNDLE, bundle.id(), bundle.skinIds(),
                bundle.price(), message);
    }

    private CompletionStage<GiftResult> gift(Player sender, UUID recipientId, String recipientName, PurchaseKind kind,
                                             String targetId, List<String> skinIds, SkinPrice price,
                                             @Nullable String message) {
        if (!isEnabled()) {
            return AsyncUtil.completed(failure(TransactionStatus.UNAVAILABLE, "gifting disabled"));
        }
        if (sender.getUniqueId().equals(recipientId)) {
            return AsyncUtil.completed(failure(TransactionStatus.INVALID, "self"));
        }
        if (!allowsOffline() && Bukkit.getPlayer(recipientId) == null) {
            return AsyncUtil.completed(failure(TransactionStatus.UNAVAILABLE, "recipient-offline"));
        }
        long cooldown = getCooldownRemaining(sender.getUniqueId());
        if (cooldown > 0) {
            return AsyncUtil.completed(failure(TransactionStatus.BUSY, String.valueOf(cooldown)));
        }
        var provider = economy.providerFor(price.provider(), price.currency()).orElse(null);
        if (provider == null) {
            return AsyncUtil.completed(failure(TransactionStatus.PROVIDER_UNAVAILABLE, price.provider()));
        }
        String sanitized = sanitize(message);
        return recipientOwned(recipientId).thenCompose(owned -> {
            boolean recipientMissingSomething = skinIds.stream().map(GiftServiceImpl::normalize)
                    .anyMatch(skinId -> !owned.contains(skinId));
            if (!recipientMissingSomething) {
                return AsyncUtil.completed(failure(TransactionStatus.ALREADY_OWNED, targetId));
            }
            return callGiftEvent(sender, recipientId, recipientName, kind, targetId, price, sanitized)
                    .thenCompose(allowed -> {
                        if (!allowed) {
                            return AsyncUtil.completed(failure(TransactionStatus.CANCELLED, "event"));
                        }
                        GiftDraft draft = new GiftDraft(UUID.randomUUID().toString(), sender.getUniqueId(),
                                sender.getName(), recipientId, recipientName, kind, targetId, price, sanitized);
                        return writeGift(draft).thenCompose(written -> {
                            if (!written) {
                                return AsyncUtil.completed(failure(TransactionStatus.STORAGE_FAILED, "gift-record"));
                            }
                            EntityEconomyDispatcher dispatcher = new EntityEconomyDispatcher(scheduler, sender);
                            var request = new PurchaseTransactionService.TransactionRequest(sender.getUniqueId(),
                                    recipientId, kind, targetId, skinIds, ignored -> price.amount(), 0, price.amount(),
                                    null, null, price, provider, recipientId, "gift",
                                    "gift " + targetId + " to " + recipientName, draft.giftId(), dispatcher);
                            return transactions.execute(request, dispatcher).thenCompose(result -> finish(draft, result));
                        });
                    });
        });
    }

    private CompletionStage<GiftResult> finish(GiftDraft draft, TransactionResult result) {
        boolean delivered = result.status() == TransactionStatus.SUCCESS;
        int cooldownSeconds = currentSettings().getCooldownSeconds();
        if (delivered && cooldownSeconds > 0) {
            cooldowns.put(draft.senderId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        return AsyncUtil.safe(() -> gifts.updateStatus(draft.giftId(), delivered ? GiftStatus.DELIVERED : GiftStatus.FAILED, false))
                .exceptionally(error -> false)
                .thenApply(updated -> {
                    if (delivered) {
                        ownership.invalidate(draft.recipientId());
                        plugin.getProfileService().invalidate(draft.recipientId());
                        notifyRecipient(draft);
                        plugin.getDiscordWebhook().gift(draft.senderName(), draft.recipientName(), draft.targetId(),
                                draft.price().amount());
                    }
                    return new GiftResult(result.status(), draft.giftId(), result.transactionId(), result.charged(),
                            result.detail());
                });
    }

    private CompletionStage<Boolean> writeGift(GiftDraft draft) {
        GiftRecord record = new GiftRecord(draft.giftId(), draft.senderId(), draft.senderName(), draft.recipientId(),
                draft.recipientName(), draft.kind(), draft.targetId(), draft.price().amount(),
                draft.price().currency(), draft.price().provider(), draft.message(), System.currentTimeMillis(), null, false);
        return AsyncUtil.safe(() -> gifts.insert(record)).exceptionally(error -> {
            logger.accept("Could not store the gift record " + draft.giftId() + ": " + AsyncUtil.describe(error));
            return false;
        });
    }

    private void notifyRecipient(GiftDraft draft) {
        scheduler.runGlobal(() -> {
            Player recipient = Bukkit.getPlayer(draft.recipientId());
            if (recipient == null || !recipient.isOnline()) {
                return;
            }
            scheduler.runOnEntity(recipient, () -> {
                if (!currentSettings().isNotifyOnJoin() || !recipient.isOnline()) {
                    return;
                }
                send(recipient, "shop.gift.received", TagResolver.resolver(
                        Placeholder.unparsed("player", draft.senderName()),
                        Placeholder.unparsed("target", draft.targetId()),
                        Placeholder.unparsed("message", draft.message() == null ? "" : draft.message())));
                AsyncUtil.safe(() -> gifts.markNotified(draft.recipientId(), List.of(draft.giftId())));
            });
        });
    }

    @Override
    public @NotNull CompletionStage<List<GiftRecord>> pendingNotifications(@NotNull UUID recipientId) {
        return AsyncUtil.safe(() -> gifts.pendingNotifications(recipientId))
                .thenApply(records -> records == null ? List.<GiftRecord>of() : records)
                .exceptionally(error -> List.<GiftRecord>of());
    }

    @Override
    public @NotNull CompletionStage<Boolean> markNotified(@NotNull UUID recipientId, @NotNull List<String> giftIds) {
        return AsyncUtil.safe(() -> gifts.markNotified(recipientId, giftIds)).exceptionally(error -> false);
    }

    @Override
    public void notifyPending(@NotNull Player player) {
        if (!currentSettings().isNotifyOnJoin()) {
            return;
        }
        pendingNotifications(player.getUniqueId()).thenAccept(records -> {
            if (records.isEmpty()) {
                return;
            }
            scheduler.runOnEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                List<String> notified = new ArrayList<>(records.size());
                for (GiftRecord record : records) {
                    send(player, "shop.gift.pending", TagResolver.resolver(
                            Placeholder.unparsed("player", record.senderName() == null ? "?" : record.senderName()),
                            Placeholder.unparsed("target", record.targetId()),
                            Placeholder.unparsed("message", record.message() == null ? "" : record.message())));
                    notified.add(record.giftId());
                }
                AsyncUtil.safe(() -> gifts.markNotified(player.getUniqueId(), notified));
            });
        });
    }

    private CompletionStage<Boolean> callGiftEvent(Player sender, UUID recipientId, String recipientName,
                                                   PurchaseKind kind, String targetId, SkinPrice price,
                                                   @Nullable String message) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable call = () -> {
            try {
                SkinGiftEvent event = new SkinGiftEvent(sender, recipientId, recipientName, kind, targetId,
                        price.amount(), price.currency(), message);
                Bukkit.getPluginManager().callEvent(event);
                result.complete(!event.isCancelled());
            } catch (Throwable throwable) {
                plugin.getLogger().warning("A gift event handler failed: " + AsyncUtil.describe(throwable));
                result.complete(true);
            }
        };
        if (!scheduler.isFolia() && Bukkit.isPrimaryThread()) {
            call.run();
        } else {
            scheduler.runOnEntity(sender, call);
        }
        return result;
    }

    private CompletionStage<Set<String>> recipientOwned(UUID recipientId) {
        return AsyncUtil.safe(() -> ownership.getOwnedSkinIds(recipientId))
                .thenApply(owned -> owned == null ? Set.<String>of() : owned)
                .exceptionally(error -> Set.<String>of());
    }

    /** The newest gifts a player received, for the profile screen. */
    public @NotNull CompletionStage<List<GiftRecord>> history(@NotNull UUID recipientId, int limit) {
        return AsyncUtil.safe(() -> gifts.history(recipientId, limit))
                .thenApply(records -> records == null ? List.<GiftRecord>of() : records)
                .exceptionally(error -> List.<GiftRecord>of());
    }

    /** Gifts a player still has to be notified about, oldest first. */
    public @NotNull CompletionStage<List<GiftRecord>> openNotifications(@NotNull UUID recipientId) {
        return pendingNotifications(recipientId).thenApply(records -> records.stream()
                .sorted(Comparator.comparingLong(GiftRecord::createdAt)).toList());
    }

    private GiftResult failure(TransactionStatus status, String detail) {
        return new GiftResult(status, "", "", 0, detail);
    }

    private @Nullable String sanitize(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        int max = currentSettings().getMessageMaxLength();
        if (max > 0 && trimmed.length() > max) {
            return trimmed.substring(0, max);
        }
        return trimmed;
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        String value = plugin.getLanguageManager().get(player, key);
        StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, value, resolvers));
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    private record GiftDraft(String giftId, UUID senderId, String senderName, UUID recipientId, String recipientName,
                             PurchaseKind kind, String targetId, SkinPrice price, @Nullable String message) {
    }
}
