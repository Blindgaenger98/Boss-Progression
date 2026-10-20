package com.example.bossprogression.command;

import com.example.bossprogression.PluginTestBase;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.CommandResult;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AltarCommand}.
 *
 * <p>Fix #7 (review-findings): {@code /altar} must enforce the
 * {@code bossplugin.altar} permission that is declared in {@code plugin.yml}.
 * The check is done in the executor (same style as {@code /boss status} and
 * {@code /revive}) and reuses the shared {@code no-permission} message key.
 *
 * <p>Note: {@code /altar} only reports the altar coordinates — the altar GUI
 * is opened by block right-click ({@code AltarManager#openGui}), not by the
 * command — so the "with permission" case verifies the location message.
 */
class AltarCommandTest extends PluginTestBase {

    private void setupAltar() {
        plugin.dataStore().setAltarLocation(new Location(world, 0, 64, 0));
    }

    private CommandResult runAltar(PlayerMock player) {
        return server.execute("altar", player, new String[0]);
    }

    @Test
    void playerWithoutAltarPermissionReceivesNoPermissionMessage() {
        setupAltar();
        PlayerMock player = server.addPlayer("NoPerm");
        // bossplugin.altar defaults to true; explicitly revoke it.
        player.addAttachment(plugin, "bossplugin.altar", false);

        CommandResult result = runAltar(player);

        assertEquals(plugin.messages().raw("no-permission"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid(); // no location message must leak
    }

    @Test
    void permissionCheckTakesPrecedenceOverMissingAltar() {
        // No altar registered, but the player also lacks the permission:
        // the permission denial must win (and must not hint at altar state).
        PlayerMock player = server.addPlayer("NoPerm");
        player.addAttachment(plugin, "bossplugin.altar", false);

        CommandResult result = runAltar(player);

        assertEquals(plugin.messages().raw("no-permission"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid();
    }

    @Test
    void playerWithAltarPermissionSeesAltarLocation() {
        setupAltar();
        PlayerMock player = server.addPlayer("WithPerm");
        player.addAttachment(plugin, "bossplugin.altar", true);

        CommandResult result = runAltar(player);

        assertEquals(plugin.messages().raw("altar.location", Map.of(
                "world", "world", "x", "0", "y", "64", "z", "0")),
                result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid();
    }

    @Test
    void playerWithAltarPermissionAndNoAltarRegisteredSeesNotSetMessage() {
        PlayerMock player = server.addPlayer("WithPerm");
        player.addAttachment(plugin, "bossplugin.altar", true);

        CommandResult result = runAltar(player);

        assertEquals(plugin.messages().raw("altar.not-set"), result.getSender().nextMessage());
        result.getSender().assertNoMoreSaid();
    }
}
