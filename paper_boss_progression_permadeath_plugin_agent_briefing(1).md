# Agent Briefing: Paper Boss-Progression & Permadeath Plugin

## Objective

Develop a custom Minecraft Paper (1.20+) plugin implementing a sequential boss progression system combined with a conditional permadeath and player revive mechanism based on boss drops.

## 🏗️ Core Architecture & Requirements

### Module 1: Configuration & Data Persistence

* **Config file (`config.yml`):**

  * Linear array of boss tiers containing:

    * Tier ID / Number

    * Entity Type (Vanilla `EntityType` or MythicMobs Internal Name)

    * Boss display name

    * Required Revive Item (Material, Custom Model Data, Display Name, or Custom NBT/PDC tag)

  * Altar System Settings (Structure pattern, activation block, particle/sound effects).

  * Punishment settings for premature boss fights (debuffs, damage values).

  * Hardmode scaling factors per tier.

* **Data storage (`data.yml` or SQLite):**

  * `current_boss_tier` (Integer, initial value: `0`).

  * `dead_players` (List of UUIDs currently in spectator state).

  * `altar_location` (World coordinates for the revive altar, if registered).

### Module 2: Sequential Boss Progression & Penalty System

* **Pre-boss encounter check:**

  * Intercept vanilla boss spawns (`EntitySpawnEvent`, `PlayerInteractEvent`) AND MythicMobs spawn events (`MythicMobSpawnEvent`).

  * Verify if the spawned/challenged boss tier corresponds to `current_boss_tier + 1`.

* **Penalty Logic (Premature Attempt):**

  * Cancel spawning/activation if the tier is out of sequence.

  * Apply severe penalties to nearby players (e.g., *Wither II*, *Slowness*, percentage-based lightning damage).

* **Completion Trigger:**

  * Intercept vanilla `EntityDeathEvent` and MythicMobs `MythicMobDeathEvent` for valid boss kills.

  * Increment `current_boss_tier` by 1.

  * Announce progress globally via chat/actionbar.

  * Inject the specific revive item into the boss loot drop or MythicMobs drop table dynamically.

### Module 3: Dynamic Permadeath & Revive System

* **Death Handler (`PlayerDeathEvent`):**

  * If `current_boss_tier == 0`: Allow normal vanilla respawn (Preparation Phase).

  * If `current_boss_tier > 0`:

    * Put player into `GameMode.SPECTATOR`.

    * Mark player UUID as dead in state storage.

* **Revive Mechanism Options:**

  1. **Command-based:** `/revive <player>` (Consumes the required item from inventory).

  2. **Physical Altar System:**

     * Interactive block/structure (e.g., Crying Obsidian block surrounded by Soul Torches).

     * Right-clicking the Altar opens a GUI listing dead players, or right-clicking with the revive item prompts selection via chat/GUI.

     * Consumes the item upon confirmation, triggers particle effects, and revives the target player at the Altar's location.

* **Revive Process:**

  * Verify the item corresponds strictly to the **latest unlocked boss tier**.

  * On consumption of item:

    * Remove target player from death storage.

    * Set target player to `GameMode.SURVIVAL`.

    * Teleport target to the Altar location (or reviving player's location if using command) with brief invulnerability and regeneration buffs.

### Module 4: MythicMobs & Third-Party Integration API

* **Soft Dependency on MythicMobs:**

  * Check for MythicMobs presence on plugin startup (`Bukkit.getPluginManager().isPluginEnabled("MythicMobs")`).

  * Hook into `MythicMobSpawnEvent` to enforce tier progression checks on custom boss mobs.

  * Hook into `MythicMobDeathEvent` to handle progression advancement and custom item drops via MythicMobs API.

### Module 5: Hardmode+ Modifiers

* Scale world monster attributes (health, attack damage, speed) dynamically based on `current_boss_tier`.

* Optional: Nerf health recovery rates or golden apple effects as tier progression increases.

### Module 6: Commands & Permissions

#### Player Commands

* `/boss status` - Displays current tier, next allowed boss, and required revive materials.

* `/revive <player>` - Consumes required boss drop to revive a fallen player (if enabled).

* `/altar` - Displays coordinates of the revive altar (if registered).

#### Admin Commands (`permission: bossplugin.admin`)

* `/bossadmin settier <tier>` - Manually update server progression tier.

* `/bossadmin revive <player>` - Force revive a player without consuming items.

* `/bossadmin setaltar` - Sets the revival altar at the admin's current targeted block/location.

* `/bossadmin reload` - Reload configuration file.

## 🛠️ Tech Stack & Constraints

* **Platform:** Paper API (Java 17 / 21, Minecraft 1.20+)

* **Optional Dependencies:** MythicMobs API (Soft Depend)

* **Build Tool:** Maven or Gradle

* **Code Style:** Clean OOP design, event-driven architecture, robust null-safety, and descriptive logging.