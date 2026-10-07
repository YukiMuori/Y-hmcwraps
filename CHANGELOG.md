# Changelog

## 2.1.1 — 2026-10-06

### Fixed
- Preview cancellation no longer throws when its scheduled tasks have not been created; previews are unregistered before cancellation so a failed cancel cannot strand them or break later previews and reloads.
- Floating preview entity types are resolved through CraftBukkit, avoiding removed static entity fields on current Paper/Leaf builds.

### Added
- Configurable floating preview entities: armor stand, item display, mannequin, or automatic selection. Mannequins equip armor in the matching slot and hold other items in their main hand.

### Changed
- Nexo `nexo:<id>` references use the modern `item_model` component on supported servers without also applying legacy CustomModelData.

## 2.1.0 — 2026-10-06

### Added
- **Integrated skin shop** with a rotating daily section, automatic or hand-picked featured entries, timed event shops and bundles, all driven by `shops.yml`.
- **Bundles** with full, missing-only or both purchase modes, dynamic pricing for the missing skins and a per-bundle discount that can never exceed the bundle price.
- **Coupons** (`coupons.yml`) with percentage or fixed discounts, global and per-player usage limits, expiry, minimum spend and targeting by skin, bundle, category or channel. Usage limits are reserved atomically inside a database transaction, so two concurrent purchases can never exceed a limit.
- **Transactional purchases:** every paid operation writes a journal row before money leaves the account, re-reads ownership and recalculates the price server-side, and refunds plus revokes ownership when anything fails. Interrupted transactions are reconciled on the next start.
- **Economy facade** with `auto`/explicit provider selection, ExcellentEconomy and Vault adapters, per-currency providers, and economy calls marshalled onto the entity thread.
- **Gifting** of skins and bundles to online and (optionally) offline players, with cooldowns, messages, join notifications and a stored gift history.
- **Skin collections** with derived progress, collection XP and milestone rewards (money, skins, bundles, XP, permission, command, item, message) claimed journal-first so a reward can never be duplicated.
- **Player profiles** with owned/favorite/collection/purchase/gift/coupon statistics, recent purchase history and a first-purchase timestamp.
- **Shop GUIs** for the shop home, daily offers, bundles, events, coupons, gifts, profiles and bundle/gift confirmations, reachable through `/itemskin shop|bundles|events|coupons|profile|gifts|gift|coupon`.
- **Discord webhooks** for purchases, bundle purchases, gifts, collection completions and rewards, coupon redemptions, shop refreshes and event start/end, with per-event toggles, custom messages, a server name prefix and per-event failure throttling.
- **Public shop API** (`ShopService`, `CouponService`, `GiftService`, `ProfileService`, `CollectionService`, purchases, quotes and shop events) so add-ons can quote, purchase and observe the shop without touching internals.
- **Folia-aware scheduling** through a `Scheduler` boundary, plus SQLite storage for ownership, favorites, transactions, coupon redemptions, shop rotations, collection rewards, gifts and player settings (schema v6, dialect-neutral migrations).

### Changed
- `/wraps validate` also validates `shops.yml` and `coupons.yml`: bundle contents and discounts, purchase modes, daily pool and reset time, event time windows, featured and event entries, coupon types, values, limits, expiries and every skin/bundle/category reference.
- Shop definition files (`shops.yml`, `coupons.yml`) are written through an atomic YAML store, so a crash cannot leave a half-written file.
- `config.yml` gained `economy`, `shop`, `gifts`, `discord` and `debug` sections; both bundled locales cover every new message.

## Unreleased

### Fixed
- Economy provider IDs now treat hyphens and underscores as aliases, so both `excellent_economy` and the previously documented `excellent-economy` resolve correctly in skin prices and automatic priority selection.
- Nexo skins now always apply their canonical `minecraft:item_model` (`nexo:<id>`) while leaving the target item's CustomModelData untouched; transitions to and from legacy model-data wraps preserve the original component.
- Floating preview cancellation is idempotent and safe when reload, timeout, and sneak cancellation happen concurrently.
- Item-display sword previews now use a clockwise −90° Z-axis turn in the display plane, so the blade points upward instead of downward or lying horizontally.
- Hand previews temporarily hide and then restore the client-side off-hand item, preventing a sword held there from appearing duplicated.

### Added
- Compact Nexo/DeluxeMenus-style skin files using top-level `material`, `item-model`, `lore`, `compatible-materials` and `compatible-items`, while retaining the advanced schema.
- A configurable Unskin button in the skin browser restores the selected item’s original appearance.
- A configurable Shop button opens the integrated shop backed by `shops.yml` and `coupons.yml` directly from `/itemskin`.
- `/itemskin give <skin> <player>` lets administrators grant permanent skin ownership for giveaways without charging economy.
- Themed skin collections with category subcategories, persistent SQLite favorites, catalog search, and configurable Search/Favorites/Collection GUI controls.
- Online player skin gifts/trades with ownership checks, two-party confirmation, expiration/cancel handling, and a single atomic storage transfer.
- PlaceholderAPI skin totals, ownership/favorite counts, per-skin status, main-hand skin ID, and compatible-skin counts.

