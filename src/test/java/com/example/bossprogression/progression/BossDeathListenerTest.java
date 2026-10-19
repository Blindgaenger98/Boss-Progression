package com.example.bossprogression.progression;

import org.mockbukkit.mockbukkit.entity.WardenMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;
import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import io.papermc.paper.registry.keys.DamageTypeKeys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockBukkit 3.93.2 ships no WitherMock/EnderDragonMock, so the boss-death logic
 * is exercised with the WARDEN (config tier 1), the only mockable boss entity.
 */
class BossDeathListenerTest extends PluginTestBase {

    private DamageSource genericKill() {
        DamageType type = server.getRegistry(DamageType.class).getOrThrow(DamageTypeKeys.GENERIC_KILL);
        return DamageSource.builder(type).build();
    }

    /**
     * Kills the warden at a fixed location in the test world and returns the
     * item entities that ended up in the world afterwards.
     */
    private List<Item> kill(WardenMock warden) {
        warden.setLocation(new Location(world, 0, 64, 0));
        server.getPluginManager().callEvent(new EntityDeathEvent(warden, genericKill(), List.of()));
        return List.copyOf(world.getEntitiesByClass(Item.class));
    }

    @Test
    void outOfSequenceWardenDeathIsIgnored() {
        plugin.progressionManager().setTier(1); // WARDEN (tier 1) is no longer the next boss
        WardenMock warden = new WardenMock(server, UUID.randomUUID());

        List<Item> dropped = kill(warden);

        assertEquals(1, plugin.dataStore().getCurrentBossTier(), "Tier must not advance");
        assertTrue(dropped.isEmpty(), "No revive item may be dropped out of sequence");
    }

    @Test
    void inSequenceWardenDeathAdvancesProgression() {
        WardenMock warden = new WardenMock(server, UUID.randomUUID());

        List<Item> dropped = kill(warden);

        assertEquals(1, plugin.dataStore().getCurrentBossTier());
        assertNotNull(plugin.progressionManager().getNextAllowedBoss());
        assertEquals(2, plugin.progressionManager().getNextAllowedBoss().tierNumber());
        assertEquals(1, dropped.size(), "Exactly one revive item must be dropped in sequence");
    }

    @Test
    void inSequenceDeathDropsReviveItemAtBossLocation() {
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        Material expected = tier1.reviveItem().getMaterial();
        WardenMock warden = new WardenMock(server, UUID.randomUUID());
        Location bossLocation = new Location(world, 0, 64, 0);

        warden.setLocation(bossLocation);
        server.getPluginManager().callEvent(new EntityDeathEvent(warden, genericKill(), List.of()));

        List<Item> dropped = List.copyOf(world.getEntitiesByClass(Item.class));
        assertEquals(1, dropped.size(), "Exactly one item must be dropped");
        assertEquals(expected, dropped.get(0).getItemStack().getType(),
                "Revive item of the defeated tier must be dropped");
        assertEquals(bossLocation.getBlockX(), dropped.get(0).getLocation().getBlockX());
        assertEquals(bossLocation.getBlockY(), dropped.get(0).getLocation().getBlockY());
        assertEquals(bossLocation.getBlockZ(), dropped.get(0).getLocation().getBlockZ());
        assertFalse(plugin.progressionManager().isProgressionComplete());
    }

    @Test
    void nonBossDeathIsIgnored() {
        ZombieMock zombie = new ZombieMock(server, UUID.randomUUID());
        zombie.setLocation(new Location(world, 0, 64, 0));
        server.getPluginManager().callEvent(new EntityDeathEvent(zombie, genericKill(), List.of()));

        assertEquals(0, plugin.dataStore().getCurrentBossTier());
        assertTrue(world.getEntitiesByClass(Item.class).isEmpty());
    }
}
