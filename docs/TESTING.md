# Test plan

## Automated tests

Run with Java 21:

```shell
./gradlew clean build
./gradlew test
```

Current unit coverage is intended to check:

- `VersionParser` handling of legacy (`1.21.4-R0.1-SNAPSHOT`), new-scheme (`26.1.2`), non-numeric (`26.2.build.123-alpha`) and unparseable version strings.
- Preview cancellation before tasks are scheduled, including cleanup that prevents failed cancellation from stranding previews.
- `SkinPrice` provider normalization, custom currency IDs and invalid amounts.
- Rarity identifier normalization/validation.
- Ownership-cache read behavior, favorite-cache invalidation, grants and purchase-audit unlock calls.
- SQLite favorite persistence and atomic, duplicate-safe ownership transfer.
- Themed collection/category references and expanded menu validation.
- Custom economy provider registration/replacement.
- Purchase success, custom currency forwarding, insufficient balance, failed withdrawal, persistence failure/refund compensation and repeated-click rejection.
- Read-only configuration validation for duplicate skin IDs, invalid compatibility materials, malformed YAML and out-of-range GUI slots.
- Shop definition validation: bundle contents/discounts/purchase modes, the daily pool, reset time, event windows, featured/event entries, and coupon types, values, limits, expiries and references.
- English/Italian locale key parity, including the shop menu, event and editor texts.

These tests do not replace a Paper server regression run. The implementation environment may lack Java 21 and a downloadable JDK; in that case record the task as not run rather than treating it as a pass.

## Manual Paper regression matrix

Use a disposable Paper 1.21.4+ server, fresh player UUIDs and a backup of the data folder. Repeat important cases with optional integrations absent and present.

### Catalog, language and GUI

- [ ] First start copies examples; reload does not overwrite edited skin/lang/rarity/category/GUI files, and missing built-in language keys are added safely.
- [ ] Invalid YAML, unknown rarity/category, duplicate skin ID/UUID, bad material and empty compatibility entries are skipped/logged without breaking legacy startup.
- [ ] `/wraps validate` reports malformed YAML, broken references, translation gaps, invalid materials/prices and GUI slot conflicts in the sender's language; verify that it neither reloads nor modifies any file.
- [ ] English and Italian translations cover every legacy `Messages` key plus item-skin UI, debug replies, update notices and command-help descriptions; client-locale and configured-default fallback work; `<lang:key>` and `<glyph:key>` resolve safely.
- [ ] Custom values in a pre-existing `messages.properties` are migrated into missing English `legacy.*` keys, while any language YAML values already edited remain unchanged.
- [ ] Rare/legendary priorities sort descending by default. Configure ascending, name/price/owned sorting and verify ties remain stable.
- [ ] Category selection, all-category toggle, each configured filter and pagination show only matching compatible skins.
- [ ] Themed `angelico` collection cycles from all to series and back; its sword/tool/armor categories remain selectable even when one category or search query is active.
- [ ] Search matches skin IDs, localized display names, categories and collection names; `clear` and `cancel` behave as documented.
- [ ] Search/Favorites/Collection controls, target-item display and filler can be individually disabled; configured slots/icons render and do not overlap content.
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
- [ ] Floating preview modes (`ARMOR_STAND`, `ITEM_DISPLAY`, `MANNEQUIN`, `AUTO`) render on supported servers; mannequins equip armor in the matching slot and hold other items in their main hand.
- [ ] Cancel `ItemWrapEvent` from a test plugin: the GUI must not report a successful apply or alter the item.
- [ ] `/itemskin remove` does not unwrap a legacy wrap; `/wraps` and the legacy preview path still behave as before.

### Ownership, SQLite and economy

