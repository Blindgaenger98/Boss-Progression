package com.example.bossprogression.progression;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

import java.util.Map;
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
    void advanceBroadcastsGlobalMessageViaMessages() {
        // N2: the announcement must go through Messages (prefix + key lookup),
        // not a hardcoded chat string.
        PlayerMock player = server.addPlayer("Watcher");
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        String raw = plugin.messages().raw("boss-killed.global",
                Map.of("boss", tier1.displayName(), "tier", "1"));
        // The message travels as a Component on the wire: Bukkit deserializes the
        // legacy string and PlayerMock#nextMessage() serializes it back, which
        // collapses redundant color codes ("&e&5" -> "&5"). Compare against the
        // same round trip so the assertion matches what the player actually sees.
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        String expected = legacy.serialize(legacy.deserialize(raw));

        manager().advance();

        assertEquals(expected, player.nextMessage());
    }
    
    @Test
    void advanceKeepsBossDisplayNameColorCodes() {
        // F6: the '&' codes of the configured display name must survive as '§'
        // codes in both the chat broadcast and the actionbar.
        PlayerMock player = server.addPlayer("Watcher");
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        assertTrue(tier1.displayName().startsWith("&"), "test relies on a colored display name");
    
        manager().advance();
    
        String chat = player.nextMessage();
        assertTrue(chat.contains("\u00a75\u00a7lThe Warden"),
                "chat message must carry the translated color codes of the boss name");
        Component actionbar = player.nextActionBar();
        assertNotNull(actionbar, "an actionbar must be sent alongside the chat message");
        assertEquals(chat, LegacyComponentSerializer.legacySection().serialize(actionbar),
                "actionbar must carry the same message text as the chat broadcast");
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
