# Project Coding Rules (Non-Obvious Only)

- All player-facing text MUST come from `src/main/resources/messages.yml` via `plugin.messages().raw(key, Map.of("ph", "val"))` / `plain(...)` — never build chat strings inline. `raw` adds the prefix; `plain` is for GUI titles / item names.
- `&` color codes in config values are converted with `.replace('&', '§')` when passed to Adventure components; use `LegacyComponentSerializer.legacySection().deserialize(...)` for actionbars (see [`ProgressionManager#advance`](src/main/java/com/example/bossprogression/progression/ProgressionManager.java)).
- Never mutate `DataStore` fields directly outside the manager that owns them — `ProgressionManager` is the ONLY allowed mutator of the boss tier.
- Accessor style on `BossProgressionPlugin`: bare methods (`plugin.dataStore()`, `plugin.configManager()`) — do not introduce getters/setters in that style. `DataStore` is the exception (traditional getters/setters).
- New components: `final` class, single `BossProgressionPlugin` constructor param, no Lombok, no SLF4J (use `plugin.getLogger()`).
- New event listeners: `@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)` and register in `BossProgressionPlugin#onEnable`.
- Adding a command: MUST update BOTH `src/main/resources/plugin.yml` (command + permission entries) AND `registerCommand(...)` in `onEnable` — tests will pass either way, but the plugin will be broken at runtime if they drift.
- Adding a boss: entry in `config.yml` `boss-tiers:` (tier number = 1-based position, `entity-type` = Bukkit `EntityType` name, `revive-item.pdc-key` must be bare without `:`).
- Third-party hook code must never throw into core gameplay — wrap all hook calls in `try/catch (Throwable)` (see [`BossHookManager`](src/main/java/com/example/bossprogression/hook/BossHookManager.java)); new hooks implement `BossHook` and are gated on `PluginManager#isPluginEnabled` in `BossHookManager#discoverAndEnable`.
- Admin exemption: check `player.hasPermission("bossplugin.admin")` before applying penalties/effects to players.
- PDC item validation: match on `pdc-key`/`pdc-value` first, then `custom-model-data`, then display name (see [`ReviveItemValidator`](src/main/java/com/example/bossprogression/revive/ReviveItemValidator.java)).
