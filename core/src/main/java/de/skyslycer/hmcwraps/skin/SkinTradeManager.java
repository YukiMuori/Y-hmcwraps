package de.skyslycer.hmcwraps.skin;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;
import de.skyslycer.hmcwraps.util.StringUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Online, two-party-confirmed trades backed by an atomic storage ownership transfer. */
public final class SkinTradeManager implements Listener {
    private static final long TRADE_TIMEOUT_TICKS = 20L * 60L * 5L;

    private final HMCWrapsPlugin plugin;
    private final ItemSkinManagerImpl skinManager;
    private final Map<UUID, PendingTrade> activeTrades = new ConcurrentHashMap<>();

    public SkinTradeManager(HMCWrapsPlugin plugin, ItemSkinManagerImpl skinManager) {
        this.plugin = plugin;
        this.skinManager = skinManager;
    }

    public void offer(Player sender, Player recipient, ItemSkin skin) {
        if (sender.getUniqueId().equals(recipient.getUniqueId())) {
            send(sender, "messages.trade-self");
            return;
        }
        if (skinManager.getSkin(skin.id()).isEmpty()) {
            send(sender, "messages.unknown-skin", Placeholder.unparsed("skin", skin.id()));
            return;
        }
        if (!skinManager.getStorageProvider().supportsSkinTransfers()) {
            send(sender, "messages.trade-storage-unsupported");
            return;
        }

        PendingTrade trade = new PendingTrade(sender.getUniqueId(), sender.getName(), recipient.getUniqueId(),
                recipient.getName(), skin.id(), skin.displayName(), System.currentTimeMillis());
        if (activeTrades.putIfAbsent(trade.senderId, trade) != null) {
            send(sender, "messages.trade-busy");
            return;
        }
        if (activeTrades.putIfAbsent(trade.recipientId, trade) != null) {
            activeTrades.remove(trade.senderId, trade);
            send(sender, "messages.trade-busy");
            return;
        }

        plugin.getFoliaLib().getScheduler().runAtEntityLater(sender, () -> expire(trade), TRADE_TIMEOUT_TICKS);
        CompletionStage<OwnershipSnapshot> state = skinManager.ownedSkinIds(trade.senderId)
                .thenCombine(skinManager.ownedSkinIds(trade.recipientId), OwnershipSnapshot::new);
        state.whenComplete((snapshot, error) -> plugin.getFoliaLib().getScheduler().runAtEntity(sender, ignored -> {
            if (activeTrades.get(trade.senderId) != trade || trade.state != State.CHECKING) return;
            if (!sender.isOnline() || Bukkit.getPlayer(trade.recipientId) == null) {
                cancelInternal(trade, "messages.trade-player-offline", true);
                return;
            }
            if (error != null || snapshot == null) {
                cancelInternal(trade, "messages.trade-storage-error", true);
                return;
            }
            Set<String> senderOwned = snapshot.senderOwned() == null ? Set.of() : snapshot.senderOwned();
            Set<String> recipientOwned = snapshot.recipientOwned() == null ? Set.of() : snapshot.recipientOwned();
            if (!senderOwned.contains(normalize(trade.skinId))) {
                cancelInternal(trade, "messages.trade-not-owned", true);
                return;
            }
            if (recipientOwned.contains(normalize(trade.skinId))) {
                cancelInternal(trade, "messages.trade-already-owned", true);
                return;
            }
            trade.state = State.OFFERED;
            send(sender, "messages.trade-offer-sent", tradePlaceholders(trade));
            sendToPlayer(trade.recipientId, "messages.trade-offer-received", tradePlaceholders(trade));
        }));
    }

    public void confirm(Player player) {
        PendingTrade trade = activeTrades.get(player.getUniqueId());
        if (trade == null) {
            send(player, "messages.trade-none");
            return;
        }
        if (System.currentTimeMillis() - trade.createdAt > TRADE_TIMEOUT_TICKS * 50L) {
            cancelInternal(trade, "messages.trade-expired", true);
            return;
        }

        boolean execute = false;
        synchronized (trade) {
            if (trade.state == State.CHECKING) {
                send(player, "messages.trade-still-checking");
                return;
            }
            if (trade.state != State.OFFERED) {
                send(player, "messages.trade-not-confirmable");
                return;
            }
            if (player.getUniqueId().equals(trade.senderId)) trade.senderConfirmed = true;
            else if (player.getUniqueId().equals(trade.recipientId)) trade.recipientConfirmed = true;
            else return;

            if (trade.senderConfirmed && trade.recipientConfirmed) {
                trade.state = State.PROCESSING;
                execute = true;
            }
        }

        if (execute) {
            sendToPlayer(trade.senderId, "messages.trade-processing", tradePlaceholders(trade));
            sendToPlayer(trade.recipientId, "messages.trade-processing", tradePlaceholders(trade));
            skinManager.transferSkin(trade.senderId, trade.recipientId, trade.skinId).whenComplete((success, error) -> {
                if (error != null) plugin.getLogger().warning("Skin trade failed for " + trade.skinId + ": " + error.getMessage());
                finish(trade, error == null && Boolean.TRUE.equals(success));
            });
        } else {
            send(player, "messages.trade-confirmed", tradePlaceholders(trade));
            UUID otherId = player.getUniqueId().equals(trade.senderId) ? trade.recipientId : trade.senderId;
            sendToPlayer(otherId, "messages.trade-other-confirmed", tradePlaceholders(trade));
        }
    }

