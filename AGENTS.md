# AGENTS.md

This file provides guidance to agents when working with code in this repository.

BossProgression: Paper plugin (Minecraft 1.26.3 / Paper `26.3.build.200-stable`), Java 25, Maven.

## Environment & Commands

- **No Maven wrapper**: use local Maven at `%USERPROFILE%\.tools\apache-maven-3.9.16\bin` (not on PATH by default).
- **Build**: `set "PATH=%PATH%;%USERPROFILE%\.tools\apache-maven-3.9.16\bin" && mvn clean verify -B -ntp` → artifact `target/BossProgression-1.0.0.jar`.
- **All tests**: `mvn test -B -ntp`. **Single test**: `mvn test -B -ntp -Dtest=ProgressionManagerTest`.
- Requires JDK 25 (Temurin 25.0.4 installed). `pom.xml` sets `maven.compiler.release=25`.

## Critical Paper 26.x API gotchas (learned by migrating from 1.21)

- `ItemMeta#displayName()` (Component getter) / `ItemMeta#displayName(Component)` (setter) are the **Adventure `Component`-based** accessors — use them and serialize with `net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer`. `getDisplayName()` / `setDisplayName(String)` are legacy `String` variants (as is `hasDisplayName()`); `ChatColor.translateAlternateColorCodes` on ItemMeta results is the wrong path for metadata.
- `DamageSource.build()` **requires a `DamageType` argument**; use `org.bukkit.damage.DamageTypeKeys.VOID` / `GENERIC_KILL` constants.
- `EntityDeathEvent.getDrops()` is **read-only** — compute drops from `event.getEntity().getEquipment()`.
- `NamespacedKey` key part must not contain `:` — PDC keys in `config.yml` are bare (e.g. `revive`); the plugin namespace `bossprogression:` is prepended by the `NamespacedKey` constructor.
- `Location` is **immutable** in the new API — use `location.clone()` / `toLocation()`; `EntityMock#getLocation()` returns a different type than in 1.21.
- `Player#getItemOnCursor()` no longer exists — use `getCursorItem()`.

## Testing (MockBukkit 4.x, `org.mockbukkit.mockbukkit:mockbukkit-v26.3`)

- All tests extend [`PluginTestBase`](src/test/java/com/example/bossprogression/PluginTestBase.java): `MockBukkit.load(BossProgressionPlugin.class)` per test + `MockBukkit.unmock()` in teardown. The plugin loads real `config.yml`/`messages.yml` from `src/main/resources` via `saveDefaultConfig()`.
- No tests are `@Disabled` for chunk access: [`SpawnGuardListenerTest`](src/test/java/com/example/bossprogression/progression/SpawnGuardListenerTest.java) drives the spawn guard via `EntitySpawnEvent` + teleport, so `WorldMock#getChunkAt` is never required.
- Events are fired via `server.getPluginManager().callEvent(event)` — listeners are auto-registered when the plugin loads.

## Architecture invariants

- **`DataStore` is the single source of truth** for tier/dead players/altar; **`ProgressionManager` is the only component allowed to mutate the tier**.
- All player-facing strings live in `src/main/resources/messages.yml`, accessed via [`Messages`](src/main/java/com/example/bossprogression/util/Messages.java) (`raw`/`plain` + `{placeholder}` substitution, `&` color codes). Never hardcode chat strings.
- Boss identity is config-driven: `config.yml` → `ConfigManager` → `BossTier` records (entity type + revive item). Vanilla-only in Phase 1; [`MythicMobsHook`](src/main/java/com/example/bossprogression/hook/MythicMobsHook.java) is a placeholder — hooks are soft-depends, all hook calls are wrapped in try/catch so a failing hook never breaks core gameplay.
- Commands (`/boss`, `/altar`, `/revive`, `/bossadmin`) are registered in `plugin.yml` AND wired in `BossProgressionPlugin#onEnable`; keep both in sync.
- Admin permission string is `bossplugin.admin` (checked inline, e.g. `SpawnGuardListener#applyPenalty` exempts admins from the penalty).

## Conventions

- No Lombok/MapStruct; plain Java, `final` classes, constructor injection of `BossProgressionPlugin`; accessors are bare methods (`plugin.dataStore()`, not `getDataStore()` — `DataStore` itself is the exception with traditional getters).
- Event handlers: `@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)`.
- Logging via `plugin.getLogger()` with plain string messages (no SLF4J in runtime).
