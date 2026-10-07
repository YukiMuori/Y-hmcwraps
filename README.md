# Y-HMCWraps 2.1

Y-HMCWraps is a Paper/Spigot item-cosmetics plugin. It keeps the established **wrap** system and adds a separate, catalog-driven **item-skin** system with rarities, categories, translations, ownership, optional economy providers, previews and a configurable inventory UI. On top of it, 2.1 ships an integrated **skin shop** with daily rotations, featured entries, timed event shops, bundles, coupons, gifting, collection rewards and player profiles.

The new catalog uses the existing wrap engine as its application adapter. This keeps previously applied wraps, legacy configuration, permissions, commands, preview behavior and item PDC data readable instead of replacing them with a new item format.

## Requirements

- Java 21 or newer.
- A Paper server is recommended. The core compiles against Spigot API 1.21.4; newer item components are used when the running server supports them.
- ExcellentEconomy, Vault, Nexo, ItemsAdder, Oraxen, CraftEngine and MythicCrucible are optional. The plugin can start without them.

## Install

1. Put the shaded `HMCWraps-2.1.1.jar` in `plugins/` and restart the server.
2. The plugin creates/updates its legacy files and copies the v2 examples into `plugins/HMCWraps/` on first start.
3. Configure skin files in `plugins/HMCWraps/skins/`, themed series in `skin-collections.yml`, all plugin text in `lang/*.yml`, rarity priorities in `rarities.yml`, categories in `categories.yml`, and the browser in `itemskin-gui.yml`.
4. Set `language.default: it` in `config.yml` for Italian, or enable `language.player-locale` to use a matching player's client locale.
5. Restart or use `/wraps reload` after changing configuration.

The repository's build artifact is produced by `./gradlew clean build`; the distributable jar is `build/libs/HMCWraps-2.1.1.jar` after a successful build.

## Player commands

| Command | Description |
|---|---|
| `/itemskin` or `/itemskin open` | Open the v2 skin browser for the item in your main hand. It lists every compatible skin, including skins the player does not own. |
| `/itemskin preview <skin-id>` | Preview a configured skin on the held item. Preview does not require ownership. |
| `/itemskin remove` | Remove a v2 skin from the held item. It does not unwrap a legacy wrap. |
| `/itemskin trade <player> <skin-id>` | Offer an owned skin to an online player. Both players must confirm with `/itemskin trade confirm`; either can cancel with `/itemskin trade cancel`. Offers expire after five minutes. |
| `/itemskin trade confirm` | Confirm your side of the active trade. Ownership transfers only after both confirmations. |
| `/itemskin trade cancel` | Cancel an active trade before its atomic transfer begins. |
| `/itemskin shop` | Open the shop: featured entries, the daily rotation, bundles, event shops, coupons, gifts and your profile. |
| `/itemskin bundles`, `/itemskin events` | Jump straight to the bundle list or the event shops. |
| `/itemskin coupons` | List the configured coupons and select the one used at checkout (right-click clears it). |
| `/itemskin profile` | Show ownership, collection, purchase, gift and coupon statistics and recent purchases. |
| `/itemskin gifts` | Show gifts that were paid for while you were offline. |
| `/itemskin gift <player> <skin-id>` | Gift a skin to an online or (if enabled) offline player. You pay the price; the recipient receives the ownership. |
| `/itemskin coupon <code>` | Apply a coupon code to your next purchase. |
| `/itemskin editor` | *(Permission `hmcwraps.commands.itemskin.editor`)* edit `shops.yml`/`coupons.yml` in game. |
| `/itemskin give <skin-id> <player>` | *(Permission `hmcwraps.commands.itemskin.give`)* permanently grant a skin for giveaways without charging the player. |
| `/itemskin reload` | *(Permission `hmcwraps.commands.reload`)* safely close plugin GUIs and reload configuration, skins, language, GUI and shop files. This remains available in ItemSkin-only mode. |
| `/wraps` | Legacy wrap inventory; registered only when `legacy-wraps.enabled: true`. |

