package com.example.bossprogression.data;

import com.example.bossprogression.PluginTestBase;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStoreTest extends PluginTestBase {

    @Test
    void initialTierIsZeroAndNoDeadPlayers() {
        DataStore store = plugin.dataStore();
        assertEquals(0, store.getCurrentBossTier());
        assertTrue(store.getDeadPlayers().isEmpty());
    }

    @Test
    void tierClampsToZeroMinimum() {
        DataStore store = plugin.dataStore();
        store.setCurrentBossTier(-5);
        assertEquals(0, store.getCurrentBossTier());
        store.setCurrentBossTier(3);
        assertEquals(3, store.getCurrentBossTier());
    }

    @Test
    void deadPlayerTracking() {
        DataStore store = plugin.dataStore();
        PlayerMock player = server.addPlayer("Doomed");
        UUID uuid = player.getUniqueId();

        assertFalse(store.isDead(uuid));
        store.markDead(uuid);
        assertTrue(store.isDead(uuid));

        Set<UUID> dead = store.getDeadPlayers();
        assertNotSame(dead, store.getDeadPlayers(), "getDeadPlayers must return a defensive copy");
        assertTrue(dead.contains(uuid));

        store.markAlive(uuid);
        assertFalse(store.isDead(uuid));
    }

    @Test
    void saveAndReloadRoundTrip() {
        DataStore store = plugin.dataStore();
        store.setCurrentBossTier(2);

        PlayerMock player = server.addPlayer("Fallen");
        store.markDead(player.getUniqueId());

        Location altar = new Location(world, 10.5, 64.0, -20.5, 90.0f, -10.0f);
        store.setAltarLocation(altar);

        store.save();

        // Reload from disk into a fresh store to verify persistence.
        DataStore fresh = new DataStore(plugin);
        fresh.load();

        assertEquals(2, fresh.getCurrentBossTier());
        assertTrue(fresh.isDead(player.getUniqueId()));
        assertNotNull(fresh.getAltarLocation());
        assertEquals(10.5, fresh.getAltarLocation().getX(), 0.001);
        assertEquals(-20.5, fresh.getAltarLocation().getZ(), 0.001);
        assertEquals(90.0f, fresh.getAltarLocation().getYaw(), 0.001);
    }

    @Test
    void nullAltarLocationClearsSavedLocation() {
        DataStore store = plugin.dataStore();
        store.setAltarLocation(new Location(world, 1, 2, 3));
        store.save();

        store.setAltarLocation(null);
        store.save();

        DataStore fresh = new DataStore(plugin);
        fresh.load();
        assertNull(fresh.getAltarLocation());
    }
}
