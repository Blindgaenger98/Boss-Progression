# Project Documentation Rules (Non-Obvious Only)

- `plans/boss-progression-plugin-plan.md` is the **design source of truth** (Phase 1 = vanilla-only, Phase 2 = MythicMobs) — Javadoc in code and this plan are aligned; check the plan before answering "what is intended" questions.
- The root-level `paper_boss_progression_permadeath_plugin_agent_briefing(1).md` is the original agent briefing that seeded the project — useful for "why" context, not for current behavior.
- Package layout mirrors gameplay subsystems 1:1 (`altar`, `hardmode`, `hook`, `permadeath`, `progression`, `revive`, `command`, `config`, `data`, `util`) — the plugin main class `BossProgressionPlugin` is the only cross-subsystem wiring point; there is no service-locator or DI framework.
- "Tier" means two different numbers: `currentBossTier` in `DataStore` = count of bosses **defeated** (0 = prep phase), while `BossTier.tierNumber()` = 1-based **position** in config. The "allowed next boss" is `currentTier + 1`.
- `config.yml` and `messages.yml` in `src/main/resources` are **defaults only** — at runtime they are copied to `plugins/BossProgression/` and user edits there win; `messages.yml` is force-resaved only if the file is missing (`saveResource(..., false)`).
- The `hook` package is the Phase 2 extension point; in Phase 1 it ships a no-op `MythicMobsHook` skeleton, so "MythicMobs support" questions must reference the placeholder, not real integration.
