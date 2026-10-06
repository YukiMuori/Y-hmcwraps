# Changelog

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
