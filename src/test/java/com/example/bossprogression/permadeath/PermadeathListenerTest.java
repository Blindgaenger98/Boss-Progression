package com.example.bossprogression.permadeath;

import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.example.bossprogression.PluginTestBase;
import io.papermc.paper.registry.keys.DamageTypeKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

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
}
