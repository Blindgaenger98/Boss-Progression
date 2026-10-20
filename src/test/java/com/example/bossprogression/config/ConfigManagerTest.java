package com.example.bossprogression.config;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the atomic reload guarantee of {@link ConfigManager#load()}.
 *
 * <p>Fix #10 (review-findings): a failed reload must NOT leave a hybrid state
 * (e.g. a new boss-tier list combined with a stale altar config). Every part
 * is parsed and validated before anything is published; on failure the
 * previously loaded configuration must be fully retained.
 */
class ConfigManagerTest extends PluginTestBase {

    @Test
    void failedReloadWithLateFailingPartKeepsPreviousConfigurationUnchanged() {
        // Baseline: valid default config (tier 1 = WARDEN, altar = CRYING_OBSIDIAN).
        assertEquals(EntityType.WARDEN, plugin.configManager().getTier(1).entityType());
        assertEquals(Material.CRYING_OBSIDIAN, plugin.configManager().getAltarConfig().getAltarBlock());

        // Corrupt two different parts at once:
        // 1) a VALID but different entity type -> boss-tiers parsing succeeds,
        // 2) an INVALID altar block            -> altar parsing fails.
        // With a non-atomic reload this would produce a hybrid state
        // (new boss tiers + old altar config).
        plugin.getConfig().set("boss-tiers.warden.entity-type", "WITHER");
        plugin.getConfig().set("altar.block", "NOT_A_BLOCK");
        plugin.saveConfig();

        assertFalse(plugin.configManager().load(), "load must fail on the invalid altar block");

        // No hybrid state: the previously loaded values are retained.
        assertEquals(EntityType.WARDEN, plugin.configManager().getTier(1).entityType());
        assertEquals(3, plugin.configManager().getBossTiers().size());
        assertEquals(Material.CRYING_OBSIDIAN, plugin.configManager().getAltarConfig().getAltarBlock());
    }

    @Test
    void failedReloadWithEarlyFailingPartKeepsLaterPartsUnchanged() {
        // Baseline values.
        assertEquals(EntityType.WITHER, plugin.configManager().getTier(2).entityType());
        assertEquals(Material.CRYING_OBSIDIAN, plugin.configManager().getAltarConfig().getAltarBlock());

        // Change the altar to a VALID but different block and corrupt a boss tier
        // (which is parsed BEFORE the altar). The altar change must NOT be picked up.
        plugin.getConfig().set("altar.block", "OBSIDIAN");
        plugin.getConfig().set("boss-tiers.wither.entity-type", "NOT_AN_ENTITY");
        plugin.saveConfig();

        assertFalse(plugin.configManager().load(), "load must fail on the invalid entity type");

        // Boss tiers retained from the previous load ...
        assertEquals(EntityType.WITHER, plugin.configManager().getTier(2).entityType());
        // ... and the valid-but-new altar value must NOT have been applied.
        assertEquals(Material.CRYING_OBSIDIAN, plugin.configManager().getAltarConfig().getAltarBlock());
    }

    @Test
    void successfulReloadPublishesAllPartsTogether() {
        // Change two independent parts at once: a valid new entity type for
        // tier 1 and a valid new altar block.
        plugin.getConfig().set("boss-tiers.warden.entity-type", "WITHER");
        plugin.getConfig().set("altar.block", "OBSIDIAN");
        plugin.saveConfig();

        assertTrue(plugin.configManager().load(), "load must succeed for a fully valid config");

        // Both parts must be published together in the same step.
        assertEquals(EntityType.WITHER, plugin.configManager().getTier(1).entityType());
        assertEquals(Material.OBSIDIAN, plugin.configManager().getAltarConfig().getAltarBlock());
    }

}
