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

/**
 * Applies the configured penalty (wither, slowness, percentage lightning damage)
 * to players. Shared by the spawn guard, raid listener and structure listener.
 * Players with {@code bossplugin.admin} are always exempt.
 */
public final class PenaltyApplier {

    private final BossProgressionPlugin plugin;

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
     * Applies the penalty to a single player (admin players are exempt).
     */
    public void applyTo(Player player, BossTier offendingTier) {
        PenaltyConfig penalty = plugin.configManager().getPenaltyConfig();
        if (!penalty.isEnabled()) {
            return;
        }
        if (player.hasPermission("bossplugin.admin")) {
            return; // admins are exempt
        }

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
}
