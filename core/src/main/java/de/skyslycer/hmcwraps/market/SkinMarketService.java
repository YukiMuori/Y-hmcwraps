package de.skyslycer.hmcwraps.market;

import de.skyslycer.hmcwraps.HMCWrapsPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Persistent escrow market. Ownership is removed from the seller while a listing is active. */
public final class SkinMarketService {
    private static final UUID ESCROW = UUID.nameUUIDFromBytes("HMCWraps:market-escrow".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final HMCWrapsPlugin plugin;
    private final MarketRepository repository;
    private final java.util.Set<UUID> processing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SkinMarketService(HMCWrapsPlugin plugin) {
        this.plugin = plugin;
        this.repository = new MarketRepository(plugin.getDatabase());
    }

    public CompletionStage<Result> sell(UUID seller, String sellerName, String skinId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || plugin.getItemSkinManager().getSkin(skinId).isEmpty()) return done(Result.INVALID);
        String currency = plugin.getConfiguration().getEconomy().getDefaultCurrency();
        String provider = plugin.getEconomyService().getConfiguredProvider();
        MarketListing listing = new MarketListing(UUID.randomUUID(), seller, sellerName, skinId, amount, currency, provider, System.currentTimeMillis());
        return plugin.getItemSkinManager().transferSkin(seller, ESCROW, skinId).thenCompose(moved -> {
            if (!moved) return done(Result.NOT_OWNED);
            return repository.create(listing).handle((created, error) -> error == null && Boolean.TRUE.equals(created))
                    .thenCompose(created -> created ? done(Result.SUCCESS) : plugin.getItemSkinManager().transferSkin(ESCROW, seller, skinId).thenApply(ignored -> Result.FAILED));
        });
    }

    public CompletionStage<Result> buy(UUID buyer, UUID listingId) {
        if (!processing.add(listingId)) return done(Result.BUSY);
        return repository.find(listingId).thenCompose(listing -> {
            if (listing == null) return done(Result.NOT_FOUND);
            if (listing.sellerId().equals(buyer)) return done(Result.OWN_LISTING);
            return plugin.getSkinOwnership().hasSkin(buyer, listing.skinId()).thenCompose(owned -> {
                if (owned) return done(Result.ALREADY_OWNED);
                return plugin.getEconomyService().withdraw(buyer, listing.currency(), listing.amount(), listing.provider()).thenCompose(charged -> {
                    if (!charged) return done(Result.INSUFFICIENT);
                    return repository.complete(listing.id(), buyer).thenCompose(claimed -> {
                        if (!claimed) return refund(buyer, listing, Result.NOT_FOUND);
                        return plugin.getItemSkinManager().transferSkin(ESCROW, buyer, listing.skinId()).thenCompose(moved -> {
                            if (!moved) return refund(buyer, listing, Result.FAILED);
                            return plugin.getEconomyService().deposit(listing.sellerId(), listing.currency(), listing.amount(), listing.provider())
                                    .thenApply(paid -> paid ? Result.SUCCESS : Result.SELLER_PAYMENT_PENDING);
                        });
                    });
                });
            });
        }).exceptionally(error -> Result.FAILED).whenComplete((result, error) -> processing.remove(listingId));
    }

    public CompletionStage<Result> cancel(UUID seller, UUID listingId) {
        if (!processing.add(listingId)) return done(Result.BUSY);
        return repository.find(listingId).thenCompose(listing -> {
            if (listing == null) return done(Result.NOT_FOUND);
            if (!listing.sellerId().equals(seller)) return done(Result.NOT_OWNER);
            return repository.cancel(listingId, seller).thenCompose(cancelled -> !cancelled ? done(Result.NOT_FOUND)
                    : plugin.getItemSkinManager().transferSkin(ESCROW, seller, listing.skinId()).thenApply(moved -> moved ? Result.SUCCESS : Result.FAILED));
        }).exceptionally(error -> Result.FAILED).whenComplete((result, error) -> processing.remove(listingId));
    }

    public CompletionStage<List<MarketListing>> listings() { return repository.active(); }

    private CompletionStage<Result> refund(UUID buyer, MarketListing listing, Result result) {
        return plugin.getEconomyService().deposit(buyer, listing.currency(), listing.amount(), listing.provider()).thenApply(ignored -> result);
    }
    private static CompletionStage<Result> done(Result result) { return CompletableFuture.completedFuture(result); }

    public enum Result { SUCCESS, INVALID, NOT_OWNED, NOT_FOUND, OWN_LISTING, NOT_OWNER, ALREADY_OWNED, INSUFFICIENT, BUSY, SELLER_PAYMENT_PENDING, FAILED }
}