### Changed
- Floating previews default to automatic item-display/mannequin selection so modern item-model components render correctly.
- Bundled wrap, skin, shop, and editor GUIs now use a consistent MiniMessage palette, typography, navigation, localized control lore, and explicitly non-italic item text.
- Skin-icon lore is minimal by default and fully controlled by the `gui.skin-lore` template, including expansion of each skin file's own lore and optional dynamic placeholders.
- The selected item in slot 4 is now the unified browser control: left click changes sorting and right click changes filters; both its name and lore are configurable through `gui.item-name` and `gui.item-lore`.
- Previous/next arrows are rendered only when their destination page exists, and catalog/category icons hide vanilla technical tooltip details so only configured presentation text remains.
- ItemSkin category controls now use DeluxeMenus-style `enabled`, `slot`, and complete `item` sections, while legacy `category-slots` remains readable.
- MiniMessage output throughout the plugin inherits an explicit gray base color whenever no more specific color is configured.

### Removed
- The favorites feature, including legacy wrap actions/configuration, skin GUI controls, persistence repositories, public favorite APIs, profile statistics and PlaceholderAPI expansions. Schema migration 7 removes the obsolete `skin_favorites` table and its stored data.

### Added
- `legacy-wraps.enabled` completely gates the classic wrap subsystem. It defaults to `false`, so legacy files, collections, `/wraps` commands, GUI shortcuts, physical wrappers, actions, permission scans and legacy placeholders stay inactive while `/itemskin` retains the shared cosmetic engine.
- `/wraps validate` now checks themed skin collection definitions/references and the expanded GUI controls.

## 2.0.1 — 2026-10-05

### Fixed
- Startup crash on servers whose version string contains non-numeric segments (for example Leaf's `26.2.build.123-alpha`) by upgrading Lamp to 4.0.0-rc.18 and making the plugin's own version parsing tolerant.
- This is the fix merged to `master` in pull request #3. The `v2.0.0` tag was cut from `d600c88` (merge of pull request #2) before that merge, so the published `HMCWraps-2.0.0.jar` does **not** contain it. 2.0.1 is the first release built from a commit that includes it.

### Changed
- The plugin version is now `2.0.1`, so the distributable is `HMCWraps-2.0.1.jar`. The `Build and test` workflow verifies the JAR by pattern instead of a hardcoded filename.

## 2.0.0 — 2026-10-05

### Added
- A separate v2 skin catalog with configurable rarities, categories, material/custom-item compatibility and legacy-wrap cosmetic payloads.
- Locale-aware YAML translations and MiniMessage `<lang:...>` / `<glyph:...>` references.
- Configurable `/itemskin` inventory with all compatible owned/unowned skins, rarity sorting, categories, filters, pagination, custom item icons, item models and tooltip styles.
- Left-click apply, right-click target-item preview, optional purchase clicks, `/itemskin preview` and `/itemskin remove`.
- Public item-skin API for catalog lookup, access, ownership, purchases, apply/remove, previews, menus and custom economy/compatibility providers.
- Asynchronous SQLite ownership storage and per-player/skin purchase serialization with payment-failure handling and refund compensation if ownership persistence fails.
- Optional reflective ExcellentEconomy and Vault economy adapters; optional compatibility provider instances for Nexo, ItemsAdder, Oraxen, CraftEngine and MythicCrucible.
- API and core unit tests plus configuration and migration documentation.

### Fixed
- *(Listed here for completeness, but **not** part of the published `HMCWraps-2.0.0.jar`: this fix was merged to `master` after the `v2.0.0` tag was cut. See 2.0.1.)* Startup crash on servers whose version string contains non-numeric segments (for example Leaf's `26.2.build.123-alpha`) by upgrading Lamp to 4.0.0-rc.18 and making the plugin's own version parsing tolerant.

### Compatibility
- Legacy `/wraps`, existing wrap configuration, PDC identifiers, preview APIs and wrap application remain available. V2 skins adapt through the existing wrapper/modifier pipeline rather than replacing it.
- ExcellentEconomy, Vault and custom-item integrations are optional. SQLite is bundled in the core jar.

### Verification
- Tagged at `d600c88` (merge of pull request #2) and published from the `Release` workflow: build, unit tests and distributable JAR verification passed on Java 21, and the workflow attached `HMCWraps-2.0.0.jar`, `MIGRATION-2.0.md` and `SHA256SUMS`. Pull request #3 landed on `master` after the tag was pushed, so the crash fix above is only present from 2.0.1 onward.
