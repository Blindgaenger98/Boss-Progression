package com.example.bossprogression.altar;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AltarManager}.
 *
 * <p>Phase-1 regression tests: {@link AltarManager#handleGuiClick} must not
 * double-consume the revive item and must keep the victim dead when the actor
 * has no item (review finding #2).
 *
 * <p>Fix #5 (Top-10 #6): the two-player collision test proves that opening the
 * GUI of a second player no longer corrupts the slot -> dead player mapping of
 * the first player. Since the mapping now lives on the per-player
 * {@link AltarGuiHolder} attached to each GUI inventory, the test resolves the
 * holder the same way {@link AltarListener} does
 * ({@code event.getInventory().getHolder()}), using the player's open
 * inventory view as MockBukkit provides it
 * ({@code player.getOpenInventory().getTopInventory()}).
 */
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
        AltarGuiHolder holder = (AltarGuiHolder) topInventory(actor).getHolder();
        plugin.altarManager().handleGuiClick(actor, holder, 0);

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
        AltarGuiHolder holder = (AltarGuiHolder) topInventory(actor).getHolder();
        plugin.altarManager().handleGuiClick(actor, holder, 0);

        assertTrue(plugin.dataStore().isDead(victim.getUniqueId()));
        assertNull(actor.getInventory().getItem(0));
    }

    /**
     * Fix #5 / Top-10 #6: two players open the altar GUI; the second player
     * must not corrupt the first player's slot mapping.
     *
     * <p>Scenario: dead player D belongs to player A's view. Player A opens
     * the GUI, then player B opens his own GUI. When A clicks the slot with D,
     * the revive must target D — and not be lost or redirected because B's GUI
     * open cleared a shared slot -> UUID map (old implementation).
     *
     * <p>Implementation note: MockBukkit 4.x tracks each player's open view in
     * {@code HumanEntityMock#inventoryView} ({@code openInventory(Inventory)}
     * sets it), so the test resolves the holder through the real Bukkit API
     * path — exactly what {@link AltarListener} does for
     * {@code InventoryClickEvent}.
     */
    @Test
    void secondPlayerOpeningGuiDoesNotCorruptFirstPlayersSlotMapping() {
        setupAltar();
        PlayerMock victim = addDeadPlayer("Victim");
        PlayerMock playerA = server.addPlayer("PlayerA");
        PlayerMock playerB = server.addPlayer("PlayerB");

        BossTier tier1 = plugin.configManager().getTier(1);
        playerA.getInventory().setItem(0, tier1.reviveItem().createItem(plugin, 1));

        // A opens the GUI ...
        assertTrue(plugin.altarManager().openGui(playerA));
        AltarGuiHolder holderA = (AltarGuiHolder) topInventory(playerA).getHolder();
        assertEquals(playerA.getUniqueId(), holderA.getOwnerId());
        assertEquals(victim.getUniqueId(), holderA.getSlotOwner(0));

        // ... then B opens his own GUI. With the old shared slotOwners map
        // this step wiped A's mapping (slotOwners.clear()).
        assertTrue(plugin.altarManager().openGui(playerB));

        // A's mapping must survive and point at the correct dead player.
        assertEquals(playerA.getUniqueId(), holderA.getOwnerId(),
                "A's GUI must still belong to A");
        assertEquals(victim.getUniqueId(), holderA.getSlotOwner(0),
                "A's slot 0 must still map to the dead player A saw");
        assertEquals(holderA, topInventory(playerA).getHolder(),
                "A's open inventory must still carry A's holder");

        // Now A clicks: the revive must reach the correct (A's seen) target.
        plugin.altarManager().handleGuiClick(playerA, holderA, 0);

        assertFalse(plugin.dataStore().isDead(victim.getUniqueId()),
                "A's click must have revived A's target dead player");
        assertNull(playerA.getInventory().getItem(0),
                "revive item must be consumed exactly once");
        assertNull(playerB.getInventory().getItem(0),
                "B must not be affected by A's click");
    }

    /**
     * Unit test for {@link AltarGuiHolder}: the per-player slot mapping is
     * immutable and index-safe (out-of-range / negative slots resolve to null).
     */
    @Test
    void guiHolderResolvesSlotOwnersPerPlayer() {
        java.util.UUID ownerA = java.util.UUID.randomUUID();
        java.util.UUID ownerB = java.util.UUID.randomUUID();
        java.util.UUID dead1 = java.util.UUID.randomUUID();
        java.util.UUID dead2 = java.util.UUID.randomUUID();

        AltarGuiHolder holderA = new AltarGuiHolder(ownerA, java.util.List.of(dead1, dead2));
        AltarGuiHolder holderB = new AltarGuiHolder(ownerB, java.util.List.of(dead2));

        // Independent mappings: B's single slot never leaks into A's view.
        assertEquals(dead1, holderA.getSlotOwner(0));
        assertEquals(dead2, holderA.getSlotOwner(1));
        assertNull(holderA.getSlotOwner(2));
        assertNull(holderA.getSlotOwner(-1));

        assertEquals(dead2, holderB.getSlotOwner(0));
        assertNull(holderB.getSlotOwner(1));
        assertEquals(ownerA, holderA.getOwnerId());
        assertEquals(ownerB, holderB.getOwnerId());
    }

    /** @return the top inventory of the player's currently open view (the altar GUI). */
    private Inventory topInventory(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        assertTrue(top != null && top.getHolder() instanceof AltarGuiHolder,
                "expected the player's open inventory to be an altar GUI");
        return top;
    }
}
