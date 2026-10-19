package com.example.bossprogression.progression;

import org.mockbukkit.mockbukkit.entity.WardenMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;
import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import io.papermc.paper.registry.keys.DamageTypeKeys;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private List<ItemStack> kill(WardenMock warden) {
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().callEvent(new EntityDeathEvent(warden, genericKill(), drops));
        return drops;
    }

    @Test
    void outOfSequenceWardenDeathIsIgnored() {
        plugin.progressionManager().setTier(1); // WARDEN (tier 1) is no longer the next boss
        WardenMock warden = new WardenMock(server, UUID.randomUUID());

        List<ItemStack> drops = kill(warden);

        assertEquals(1, plugin.dataStore().getCurrentBossTier(), "Tier must not advance");
        assertTrue(drops.isEmpty(), "No revive item may be dropped out of sequence");
    }

    @Test
    void inSequenceWardenDeathAdvancesProgression() {
        kill(new WardenMock(server, UUID.randomUUID()));

        assertEquals(1, plugin.dataStore().getCurrentBossTier());
        assertNotNull(plugin.progressionManager().getNextAllowedBoss());
        assertEquals(2, plugin.progressionManager().getNextAllowedBoss().tierNumber());
    }

    @Test
    void inSequenceDeathAddsReviveItemToDrops() {
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        Material expected = tier1.reviveItem().getMaterial();

        List<ItemStack> drops = kill(new WardenMock(server, UUID.randomUUID()));

        boolean hasReviveItem = drops.stream().anyMatch(item -> item.getType() == expected);
        assertTrue(hasReviveItem, "Revive item of the defeated tier must be in the drops");
        assertFalse(plugin.progressionManager().isProgressionComplete());
    }

    @Test
    void nonBossDeathIsIgnored() {
        ZombieMock zombie = new ZombieMock(server, UUID.randomUUID());
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().callEvent(new EntityDeathEvent(zombie, genericKill(), drops));

        assertEquals(0, plugin.dataStore().getCurrentBossTier());
        assertTrue(drops.isEmpty());
    }
}
