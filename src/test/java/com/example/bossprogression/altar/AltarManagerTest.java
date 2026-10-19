package com.example.bossprogression.altar;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltarManagerTest extends PluginTestBase {

    private PlayerMock addDeadPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        plugin.dataStore().markDead(player.getUniqueId());
        return player;
    }

    private void setupAltar() {
        plugin.progressionManager().setTier(1);
        plugin.dataStore().setAltarLocation(new Location(world, 0, 64, 0));
    }

    @Test
    void altarGuiClickWithSingleItemConsumesExactlyOnce() {
        setupAltar();
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Healer");

        BossTier tier1 = plugin.configManager().getTier(1);
        assertNotNull(tier1);
        actor.getInventory().setItem(0, tier1.reviveItem().createItem(plugin, 1));

        assertTrue(plugin.altarManager().openGui(actor));
        plugin.altarManager().handleGuiClick(actor, 0);

        // Revive succeeded — no NO_ITEM, victim is alive.
        assertFalse(plugin.dataStore().isDead(victim.getUniqueId()));
        // Exactly one item was consumed (the normal case is amount == 1,
        // so the slot must be empty and no second consumption is possible).
        assertNull(actor.getInventory().getItem(0));
        assertFalse(plugin.altarManager().openGui(actor)); // no more dead players
    }

    @Test
    void altarGuiClickWithoutItemDoesNotConsumeAndKeepsVictimDead() {
        setupAltar();
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock actor = server.addPlayer("Broke");

        assertTrue(plugin.altarManager().openGui(actor));
        plugin.altarManager().handleGuiClick(actor, 0);

        assertTrue(plugin.dataStore().isDead(victim.getUniqueId()));
    }
}
