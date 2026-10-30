package com.example.bossprogression.util;

import com.example.bossprogression.BossProgressionPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized access to {@code messages.yml}. All player-facing strings live there.
 */
public final class Messages {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\w+\\}");

    private final BossProgressionPlugin plugin;
    private volatile YamlConfiguration yaml;

    public Messages(BossProgressionPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Raw message with prefix and color codes translated. */
    public String raw(String key) {
        String value = yaml.getString(key, "&cMissing message: " + key);
        return prefix() + ChatColor.translateAlternateColorCodes('&', value);
    }

    /** Raw message with placeholders replaced and color codes translated. */
    public String raw(String key, Map<String, String> placeholders) {
        String value = substitute(yaml.getString(key, "&cMissing message: " + key), placeholders);
        return prefix() + ChatColor.translateAlternateColorCodes('&', value);
    }

    /** Raw message without prefix, color codes translated (for GUI titles / item names). */
    public String plain(String key) {
        String value = yaml.getString(key, "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    /** Raw message without prefix, placeholders replaced, color codes translated. */
    public String plain(String key, Map<String, String> placeholders) {
        String value = substitute(yaml.getString(key, "&cMissing message: " + key), placeholders);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    /**
     * Single-pass placeholder substitution: every {@code {key}} in the original
     * string is replaced exactly once via {@link Matcher#appendReplacement}.
     * Placeholder values are never re-scanned, so values containing
     * {@code {other}}-style text are inserted verbatim (no recursion, no
     * order-dependence on the map). Unknown keys are left unchanged in the
     * output.
     */
    private static String substitute(String value, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return value;
        }
        Matcher m = PLACEHOLDER_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group().substring(1, m.group().length() - 1);
            String replacement = placeholders.getOrDefault(key, m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String prefix() {
        String p = yaml.getString("prefix", "");
        return ChatColor.translateAlternateColorCodes('&', p);
    }

    // ---- Convenience senders ----

    public void send(CommandSender sender, String key) {
        sender.sendMessage(raw(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(raw(key, placeholders));
    }

    public void broadcast(String key) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(raw(key));
        }
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(raw(key, placeholders));
        }
    }
}
