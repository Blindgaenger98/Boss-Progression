package com.example.bossprogression.progression;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.config.PenaltyConfig;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void installNoopPenaltyApplier() {
        // WorldMock does not implement strikeLightningEffect and LivingEntityMock
        // does not implement damage(double, DamageSource); stub both seams.
        plugin.setPenaltyApplier(new PenaltyApplier(plugin) {
            @Override
            void strikeLightning(Player player) {
                // no-op
            }

            @Override
            void applyLightningDamage(Player player, double amount) {
                // plain overload is implemented in MockBukkit (no DamageSource)
                player.damage(amount);
            }
        });
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
        assertFalse(admin.hasPotionEffect(PotionEffectType.WITHER));
        assertFalse(admin.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void strikeLightningIsInvokedWhenPenaltyApplies() {
        int[] strikes = {0};
        plugin.setPenaltyApplier(new PenaltyApplier(plugin) {
            @Override
            void strikeLightning(Player player) {
                strikes[0]++;
            }

            @Override
            void applyLightningDamage(Player player, double amount) {
                player.damage(amount);
            }
        });
        PlayerMock player = server.addPlayer("Rusher");

        plugin.penaltyApplier().applyTo(player, wardenTier());

        assertEquals(1, strikes[0], "strikeLightning must be called once per applied penalty");
        assertTrue(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void strikeLightningIsNotInvokedForAdmins() {
        int[] strikes = {0};
        plugin.setPenaltyApplier(new PenaltyApplier(plugin) {
            @Override
            void strikeLightning(Player player) {
                strikes[0]++;
            }

            @Override
            void applyLightningDamage(Player player, double amount) {
                player.damage(amount);
            }
        });
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, "bossplugin.admin", true);

        plugin.penaltyApplier().applyTo(admin, wardenTier());

        assertEquals(0, strikes[0], "admins must never trigger the lightning effect");
    }
}
