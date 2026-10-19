package com.example.bossprogression.altar;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Listens for right-clicks on the registered altar block and for clicks inside
 * the altar GUI.
 */
public final class AltarListener implements Listener {

    private final AltarManager altarManager;

    public AltarListener(AltarManager altarManager) {
        this.altarManager = altarManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Location location = block.getLocation();
        if (!altarManager.isAltarBlock(location)) {
            return;
        }
        if (block.getType() != altarManager.getAltarBlock()) {
            // The block type changed since the altar was registered.
            player.sendMessage("§cThe altar block was changed - please re-register with /bossadmin setaltar.");
            return;
        }
        altarManager.openGui(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!altarManager.getGuiTitle().equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        altarManager.handleGuiClick(player, event.getSlot());
    }
}
