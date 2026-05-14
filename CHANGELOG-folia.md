# UltimateDonutSmp Changelog

## 1.2 Feature Toggle Build - Folia - 2026-05-14

### Added
- Added an admin feature toggle system through `/uds features`.
- Added in-game feature toggle GUI with paginated feature items, enabled/disabled status, and click-to-toggle behavior.
- Added console/admin subcommands:
  - `/uds features list`
  - `/uds features toggle <feature>`
  - `/uds features enable <feature>`
  - `/uds features disable <feature>`
- Added persistent `FEATURES.<FEATURE_KEY>.ENABLED` settings in `config.yml`.
- Added `FEATURE-TOGGLE-MENU` defaults in `menus.yml`.
- Added user-facing feature toggle messages in `messages.yml`.
- Added `ultimatedonutsmp.admin.features` permission.

### Changed / Improved
- Disabled features now block their related commands at execution time instead of requiring command removal from `plugin.yml`.
- `/uds reload` and `/uds features` remain available even when other feature groups are disabled.
- Existing `COMMANDS.<KEY>` values remain backward-compatible when a matching `FEATURES` value is not present.
- Command help/setup command listings now hide commands whose feature group is disabled.
- Scoreboard and tablist toggles apply live to online players by hiding/restoring sidebar data and clearing/restoring tablist formatting.
- Shards toggle now stops shard commands, passive shard rewards, kill shard rewards, and shard cuboid rewards.
- Homes, RTP, RTP zone, crates, shop/sell/worth, auction house, orders, duels, FFA, staff mode, freeze, invsee, network servers, spawners, portals, Lunar integrations, optimization, combat, fast crystals, and key-all now respect the central feature state.
- Crate visuals/listeners and spawner generation/listeners now stop behavior while their feature is disabled.
- Folia runtime behavior uses the Folia-safe scheduler path while applying live feature state changes to online players.

### Fixed
- Fixed disabled command groups not fully stopping background or passive systems.
- Fixed scoreboard/tablist displays lingering for online players after the related feature is disabled.
- Fixed RTP zone countdown behavior continuing when RTP or RTP zone is toggled off.
- Fixed shard passive rewards and cuboid rewards continuing even when shard access is disabled.
- Fixed crate and spawner runtime behavior remaining active after their command access was disabled.
- Fixed Folia feature-state cleanup paths that need entity/global scheduling instead of direct synchronous player mutation.

### Removed
- No player data, server data, command registrations, or stored feature data were removed.

### Build
- Folia build completed with `mvn -q -DskipTests package`.
- Built artifact: `target/ultimatedonutsmp-folia-1.2.jar`.
