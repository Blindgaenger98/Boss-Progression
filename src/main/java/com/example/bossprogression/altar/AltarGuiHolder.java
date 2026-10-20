package com.example.bossprogression.altar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/**
 * Per-player {@link InventoryHolder} for the revive altar GUI.
 * <p>
 * Each player gets their own holder instance with the dead-player UUIDs
 * captured at the moment their GUI was opened. This makes the owner of a
 * clicked slot resolvable via {@code event.getInventory().getHolder()} without
 * any shared (and therefore colliding) slot -> UUID map: when another player
 * opens the GUI, the first player's mapping stays intact.
 */
public final class AltarGuiHolder implements InventoryHolder {

    private final UUID ownerId;
    private final List<UUID> slotOwners;

    /**
     * @param ownerId    the UUID of the player whose GUI this holder belongs to
     * @param slotOwners index i holds the dead player UUID shown in slot i;
     *                   slots beyond the list are empty
     */
    public AltarGuiHolder(UUID ownerId, List<UUID> slotOwners) {
        this.ownerId = ownerId;
        this.slotOwners = List.copyOf(slotOwners);
    }

    /** @return the UUID of the player this GUI instance belongs to. */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * @param slot the GUI slot to resolve
     * @return the dead player UUID shown in that slot, or null for empty slots
     */
    public UUID getSlotOwner(int slot) {
        if (slot < 0 || slot >= slotOwners.size()) {
            return null;
        }
        return slotOwners.get(slot);
    }

    @Override
    public Inventory getInventory() {
        // The GUI inventory is created with this holder via
        // Bukkit.createInventory(holder, size, title); the plugin never calls
        // this method back on the holder.
        return null;
    }
}
