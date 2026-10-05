# Changelog

## 2.0.0 — unreleased

### Added
- A separate v2 skin catalog with configurable rarities, categories, material/custom-item compatibility and legacy-wrap cosmetic payloads.
- Locale-aware YAML translations and MiniMessage `<lang:...>` / `<glyph:...>` references.
- Configurable `/itemskin` inventory with all compatible owned/unowned skins, rarity sorting, categories, filters, pagination, custom item icons, item models and tooltip styles.
- Left-click apply, right-click target-item preview, optional purchase clicks, `/itemskin preview` and `/itemskin remove`.
- Public item-skin API for catalog lookup, access, ownership, purchases, apply/remove, previews, menus and custom economy/compatibility providers.
- Asynchronous SQLite ownership storage and per-player/skin purchase serialization with payment-failure handling and refund compensation if ownership persistence fails.
- Optional reflective ExcellentEconomy and Vault economy adapters; optional compatibility provider instances for Nexo, ItemsAdder, Oraxen, CraftEngine and MythicCrucible.
- API and core unit tests plus configuration and migration documentation.

### Compatibility
- Legacy `/wraps`, existing wrap configuration, PDC identifiers, preview APIs and wrap application remain available. V2 skins adapt through the existing wrapper/modifier pipeline rather than replacing it.
- ExcellentEconomy, Vault and custom-item integrations are optional. SQLite is bundled in the core jar.

### Verification note
- This entry describes the implementation in the development branch. Build, full server integration and regression tests must pass before publishing a release. No release should be published until the pull request is merged to `master`.
