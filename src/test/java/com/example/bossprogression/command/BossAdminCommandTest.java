package com.example.bossprogression.command;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.CommandResult;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BossAdminCommand#onCommand} - subcommand {@code reload}.
 *
 * <p>Fix #10 (review-findings): the boolean result of
 * {@code ConfigManager#load()} must be evaluated. On success the admin sees the
 * {@code reloaded} message; on a failed (invalid) config load a failure message
 * ({@code admin.reload-failed}) is sent instead - never a false success.
 */
class BossAdminCommandTest extends PluginTestBase {

    private PlayerMock admin() {
        PlayerMock player = server.addPlayer("Admin");
        player.addAttachment(plugin, "bossplugin.admin", true);
        return player;
    }

    private CommandResult runReload(PlayerMock player) {
        return server.execute("bossadmin", player, "reload");
    }

    @Test
    void reloadWithValidConfigReportsSuccess() {
        PlayerMock player = admin();

        CommandResult result = runReload(player);

        assertEquals(plugin.messages().raw("reloaded"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid();
    }

    @Test
    void reloadWithBrokenConfigReportsFailureNotSuccess() {
        // Corrupt the config on disk: unknown entity-type for a boss tier.
        plugin.getConfig().set("boss-tiers.warden.entity-type", "NOT_AN_ENTITY");
        plugin.saveConfig();

        PlayerMock player = admin();

        CommandResult result = runReload(player);

        assertEquals(plugin.messages().raw("admin.reload-failed"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid(); // no success message may follow the failure message
        // And the previously loaded configuration must still be intact.
        assertFalse(plugin.configManager().getBossTiers().isEmpty());
        assertEquals("WARDEN", String.valueOf(plugin.configManager().getTier(1).entityType()));
    }

    @Test
    void forceReviveSucceedsWithoutReviveItemAndBypassesDeadCheck() {
        // F7: in force mode the dead/self/item checks are bypassed inside
        // ReviveManager, so the only reachable outcomes are SUCCESS or NOT_DEAD
        // (teleport failure). A live target without any revive item must succeed.
        PlayerMock admin = admin();
        PlayerMock target = server.addPlayer("LivingTarget");

        server.execute("bossadmin", admin, "revive", "LivingTarget");

        assertFalse(plugin.dataStore().isDead(target.getUniqueId()),
                "target must be marked alive after a successful force revive");
        // No player-facing message for a successful force revive (log-only path);
        // the only messages are the standard revived announcements from ReviveManager.
        assertEquals(plugin.messages().raw("permadeath.revived-by",
                Map.of("player", "LivingTarget")), admin.nextMessage());
        assertEquals(plugin.messages().raw("permadeath.revived"),
                target.nextMessage());
        admin.assertNoMoreSaid();
    }

    @Test
    void forceReviveReportsNotDeadWhenTeleportFails() {
        // F7: the NOT_DEAD case is reachable in force mode via a failed teleport
        // (Fix-12b). Cancel the revive teleport so ReviveManager#revive returns
        // NOT_DEAD without marking the target alive.
        PlayerMock admin = admin();
        PlayerMock target = server.addPlayer("Stranded");
        plugin.dataStore().markDead(target.getUniqueId());
        Listener cancelTeleports = new Listener() {
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            void onTeleport(PlayerTeleportEvent event) {
                event.setCancelled(true);
            }
        };
        server.getPluginManager().registerEvents(cancelTeleports, plugin);

        server.execute("bossadmin", admin, "revive", "Stranded");

        assertTrue(plugin.dataStore().isDead(target.getUniqueId()),
                "a failed teleport must not mark the target alive");
        assertEquals(plugin.messages().raw("permadeath.self-already-alive",
                Map.of("player", "Stranded")), admin.nextMessage());
        admin.assertNoMoreSaid();
    }

    @Test
    void reloadWithMissingBossTiersSectionReportsFailure() {
        // Remove the whole boss-tiers section -> load() must fail.
        plugin.getConfig().set("boss-tiers", null);
        plugin.saveConfig();

        PlayerMock player = admin();

        CommandResult result = runReload(player);

        assertEquals(plugin.messages().raw("admin.reload-failed"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid();
    }
}
