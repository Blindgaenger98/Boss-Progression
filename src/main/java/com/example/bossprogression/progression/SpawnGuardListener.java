package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.Optional;

/**
 * Enforces the sequential boss progression.
 * <ul>
 *   <li>{@link EntitySpawnEvent}: any boss entity type from the config that is not the
 *       next allowed tier is cancelled and nearby players are penalized.</li>
 *   <li>{@link PlayerInteractAtEntityEvent}: right-clicking a boss entity that is not
 *       unlocked yet is cancelled and penalized.</li>
 * </ul>
 */
public final class SpawnGuardListener implements Listener {

    private final BossProgressionPlugin plugin;

    public SpawnGuardListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /** Resolves which configured entity tier (if any) an entity type belongs to. */
    private Optional<BossTier> tierFor(EntityType type) {
        return plugin.configManager().getBossTiers().stream()
                .filter(t -> t.isEntityTypeTier() && t.entityType() == type)
                .findFirst();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Optional<BossTier> tier = tierFor(event.getEntityType());
        if (tier.isEmpty()) {
            return;
        }

        // Third-party hooks may veto boss spawns independently of progression.
        if (plugin.hookManager().anyVetoSpawn(event.getEntity(), tier.get())) {
            event.setCancelled(true);
            plugin.getLogger().info("Spawn of " + event.getEntityType()
                    + " vetoed by a boss hook.");
            return;
        }

        if (plugin.progressionManager().isAllowed(tier.get().tierNumber())) {
            return;
        }

        event.setCancelled(true);
        plugin.getLogger().info("Cancelled out-of-sequence spawn of "
                + event.getEntityType() + " (tier " + tier.get().tierNumber() + ", allowed: "
                + (plugin.progressionManager().getCurrentTier() + 1) + ")");

        plugin.penaltyApplier().applyAround(event.getLocation(), tier.get());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        Optional<BossTier> tier = tierFor(entity.getType());
        if (tier.isEmpty() || plugin.progressionManager().isAllowed(tier.get().tierNumber())) {
            return;
        }

        event.setCancelled(true);
        plugin.penaltyApplier().applyAround(entity.getLocation(), tier.get());
    }
}
