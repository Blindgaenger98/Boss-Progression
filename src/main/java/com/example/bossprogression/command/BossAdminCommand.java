package com.example.bossprogression.command;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.revive.ReviveManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /bossadmin - administration commands (permission: bossplugin.admin).
 * Subcommands: settier <tier> | revive <player> | setaltar | reload
 */
public final class BossAdminCommand implements TabExecutor {

    private final BossProgressionPlugin plugin;

    public BossAdminCommand(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bossplugin.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "settier" -> handleSetTier(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "setaltar" -> handleSetAltar(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSetTier(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.bossadmin-settier");
            return;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "usage.bossadmin-invalid-tier", Map.of("input", args[1]));
            return;
        }
        if (plugin.progressionManager().setTier(tier)) {
            plugin.messages().send(sender, "boss-status.current-tier",
                    Map.of("tier", String.valueOf(tier)));
            plugin.getLogger().info("Admin " + sender.getName() + " set boss tier to " + tier);
        } else {
            plugin.messages().send(sender, "usage.bossadmin-tier-range",
                    Map.of("max", String.valueOf(plugin.configManager().getBossTiers().size())));
        }
    }

    private void handleRevive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.bossadmin-revive");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        Player actor = (Player) sender;

        ReviveManager reviveManager = new ReviveManager(plugin);
        Location destination = reviveManager.resolveDestination(target, actor);
        ReviveManager.ReviveResult result =
                reviveManager.revive(target, actor, destination, true);
        switch (result) {
            case NOT_DEAD -> plugin.messages().send(sender, "permadeath.self-already-alive",
                    Map.of("player", target.getName()));
            case CANNOT_REVIVE_SELF -> plugin.messages().send(sender, "permadeath.cannot-revive-self");
            case NO_ITEM -> plugin.messages().send(sender, "permadeath.no-item");
            case SUCCESS -> plugin.getLogger().info("Admin " + actor.getName()
                    + " force-revived " + target.getName());
        }
    }

    private void handleSetAltar(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        Player player = (Player) sender;
        plugin.dataStore().setAltarLocation(player.getLocation());
        plugin.dataStore().markDirty();
        plugin.messages().send(sender, "altar.set", Map.of(
                "x", String.valueOf(Math.round(player.getLocation().getX())),
                "y", String.valueOf(Math.round(player.getLocation().getY())),
                "z", String.valueOf(Math.round(player.getLocation().getZ())),
                "world", player.getWorld().getName()));
        plugin.getLogger().info("Admin " + player.getName() + " set the revive altar location");
    }

    private void handleReload(CommandSender sender) {
        if (plugin.configManager().load()) {
            plugin.messages().reload();
            plugin.dataStore().markDirty();
            plugin.messages().send(sender, "reloaded");
            plugin.getLogger().info("Admin " + sender.getName() + " reloaded configuration");
        } else {
            plugin.messages().send(sender, "admin.reload-failed");
            plugin.getLogger().severe("Admin " + sender.getName()
                    + " attempted a config reload, but validation failed - previous configuration retained");
        }
    }

    private void sendUsage(CommandSender sender) {
        plugin.messages().send(sender, "usage.bossadmin-line-settier");
        plugin.messages().send(sender, "usage.bossadmin-line-revive");
        plugin.messages().send(sender, "usage.bossadmin-line-setaltar");
        plugin.messages().send(sender, "usage.bossadmin-line-reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bossplugin.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> subcommands = List.of("settier", "revive", "setaltar", "reload");
            List<String> matches = new ArrayList<>();
            for (String sub : subcommands) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        if (args.length == 2) {
            String prefix = args[1];
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "settier" -> {
                    List<String> tiers = new ArrayList<>();
                    for (int i = 0; i <= plugin.configManager().getBossTiers().size(); i++) {
                        if (String.valueOf(i).startsWith(prefix)) {
                            tiers.add(String.valueOf(i));
                        }
                    }
                    return tiers;
                }
                case "revive" -> {
                    List<String> players = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (plugin.dataStore().isDead(player.getUniqueId())
                                && player.getName().toLowerCase(Locale.ROOT)
                                        .startsWith(prefix.toLowerCase(Locale.ROOT))) {
                            players.add(player.getName());
                        }
                    }
                    return players;
                }
                default -> {
                    // no further completions
                }
            }
        }
        return Collections.emptyList();
    }
}
