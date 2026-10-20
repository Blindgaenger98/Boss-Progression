package com.example.bossprogression.progression;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionManagerTest extends PluginTestBase {

    private ProgressionManager manager() {
        return plugin.progressionManager();
    }

    @Test
    void initialTierIsZero() {
        assertEquals(0, manager().getCurrentTier());
    }

    @Test
    void nextAllowedBossIsFirstConfigTier() {
        BossTier next = manager().getNextAllowedBoss();
        assertNotNull(next);
        assertEquals(1, next.tierNumber());
    }

    @Test
    void isAllowedOnlyForNextTier() {
        assertTrue(manager().isAllowed(1));
        assertFalse(manager().isAllowed(2));
    }

    @Test
    void advanceThroughAllTiersCompletes() {
        BossTier first = manager().advance();
        assertNotNull(first);
        assertEquals(1, manager().getCurrentTier());

        BossTier second = manager().advance();
        assertNotNull(second);
        assertEquals(2, manager().getCurrentTier());

        BossTier third = manager().advance();
        assertNotNull(third);
        assertEquals(3, manager().getCurrentTier());

        assertTrue(manager().isProgressionComplete());
    }

    @Test
    void advanceBeyondCompleteReturnsNull() {
        manager().setTier(3);
        assertNull(manager().advance());
        assertEquals(3, manager().getCurrentTier());
    }

    @Test
    void setTierRejectsOutOfBounds() {
        assertFalse(manager().setTier(-1));
        assertFalse(manager().setTier(4));
        assertEquals(0, manager().getCurrentTier());
    }

    @Test
    void setTierWithinBounds() {
        assertTrue(manager().setTier(2));
        assertEquals(2, manager().getCurrentTier());
        assertTrue(manager().setTier(0));
        assertEquals(0, manager().getCurrentTier());
    }

    @Test
    void setTierToZeroClearsDeadPlayers() {
        assertTrue(manager().setTier(1));
        UUID dead = UUID.randomUUID();
        plugin.dataStore().markDead(dead);
        assertTrue(plugin.dataStore().isDead(dead));

        assertTrue(manager().setTier(0));

        assertFalse(plugin.dataStore().isDead(dead));
        assertTrue(plugin.dataStore().getDeadPlayers().isEmpty());
    }

    @Test
    void setTierResetsHardmodeBaseValueCache() {
        // config defaults: multiplier = 1.0 + 0.5 * tier
        assertTrue(manager().setTier(1)); // multiplier 1.5
        ZombieMock zombie = zombie();
        double base = maxHealth(zombie);

        assertTrue(plugin.hardmodeManager().applyTo(zombie));
        assertEquals(base * 1.5, maxHealth(zombie), 1e-9);

        // setTier must invalidate the cached base value (documented contract);
        // re-applying at tier 2 then scales from the current (already scaled)
        // value instead of the stale cached base.
        assertTrue(manager().setTier(2)); // multiplier 2.0
        assertTrue(plugin.hardmodeManager().applyTo(zombie));

        assertEquals(base * 1.5 * 2.0, maxHealth(zombie), 1e-9);
    }

    private ZombieMock zombie() {
        ZombieMock zombie = new ZombieMock(server, UUID.randomUUID());
        zombie.setLocation(new Location(world, 0, 64, 0));
        return zombie;
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 0.0 : instance.getBaseValue();
    }
}