    public void cancel(Player player) {
        PendingTrade trade = activeTrades.get(player.getUniqueId());
        if (trade == null) {
            send(player, "messages.trade-none");
            return;
        }
        synchronized (trade) {
            if (trade.state == State.PROCESSING) {
                send(player, "messages.trade-not-cancelable");
                return;
            }
            trade.state = State.CANCELLED;
        }
        remove(trade);
        send(player, "messages.trade-cancelled", tradePlaceholders(trade));
        UUID otherId = player.getUniqueId().equals(trade.senderId) ? trade.recipientId : trade.senderId;
        sendToPlayer(otherId, "messages.trade-cancelled-other", tradePlaceholders(trade));
    }

    public void cancelAll() {
        activeTrades.values().stream().distinct().toList().forEach(trade -> {
            synchronized (trade) {
                if (trade.state != State.PROCESSING) trade.state = State.CANCELLED;
            }
            remove(trade);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PendingTrade trade = activeTrades.get(event.getPlayer().getUniqueId());
        if (trade == null) return;
        synchronized (trade) {
            if (trade.state == State.PROCESSING) return;
            trade.state = State.CANCELLED;
        }
        remove(trade);
        UUID otherId = event.getPlayer().getUniqueId().equals(trade.senderId) ? trade.recipientId : trade.senderId;
        sendToPlayer(otherId, "messages.trade-player-offline", tradePlaceholders(trade));
    }

    private void expire(PendingTrade trade) {
        synchronized (trade) {
            if (trade.state == State.CANCELLED || trade.state == State.COMPLETE || trade.state == State.PROCESSING) return;
            if (System.currentTimeMillis() - trade.createdAt <= TRADE_TIMEOUT_TICKS * 50L) return;
            trade.state = State.CANCELLED;
        }
        remove(trade);
        sendToPlayer(trade.senderId, "messages.trade-expired", tradePlaceholders(trade));
        sendToPlayer(trade.recipientId, "messages.trade-expired", tradePlaceholders(trade));
    }

    private void finish(PendingTrade trade, boolean success) {
        synchronized (trade) {
            if (trade.state != State.PROCESSING) return;
            trade.state = success ? State.COMPLETE : State.CANCELLED;
        }
        remove(trade);
        String key = success ? "messages.trade-success" : "messages.trade-failed";
        sendToPlayer(trade.senderId, key, tradePlaceholders(trade));
        sendToPlayer(trade.recipientId, key, tradePlaceholders(trade));
    }

    private void cancelInternal(PendingTrade trade, String messageKey, boolean notifyBoth) {
        synchronized (trade) {
            if (trade.state == State.CANCELLED || trade.state == State.COMPLETE || trade.state == State.PROCESSING) return;
            trade.state = State.CANCELLED;
        }
        remove(trade);
        if (notifyBoth) {
            sendToPlayer(trade.senderId, messageKey, tradePlaceholders(trade));
            sendToPlayer(trade.recipientId, messageKey, tradePlaceholders(trade));
        }
    }

    private void remove(PendingTrade trade) {
        activeTrades.remove(trade.senderId, trade);
        activeTrades.remove(trade.recipientId, trade);
    }

    private TagResolver[] tradePlaceholders(PendingTrade trade) {
        return new TagResolver[]{
                Placeholder.unparsed("player", trade.senderName),
                Placeholder.unparsed("recipient", trade.recipientName),
                Placeholder.unparsed("skin_id", trade.skinId),
                Placeholder.component("skin", plugin.getLanguageManager().parse(null, trade.skinName))
        };
    }

    private void sendToPlayer(UUID playerId, String key, TagResolver... resolvers) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;
        plugin.getFoliaLib().getScheduler().runAtEntity(player, ignored -> {
            if (player.isOnline()) send(player, key, resolvers);
        });
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        String message = plugin.getLanguageManager().get(player, key);
        StringUtil.sendComponent(player, plugin.getLanguageManager().parse(player, message, resolvers));
    }

    private static String normalize(String id) { return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT).trim(); }

    private enum State { CHECKING, OFFERED, PROCESSING, COMPLETE, CANCELLED }
    private record OwnershipSnapshot(Set<String> senderOwned, Set<String> recipientOwned) { }

    private static final class PendingTrade {
        private final UUID senderId;
        private final String senderName;
        private final UUID recipientId;
        private final String recipientName;
        private final String skinId;
        private final String skinName;
        private final long createdAt;
        private volatile State state = State.CHECKING;
        private boolean senderConfirmed;
        private boolean recipientConfirmed;

        private PendingTrade(UUID senderId, String senderName, UUID recipientId, String recipientName,
                             String skinId, String skinName, long createdAt) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.recipientId = recipientId;
            this.recipientName = recipientName;
            this.skinId = skinId;
            this.skinName = skinName;
            this.createdAt = createdAt;
        }
    }
}
