package com.example.bossprogression.revive;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviveManagerTest extends PluginTestBase {

    private ReviveManager manager() {
        return new ReviveManager(plugin);
    }

    private PlayerMock addDeadPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        plugin.dataStore().markDead(player.getUniqueId());
        return player;
    }

    private ItemStack reviveItem() {
        plugin.progressionManager().setTier(1);
        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        return tier1.reviveItem().createItem(plugin, 1);
    }

    @Test
    void singleItemReviveSucceedsAndClearsSlot() {
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Healer");
        actor.getInventory().setItem(0, reviveItem());

        ReviveManager.ReviveResult result =
                manager().revive(victim, actor, actor.getLocation(), false);

        assertEquals(ReviveManager.ReviveResult.SUCCESS, result);
        assertFalse(plugin.dataStore().isDead(victim.getUniqueId()));
        // The consumed item must be gone from the real inventory.
        assertNull(actor.getInventory().getItem(0));
        assertNull(actor.getInventory().getStorageContents()[0]);
    }

    @Test
    void secondReviveWithoutItemFailsWithNoItem() {
        PlayerMock victim1 = addDeadPlayer("VictimOne");
        PlayerMock victim2 = addDeadPlayer("VictimTwo");
        PlayerMock actor = server.addPlayer("Healer");
        actor.getInventory().setItem(0, reviveItem());

        assertEquals(ReviveManager.ReviveResult.SUCCESS,
                manager().revive(victim1, actor, actor.getLocation(), false));
        assertNull(actor.getInventory().getItem(0));

        // No item left — the second revive must not succeed.
        assertEquals(ReviveManager.ReviveResult.NO_ITEM,
                manager().revive(victim2, actor, actor.getLocation(), false));
        assertTrue(plugin.dataStore().isDead(victim2.getUniqueId()));
    }

    @Test
    void forceReviveWithoutItemSucceedsAndDoesNotConsume() {
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Admin");
        actor.getInventory().setItem(0, new ItemStack(Material.STONE));

        ReviveManager.ReviveResult result =
                manager().revive(victim, actor, actor.getLocation(), true);

        assertEquals(ReviveManager.ReviveResult.SUCCESS, result);
        assertFalse(plugin.dataStore().isDead(victim.getUniqueId()));
        // Nothing was consumed (only a stone in the inventory anyway).
        ItemStack stone = actor.getInventory().getItem(0);
        assertNotNull(stone);
        assertEquals(Material.STONE, stone.getType());
    }

    @Test
    void reviveAppliesBuffsAndRestoresVitals() {
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Healer");
        actor.getInventory().setItem(0, reviveItem());

        ReviveManager.ReviveResult result =
                manager().revive(victim, actor, actor.getLocation(), false);

        assertEquals(ReviveManager.ReviveResult.SUCCESS, result);
        assertEquals(GameMode.SURVIVAL, victim.getGameMode());
        double maxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        assertEquals(maxHealth, victim.getHealth(), 0.01);
        assertEquals(20, victim.getFoodLevel());
        assertEquals(0, victim.getFireTicks());

        PotionEffect regeneration = victim.getPotionEffect(PotionEffectType.REGENERATION);
        assertNotNull(regeneration);
        assertEquals(10 * 20, regeneration.getDuration());
        assertEquals(1, regeneration.getAmplifier());

        PotionEffect speed = victim.getPotionEffect(PotionEffectType.SPEED);
        assertNotNull(speed);
        assertEquals(5 * 20, speed.getDuration());
        assertEquals(0, speed.getAmplifier());
    }

    @Test
    void reviveTeleportsTargetToDestination() {
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Healer");
        actor.getInventory().setItem(0, reviveItem());
        Location destination = new Location(world, 10, 64, 10);

        ReviveManager.ReviveResult result =
                manager().revive(victim, actor, destination, false);

        assertEquals(ReviveManager.ReviveResult.SUCCESS, result);
        assertFalse(plugin.dataStore().isDead(victim.getUniqueId()));
        assertEquals(destination.getWorld(), victim.getLocation().getWorld());
        assertEquals(destination.getBlockX(), victim.getLocation().getBlockX());
        assertEquals(destination.getBlockY(), victim.getLocation().getBlockY());
        assertEquals(destination.getBlockZ(), victim.getLocation().getBlockZ());
    }

    @Test
    void resolveDestinationUsesAltarWhenTargetSharesWorld() {
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Healer");
        plugin.dataStore().setAltarLocation(new Location(world, 5, 65, 5));

        Location destination = manager().resolveDestination(victim, actor);

        assertEquals(world, destination.getWorld());
        assertEquals(5, destination.getBlockX());
        assertEquals(65, destination.getBlockY());
        assertEquals(5, destination.getBlockZ());
    }

    @Test
    void resolveDestinationFallsBackToActorWhenInDifferentWorld() {
        PlayerMock victim = addDeadPlayer("Victim");
        WorldMock otherWorld = server.addSimpleWorld("other");
        victim.teleport(otherWorld.getSpawnLocation());
        PlayerMock actor = server.addPlayer("Healer");
        plugin.dataStore().setAltarLocation(new Location(world, 5, 65, 5));

        Location destination = manager().resolveDestination(victim, actor);

        assertEquals(actor.getLocation().getWorld(), destination.getWorld());
        assertEquals(actor.getLocation().getBlockX(), destination.getBlockX());
        assertEquals(actor.getLocation().getBlockZ(), destination.getBlockZ());
    }
}
