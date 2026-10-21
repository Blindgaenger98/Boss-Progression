package com.example.bossprogression.command;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Map;

/**
 * /altar - shows the location of the registered revive altar.
 * Permission: bossplugin.altar
 */
public final class AltarCommand implements TabExecutor {

    private final BossProgressionPlugin plugin;

    public AltarCommand(BossProgressionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bossplugin.altar")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (plugin.dataStore().getAltarLocation() == null) {
            plugin.messages().send(sender, "altar.not-set");
            return true;
        }
        Location altar = plugin.dataStore().getAltarLocation();
        plugin.messages().send(sender, "altar.location", Map.of(
                "world", altar.getWorld().getName(),
                "x", String.valueOf(altar.getBlockX()),
                "y", String.valueOf(altar.getBlockY()),
                "z", String.valueOf(altar.getBlockZ())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
