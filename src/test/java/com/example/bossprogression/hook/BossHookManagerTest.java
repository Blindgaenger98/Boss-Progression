package com.example.bossprogression.hook;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.WardenMock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Fix-12h (F1): every hook invocation - including {@code isBossType} - must be
 * wrapped in the manager's try/catch(Throwable) guards, so a throwing hook can
 * never interrupt core gameplay (hooks are soft-depends).
 */
class BossHookManagerTest extends PluginTestBase {

    /** Hook whose {@code isBossType} always throws - simulates a crashing soft-dependency. */
    private static final class ThrowingHook implements BossHook {
        @Override
        public String id() {
            return "throwing-test";
        }

        @Override
        public void onEnable(BossProgressionPlugin plugin) {
            // no-op
        }

        @Override
        public boolean isBossType(org.bukkit.entity.EntityType type) {
            throw new IllegalStateException("simulated hook crash");
        }
    }

    /** Hook that claims WARDEN entities (and vetoes their spawn). */
    private static final class ClaimingHook implements BossHook {
        @Override
        public String id() {
            return "claiming-test";
        }

        @Override
        public void onEnable(BossProgressionPlugin plugin) {
            // no-op
        }

        @Override
        public boolean isBossType(org.bukkit.entity.EntityType type) {
            return type == org.bukkit.entity.EntityType.WARDEN;
        }

        @Override
        public boolean onBossSpawn(org.bukkit.entity.Entity entity, BossTier tier) {
            return true;
        }
    }

    private BossTier tier1() {
        return plugin.configManager().getTier(1);
    }

    @Test
    void hookForReturnsClaimingHook() {
        ClaimingHook hook = new ClaimingHook();
        plugin.hookManager().registerHook(hook);

        Optional<BossHook> found = plugin.hookManager().hookFor(org.bukkit.entity.EntityType.WARDEN);

        assertFalse(found.isEmpty(), "the claiming hook must be found for WARDEN");
        assertSame(hook, found.get());
    }

    @Test
    void hookForCatchesThrowingIsBossType() {
        // F1: isBossType ran outside the guard before the fix; a throwing hook
        // must now be swallowed (and logged) instead of propagating to the caller.
        plugin.hookManager().registerHook(new ThrowingHook());

        Optional<BossHook> found = assertDoesNotThrow(
                () -> plugin.hookManager().hookFor(org.bukkit.entity.EntityType.WARDEN),
                "a hook throwing in isBossType must not propagate out of hookFor");

        assertFalse(found.isPresent(), "a crashed hook must not claim any entity type");
    }

    @Test
    void anyVetoSpawnCatchesThrowingIsBossType() {
        // F1: the isBossType check was outside the try/catch; a throwing hook
        // must be caught so spawn handling can continue.
        plugin.hookManager().registerHook(new ThrowingHook());
        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());

        boolean veto = assertDoesNotThrow(
                () -> plugin.hookManager().anyVetoSpawn(warden, tier1()),
                "a hook throwing in isBossType must not propagate out of anyVetoSpawn");

        assertFalse(veto, "a crashed hook must not veto the spawn");
    }

    @Test
    void onBossDeathCatchesThrowingIsBossType() {
        // F1: same guard gap as in anyVetoSpawn, for the death path.
        plugin.hookManager().registerHook(new ThrowingHook());
        WardenMock warden = new WardenMock(server, java.util.UUID.randomUUID());

        assertDoesNotThrow(
                () -> plugin.hookManager().onBossDeath(warden, tier1()),
                "a hook throwing in isBossType must not propagate out of onBossDeath");
    }
}
