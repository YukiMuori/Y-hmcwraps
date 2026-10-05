# Test plan

## Automated tests

Run with Java 21:

```shell
./gradlew clean build
./gradlew test
```

Current unit coverage is intended to check:

- `SkinPrice` provider normalization, custom currency IDs and invalid amounts.
- Rarity identifier normalization/validation.
- Ownership-cache read behavior, grants and purchase-audit unlock calls.
- Custom economy provider registration/replacement.
- Purchase success, custom currency forwarding, insufficient balance, failed withdrawal, persistence failure/refund compensation and repeated-click rejection.

These tests do not replace a Paper server regression run. The implementation environment may lack Java 21 and a downloadable JDK; in that case record the task as not run rather than treating it as a pass.

## Manual Paper regression matrix

Use a disposable Paper 1.21.4+ server, fresh player UUIDs and a backup of the data folder. Repeat important cases with optional integrations absent and present.

### Catalog, language and GUI

- [ ] First start copies examples; reload does not overwrite edited skin/lang/rarity/category/GUI files.
- [ ] Invalid YAML, unknown rarity/category, duplicate skin ID/UUID, bad material and empty compatibility entries are skipped/logged without breaking legacy startup.
- [ ] English and Italian translations resolve; client-locale fallback and default-language fallback work; `<lang:key>` and `<glyph:key>` are translated safely.
- [ ] Rare/legendary priorities sort descending by default. Configure ascending, name/price/owned sorting and verify ties remain stable.
- [ ] Category selection, all-category toggle, each configured filter and pagination show only matching compatible skins.
- [ ] `/itemskin` lists compatible free, owned, purchasable, permission-locked and provider-locked entries; a skin for another material/custom item is absent.
- [ ] Test configured filler, category icons, player heads, custom model/item model, tooltip style and hook-provided item icons.
- [ ] Try left/right/shift click, number keys, drag, shift-click from player inventory and double-click. No GUI item may be taken or duplicated.

### Apply, remove, preview and preservation

For each case, compare a saved pre-operation item and test both applying a skin and removing it. Include an existing legacy wrap and a v2 skin swap.

- [ ] Vanilla sword/tool/armor compatibility and invalid-item rejection.
- [ ] Enchantments, display name, lore, item flags, attributes, amount and durability.
- [ ] PDC belonging to another plugin, HMCWraps wrap/owner/physical keys and opaque NBT data.
- [ ] Custom model data, item model, tooltip style, glint override, armor trim, color, equippable components and other server-supported item data.
- [ ] Nexo data and original custom-item identity if Nexo is installed; repeat equivalent checks for ItemsAdder, Oraxen, CraftEngine and MythicCrucible when available.
- [ ] Preview uses a copy of the selected target item, does not mutate the held item, and restores/reopens the configured menu after timeout/cancel.
- [ ] Cancel `ItemWrapEvent` from a test plugin: the GUI must not report a successful apply or alter the item.
- [ ] `/itemskin remove` does not unwrap a legacy wrap; `/wraps` and the legacy preview path still behave as before.

### Ownership, SQLite and economy

- [ ] SQLite creates `plugins/HMCWraps/skins.db`; grant, query, purchase, restart and query again to verify persistence.
- [ ] Free skins work when SQLite is unavailable; paid skins stay locked and never charge when storage is unavailable.
- [ ] ExcellentEconomy provider/currency lookup; use a custom currency such as `coins` and a price such as `5000`.
- [ ] Sufficient balance charges once and grants once; insufficient balance, unavailable provider/currency and failed withdraw grant nothing.
- [ ] Simulate storage failure after a successful withdrawal: verify one refund attempt and an actionable server log if refund fails.
- [ ] Rapid double click/parallel purchase of the same player+skin never charges twice.
- [ ] Vault accepts only the documented default-currency aliases; unsupported currency IDs remain locked.
- [ ] Startup and `/wraps reload` both succeed without ExcellentEconomy, Vault, Nexo or any custom-item plugin installed.

## Release gate

Do not mark the preservation round-trip, optional-provider integration, full compatibility matrix or release as verified until the matching automated/server tests have been run and their results recorded. Publish `v2.0.0` only after merge to `master`.
