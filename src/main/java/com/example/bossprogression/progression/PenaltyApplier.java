package com.example.bossprogression.progression;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.PenaltyConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the configured penalty (wither, slowness, percentage lightning damage)
 * to players. Shared by the spawn guard, raid listener and structure listener.
 * Players with {@code bossplugin.admin} are always exempt.
 *
 * <p>Per-player cooldown: within {@code penalty.cooldown-seconds} after a penalty
 * was applied, further penalties for the same player are skipped (checked after
 * the admin exemption, before any effect is applied). Timestamps use the server
 * tick counter so the cooldown is deterministic and testable.</p>
 */
public final class PenaltyApplier {

    private final BossProgressionPlugin plugin;

    /** Last penalty tick per player UUID; removed when the player disconnects. */
    private final Map<UUID, Long> lastPenaltyTicks = new ConcurrentHashMap<>();

    public PenaltyApplier(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the penalty to all players within the configured radius of {@code center}.
     */
    public void applyAround(Location center, BossTier offendingTier) {
        PenaltyConfig penalty = plugin.configManager().getPenaltyConfig();
        if (!penalty.isEnabled()) {
            return;
        }
        for (Player player : center.getNearbyPlayers((int) penalty.getRadius())) {
            applyTo(player, offendingTier);
        }
    }

    /**
     * Applies the penalty to a single player (admin players are exempt, players
     * within the per-player cooldown are skipped silently).
     */
    public void applyTo(Player player, BossTier offendingTier) {
        PenaltyConfig penalty = plugin.configManager().getPenaltyConfig();
        if (!penalty.isEnabled()) {
            return;
        }
        if (player.hasPermission("bossplugin.admin")) {
            return; // admins are exempt
        }
        if (onCooldown(player, penalty.getCooldownSeconds())) {
            return; // inside per-player cooldown, skip silently
        }
        lastPenaltyTicks.put(player.getUniqueId(), currentTick());

        String bossName = offendingTier.displayName().replace('&', '\u00a7');
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER, penalty.getEffectDurationTicks(),
                Math.max(0, penalty.getWitherAmplifier()), false, false, true));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, penalty.getEffectDurationTicks(),
                Math.max(0, penalty.getSlownessAmplifier()), false, false, true));

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.damage(maxHealth * (penalty.getLightningDamagePercent() / 100.0));
        // Visual lightning strike at the player.
        player.getWorld().strikeLightningEffect(player.getLocation());

        plugin.messages().send(player, "premature-attempt.spawn-cancelled");
        player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize("&c" + bossName + " is not ready yet!"));
    }

    /**
     * Called on player quit to keep the cooldown map free of stale entries.
     */
    public void onPlayerQuit(UUID playerId) {
        lastPenaltyTicks.remove(playerId);
    }

    /**
     * @return true if the player's last penalty was applied less than
     *         {@code cooldownSeconds} (in ticks: seconds * 20) ago.
     */
    private boolean onCooldown(Player player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return false; // cooldown disabled
        }
        Long last = lastPenaltyTicks.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        return currentTick() - last < cooldownSeconds * 20L;
    }

    private long currentTick() {
        return plugin.getServer().getCurrentTick();
    }
}
