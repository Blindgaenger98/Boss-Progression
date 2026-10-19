package com.example.bossprogression.command;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.config.BossTier;
import com.example.bossprogression.revive.ReviveItemValidator;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /boss status - shows the current progression state.
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

        ReviveItemValidator validator = new ReviveItemValidator(plugin);
        BossTier unlocked = validator.latestUnlockedTier();
        if (unlocked != null) {
            plugin.messages().send(sender, "boss-status.revive-item",
                    java.util.Map.of("item", unlocked.reviveItem().getDisplayName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new java.util.ArrayList<>();
            for (BossTier tier : plugin.configManager().getBossTiers()) {
                if (tier.displayName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(tier.displayName());
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
