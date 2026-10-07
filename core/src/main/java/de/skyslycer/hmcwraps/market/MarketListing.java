package de.skyslycer.hmcwraps.market;

import java.util.UUID;

public record MarketListing(UUID id, UUID sellerId, String sellerName, String skinId,
                            double amount, String currency, String provider, long createdAt) { }
