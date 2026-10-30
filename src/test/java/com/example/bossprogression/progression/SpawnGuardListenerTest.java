package com.example.bossprogression.progression;

import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WardenMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;
import com.example.bossprogression.PluginTestBase;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnGuardListenerTest extends PluginTestBase {

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
                // no-op
            }
        });
    }

    @Test
    void wardenSpawnAllowedAtTierZero() {
        EntitySpawnEvent event = new EntitySpawnEvent(
                new WardenMock(server, java.util.UUID.randomUUID()));
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void outOfSequenceWardenSpawnIsCancelledWithPenalty() {
        plugin.progressionManager().setTier(1);

        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());
        PlayerMock player = server.addPlayer("Rusher");
        player.teleport(warden.getLocation());

        EntitySpawnEvent event = new EntitySpawnEvent(warden);
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertTrue(player.hasPotionEffect(PotionEffectType.WITHER));
        assertTrue(player.hasPotionEffect(PotionEffectType.SLOWNESS));
    }

    @Test
    void adminIsExemptFromPenalty() {
        plugin.progressionManager().setTier(1);

        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, "bossplugin.admin", true);
        admin.teleport(warden.getLocation());

        server.getPluginManager().callEvent(new EntitySpawnEvent(warden));

        assertFalse(admin.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void nonBossSpawnIsIgnored() {
        plugin.progressionManager().setTier(1);
        EntitySpawnEvent event = new EntitySpawnEvent(
                new ZombieMock(server, java.util.UUID.randomUUID()));
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void outOfSequenceInteractionIsCancelled() {
        plugin.progressionManager().setTier(1);

        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());
        PlayerMock player = server.addPlayer("Toucher");
        player.teleport(warden.getLocation());

        PlayerInteractAtEntityEvent event =
                new PlayerInteractAtEntityEvent(player, warden, new Vector(0, 0, 0));
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled());
        assertTrue(player.hasPotionEffect(PotionEffectType.WITHER));
    }

    @Test
    void adminInteractionWithOutOfSequenceBossIsAllowed() {
        plugin.progressionManager().setTier(1);

        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());
        PlayerMock admin = server.addPlayer("BossAdmin");
        admin.addAttachment(plugin, "bossplugin.admin", true);
        admin.teleport(warden.getLocation());

        PlayerInteractAtEntityEvent event =
                new PlayerInteractAtEntityEvent(admin, warden, new Vector(0, 0, 0));
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled());
        assertFalse(admin.hasPotionEffect(PotionEffectType.WITHER));
    }
}
