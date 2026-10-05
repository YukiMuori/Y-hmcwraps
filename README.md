# Y-HMCWraps 2.0

Y-HMCWraps is a Paper/Spigot item-cosmetics plugin. It keeps the established **wrap** system and adds a separate, catalog-driven **item-skin** system with rarities, categories, translations, ownership, optional economy providers, previews and a configurable inventory UI.

The new catalog uses the existing wrap engine as its application adapter. This keeps previously applied wraps, legacy configuration, permissions, commands, preview behavior and item PDC data readable instead of replacing them with a new item format.

## Requirements

- Java 21 or newer.
- A Paper server is recommended. The core compiles against Spigot API 1.21.4; newer item components are used when the running server supports them.
- ExcellentEconomy, Vault, Nexo, ItemsAdder, Oraxen, CraftEngine and MythicCrucible are optional. The plugin can start without them.

## Install

1. Put the shaded `HMCWraps-2.0.0.jar` in `plugins/` and restart the server.
2. The plugin creates/updates its legacy files and copies the v2 examples into `plugins/HMCWraps/` on first start.
3. Configure skin files in `plugins/HMCWraps/skins/`, names in `lang/*.yml`, rarity priorities in `rarities.yml`, categories in `categories.yml`, and the browser in `itemskin-gui.yml`.
4. Restart or use `/wraps reload` after changing configuration.

The repository's build artifact is produced by `./gradlew clean build`; the distributable jar is `build/libs/HMCWraps-2.0.0.jar` after a successful build.

## Player commands

| Command | Description |
|---|---|
| `/itemskin` or `/itemskin open` | Open the v2 skin browser for the item in your main hand. It lists every compatible skin, including skins the player does not own. |
| `/itemskin preview <skin-id>` | Preview a configured skin on the held item. Preview does not require ownership. |
| `/itemskin remove` | Remove a v2 skin from the held item. It does not unwrap a legacy wrap. |
| `/wraps` | Existing legacy wrap inventory. |

In the browser, left click applies a free/owned skin, right click previews it, and Shift-click buys a configured paid skin. Sorting defaults to rarity priority descending. Categories, filters, content slots, buttons, filler items, icons, item models, tooltip styles and MiniMessage titles are configured in `itemskin-gui.yml` and the catalog files.

## Skin configuration

Each YAML file under `plugins/HMCWraps/skins/` describes one skin. See `core/src/main/resources/skins/README.yml` for a copyable schema and `ruby_sword.yml` for a loaded example. A skin has a stable `id`, translated display name, rarity, categories, icon, material/custom-item compatibility, optional permission and a legacy `cosmetic` payload.

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

Skin text and rarity/category display names are MiniMessage-aware. Language files are in `plugins/HMCWraps/lang/`; `<lang:key>` resolves a translation and `<glyph:key>` resolves `glyphs.key`. Unknown translations fall back to the configured default language and then to the key.

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

`ItemSkinManager` also provides catalog lookup, compatibility lookup, access/ownership, async purchases, grants, apply/remove and preview operations. `HMCWraps#getLanguageService()` exposes locale-aware lookup and MiniMessage parsing with `<lang:...>` and `<glyph:...>` tags; the legacy MiniMessage parser delegates these tags to it when available. `registerEconomyProvider(...)` and `registerCompatibilityProvider(...)` let other plugins extend the optional provider boundaries. Bukkit item/player methods should be called on the appropriate server/entity thread; storage and economy results are represented as `CompletionStage`s.

## Data and compatibility

- Existing `config.yml`, wrap files, collections, permissions, `/wraps`, wrap APIs and item PDC identifiers are not renamed or replaced.
- New skins are registered as legacy `Wrap` payloads, so the current modifier system continues to apply/remove them and already wrapped items remain readable.
- Ownership is new in 2.0 and is stored in `plugins/HMCWraps/skins.db` using SQLite. It is not inferred from old wrap permissions: legacy wraps remain governed by their existing rules.
- Optional providers are discovered at runtime. Missing integrations do not disable the plugin or prevent free/legacy wraps from loading.
- Back up the complete `plugins/HMCWraps/` folder before upgrading or rolling back. See [Migration notes](docs/MIGRATION-2.0.md).

## Development and verification

```shell
./gradlew clean build
./gradlew test
```

Unit tests cover pricing validation, rarity identifiers, ownership cache/persistence contracts, custom provider registration, and purchase success/failure/refund/double-click behavior. The manual server matrix is in [Testing](docs/TESTING.md). Build against Java 21; the plugin itself does not require ExcellentEconomy, Vault, or a custom-item plugin to compile.

## License and upstream

This repository is based on HMCWraps by HibiscusMC. Preserve the repository's license and attribution requirements when distributing modified builds. See [LICENSE](LICENSE), [CONTRIBUTING.md](CONTRIBUTING.md), and [CHANGELOG.md](CHANGELOG.md).
