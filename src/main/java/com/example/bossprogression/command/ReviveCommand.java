package com.example.bossprogression.command;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.revive.ReviveManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /revive <player> - revives a dead player at the reviver's location,
 * consuming one revive item of the latest unlocked tier.
 * Permission: bossplugin.revive
 */
public final class ReviveCommand implements TabExecutor {

    private final BossProgressionPlugin plugin;
    private final ReviveManager reviveManager;

    public ReviveCommand(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        this.reviveManager = new ReviveManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("bossplugin.revive")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.messages().send(player, "usage.revive");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "unknown-player",
                    java.util.Map.of("player", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "permadeath.cannot-revive-self");
            return true;
        }
        if (!plugin.dataStore().isDead(target.getUniqueId())) {
            plugin.messages().send(player, "permadeath.self-already-alive",
                    java.util.Map.of("player", target.getName()));
            return true;
        }

        ReviveManager.ReviveResult result = reviveManager.revive(
                target, player, reviveManager.resolveDestination(target, player), false);
        switch (result) {
            case NOT_DEAD -> plugin.messages().send(player, "permadeath.self-already-alive",
                    java.util.Map.of("player", target.getName()));
            case NO_ITEM -> plugin.messages().send(player, "permadeath.no-item");
            case SUCCESS -> { /* handled inside ReviveManager */ }
            default -> { /* handled inside ReviveManager */ }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        for (var uuid : plugin.dataStore().getDeadPlayers()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                candidates.add(name);
            }
        }
        return candidates;
    }
}
