package com.example.bossprogression.revive;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.GameMode;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.logging.Level;

/**
 * Orchestrates the revive process: item consumption, game mode switch,
 * teleportation, post-revive buffs and notifications.
 * <p>
 * Callers (ReviveCommand, AltarManager, BossAdminCommand) are responsible for
 * permission checks and user-facing error messages; this class returns a
 * {@link ReviveResult} so the caller can pick the right message key.
 */
public final class ReviveManager {

    /** Result of a revive attempt. */
    public enum ReviveResult {
        /** The player was revived successfully. */
        SUCCESS,
        /** The target is not in the dead player list. */
        NOT_DEAD,
        /** The actor tried to revive themselves. */
        CANNOT_REVIVE_SELF,
        /** The actor did not have the required revive item (or no boss was defeated yet). */
        NO_ITEM
    }

    private static final int INVULNERABILITY_TICKS = 10 * 20; // 10 seconds
    private static final int REGENERATION_TICKS = 10 * 20;    // 10 seconds

    private final BossProgressionPlugin plugin;
    private final ReviveItemValidator validator;

    public ReviveManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.validator = new ReviveItemValidator(plugin);
    }

    /**
     * Attempts to revive the given player.
     *
     * @param target  the dead (spectator) player to revive
     * @param actor   the player performing the revive (may be an admin for force revives)
     * @param location where the target is teleported to (altar or actor location)
     * @param force   when true, no item is consumed and the dead check is bypassed
     *                (used by /bossadmin revive)
     * @return the result of the attempt
     */
    public ReviveResult revive(Player target, Player actor, Location location, boolean force) {
        if (!force && !plugin.dataStore().isDead(target.getUniqueId())) {
            return ReviveResult.NOT_DEAD;
        }
        if (!force && target.getUniqueId().equals(actor.getUniqueId())) {
            return ReviveResult.CANNOT_REVIVE_SELF;
        }

        if (!force) {
            BossTier tier = validator.latestUnlockedTier();
            if (tier == null
                    || !validator.consumeOne(actor.getInventory().getStorageContents(), tier.reviveItem())) {
                return ReviveResult.NO_ITEM;
            }
        }

        Location destination = location != null ? location.clone() : target.getLocation();

        plugin.dataStore().markAlive(target.getUniqueId());
        target.setGameMode(GameMode.SURVIVAL);
        target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        target.setFoodLevel(20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.getByName("REGENERATION"),
                REGENERATION_TICKS, 1, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.getByName("SPEED"),
                INVULNERABILITY_TICKS / 2, 0, true, true, true));
        target.setFireTicks(0);
        target.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // Visual feedback at the destination.
        destination.getWorld().playSound(destination, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        destination.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, destination, 60, 0.5, 0.5, 0.5, 0.2);

        // Notifications.
        plugin.messages().send(target, "permadeath.revived");
        plugin.messages().broadcast("permadeath.revived-by",
                Map.of("player", target.getName()));

        plugin.getLogger().log(Level.INFO, "Player {0} was revived{1} at {2} ({3}, {4}, {5})",
                new String[] { target.getName(),
                        force ? " (forced by admin)" : " by " + actor.getName(),
                        destination.getWorld().getName(),
                        String.valueOf(destination.getBlockX()),
                        String.valueOf(destination.getBlockY()),
                        String.valueOf(destination.getBlockZ()) });

        return ReviveResult.SUCCESS;
    }

    /**
     * @return the location of the registered revive altar, or null if not set.
     */
    public Location getAltarLocation() {
        return plugin.dataStore().getAltarLocation();
    }

    /**
     * Resolves the revive destination for the given target: the registered altar
     * if it exists and the target is in its world, otherwise the actor's location,
     * otherwise the world spawn as a last resort.
     */
    public Location resolveDestination(Player target, Player actor) {
        Location altar = plugin.dataStore().getAltarLocation();
        if (altar != null && altar.getWorld() != null) {
            return altar;
        }
        if (actor != null) {
            return actor.getLocation();
        }
        return target.getWorld().getSpawnLocation();
    }
}