The bundled configuration runs in ItemSkin-only mode. Set `legacy-wraps.enabled: true` and restart the server only if you need the classic wrap files, collections, `/wraps` commands, GUI, physical wrappers and legacy actions. When disabled, the internal cosmetic payload engine remains active exclusively so `/itemskin` can apply and remove skins safely; legacy files are not loaded or copied into the data folder.

For floating Item Display previews, `preview.item-display-transform` in `config.yml` controls local translation and separate XYZ rotations for swords and other items. Rotation uses degrees in X → Y → Z order: X tilts toward/away from the viewer, Y changes which face is visible, and Z rotates within the screen plane. The default sword `0/0/-90` is upright; useful alternatives are `0/180/-90` for the opposite face and `0/90/-90` for an edge/side view. Because resource-pack models can define their own FIXED transform, these values can be changed and applied with `/itemskin reload` without restarting.

In the browser, left click applies a free/owned skin, right click previews it, and Shift-click buys a configured paid skin. The selected item in slot 4 is the compact browser control: left click changes sorting and right click changes filters. Its name and lore are configurable with `item-name` and `item-lore`. The Shop control opens the integrated `shops.yml`/`coupons.yml` menus, while Unskin restores the selected item. Search opens chat input (`clear` resets it; `cancel` returns without changing the search), and the collection button cycles through themed series. Previous and next arrows appear only when their destination page exists. Every control and category has independent `enabled`, `slot`, and complete `item` settings in `itemskin-gui.yml`.

### Themed collections and trades

Define themed series in `skin-collections.yml`, with a translated `display-name-key`, optional icon/priority and category list. Assign a skin with `collection: angelico`; the collection button narrows compatible skins to that series, while the configured category buttons act as its subcategories (for example swords, tools and armor). A skin without `collection` remains in the all-collections view. Ownership lives in `skins.db` (SQLite by default). Player trades require both players to confirm, check ownership again in storage and use a single SQLite transaction to move—not duplicate—the skin. Trades are in-memory, online-only offers and are cancelled on disconnect or after five minutes.

Administrators can run `/wraps validate` for a read-only check of YAML files, skin/wrap IDs and references, translations, materials, economy settings, GUI slots and the shop definitions (`shops.yml`, `coupons.yml`): bundle contents and discounts, purchase modes, the daily pool and reset time, event time windows, featured/event entries, and coupon types, values, limits, expiries and every skin/bundle/category reference. It does not reload or edit files.

## Skin shop

The shop is defined by two files that are copied into `plugins/HMCWraps/` on first start: `shops.yml` (daily rotation, featured selection, bundles, event shops) and `coupons.yml` (discount codes). `config.yml` carries the `economy`, `shop`, `gifts`, `discord` and `debug` sections. Every price is recalculated on the server before anything is charged, and GUI state is never trusted.

```yaml
# shops.yml (excerpt)
daily-shop:
  enabled: true
  slots: 6
  reset-time: '00:00'
  zone: UTC
  pool: [skin:ruby_sword]      # or plain ids; 'bundle:<id>' is allowed too
  price: { provider: auto, currency: coins, amount: 5000 }
  discount: 10
featured:
  automatic: false
  entries: [skin:ruby_sword, bundle:starter]
bundles:
  starter:
    name: '<lang:skins.starter.name>'
    skins: [ruby_sword, ruby_pickaxe]
    price: { provider: auto, currency: coins, amount: 12000 }
    discount: 15
    purchase-mode: both          # full, missing-only or both
events:
  halloween_2026:
    name: 'Halloween 2026'
    start: '2026-10-24T00:00:00Z'
    end: '2026-11-03T00:00:00Z'
    entries: [skin:ruby_sword]
    discount: 20
```

```yaml
# coupons.yml (excerpt)
coupons:
  WELCOME10:
    type: percentage             # percentage or fixed
    value: 10
    max-uses: 500
    max-uses-per-player: 1
    min-spend: 1000
    expires: '2026-12-31T23:59:59Z'
    skins: [ruby_sword]          # optional targeting, together with bundles/categories/channels
    channels: [daily, featured, 'event:halloween_2026']
```

