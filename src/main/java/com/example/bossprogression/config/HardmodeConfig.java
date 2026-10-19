package com.example.bossprogression.config;

/**
 * Hardmode scaling settings: monster attributes are scaled dynamically based on the current boss tier.
 */
public final class HardmodeConfig {

    private final boolean enabled;
    private final double baseMultiplier;
    private final double perTierMultiplier;
    private final boolean nerfHealthRegeneration;
    private final boolean nerfGoldenApple;

    public HardmodeConfig(boolean enabled, double baseMultiplier, double perTierMultiplier,
                          boolean nerfHealthRegeneration, boolean nerfGoldenApple) {
        this.enabled = enabled;
        this.baseMultiplier = baseMultiplier;
        this.perTierMultiplier = perTierMultiplier;
        this.nerfHealthRegeneration = nerfHealthRegeneration;
        this.nerfGoldenApple = nerfGoldenApple;
    }

    /** Multiplier applied to monster attributes at the given (1-based) current tier. */
    public double multiplierForTier(int currentTier) {
        if (currentTier <= 0) {
            return 1.0;
        }
        return baseMultiplier + perTierMultiplier * currentTier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isNerfHealthRegeneration() {
        return nerfHealthRegeneration;
    }

    public boolean isNerfGoldenApple() {
        return nerfGoldenApple;
    }
}
