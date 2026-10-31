package com.example.bossprogression.command;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.revive.ReviveItemValidator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;

/**
 * /boss - shows the current progression state.
 */
public final class BossStatusCommand implements TabExecutor {

    private final BossProgressionPlugin plugin;

    public BossStatusCommand(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bossplugin.status")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        int tier = plugin.progressionManager().getCurrentTier();
        plugin.messages().send(sender, "boss-status.current-tier",
                java.util.Map.of("tier", String.valueOf(tier)));

        if (plugin.progressionManager().isProgressionComplete()) {
            plugin.messages().send(sender, "boss-status.all-completed");
            return true;
        }

        BossTier next = plugin.progressionManager().getNextAllowedBoss();
        if (next != null) {
            plugin.messages().send(sender, "boss-status.next-boss",
                    java.util.Map.of("boss", next.displayName(), "tier", String.valueOf(next.tierNumber())));
        }

        ReviveItemValidator validator = plugin.reviveManager().validator();
        BossTier unlocked = validator.latestUnlockedTier();
        if (unlocked != null) {
            plugin.messages().send(sender, "boss-status.revive-item",
                    java.util.Map.of("item", unlocked.reviveItem().getDisplayName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // /boss takes no arguments - offer no suggestions (permission-gated like onCommand).
        if (!sender.hasPermission("bossplugin.status")) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
