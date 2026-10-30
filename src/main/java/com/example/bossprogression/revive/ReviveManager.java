package com.example.bossprogression.revive;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

    private static final int REGENERATION_TICKS = 10 * 20; // 10 seconds
    private static final int SPEED_BUFF_TICKS = 5 * 20;    // 5 seconds

    private final BossProgressionPlugin plugin;
    private final ReviveItemValidator validator;

    public ReviveManager(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.validator = new ReviveItemValidator(plugin);
    }

    /** @return the item validator owned by this manager (single source of truth). */
    public ReviveItemValidator validator() {
        return validator;
    }

    /**
     * Attempts to revive the given player, consuming one revive item from the
     * actor's inventory (unless forced).
     *
     * @param target  the dead (spectator) player to revive
     * @param actor   the player performing the revive (may be an admin for force revives)
     * @param location where the target is teleported to (altar or actor location)
     * @param force   when true, no item is consumed and the dead check is bypassed
     *                (used by /bossadmin revive)
     * @return the result of the attempt
     */
    public ReviveResult revive(Player target, Player actor, Location location, boolean force) {
        return revive(target, actor, location, force, !force);
    }

    /**
     * Attempts to revive the given player.
     * <p>
     * Consumption happens here and only here: callers must NOT consume the revive
     * item themselves (that used to cause a double consumption in the altar flow).
     *
     * @param target  the dead (spectator) player to revive
     * @param actor   the player performing the revive (may be an admin for force revives)
     * @param location where the target is teleported to (altar or actor location)
     * @param force   when true, the dead check is bypassed (used by /bossadmin revive)
     * @param consume when true (and not forced), one matching revive item is removed
     *                from the actor's storage inventory; a missing item yields
     *                {@link ReviveResult#NO_ITEM}
     * @return the result of the attempt
     */
    public ReviveResult revive(Player target, Player actor, Location location,
                               boolean force, boolean consume) {
        if (!force && !plugin.dataStore().isDead(target.getUniqueId())) {
            return ReviveResult.NOT_DEAD;
        }
        if (!force && target.getUniqueId().equals(actor.getUniqueId())) {
            return ReviveResult.CANNOT_REVIVE_SELF;
        }

        if (!force && consume) {
            BossTier tier = validator.latestUnlockedTier();
            if (tier == null
                    || !validator.consumeOne(actor.getInventory(), tier.reviveItem())) {
                return ReviveResult.NO_ITEM;
            }
        }

        Location destination = location != null ? location.clone() : target.getLocation();

        target.setGameMode(GameMode.SURVIVAL);
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            target.setHealth(maxHealth.getValue());
        }
        target.setFoodLevel(20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                REGENERATION_TICKS, 1, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                SPEED_BUFF_TICKS, 0, true, true, true));
        target.setFireTicks(0);

        // The teleport must succeed before the player is marked alive; otherwise
        // the player would be counted as revived while stranded at the old location.
        if (!target.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            plugin.getLogger().log(Level.WARNING,
                    "Revive teleport for {0} to world {1} failed; player was not marked alive",
                    new String[] { target.getName(), destination.getWorld().getName() });
            return ReviveResult.NOT_DEAD;
        }

        plugin.dataStore().markAlive(target.getUniqueId());

        // Visual feedback at the destination.
        destination.getWorld().playSound(destination, "entity.ender_dragon.growl", 1.0f, 1.0f);
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
        if (altar != null && altar.getWorld() != null
                && target.getWorld() != null
                && altar.getWorld().equals(target.getWorld())) {
            return altar;
        }
        if (actor != null) {
            return actor.getLocation();
        }
        return target.getWorld().getSpawnLocation();
    }
}