- **Bundles** support full, missing-only or both purchase modes. Missing-only charges a dynamic price for the skins the player does not own yet, capped at the bundle price, and never grants an ownership twice.
- **Coupons** are checked atomically: the usage limit check and the redemption insert happen in one database transaction, so two concurrent purchases can never exceed `max-uses` or `max-uses-per-player`. Purchases are scoped by skin, bundle, category or shop channel.
- **Paid flows are transactional.** Every purchase, bundle purchase and gift writes a journal row before money leaves the account, then grants ownership in one batch. On any failure the payment is refunded and already granted ownerships are revoked; if the refund itself fails the transaction is logged for an administrator. Interrupted transactions are reconciled on the next start.
- **Gifting** supports online and optionally offline recipients, a cooldown, an optional message and a join notification. Gifts are stored before the sender is charged, so a crash cannot lose a paid gift.
- **Collections** derive progress from live ownership. Milestone rewards (money, skins, bundles, collection XP, permission, command, item, message) are claimed journal-first and can never be duplicated.
- **Discord webhooks** (`discord.events.*` in `config.yml`) can announce purchases, bundle purchases, gifts, collection completions and rewards, coupon redemptions, shop refreshes and event shop openings/closings, with custom message templates and per-event throttled error reporting.
- **Event shops** open and close automatically: the plugin announces a newly active or expired window exactly once (the state is seeded on start, so a restart does not repeat an announcement).
- Administrators edit the definition files with `/itemskin editor`: left-click toggles a boolean, right-click asks for a new value in chat (`cancel` aborts). Files are written atomically and the shop reloads right afterwards.

## Skin configuration

Each YAML file under `plugins/HMCWraps/skins/` describes one skin. The default compact format is intentionally similar to Nexo/DeluxeMenus: `id`, `display-name`, `material`, `item-model`, `lore`, `rarity`, `categories` and `compatible-materials`. See `core/src/main/resources/skins/README.yml` for a copyable example. Optional prices, permissions, collections and `compatible-items` can be added directly; the advanced `icon`, `compatibility` and `cosmetic` sections remain backward compatible.

Example paid-skin fragment:

```yaml
id: ruby_sword
display-name: '<lang:skins.ruby_sword.name>'
rarity: legendary
categories: [swords]
compatibility:
  materials: [DIAMOND_SWORD, NETHERITE_SWORD]
  items: [nexo:weapons:ruby_blade]
price:
  provider: excellent_economy
  currency: coins
  amount: 5000
cosmetic:
  id: '42042'
  wrap-name: '<red>Ruby Sword'
```

`price` is optional; omit it for a free skin. If a price is configured, the provider and currency are required and the amount must be finite and greater than zero; invalid price definitions are skipped rather than silently becoming free. Currency IDs are provider-specific. The included `excellent_economy` adapter discovers ExcellentEconomy through Bukkit's ServicesManager and does not link its optional API at compile time. `vault` supports Vault's single default currency (`vault`, `money` or `default`). Other plugins can register custom `EconomyProvider`s through the public API.

## Language and translations

All player-facing plugin text is configurable in `plugins/HMCWraps/lang/<locale>.yml`, including legacy wrap/command messages, item-skin GUI labels and messages, debug replies, update notices and `/wraps help` descriptions. English (`en`) and Italian (`it`) are included. To use Italian by default, set this in `plugins/HMCWraps/config.yml`:

```yaml
language:
  default: it
  player-locale: false
```

Set `player-locale: true` to select a matching player's client language first. Existing built-in language files receive newly added keys on reload without replacing edited translations. On upgrade, custom English messages from an existing `messages.properties` are migrated into missing `legacy.*` entries in `lang/en.yml`; edit the language YAML files from then on.

In each locale file, edit `legacy` for wrap/command text, `gui` and `messages` for item skins, and `debug`/`updates` for diagnostics and update notices. Skin names, rarity/category display names and message text support MiniMessage. `<lang:key>` resolves a translation and `<glyph:key>` resolves `glyphs.key`; missing translations fall back to the configured default language and then to the key.

## Public API

