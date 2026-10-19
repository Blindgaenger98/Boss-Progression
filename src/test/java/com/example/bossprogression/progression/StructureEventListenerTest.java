package com.example.bossprogression.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.TrialSpawner;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link StructureEventListener}: interact gating and the trial-spawner clear polling cycle.
 *
 * <p>Package-private here so {@link StructureEventListener#pollSpawners()} can be invoked directly.
 */
class StructureEventListenerTest extends PluginTestBase {

    // Must stay contiguous with the default tiers 1-3 in config.yml (tier numbers are 1..N).
    private static final int STRUCTURE_TIER = 4;

    private void setupStructureTier() {
        plugin.getConfig().set("boss-tiers.trial-chamber", Map.of(
                "tier", STRUCTURE_TIER,
                "type", "STRUCTURE_CLEAR",
                "structure-kind", "TRIAL_CHAMBER",
                "required-spawner-clears", 2,
                "revive-item", Map.of(
                        "material", "TRIAL_KEY",
                        "pdc-key", "revive",
                        "pdc-value", "tier4")));
        plugin.saveConfig();
        assertTrue(plugin.configManager().load());
    }

    private Block spawnerAt(int x, int z) {
        Block block = world.getBlockAt(x, 70, z);
        block.setType(Material.TRIAL_SPAWNER);
        return block;
    }

    private void setSpawnerState(Block block, TrialSpawner.State state) {
        TrialSpawner data = (TrialSpawner) block.getBlockData();
        data.setTrialSpawnerState(state);
        block.setBlockData(data);
    }

    private void interactWithSpawner(Player player, Block spawner) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STICK), spawner, BlockFace.UP);
        server.getPluginManager().callEvent(event);
    }

    @Test
    void interactBlockedWhenStructureTierIsNotNext() {
        setupStructureTier();
        assertTrue(plugin.progressionManager().setTier(0));
        Player player = server.addPlayer("Rusher");
        Block spawner = spawnerAt(0, 0);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STICK), spawner, BlockFace.UP);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertTrue(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void interactAllowedWhenStructureTierIsNext() {
        setupStructureTier();
        assertTrue(plugin.progressionManager().setTier(STRUCTURE_TIER - 1));
        Player player = server.addPlayer("Explorer");
        Block spawner = spawnerAt(1, 0);

        interactWithSpawner(player, spawner);

        // No penalty applied when the interaction is allowed.
        assertFalse(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void interactAllowedWhenProgressionComplete() {
        setupStructureTier();
        // Set to the last tier so that the next allowed boss is null (progression complete).
        assertTrue(plugin.progressionManager().setTier(STRUCTURE_TIER));
        Player player = server.addPlayer("Explorer");
        Block spawner = spawnerAt(2, 0);

        interactWithSpawner(player, spawner);

        assertFalse(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void interactWithNonSpawnerBlockIsIgnored() {
        setupStructureTier();
        assertTrue(plugin.progressionManager().setTier(0));
        Player player = server.addPlayer("Rusher");
        Block stone = world.getBlockAt(3, 70, 0);
        stone.setType(Material.STONE);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STICK), stone, BlockFace.UP);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertFalse(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void fullPollCycleClearsTwoSpawnersAndAdvances() {
        setupStructureTier();
        assertTrue(plugin.progressionManager().setTier(STRUCTURE_TIER - 1));
        Player player = server.addPlayer("Explorer");

        Block spawnerA = spawnerAt(0, 0);
        Block spawnerB = spawnerAt(5, 0);

        // Track both spawners via (allowed) interaction.
        interactWithSpawner(player, spawnerA);
        interactWithSpawner(player, spawnerB);

        // Baseline: both active -> no clear yet.
        setSpawnerState(spawnerA, TrialSpawner.State.ACTIVE);
        setSpawnerState(spawnerB, TrialSpawner.State.ACTIVE);
        plugin.structureEventListener().pollSpawners();
        assertEquals(STRUCTURE_TIER - 1, plugin.dataStore().getCurrentBossTier());

        // First spawner cleared -> progress, still below threshold.
        setSpawnerState(spawnerA, TrialSpawner.State.WAITING_FOR_REWARD_EJECTION);
        plugin.structureEventListener().pollSpawners();
        assertEquals(STRUCTURE_TIER - 1, plugin.dataStore().getCurrentBossTier());

        // Second spawner cleared -> threshold reached -> advance + drop revive item.
        setSpawnerState(spawnerB, TrialSpawner.State.EJECTING_REWARD);
        plugin.structureEventListener().pollSpawners();

        assertEquals(STRUCTURE_TIER, plugin.dataStore().getCurrentBossTier());

        boolean itemDropped = false;
        for (Item drop : world.getEntitiesByClass(Item.class)) {
            ItemStack stack = drop.getItemStack();
            if (stack != null && stack.getType() == Material.TRIAL_KEY) {
                PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
                String value = pdc.get(new org.bukkit.NamespacedKey(plugin, "revive"), PersistentDataType.STRING);
                if ("tier4".equals(value)) {
                    itemDropped = true;
                }
            }
        }
        assertTrue(itemDropped, "a TRIAL_KEY revive item with PDC value tier5 should be dropped on the ground");
    }
}
