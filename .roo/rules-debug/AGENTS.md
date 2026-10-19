# Project Debug Rules (Non-Obvious Only)

- There is **no runtime log file** — everything goes to the server console via `plugin.getLogger()` (java.util.logging, no SLF4J binding in runtime). On a live server, debug via the console only.
- The plugin **self-disables on invalid config** (`ConfigManager#load()` returns false → `disablePlugin(this)` in `BossProgressionPlugin#onEnable`) — a missing "Enabling BossProgression" line after config edits means config.yml is broken, not the code.
- `data.yml` (tier, dead players, altar) is written debounced via `DataStore#markDirty()` + periodic auto-save (`data.auto-save-minutes`) — in-memory state can lag behind disk by up to one tick-task; always `bossadmin reload`/restart before comparing `data.yml` against observed behavior.
- In tests, MockBukkit logs the plugin lifecycle to the console (e.g. "Loaded 3 boss tier(s)"); a failed test usually shows the exact listener log line that fired (or didn't) — check it before re-reading listener code.
- `WorldMock#getChunkAt` is unmocked in MockBukkit 4.x — chunk-dependent code paths (e.g. block-based checks) silently NPE or skip in tests; affected tests are `@Disabled`, so a "passing" suite does NOT cover those paths.
- Actionbar/chat output in tests is not assertable easily — assertions should target state (tier, dead-player set, event cancelled, potion effects) instead of rendered messages.
