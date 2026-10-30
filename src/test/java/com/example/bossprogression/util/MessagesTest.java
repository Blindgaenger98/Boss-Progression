package com.example.bossprogression.util;

import com.example.bossprogression.BossProgressionPlugin;
import com.example.bossprogression.PluginTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Messages}: single-pass {@code {placeholder}} substitution,
 * missing-key fallback and raw/plain color handling.
 *
 * <p>The plugin under test loads the real {@code messages.yml} from
 * {@code src/main/resources} via {@code saveDefaultConfig()}, so all
 * expected strings below are the translated ({@code &} → {@code §})
 * forms of the config values.
 */
class MessagesTest extends PluginTestBase {

    private Messages messages() {
        return plugin.messages();
    }

    /**
     * 1+2. A message with (multiple) placeholders is substituted in a single
     * pass. {@code boss-killed.global} contains both {@code {boss}} and
     * {@code {tier}} and must come out fully replaced.
     */
    @Test
    void rawSubstitutesMultiplePlaceholdersInSinglePass() {
        String out = messages().raw("boss-killed.global",
                Map.of("boss", "Wither", "tier", "2"));
        assertEquals(
                "§8[§6BossProgress§8] "
                        + "§6§l[BOSS DEFEATED] §eWither has been slain! "
                        + "Progression advances to Tier §f2§e.",
                out);
    }

    /**
     * 3. A placeholder whose value is NOT in the map is left unchanged in the
     * output (documented single-pass fallback).
     */
    @Test
    void rawLeavesUnknownPlaceholderUnchanged() {
        String out = messages().raw("unknown-player", Map.of("player", "Steve"));
        assertEquals("§8[§6BossProgress§8] §cUnknown player: Steve", out);

        // Only one of the two placeholders of the message is provided:
        String partial = messages().raw("boss-status.next-boss", Map.of("boss", "Wither"));
        assertEquals(
                "§8[§6BossProgress§8] §eNext allowed boss: §fWither §7(Tier {tier})",
                partial);
    }

    /**
     * 4. Regression test for the old order-dependent substitution: a
     * placeholder value that itself looks like {@code {other}} must be
     * inserted verbatim and NOT re-scanned, even if {@code other} is also
     * present in the map.
     */
    @Test
    void rawDoesNotRecurseIntoPlaceholderValues() {
        String out = messages().raw("unknown-player",
                Map.of("player", "{boss}", "boss", "Wither"));
        assertEquals("§8[§6BossProgress§8] §cUnknown player: {boss}", out);
    }

    /**
     * 5. Both {@code raw(...)} and {@code plain(...)} translate {@code &}
     * color codes to {@code §}; the documented difference is that {@code raw}
     * prepends the configured prefix while {@code plain} does not (plain is
     * intended for GUI titles / item names).
     */
    @Test
    void rawKeepsPrefixAndColorsWhilePlainHasNoPrefix() {
        String raw = messages().raw("boss-status.current-tier", Map.of("tier", "3"));
        String plain = messages().plain("boss-status.current-tier", Map.of("tier", "3"));

        assertEquals("§8[§6BossProgress§8] §eCurrent tier: §f3", raw);
        assertEquals("§eCurrent tier: §f3", plain);
        assertTrue(raw.startsWith("§8[§6BossProgress§8] "),
                "raw must carry the translated prefix");
    }

    /**
     * 6. Fallback for a key that does not exist in messages.yml: the
     * configured default {@code "&cMissing message: " + key} is used (i.e. the
     * key is echoed, red), still prefixed and color-translated.
     */
    @Test
    void rawFallsBackToMissingMessageForUnknownKey() {
        String out = messages().raw("does.not.exist");
        assertEquals("§8[§6BossProgress§8] §cMissing message: does.not.exist", out);

        String plain = messages().plain("does.not.exist");
        assertEquals("§cMissing message: does.not.exist", plain);
    }

    /**
     * Substitution without placeholders (null or empty map) returns the
     * message verbatim (colors still translated).
     */
    @Test
    void rawWithoutPlaceholdersReturnsUntouchedMessage() {
        assertEquals(
                "§8[§6BossProgress§8] §cThis command can only be used by players.",
                messages().raw("player-only", Map.of()));
        assertEquals(
                "§8[§6BossProgress§8] §cThis command can only be used by players.",
                messages().raw("player-only", null));
    }
}
