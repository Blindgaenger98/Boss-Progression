package com.example.bossprogression.config;

/**
 * Categorizes how a boss tier is defeated.
 * <p>
 * Phase 2 extension: besides classic entity-based tiers, the progression can
 * now contain raid victories and structure-clear milestones.
 */
public enum TierType {

    /** Defeated by killing the configured vanilla {@code entityType}. */
    VANILLA,

    /**
     * Defeated by killing a MythicMobs boss. The {@code entityType} is the
     * vanilla entity type MythicMobs uses for spawning; the actual hook
     * integration is a separate follow-up (placeholder in Phase 2).
     */
    MYTHIC_MOB,

    /** Defeated by winning a vanilla raid (see {@code required-raid-waves}). */
    RAID,

    /**
     * Defeated by clearing a structure. Phase 2 only implements trial chambers
     * ({@code TrialSpawnerStateChangeEvent}); temple support is modeled in the
     * config but has no event logic yet.
     */
    STRUCTURE_CLEAR
}
