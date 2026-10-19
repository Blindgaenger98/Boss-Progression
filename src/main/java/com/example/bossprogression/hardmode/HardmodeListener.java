package com.example.bossprogression.hardmode;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.Material;

/**
 * Applies hardmode scaling when monsters spawn and enforces the optional
 * nerfs (health regeneration, golden apples).
 */
public final class HardmodeListener implements Listener {

    private final BossProgressionPlugin plugin;
    private final HardmodeManager hardmodeManager;

    public HardmodeListener(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.hardmodeManager = new HardmodeManager(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            hardmodeManager.applyTo(living);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        // Monsters loaded from chunks are not "spawned" - scale them on teleport too.
        if (event.getEntity() instanceof LivingEntity living) {
            hardmodeManager.applyTo(living);
        }
    }

    /** Nerf: health regeneration is disabled for all players when enabled in config. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!pluginConfig().isNerfHealthRegeneration()) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && player.getFoodLevel() < 20
                && event.getFoodLevel() == 20) {
            // Cap food at 19: block the automatic regeneration tick reaching 20.
            player.setFoodLevel(19);
        }
    }

    /** Nerf: golden apples no longer grant effects. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!pluginConfig().isNerfGoldenApple()) {
            return;
        }
        if (event.getItem().getType() == Material.GOLDEN_APPLE) {
            event.setCancelled(true);
        }
    }

    private com.example.bossprogression.config.HardmodeConfig pluginConfig() {
        return plugin.configManager().getHardmodeConfig();
    }
}
