package com.example.bossprogression.progression;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.PenaltyConfig;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the per-player penalty cooldown (Fix #8): repeated penalties within
 * the configured cooldown must be skipped, a second penalty must apply after
 * the cooldown has elapsed, and admins stay exempt throughout.
 */
class PenaltyApplierTest extends PluginTestBase {

    private BossTier wardenTier() {
        return plugin.configManager().getTier(1);
    }

    private int cooldownTicks() {
        PenaltyConfig penalty = plugin.configManager().getPenaltyConfig();
        return penalty.getCooldownSeconds() * 20;
    }

    @Test
    void repeatedPenaltyWithinCooldownIsSkipped() {
        PlayerMock player = server.addPlayer("Rusher");
        double healthBefore = player.getHealth();

        plugin.penaltyApplier().applyTo(player, wardenTier());
        double healthAfterFirst = player.getHealth();
        assertTrue(healthAfterFirst < healthBefore, "first penalty must damage the player");

        // Second attempt inside the cooldown: no additional damage.
        plugin.penaltyApplier().applyTo(player, wardenTier());
        plugin.penaltyApplier().applyTo(player, wardenTier());
        plugin.penaltyApplier().applyTo(player, wardenTier());

        assertEquals(healthAfterFirst, player.getHealth(),
                "penalties within the cooldown must be skipped");
    }

    @Test
    void secondPenaltyAppliesAfterCooldownElapses() {
        PlayerMock player = server.addPlayer("Rusher");

        plugin.penaltyApplier().applyTo(player, wardenTier());
        double healthAfterFirst = player.getHealth();

        // Fast-forward past the cooldown (ticks, not wall clock).
        server.getScheduler().performTicks(cooldownTicks() + 1);

        plugin.penaltyApplier().applyTo(player, wardenTier());

        assertTrue(player.getHealth() < healthAfterFirst,
                "penalty after cooldown expiry must damage the player again");
    }

    @Test
    void adminIsExemptRegardlessOfCooldownState() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, "bossplugin.admin", true);
        double healthBefore = admin.getHealth();

        plugin.penaltyApplier().applyTo(admin, wardenTier());
        server.getScheduler().performTicks(cooldownTicks() + 1);
        plugin.penaltyApplier().applyTo(admin, wardenTier());

        assertEquals(healthBefore, admin.getHealth(), "admins must never be penalized");
        assertFalse(admin.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER));
        assertFalse(admin.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }
}
