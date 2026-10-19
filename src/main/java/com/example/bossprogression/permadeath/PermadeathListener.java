package com.example.bossprogression.permadeath;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the conditional permadeath rule:
 * <ul>
 *   <li>tier 0 (preparation phase): normal vanilla death, no tracking.</li>
 *   <li>tier > 0: the player becomes a persistent SPECTATOR ("ghost") and is
 *       tracked in the {@link com.example.bossprogression.data.DataStore} until
 *       revived via /revive or the altar.</li>
 * </ul>
 */
public final class PermadeathListener implements Listener {

    private final BossProgressionPlugin plugin;

    public PermadeathListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.progressionManager().getCurrentTier() == 0) {
            return; // preparation phase - vanilla behavior
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return; // already a ghost
        }

        // Optional: drop inventory on death (config: permadeath.clear-inventory-on-death).
        // getDrops() is read-only in Paper 26, so items are dropped via the world.
        if (plugin.configManager().isClearInventoryOnDeath()) {
            PlayerInventory inventory = player.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack item : inventory.getStorageContents()) {
                if (item != null) {
                    items.add(item);
                }
            }
            for (ItemStack armor : inventory.getArmorContents()) {
                if (armor != null) {
                    items.add(armor);
                }
            }
            ItemStack offhand = inventory.getItemInOffHand();
            if (offhand != null) {
                items.add(offhand);
            }
            for (ItemStack item : items) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            inventory.clear();
        }

        player.setGameMode(GameMode.SPECTATOR);
        plugin.dataStore().markDead(player.getUniqueId());
        plugin.messages().send(player, "permadeath.death-message");

        plugin.getLogger().info(player.getName() + " died at tier "
                + plugin.progressionManager().getCurrentTier() + " - now spectator.");
    }

    /**
     * Recovery: if a player marked as dead rejoins (server restart, /spectate abuse,
     * relog), force them back into spectator mode until revived.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.dataStore().isDead(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
            plugin.messages().send(player, "permadeath.death-message");
        }
        plugin.getLogger().info(player.getName() + " rejoined while marked dead - forced to spectator.");
    }
}