Depend on the `api` module and soft-depend on `HMCWraps`. The API retains the legacy wrapper methods and exposes the v2 manager:

```java
HMCWraps wraps = (HMCWraps) Bukkit.getPluginManager().getPlugin("HMCWraps");
if (wraps != null && wraps.getItemSkinManager() != null) {
    ItemSkinManager skins = wraps.getItemSkinManager();
    ItemStack target = player.getInventory().getItemInMainHand();
    skins.openMenu(player, target); // target must be present in that player's inventory
}
```

`ItemSkinManager` also provides collection/catalog lookup, compatibility lookup, asynchronous owned-skin access, access/ownership, purchases, grants, apply/remove and preview operations. The shop is exposed through `ShopService` (daily/featured/event listings, quotes, purchases, bundle modes, collections), `CouponService`, `GiftService`, `ProfileService`, `CollectionService` and `PurchaseTransactionService`, together with the cancellable `SkinPurchaseEvent`, `BundlePurchaseEvent`, `SkinGiftEvent`, `CouponRedeemEvent`, `ShopRefreshEvent`, `CollectionCompleteEvent` and `CollectionRewardClaimEvent`. Other plugins can register economy providers (`EconomyService#register`) and read live progress through the same interfaces the GUIs use. `HMCWraps#getLanguageService()` exposes locale-aware lookup and MiniMessage parsing with `<lang:...>` and `<glyph:...>` tags; the legacy MiniMessage parser delegates these tags to it when available. `registerEconomyProvider(...)` and `registerCompatibilityProvider(...)` let other plugins extend the optional provider boundaries. Bukkit item/player methods should be called on the appropriate server/entity thread; storage and economy results are represented as `CompletionStage`s.

### PlaceholderAPI

When PlaceholderAPI is installed, HMCWraps exposes these skin statistics using the asynchronously populated ownership cache:

| Placeholder | Result |
|---|---|
| `%hmcwraps_skins_total%` | Number of configured skins. |
| `%hmcwraps_skins_owned%` | Number of skins owned by the player. |
| `%hmcwraps_skins_owned_percentage%` | Integer percentage of the catalog owned. |
| `%hmcwraps_skin_owned_<skin-id>%` | `true`/`false` for an individual skin. |
| `%hmcwraps_mainhand_skin%` | Skin ID applied to the main-hand item, or empty. |
| `%hmcwraps_mainhand_compatible%` | Number of skins compatible with the main-hand item. |

## Data and compatibility

- Existing `config.yml`, wrap files, collections, permissions, `/wraps`, wrap APIs and item PDC identifiers are not renamed or replaced.
- New skins are registered as legacy `Wrap` payloads, so the current modifier system continues to apply/remove them and already wrapped items remain readable.
- Ownership is new in 2.0 and is stored in `plugins/HMCWraps/skins.db` using SQLite. It is not inferred from old wrap permissions: legacy wraps remain governed by their existing rules.
- The shop adds tables for transactions, coupon redemptions, the shop rotation, collection rewards, gifts and player settings (schema v7). They are created automatically through dialect-neutral migrations; `shop.reconcile-interrupted-transactions` only controls whether interrupted rows are reported at startup.
- Optional providers are discovered at runtime. Missing integrations do not disable the plugin or prevent free/legacy wraps from loading.
- Back up the complete `plugins/HMCWraps/` folder before upgrading or rolling back. See [Migration notes](docs/MIGRATION-2.0.md).

## Development and verification

```shell
./gradlew clean build
./gradlew test
```

Unit tests cover pricing validation, catalog/config references, ownership cache/persistence, atomic ownership transfers, custom provider registration, and purchase success/failure/refund/double-click behavior. The manual server matrix is in [Testing](docs/TESTING.md). Build against Java 21; the plugin itself does not require ExcellentEconomy, Vault, or a custom-item plugin to compile.

## License and upstream

This repository is based on HMCWraps by HibiscusMC. Preserve the repository's license and attribution requirements when distributing modified builds. See [LICENSE](LICENSE), [CONTRIBUTING.md](CONTRIBUTING.md), and [CHANGELOG.md](CHANGELOG.md).
