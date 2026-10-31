package com.example.bossprogression.altar;

import com.example.bossprogression.PluginTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AltarListener} (Fix-12a test backlog): the block
 * right-click trigger for the altar GUI and the auto-close task lifecycle
 * (cancelled on {@code InventoryCloseEvent} and {@code PlayerQuitEvent},
 * never stacked on re-open).
 *
 * <p>Setup mirrors {@link AltarManagerTest}: the altar is registered via
 * {@code DataStore#setAltarLocation} + {@code ProgressionManager#setTier},
 * dead players via {@code DataStore#markDead}. Events are dispatched through
 * {@code server.getPluginManager().callEvent(...)} — the listener is
 * auto-registered by the plugin load in {@link PluginTestBase}.
 *
 * <p>Auto-close task state is observed through the scheduler's pending
 * plugin tasks (same technique as {@link PluginLifecycleTest}), using
 * deltas against a pre-open baseline so the assertions stay independent
 * of other plugin-owned tasks (e.g. the structure poll timer).
 */
class AltarListenerTest extends PluginTestBase {

    private void setupAltar() {
        plugin.progressionManager().setTier(1);
        plugin.dataStore().setAltarLocation(new Location(world, 0, 64, 0));
    }

    private PlayerMock addDeadPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        plugin.dataStore().markDead(player.getUniqueId());
        return player;
    }

    /** @return the configured altar block (material from config.yml, not hardcoded here). */
    private Block altarBlock() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(plugin.altarManager().getAltarBlock());
        return block;
    }

    private PlayerInteractEvent rightClick(PlayerMock player, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.STICK), block, BlockFace.UP);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private boolean hasAltarGui(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof AltarGuiHolder;
    }

    private List<BukkitTask> pendingPluginTasks() {
        return server.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == plugin)
                .toList();
    }

    @Test
    void rightClickOnAltarBlockOpensGui() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Priest");

        PlayerInteractEvent event = rightClick(player, altarBlock());

        assertFalse(event.isCancelled(), "a successful GUI open must not cancel the interact");
        assertTrue(hasAltarGui(player), "right-clicking the altar block must open the GUI");
        assertInstanceOf(AltarGuiHolder.class,
                player.getOpenInventory().getTopInventory().getHolder());
        assertEquals(player.getUniqueId(),
                ((AltarGuiHolder) player.getOpenInventory().getTopInventory().getHolder()).getOwnerId(),
                "the GUI holder must belong to the player who opened it");
    }

    @Test
    void rightClickOnForeignBlockMaterialDoesNothing() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Curious");
        Block stone = world.getBlockAt(5, 64, 5);
        stone.setType(Material.STONE);

        PlayerInteractEvent event = rightClick(player, stone);

        assertFalse(event.isCancelled(), "a non-altar block must not be cancelled");
        assertNull(player.nextComponentMessage());
        assertFalse(hasAltarGui(player), "no GUI may open for a non-altar block material");
    }

    @Test
    void rightClickWithoutAltarPermissionIsDenied() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("NoPerm");
        player.addAttachment(plugin, "bossplugin.altar", false);

        PlayerInteractEvent event = rightClick(player, altarBlock());

        assertTrue(event.isCancelled(), "the interact must be cancelled without permission");
        assertEquals(LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.messages().raw("no-permission")),
                player.nextComponentMessage(), "the no-permission message must be sent");
        assertNull(player.nextComponentMessage());
        assertFalse(hasAltarGui(player), "no GUI may open without permission");
    }

    @Test
    void rightClickWithoutRegisteredAltarSendsNotSet() {
        // No setAltarLocation call — the permission check passes (default true),
        // but openGui() must report the missing altar.
        plugin.progressionManager().setTier(1);
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Lost");

        rightClick(player, altarBlock());

        assertEquals(LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.messages().raw("altar.not-set")),
                player.nextComponentMessage(), "the altar.not-set message must be sent");
        assertNull(player.nextComponentMessage());
        assertFalse(hasAltarGui(player), "no GUI may open without a registered altar");
    }

    @Test
    void inventoryCloseCancelsAutoCloseTask() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Closer");

        int baseline = pendingPluginTasks().size();
        rightClick(player, altarBlock());
        assertTrue(hasAltarGui(player), "precondition: GUI must be open");
        assertEquals(baseline + 1, pendingPluginTasks().size(),
                "opening the GUI must schedule the auto-close task");

        server.getPluginManager().callEvent(new InventoryCloseEvent(player.getOpenInventory()));

        assertEquals(baseline, pendingPluginTasks().size(),
                "closing the GUI must cancel the auto-close task (no leaked task)");
    }

    @Test
    void playerQuitCancelsAutoCloseTask() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Leaver");

        int baseline = pendingPluginTasks().size();
        rightClick(player, altarBlock());
        assertTrue(hasAltarGui(player), "precondition: GUI must be open");
        assertEquals(baseline + 1, pendingPluginTasks().size(),
                "opening the GUI must schedule the auto-close task");

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.text("quit"),
                PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertEquals(baseline, pendingPluginTasks().size(),
                "quitting must cancel the auto-close task (no task left behind)");
    }

    @Test
    void reopeningGuiDoesNotStackAutoCloseTasks() {
        setupAltar();
        addDeadPlayer("Victim");
        PlayerMock player = server.addPlayer("Opener");

        int baseline = pendingPluginTasks().size();
        rightClick(player, altarBlock());
        assertTrue(hasAltarGui(player), "precondition: first GUI must be open");
        assertEquals(baseline + 1, pendingPluginTasks().size(),
                "first open must schedule exactly one auto-close task");

        rightClick(player, altarBlock());

        assertEquals(baseline + 1, pendingPluginTasks().size(),
                "re-opening must cancel the previous task, not add a second one");
    }
}
