package com.example.bossprogression.hardmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.WardenMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

import java.util.UUID;

/**
 * Tests for {@link HardmodeManager}: tier multipliers, monster scaling and
 * base-value caching.
 */
class HardmodeManagerTest extends PluginTestBase {

    /**
     * Lazily creates the manager because {@code plugin} is only populated by
     * {@link PluginTestBase#setUpMock()} in {@code @BeforeEach}.
     */
    private HardmodeManager manager() {
        return new HardmodeManager(plugin);
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 0.0 : instance.getBaseValue();
    }

    private ZombieMock zombie() {
        ZombieMock zombie = new ZombieMock(server, UUID.randomUUID());
        zombie.setLocation(new Location(world, 0, 64, 0));
        return zombie;
    }

    @Test
    void multiplierForTierFollowsLinearFormula() {
        // config defaults: base-multiplier 1.0, per-tier-multiplier 0.5
        assertEquals(1.0, plugin.configManager().getHardmodeConfig().multiplierForTier(0), 1e-9);
        assertEquals(1.5, plugin.configManager().getHardmodeConfig().multiplierForTier(1), 1e-9);
        assertEquals(2.5, plugin.configManager().getHardmodeConfig().multiplierForTier(3), 1e-9);
        assertEquals(1.0, plugin.configManager().getHardmodeConfig().multiplierForTier(-5), 1e-9);
    }

    @Test
    void applyToScalesMonsterAttributes() {
        assertTrue(plugin.progressionManager().setTier(1)); // multiplier 1.5
        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);

        assertTrue(manager().applyTo(zombie));

        assertEquals(base * 1.5, maxHealth(zombie), 1e-9);
    }

    @Test
    void applyToDoesNotCompoundOnSecondCall() {
        assertTrue(plugin.progressionManager().setTier(1));
        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);
        HardmodeManager manager = manager();

        assertTrue(manager.applyTo(zombie));
        assertEquals(base * 1.5, maxHealth(zombie), 1e-9);

        // tier up: must scale from the cached base, not the already-scaled value
        assertTrue(plugin.progressionManager().setTier(2)); // multiplier 2.0
        assertTrue(manager.applyTo(zombie));

        assertEquals(base * 2.0, maxHealth(zombie), 1e-9);
    }

    @Test
    void applyToResetsBaseValuesAfterReset() {
        assertTrue(plugin.progressionManager().setTier(1));
        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);
        HardmodeManager manager = manager();

        assertTrue(manager.applyTo(zombie));
        manager.reset();
        assertTrue(manager.applyTo(zombie));

        // base cache cleared: scaling applies to the already-scaled value
        assertEquals(base * 1.5 * 1.5, maxHealth(zombie), 1e-9);
    }

    @Test
    void applyToSkipsBossTierEntities() {
        assertTrue(plugin.progressionManager().setTier(1));
        WardenMock warden = new WardenMock(server, UUID.randomUUID());
        warden.setLocation(new Location(world, 0, 64, 0));
        double base = maxHealth(warden);

        assertFalse(manager().applyTo(warden));

        assertEquals(base, maxHealth(warden), 1e-9);
    }

    @Test
    void applyToSkipsWhenHardmodeDisabled() {
        plugin.getConfig().set("hardmode.enabled", false);
        plugin.saveConfig();
        assertTrue(plugin.configManager().load());
        assertTrue(plugin.progressionManager().setTier(1));

        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);

        assertFalse(manager().applyTo(zombie));

        assertEquals(base, maxHealth(zombie), 1e-9);
    }

    @Test
    void applyToSkipsWhenMultiplierIsOne() {
        // tier 0 -> multiplier 1.0 (no scaling)
        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);

        assertFalse(manager().applyTo(zombie));

        assertEquals(base, maxHealth(zombie), 1e-9);
    }
}
