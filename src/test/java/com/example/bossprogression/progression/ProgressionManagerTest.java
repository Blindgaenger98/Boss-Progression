package com.example.bossprogression.progression;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.junit.jupiter.api.Test;

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
}
