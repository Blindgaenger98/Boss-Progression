package com.example.bossprogression.altar;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.revive.ReviveItemValidator;
import com.example.bossprogression.revive.ReviveManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the physical revive altar: block validation, the dead-player chest GUI
 * and the revive flow triggered from it (item consumption + buffs via ReviveManager).
 */
public final class AltarManager {

    private final BossProgressionPlugin plugin;
    private final ReviveManager reviveManager;
    private final ReviveItemValidator validator;

    /** Maps inventory slot -> dead player UUID for the altar GUI. */
    private final Map<Integer, UUID> slotOwners = new HashMap<>();

    /** Player UUID -> open altar GUI task (to close it when they move away). */
    private final Map<UUID, BukkitTask> openTasks = new HashMap<>();

    public AltarManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.reviveManager = new ReviveManager(plugin);
        this.validator = new ReviveItemValidator(plugin);
    }

    /**
     * @return true when the given location is the registered altar block.
     */
    public boolean isAltarBlock(Location location) {
        Location altar = plugin.dataStore().getAltarLocation();
        if (altar == null || location.getWorld() == null || altar.getWorld() == null) {
            return false;
        }
        return location.getWorld().equals(altar.getWorld())
                && location.getBlockX() == altar.getBlockX()
                && location.getBlockY() == altar.getBlockY()
                && location.getBlockZ() == altar.getBlockZ();
    }

    /** @return the configured altar block material. */
    public org.bukkit.Material getAltarBlock() {
        return plugin.configManager().getAltarConfig().getAltarBlock();
    }

    /** @return the GUI title as configured in messages.yml. */
    public String getGuiTitle() {
        return plugin.messages().plain("altar.gui-title");
    }

    /**
     * Opens the revive GUI showing all dead players.
     *
     * @return true when the GUI was opened, false when there is nothing to show
     *         (no dead players / no unlocked tier).
     */
    public boolean openGui(Player player) {
        if (!plugin.configManager().getAltarConfig().isEnabled()) {
            plugin.messages().send(player, "altar.not-set");
            return false;
        }
        if (plugin.dataStore().getAltarLocation() == null) {
            plugin.messages().send(player, "altar.not-set");
            return false;
        }
        if (validator.latestUnlockedTier() == null) {
            plugin.messages().send(player, "permadeath.no-item");
            return false;
        }

        Set<UUID> dead = plugin.dataStore().getDeadPlayers();
        if (dead.isEmpty()) {
            plugin.messages().send(player, "permadeath.no-dead-players");
            return false;
        }

        slotOwners.clear();
        Inventory gui = Bukkit.createInventory(null, 27,
                plugin.messages().plain("altar.gui-title"));

        int slot = 0;
        for (UUID uuid : dead) {
            if (slot >= 27) {
                break;
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            ItemStack entry = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta meta = entry.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.messages().plain("altar.dead-player-entry",
                        Map.of("player", name == null ? "?" : name)));
                entry.setItemMeta(meta);
            }
            gui.setItem(slot, entry);
            slotOwners.put(slot, uuid);
            slot++;
        }

        player.closeInventory();
        player.openInventory(gui);

        // Close the GUI automatically if the player moves more than a few blocks away.
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.getLocation().distanceSquared(
                    plugin.dataStore().getAltarLocation()) > 16 * 16) {
                player.closeInventory();
                cancelOpenTask(player.getUniqueId());
            }
        }, 10L, 10L);
        openTasks.put(player.getUniqueId(), task);
        return true;
    }

    /**
     * Handles a click inside the altar GUI: revives the owner of the clicked slot
     * and consumes one revive item from the clicking player's inventory.
     */
    public void handleGuiClick(Player player, int rawSlot) {
        int slot = rawSlot < 27 ? rawSlot : -1;
        if (slot < 0) {
            return;
        }
        UUID uuid = slotOwners.get(slot);
        if (uuid == null) {
            return;
        }
        Player target = Bukkit.getPlayer(uuid);
        if (target == null) {
            return; // offline, entry stays until revived
        }

        if (uuid.equals(player.getUniqueId())) {
            plugin.messages().send(player, "permadeath.cannot-revive-self");
            return;
        }

        BossTier tier = validator.latestUnlockedTier();
        if (tier == null
                || !validator.consumeOne(player.getInventory().getStorageContents(), tier.reviveItem())) {
            plugin.messages().send(player, "permadeath.no-item");
            return;
        }

        Location altar = plugin.dataStore().getAltarLocation();
        ReviveManager.ReviveResult result = reviveManager.revive(target, player, altar, false);
        if (result != ReviveManager.ReviveResult.SUCCESS) {
            // Should not happen (we consumed the item first); refund is out of scope,
            // but log for debugging.
            plugin.getLogger().warning("Altar revive failed with result " + result
                    + " for target " + target.getName());
        }

        // Refresh the GUI (dead player list changed).
        cancelOpenTask(player.getUniqueId());
        openGui(player);
    }

    private void cancelOpenTask(UUID uuid) {
        BukkitTask task = openTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void close() {
        for (BukkitTask task : new ArrayList<>(openTasks.values())) {
            task.cancel();
        }
        openTasks.clear();
    }
}
