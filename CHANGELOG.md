# Changelog

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
- Startup crash on servers whose version string contains non-numeric segments (for example Leaf's `26.2.build.123-alpha`) by upgrading Lamp to 4.0.0-rc.18 and making the plugin's own version parsing tolerant.

### Compatibility
- Legacy `/wraps`, existing wrap configuration, PDC identifiers, preview APIs and wrap application remain available. V2 skins adapt through the existing wrapper/modifier pipeline rather than replacing it.
- ExcellentEconomy, Vault and custom-item integrations are optional. SQLite is bundled in the core jar.

### Verification
- Released from `master` (merge of pull request #3). Build, unit tests and distributable JAR verification run in CI on Java 21 via `./gradlew clean build`; the `Release` workflow re-runs the build for the `v2.0.0` tag and attaches `HMCWraps-2.0.0.jar`, `MIGRATION-2.0.md` and `SHA256SUMS` to the draft release.
