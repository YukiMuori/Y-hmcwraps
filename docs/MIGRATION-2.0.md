# Migration notes: 1.x to 2.0

## Before upgrading

1. Stop the server and back up the full `plugins/HMCWraps/` directory, including `config.yml`, `wraps/`, `collections/`, `messages.properties`, language/skin YAML and any integrations' data.
2. Keep the same plugin data-folder name (`HMCWraps`).
3. Install the 2.0 jar and start once. Existing legacy files are loaded through their established loaders; the new language, rarity, category, GUI and skin examples are copied only when the destination does not exist.

## What changes

- `config.yml`, legacy wrap files, collections, commands, permissions and legacy API methods keep their existing paths and semantics. A `language` section is added; it does not rewrite wrap files.
- New v2 catalog files live at `skins/*.yml`, `rarities.yml`, `categories.yml`, `lang/*.yml` and `itemskin-gui.yml`.
- Existing applied wraps remain keyed by their legacy PDC wrap UUID and are resolved through the unchanged wrap loader. The v2 catalog uses a legacy `Wrap` payload as an adapter when applying/removing a new skin.
- `skins.db` is newly created for v2 ownership. There was no equivalent v1 ownership database, so no old ownership state is imported. Existing wraps do not become owned skins automatically and continue to follow their existing permission rules.
- Price configuration is opt-in. A paid skin requires a known economy provider, a provider-supported currency ID and a positive amount. Without an economy plugin, paid skins stay locked; free skins and legacy wraps remain usable.

## Rollback

1. Stop the server.
2. Restore the backup of `plugins/HMCWraps/` and install the previous jar.
3. Keep a copy of `skins.db` if you may return to 2.0; a 1.x plugin will ignore it.

Do not delete v2 files or `skins.db` as part of rollback unless you intentionally want to discard the new catalog/ownership data.

## Notes for API integrations

- The v2 `ItemSkinManager` is additive. Legacy wrapper APIs are still present.
- NPC/plugin menus can call `itemSkinManager.openMenu(player, itemStack)`; the item stack must be in the player's inventory so the GUI can safely update the original slot.
- New async storage/economy methods return `CompletionStage`s. Do not block the server thread waiting for a purchase or ownership query.
