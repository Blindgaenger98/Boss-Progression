package com.example.bossprogression.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Phase-2 config parsing: {@link TierType} and type-conditional fields on {@link BossTier}.
 */
class ConfigManagerPhase2Test extends PluginTestBase {

    private void setTier(String key, Map<String, Object> values) {
        plugin.getConfig().set("boss-tiers." + key, values);
        plugin.saveConfig();
        assertTrue(plugin.configManager().load(), "expected config load to succeed for tier " + key);
    }

    private void setTierExpectingFailure(String key, Map<String, Object> values) {
        plugin.getConfig().set("boss-tiers." + key, values);
        plugin.saveConfig();
        assertFalse(plugin.configManager().load(), "expected config load to fail for tier " + key);
    }

    @Test
    void defaultTierIsVanilla() {
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        assertEquals(TierType.VANILLA, tier1.type());
        assertEquals(EntityType.WARDEN, tier1.entityType());
        assertTrue(tier1.isEntityTypeTier());
    }

    @Test
    void raidTierIsParsed() {
        setTier("town-raid", Map.of(
                "tier", 4,
                "type", "RAID",
                "required-raid-waves", 6,
                "revive-item", Map.of(
                        "material", "EMERALD",
                        "pdc-key", "revive",
                        "pdc-value", "tier4")));

        BossTier tier = plugin.configManager().getTier(4);
        assertNotNull(tier);
        assertEquals(TierType.RAID, tier.type());
        assertNull(tier.entityType());
        assertFalse(tier.isEntityTypeTier());
        assertEquals(6, tier.requiredRaidWaves());
        assertNull(tier.structureKind());
        assertEquals("town-raid", tier.id());
    }

    @Test
    void raidTierDefaultsRequiredWavesToZero() {
        setTier("town-raid", Map.of(
                "tier", 4,
                "type", "RAID",
                "revive-item", Map.of("material", "EMERALD")));

        BossTier tier = plugin.configManager().getTier(4);
        assertNotNull(tier);
        assertEquals(0, tier.requiredRaidWaves());
    }

    @Test
    void structureClearTierIsParsed() {
        setTier("trial-chamber", Map.of(
                "tier", 5,
                "type", "STRUCTURE_CLEAR",
                "structure-kind", "TRIAL_CHAMBER",
                "required-spawner-clears", 2,
                "revive-item", Map.of(
                        "material", "TRIAL_KEY",
                        "pdc-key", "revive",
                        "pdc-value", "tier5")));

        BossTier tier = plugin.configManager().getTier(5);
        assertNotNull(tier);
        assertEquals(TierType.STRUCTURE_CLEAR, tier.type());
        assertNull(tier.entityType());
        assertEquals(StructureKind.TRIAL_CHAMBER, tier.structureKind());
        assertEquals(2, tier.requiredSpawnerClears());
    }

    @Test
    void structureKindTempleIsAccepted() {
        setTier("temple", Map.of(
                "tier", 5,
                "type", "STRUCTURE_CLEAR",
                "structure-kind", "TEMPLE",
                "required-spawner-clears", 1,
                "revive-item", Map.of("material", "EMERALD")));

        BossTier tier = plugin.configManager().getTier(5);
        assertNotNull(tier);
        assertEquals(StructureKind.TEMPLE, tier.structureKind());
    }

    @Test
    void entityTypeOnRaidTierFailsLoad() {
        setTierExpectingFailure("bad-raid", Map.of(
                "tier", 4,
                "type", "RAID",
                "entity-type", "WARDEN",
                "revive-item", Map.of("material", "EMERALD")));
    }

    @Test
    void entityTypeOnStructureClearTierFailsLoad() {
        setTierExpectingFailure("bad-structure", Map.of(
                "tier", 5,
                "type", "STRUCTURE_CLEAR",
                "entity-type", "WARDEN",
                "required-spawner-clears", 1,
                "revive-item", Map.of("material", "TRIAL_KEY")));
    }

    @Test
    void structureClearWithoutRequiredSpawnerClearsFailsLoad() {
        setTierExpectingFailure("no-clears", Map.of(
                "tier", 5,
                "type", "STRUCTURE_CLEAR",
                "revive-item", Map.of("material", "TRIAL_KEY")));
    }

    @Test
    void unknownTypeFailsLoad() {
        setTierExpectingFailure("bad-type", Map.of(
                "tier", 4,
                "type", "FOO",
                "revive-item", Map.of("material", "EMERALD")));
    }

    @Test
    void unknownStructureKindFailsLoad() {
        setTierExpectingFailure("bad-kind", Map.of(
                "tier", 5,
                "type", "STRUCTURE_CLEAR",
                "structure-kind", "FORTRESS",
                "required-spawner-clears", 1,
                "revive-item", Map.of("material", "TRIAL_KEY")));
    }

    @Test
    void missingReviveItemFailsLoad() {
        setTierExpectingFailure("no-revive", Map.of(
                "tier", 4,
                "type", "RAID",
                "required-raid-waves", 3));
    }
}
