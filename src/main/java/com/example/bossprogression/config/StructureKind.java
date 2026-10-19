package com.example.bossprogression.config;

/**
 * The kind of structure a {@link TierType#STRUCTURE_CLEAR} tier requires to
 * clear.
 * <p>
 * Phase 2 note: only {@link #TRIAL_CHAMBER} is backed by event logic
 * ({@code TrialSpawnerStateChangeEvent}). {@link #TEMPLE} is accepted by the
 * config parser but intentionally has no progression event logic yet.
 */
public enum StructureKind {

    /** Trial chambers: count trial spawners reaching the COMPLETED state. */
    TRIAL_CHAMBER,

    /**
     * Desert temple clearing. Accepted in configuration for forward
     * compatibility; no event logic implemented in Phase 2.
     */
    TEMPLE
}
