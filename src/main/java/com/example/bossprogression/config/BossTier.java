package com.example.bossprogression.config;

import org.bukkit.entity.EntityType;

/**
 * Immutable representation of one boss tier in the configured progression sequence.
 *
 * <p>Phase 2 extended the tier concept beyond classic entity bosses: a tier can
 * now also be a raid victory ({@link TierType#RAID}) or a structure clear
 * ({@link TierType#STRUCTURE_CLEAR}).
 *
 * @param tierNumber            1-based position in the progression (1 = first boss)
 * @param type                  how this tier is defeated; {@code null} is treated as {@link TierType#VANILLA}
 * @param entityType            vanilla entity type for {@link TierType#VANILLA} / {@link TierType#MYTHIC_MOB};
 *                              must be {@code null} for other types
 * @param id                    stable identifier of the tier (e.g. "wither", "town-raid", "trial-chamber")
 * @param displayName           colored display name (with & color codes)
 * @param requiredRaidWaves     minimum raid waves for {@link TierType#RAID} tiers (0 = no threshold)
 * @param structureKind         structure kind for {@link TierType#STRUCTURE_CLEAR} tiers; must be {@code null} otherwise
 * @param requiredSpawnerClears number of cleared trial spawners for {@link TierType#STRUCTURE_CLEAR} tiers
 *                              (0 for non-structure tiers)
 * @param reviveItem            the item this boss drops to revive a player
 */
public record BossTier(int tierNumber,
                       TierType type,
                       EntityType entityType,
                       String id,
                       String displayName,
                       int requiredRaidWaves,
                       StructureKind structureKind,
                       int requiredSpawnerClears,
                       ReviveItemConfig reviveItem) {

    /**
     * Compact constructor: normalize null {@link #type} to {@link TierType#VANILLA}.
     */
    public BossTier {
        if (type == null) {
            type = TierType.VANILLA;
        }
    }

    /**
     * True if this tier is defeated by killing an entity (vanilla or MythicMobs),
     * i.e. {@link #entityType} must be set and entity-based listeners apply.
     */
    public boolean isEntityTypeTier() {
        return entityType != null;
    }

    public String getTierName() {
        return "Tier " + tierNumber;
    }
}
