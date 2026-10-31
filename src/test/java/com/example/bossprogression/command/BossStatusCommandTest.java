package com.example.bossprogression.command;

import com.example.bossprogression.PluginTestBase;
import com.example.bossprogression.config.BossTier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.CommandResult;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BossStatusCommand}.
 *
 * <p>Fix-12j F8 (variant b): {@code /boss} shows the global progression state
 * only (there is no per-boss detail query in {@code ProgressionManager}), so
 * {@code onTabComplete} offers no suggestions in any case and is
 * permission-gated like {@code onCommand}; the {@code plugin.yml} usage line
 * was reduced to {@code /<command>} to match.
 */
class BossStatusCommandTest extends PluginTestBase {

    /** The executor/tab-completer registered for /boss by the plugin's onEnable. */
    private TabExecutor bossCommand() {
        TabCompleter completer = plugin.getCommand("boss").getTabCompleter();
        if (!(completer instanceof TabExecutor executor)) {
            throw new IllegalStateException("boss tab completer is not a TabExecutor: " + completer);
        }
        return executor;
    }

    @Test
    void tabCompleteWithoutStatusPermissionReturnsEmptyList() {
        PlayerMock player = server.addPlayer("NoPerm");
        player.addAttachment(plugin, "bossplugin.status", false);

        List<String> suggestions = bossCommand().onTabComplete(player, null, "boss", new String[0]);

        assertTrue(suggestions.isEmpty());
    }

    @Test
    void tabCompleteWithStatusPermissionReturnsEmptyList() {
        PlayerMock player = server.addPlayer("WithPerm");
        player.addAttachment(plugin, "bossplugin.status", true);

        List<String> suggestions = bossCommand().onTabComplete(player, null, "boss", new String[0]);

        // /boss takes no arguments - variant (b): no suggestions even with permission.
        assertTrue(suggestions.isEmpty());
    }

    @Test
    void playerWithoutStatusPermissionReceivesOnlyNoPermissionMessage() {
        PlayerMock player = server.addPlayer("NoPerm");
        player.addAttachment(plugin, "bossplugin.status", false);

        CommandResult result = server.execute("boss", player, new String[0]);

        assertEquals(LegacyComponentSerializer.legacySection()
                        .deserialize(plugin.messages().raw("no-permission")),
                result.getSender().nextComponentMessage());
        assertNull(result.getSender().nextComponentMessage());
    }

    @Test
    void playerWithStatusPermissionSeesProgressionState() {
        PlayerMock player = server.addPlayer("WithPerm");
        player.addAttachment(plugin, "bossplugin.status", true);

        CommandResult result = server.execute("boss", player, new String[0]);

        int tier = plugin.progressionManager().getCurrentTier();
        assertEquals(LegacyComponentSerializer.legacySection().deserialize(
                        plugin.messages().raw("boss-status.current-tier", Map.of("tier", String.valueOf(tier)))),
                result.getSender().nextComponentMessage());

        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        if (plugin.progressionManager().isProgressionComplete()) {
            assertEquals(LegacyComponentSerializer.legacySection()
                            .deserialize(plugin.messages().raw("boss-status.all-completed")),
                    result.getSender().nextComponentMessage());
        } else {
            assertEquals(LegacyComponentSerializer.legacySection().deserialize(
                            plugin.messages().raw("boss-status.next-boss",
                                    Map.of("boss", next.displayName(), "tier", String.valueOf(next.tierNumber())))),
                    result.getSender().nextComponentMessage());
        }
        assertNull(result.getSender().nextComponentMessage());
    }

    @Test
    void onCommandIgnoresArguments() {
        // Separate players: both executions share the server, and each
        // CommandResult asserts against its own player's message queue.
        PlayerMock withArgs = server.addPlayer("WithArgs");
        withArgs.addAttachment(plugin, "bossplugin.status", true);
        PlayerMock withoutArgs = server.addPlayer("WithoutArgs");
        withoutArgs.addAttachment(plugin, "bossplugin.status", true);

        server.execute("boss", withArgs, new String[]{"status"});
        server.execute("boss", withoutArgs, new String[0]);

        int tier = plugin.progressionManager().getCurrentTier();
        String expectedFirst = LegacyComponentSerializer.legacySection().deserialize(
                plugin.messages().raw("boss-status.current-tier", Map.of("tier", String.valueOf(tier)))).toString();
        // Same first message regardless of arguments - args are not interpreted.
        assertEquals(expectedFirst, withArgs.nextComponentMessage().toString());
        assertEquals(expectedFirst, withoutArgs.nextComponentMessage().toString());
    }
}
