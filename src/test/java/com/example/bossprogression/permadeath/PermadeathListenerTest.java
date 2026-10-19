package com.example.bossprogression.permadeath;

import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.example.bossprogression.PluginTestBase;
import io.papermc.paper.registry.keys.DamageTypeKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermadeathListenerTest extends PluginTestBase {

    private DamageSource genericKill() {
        DamageType type = server.getRegistry(DamageType.class).getOrThrow(DamageTypeKeys.GENERIC_KILL);
        return DamageSource.builder(type).build();
    }

    private void simulateDeath(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerDeathEvent(
                player, genericKill(), Collections.<ItemStack>emptyList(), 0,
                Component.text("You died!"), false));
    }

    private void simulateJoin(PlayerMock player) {
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.text("joined")));
    }

    @Test
    void deathAtTierZeroIsVanilla() {
        assertEquals(0, plugin.dataStore().getCurrentBossTier());
        PlayerMock player = server.addPlayer("Survivor");

        simulateDeath(player);

        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        assertFalse(plugin.dataStore().isDead(player.getUniqueId()));
    }

    @Test
    void deathAfterProgressionTriggersPermadeath() {
        plugin.progressionManager().setTier(1);
        PlayerMock player = server.addPlayer("Victim");
        UUID uuid = player.getUniqueId();

        simulateDeath(player);

        assertEquals(GameMode.SPECTATOR, player.getGameMode());
        assertTrue(plugin.dataStore().isDead(uuid));
    }

    @Test
    void spectatorDeathIsIgnored() {
        plugin.progressionManager().setTier(1);
        PlayerMock player = server.addPlayer("Ghost");
        player.setGameMode(GameMode.SPECTATOR);
        UUID uuid = player.getUniqueId();

        simulateDeath(player);

        assertFalse(plugin.dataStore().isDead(uuid));
    }

    @Test
    void joiningWhileMarkedDeadForcesSpectator() {
        plugin.progressionManager().setTier(1);
        PlayerMock player = server.addPlayer("Resurrected");
        plugin.dataStore().markDead(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);

        simulateJoin(player);

        assertEquals(GameMode.SPECTATOR, player.getGameMode());
    }

    @Test
    void deathWithClearInventoryDropsAllItemsAndClearsInventory() {
        plugin.getConfig().set("permadeath.clear-inventory-on-death", true);
        plugin.saveConfig();
        assertTrue(plugin.configManager().load(), "Config reload must pick up the new setting");
        plugin.progressionManager().setTier(1);

        PlayerMock player = server.addPlayer("Looted");
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        player.getInventory().setArmorContents(new ItemStack[]{null, null, new ItemStack(Material.DIAMOND_CHESTPLATE), null});
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        UUID uuid = player.getUniqueId();

        simulateDeath(player);

        assertTrue(plugin.dataStore().isDead(uuid));
        assertEquals(GameMode.SPECTATOR, player.getGameMode());

        List<Item> dropped = List.copyOf(world.getEntitiesByClass(Item.class));
        assertEquals(3, dropped.size(), "Main slot, armor slot and offhand item must all be dropped");
        List<Material> droppedMaterials = dropped.stream()
                .map(item -> item.getItemStack().getType())
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of(Material.DIAMOND_CHESTPLATE, Material.IRON_SWORD, Material.SHIELD), droppedMaterials);

        assertFalse(Arrays.stream(player.getInventory().getStorageContents()).anyMatch(stack -> stack != null),
                "Storage contents must be empty after dropping");
        assertFalse(Arrays.stream(player.getInventory().getArmorContents()).anyMatch(stack -> stack != null),
                "Armor contents must be empty after dropping");
        assertEquals(Material.AIR, player.getInventory().getItemInOffHand().getType(),
                "Offhand must be empty after dropping");
    }
}
