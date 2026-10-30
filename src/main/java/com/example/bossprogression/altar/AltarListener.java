package com.example.bossprogression.altar;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles everything around the altar GUI:
 * <ul>
 *   <li>Right-clicking the configured altar block material opens the revive
 *       GUI (permission-gated, message feedback via messages.yml).</li>
 *   <li>Clicks inside the GUI are resolved to the dead player shown in the
 *       clicked slot (via the per-player {@link AltarGuiHolder}) and
 *       delegated to {@link AltarManager#handleGuiClick}.</li>
 *   <li>Closing the GUI (or quitting) cancels the per-player auto-close task
 *       so no scheduler task ever keeps running for a player who no longer
 *       has the GUI open.</li>
 * </ul>
 */
public final class AltarListener implements Listener {

    private final BossProgressionPlugin plugin;
    private final AltarManager altarManager;

    public AltarListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.altarManager = plugin.altarManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // The per-player holder identifies the GUI (and its owner) without
        // any shared state or title comparison.
        if (!(event.getInventory().getHolder() instanceof AltarGuiHolder holder)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        // Never allow items to be taken from / moved in the altar GUI.
        event.setCancelled(true);
        altarManager.handleGuiClick(player, holder, event.getSlot());
    }

    /**
     * Right-clicking the altar block material opens the revive GUI.
     * The block material comes from the {@code altar} config section
     * (never hardcoded).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != altarManager.getAltarBlock()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("bossplugin.altar")) {
            plugin.messages().send(player, "no-permission");
            event.setCancelled(true);
            return;
        }
        // openGui() sends the matching messages.yml feedback itself when no
        // altar is registered / no dead players / no unlocked tier.
        altarManager.openGui(player);
    }

    /**
     * When the player closes the altar GUI, cancel the per-player auto-close
     * task and drop the entry so it cannot leak or fire after the close.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Holder check: only clean up for the altar GUI, not for any
        // other inventory the player might close.
        if (event.getInventory().getHolder() instanceof AltarGuiHolder) {
            altarManager.cancelOpenTask(player.getUniqueId());
        }
    }

    /** Players leaving the server must not leave a running auto-close task behind. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        altarManager.cancelOpenTask(event.getPlayer().getUniqueId());
    }

}
