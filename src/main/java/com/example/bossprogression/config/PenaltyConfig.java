package com.example.bossprogression.config;

/**
 * Settings for the punishment applied when a player attempts to fight a boss out of sequence.
 */
public final class PenaltyConfig {

    private final boolean enabled;
    private final double radius;
    private final int witherAmplifier;
    private final int slownessAmplifier;
    private final int effectDurationTicks;
    private final double lightningDamagePercent;
    private final int cooldownSeconds;

    public PenaltyConfig(boolean enabled, double radius, int witherAmplifier, int slownessAmplifier,
                         int effectDurationTicks, double lightningDamagePercent, int cooldownSeconds) {
        this.enabled = enabled;
        this.radius = radius;
        this.witherAmplifier = witherAmplifier;
        this.slownessAmplifier = slownessAmplifier;
        this.effectDurationTicks = effectDurationTicks;
        this.lightningDamagePercent = lightningDamagePercent;
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getRadius() {
        return radius;
    }

    public int getWitherAmplifier() {
        return witherAmplifier;
    }

    public int getSlownessAmplifier() {
        return slownessAmplifier;
    }

    public int getEffectDurationTicks() {
        return effectDurationTicks;
    }

    public double getLightningDamagePercent() {
        return lightningDamagePercent;
    }

    /** Cooldown between two penalties per player, in seconds (0 = no cooldown). */
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }
}
