Planupdate: Progression-Erweiterung (Paper 26.3 / Java 25)

Context & Baseline

Phase 1 of the BossProgression plugin is fully implemented, tested, and verified (target/BossProgression-1.0.0.jar built via Paper API 26.3 and Java 25).

This incremental planupdate documents the architecture required to extend the existing ProgressionManager beyond standalone boss entities (Vanilla/MythicMobs) to support Town Raids and Structure Clears (Trial Chambers & Temples) as valid Progression Tiers.

🎯 Target Platform & Tech Stack

Server API: Paper API 26.3 (paper-api:26.3.build.200-stable, api-version: '1.21')

Language/JDK: Java 25

Build Tool: Apache Maven 3.9.x

Testing: MockBukkit 4.x (mockbukkit-v26.3), JUnit Jupiter 6.x

🏗️ Technical Architecture & Enhancements

1. Extended Tier System Model (TierType)

The BossTier record must be extended or wrapped with a TierType enum to distinguish between different milestone mechanics while maintaining backward compatibility with Phase 1.

Enum Definition (TierType)

VANILLA – Standard Vanilla Boss entities (Wither, Ender Dragon, Warden, Elder Guardian).

MYTHIC_MOB – MythicMobs entities (handled via SPI BossHook).

RAID – Village Illager Raids (RaidFinishEvent).

STRUCTURE_CLEAR – Trial Chambers (TrialSpawnerStateChangeEvent) or Temple objectives.

Config Struct Update (config.yml)

boss-tiers:
  1:
    type: "RAID"
    id: "VILLAGE_RAID_1"
    display-name: "Sieg über die Razzia"
    required-raid-waves: 3
    revive-item:
      material: NETHER_STAR
      custom-model-data: 1001
      display-name: "&aRaid-Heldenzeichen"
      pdc-key: "revive"
      pdc-value: "tier1"
  2:
    type: "STRUCTURE_CLEAR"
    id: "TRIAL_CHAMBER_OMINOUS"
    display-name: "Trial Chamber Ominous Clearance"
    required-spawner-clears: 3
    revive-item:
      material: TRIAL_KEY
      custom-model-data: 1002
      display-name: "&bOminöser Schlüssel"
      pdc-key: "revive"
      pdc-value: "tier2"
  3:
    type: "VANILLA"
    entity-type: WITHER
    display-name: "Der Wither"
    revive-item:
      material: NETHER_STAR
      custom-model-data: 1003
      display-name: "&cWither-Herz"
      pdc-key: "revive"
      pdc-value: "tier3"


2. Event Listeners & Progression Triggers

A. Town Raids (RaidEventListener)

Event Tracking: Intercept RaidFinishEvent.

Victory Check: Verify raid.getWinners() contains active players.

Sequence Validation:

If currentTier + 1 is configured as a RAID tier:

Trigger progressionManager.advanceTier().

Inject the PDC-tagged Revive Item directly into the inventory of participating winners or drop at the raid center location.

Broadcast global victory message via Messages.java.

B. Structure Clears (StructureEventListener)

Trial Chambers:

Listen for TrialSpawnerStateChangeEvent (or PlayerInteractEvent on Vaults/Spawners).

Track when a Trial Spawner transitions to CLEARED or when an Ominous Vault is unlocked.

Count completed spawners/vaults against required-spawner-clears.

Completion Trigger: When threshold is reached for currentTier + 1, invoke progressionManager.advanceTier() and drop the PDC-tagged Revive Item.

3. Premature Challenge Protection & Penalties

To maintain the linear progression constraint across all event types:

Unsanctioned Raid Trigger:

Intercept RaidTriggerEvent.

If the active server tier is NOT ready for this raid tier:

Stop/cancel the raid (raid.stop()).

Clear BAD_OMEN / RAID_OMEN potion effects from the triggering player.

Apply penalties via PenaltyConfig (Wither, Slowness, % Lightning damage).

Exemption: Players with bossplugin.admin bypass penalty execution.

Unsanctioned Structure Interaction:

Intercept PlayerInteractEvent on Trial Spawners, Ominous Vaults, or Temple key blocks.

If currentTier + 1 requirements are not met:

Cancel interaction (event.setCancelled(true)).

Send actionbar warning via Messages.plain(...).

Apply localized penalty debuffs to nearby non-admin players.

4. Integration with Core Phase 1 Systems

Data Safety (DataStore): Progression milestones (current_boss_tier) updated via ProgressionManager remain the single source of truth and are persisted automatically to data.yml.

Revive Validation (ReviveItemValidator): New items dropped by Raids/Structures must utilize the config-driven PDC mechanism (NamespacedKey("bossprogression", pdcKey)).

Altar Integration (AltarManager / ReviveManager): Revive Items obtained from Raids and Structure clears are fully compatible with both /revive <player> and right-clicking the Altar structure.

Hardmode Scaling (HardmodeManager): Attribute multipliers continue to scale dynamically following every tier advancement regardless of TierType.

📋 Implementation Checklist (Phase 2 Roadmap)

[ ] Update BossTier record / configuration parser to support TierType enum (VANILLA, MYTHIC_MOB, RAID, STRUCTURE_CLEAR).

[ ] Create RaidEventListener (RaidFinishEvent, RaidTriggerEvent) for victory progression and premature raid cancellation.

[ ] Create StructureEventListener (TrialSpawnerStateChangeEvent, PlayerInteractEvent) for Trial Chamber and Temple clears.

[ ] Connect new triggers to ProgressionManager.advanceTier() and PDC-tagged Revive Item drops.

[ ] Update Messages.java and messages.yml with keys for Raid and Structure clear announcements/warnings.

[ ] Implement MockBukkit unit tests for RaidEventListener and StructureEventListener.

[ ] Activate MythicMobsHook SPI implementation for custom entity support.