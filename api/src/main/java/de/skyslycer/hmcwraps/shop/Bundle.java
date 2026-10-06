package de.skyslycer.hmcwraps.shop;

import de.skyslycer.hmcwraps.skin.SkinPrice;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable bundle of skins sold together. Bundles are first-class shop citizens: they can be
 * featured, placed in events, discounted by coupons and partially owned without duplicating ownership.
 */
public final class Bundle {
    private final String id;
    private final String displayName;
    private final List<String> skinIds;
    private final SkinPrice price;
    private final double discount;
    private final BundlePurchaseMode purchaseMode;
    private final String permission;
    private final ItemStack icon;

    public Bundle(@NotNull String id, @NotNull String displayName, @NotNull List<String> skinIds, @NotNull SkinPrice price,
                  double discount, @NotNull BundlePurchaseMode purchaseMode, @Nullable String permission,
                  @Nullable ItemStack icon) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT).trim();
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("Bundle id must not be blank");
        }
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.skinIds = skinIds.stream().filter(skinId -> skinId != null && !skinId.isBlank())
                .map(skinId -> skinId.toLowerCase(Locale.ROOT).trim()).distinct().toList();
        if (this.skinIds.isEmpty()) {
            throw new IllegalArgumentException("Bundle '" + this.id + "' must contain at least one skin");
        }
        this.price = Objects.requireNonNull(price, "price");
        if (!Double.isFinite(discount) || discount < 0 || discount > 100) {
            throw new IllegalArgumentException("Bundle discount must be between 0 and 100");
        }
        this.discount = discount;
        this.purchaseMode = Objects.requireNonNull(purchaseMode, "purchaseMode");
        this.permission = permission == null || permission.isBlank() ? null : permission;
        this.icon = icon == null ? null : icon.clone();
    }

    public @NotNull String id() {
        return id;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    public @NotNull List<String> skinIds() {
        return skinIds;
    }

    public @NotNull SkinPrice price() {
        return price;
    }

    public double discount() {
        return discount;
    }

    public @NotNull BundlePurchaseMode purchaseMode() {
        return purchaseMode;
    }

    public @Nullable String permission() {
        return permission;
    }

    public @Nullable ItemStack icon() {
        return icon == null ? null : icon.clone();
    }

    /** Whether this bundle may be bought in full even when some skins are already owned. */
    public boolean allowsFullPurchase() {
        return purchaseMode != BundlePurchaseMode.MISSING_ONLY;
    }

    /** Whether the missing-only (dynamic price) purchase path is enabled. */
    public boolean allowsMissingOnlyPurchase() {
        return purchaseMode != BundlePurchaseMode.FULL;
    }
}
