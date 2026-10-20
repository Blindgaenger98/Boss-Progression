package com.example.bossprogression.command;

import com.example.bossprogression.PluginTestBase;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.CommandResult;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
