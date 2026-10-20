package com.example.bossprogression.altar;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks inside the altar GUI: resolves the clicked slot to the
 * dead player shown in it (via the per-player {@link AltarGuiHolder}) and
 * delegates the revive to {@link AltarManager#handleGuiClick}.
 */
public final class AltarListener implements Listener {

    private final AltarManager altarManager;

    public AltarListener(BossProgressionPlugin plugin) {
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
}