- [ ] SQLite creates `plugins/HMCWraps/skins.db`; grant, query, purchase, restart and query again to verify persistence.
- [ ] Add/remove a favorite in the GUI, verify the favorites filter, restart, and confirm the favorite is still stored.
- [ ] `%hmcwraps_skins_total%`, owned/favorite counts, per-skin status and main-hand statistics reflect the loaded catalog/player state; verify values after join/cache warm-up.
- [ ] Start `/itemskin trade <player> <skin-id>`: verify one confirmation is insufficient, both are required, and storage moves one ownership row with source `trade`.
- [ ] Trade rejection for unowned/already-owned skins, same player, busy offers and unsupported storage; cancellation, disconnect, expiry, plugin reload, and a failed storage transfer never duplicate or remove ownership.
- [ ] Free skins work when SQLite is unavailable; paid skins stay locked and never charge when storage is unavailable.
- [ ] ExcellentEconomy provider/currency lookup; use a custom currency such as `coins` and a price such as `5000`.
- [ ] Sufficient balance charges once and grants once; insufficient balance, unavailable provider/currency and failed withdraw grant nothing.
- [ ] Simulate storage failure after a successful withdrawal: verify one refund attempt and an actionable server log if refund fails.
- [ ] Rapid double click/parallel purchase of the same player+skin never charges twice.
- [ ] Vault accepts only the documented default-currency aliases; unsupported currency IDs remain locked.
- [ ] Startup and `/wraps reload` both succeed without ExcellentEconomy, Vault, Nexo or any custom-item plugin installed.

### Shop, bundles, coupons, gifts and collections

- [ ] `shops.yml`/`coupons.yml` are copied on first start; `/wraps validate` reports every broken reference (unknown skin/bundle/category, bad event window, invalid coupon value) without rewriting the files.
- [ ] Daily rotation persists in `shop_state`, rerolls at the configured reset time, does not reroll on restart, and `force` (admin refresh) always replaces it.
- [ ] Featured entries render in the shop home; automatic selection and `automatic-rotate` pick from the configured pool only.
- [ ] Event shops appear as upcoming, running and closed; a shop opening/closing is announced to chat and Discord exactly once, and a restart while an event runs does not re-announce it.
- [ ] Buy a bundle fully, then repeat with some skins already owned: full purchase charges the bundle price once, missing-only charges the dynamic price capped at the bundle price, and no ownership is granted twice.
- [ ] Apply a valid coupon: percentage and fixed discounts, minimum spend, per-player and global limits, expiry; verify the discounted amount is charged once and the redemption is recorded.
- [ ] Apply the same limited coupon twice in parallel (two accounts or two clicks): the usage limit is never exceeded and the failing purchase is not charged.
- [ ] An invalid/expired/exhausted/not-applicable coupon is refused with the matching message before anything is charged.
- [ ] Gift a skin and a bundle to an online and an offline player: the recipient receives the ownership, the sender is charged once, the cooldown is enforced, and the join notification arrives.
- [ ] Interrupt a purchase (kill the server between withdraw and grant): after restart the journal row is reconciled, ownership is either complete or refunded, and nothing is charged twice.
- [ ] Collection progress follows live ownership; claiming a milestone grants its reward once, survives a restart, and a claim cancelled by another plugin grants nothing.
- [ ] `/itemskin shop|bundles|events|coupons|profile|gifts` open the matching screens; every GUI element is unclickable/unduplicable except the intentional buttons.
- [ ] `/itemskin gift <player> <skin>` works for online and (when allowed) offline recipients, and the shop screen shift-right-click prompt resolves a typed name (`cancel` aborts).
- [ ] `/itemskin editor` (permission `hmcwraps.commands.itemskin.editor`) toggles booleans, edits numbers/texts, writes the file atomically and reloads the shop; an aborted edit (`cancel`) and an invalid value change nothing.
- [ ] Discord webhooks fire for purchases, gifts, collection completions, coupon redemptions, shop refreshes and event starts/ends when enabled, and are silent when disabled or without a webhook URL.

## Release gate

Do not mark the preservation round-trip, optional-provider integration, full compatibility matrix or release as verified until the matching automated/server tests have been run and their results recorded. Publish `v2.1.1` only after merge to `master`.
