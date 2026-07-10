package kim.biryeong.semiontd.config;

import java.util.Objects;

public final class VfxConfig {
    private static final int DEFAULT_MAX_SAMPLED_HIT_RAYS = 4;
    private static final int DEFAULT_REFILL_POINTS_PER_TICK = 1024;
    private static final int DEFAULT_BURST_CAPACITY_POINTS = 4096;
    private static final int DEFAULT_MAX_PACKETS_PER_TICK_PER_RECIPIENT = 2048;
    private static final int DEFAULT_MAX_SHAPE_INSTRUCTIONS_PER_TICK = 128;

    private final Boolean enabled;
    private final Boolean areaDamageEnabled;
    private final Boolean asyncPlanning;
    private final Integer maxSampledHitRays;
    private final VanillaConfig vanilla;
    private final GcbConfig gcb;

    public VfxConfig(
            Boolean enabled,
            Boolean areaDamageEnabled,
            Boolean asyncPlanning,
            Integer maxSampledHitRays,
            VanillaConfig vanilla,
            GcbConfig gcb
    ) {
        this.enabled = enabled;
        this.areaDamageEnabled = areaDamageEnabled;
        this.asyncPlanning = asyncPlanning;
        this.maxSampledHitRays = maxSampledHitRays;
        this.vanilla = vanilla;
        this.gcb = gcb;
    }

    public static VfxConfig defaultConfig() {
        return new VfxConfig(
                true,
                true,
                true,
                DEFAULT_MAX_SAMPLED_HIT_RAYS,
                VanillaConfig.defaultConfig(),
                GcbConfig.defaultConfig()
        );
    }

    public VfxConfig normalized() {
        VanillaConfig normalizedVanilla = vanilla().normalized();
        return new VfxConfig(
                enabled(),
                areaDamageEnabled(),
                asyncPlanning(),
                clamp(maxSampledHitRays(), 0, 8),
                normalizedVanilla,
                gcb().normalized()
        );
    }

    public boolean enabled() {
        return enabled == null ? true : enabled;
    }

    public boolean areaDamageEnabled() {
        return areaDamageEnabled == null ? true : areaDamageEnabled;
    }

    public boolean asyncPlanning() {
        return asyncPlanning == null ? true : asyncPlanning;
    }

    public int maxSampledHitRays() {
        return maxSampledHitRays == null ? DEFAULT_MAX_SAMPLED_HIT_RAYS : maxSampledHitRays;
    }

    public VanillaConfig vanilla() {
        return vanilla == null ? VanillaConfig.defaultConfig() : vanilla;
    }

    public GcbConfig gcb() {
        return gcb == null ? GcbConfig.defaultConfig() : gcb;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof VfxConfig other)) {
            return false;
        }
        return enabled() == other.enabled()
                && areaDamageEnabled() == other.areaDamageEnabled()
                && asyncPlanning() == other.asyncPlanning()
                && maxSampledHitRays() == other.maxSampledHitRays()
                && vanilla().equals(other.vanilla())
                && gcb().equals(other.gcb());
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled(), areaDamageEnabled(), asyncPlanning(), maxSampledHitRays(), vanilla(), gcb());
    }

    public static final class VanillaConfig {
        private final Integer refillPointsPerTick;
        private final Integer burstCapacityPoints;
        private final Integer maxPacketsPerTickPerRecipient;

        public VanillaConfig(
                Integer refillPointsPerTick,
                Integer burstCapacityPoints,
                Integer maxPacketsPerTickPerRecipient
        ) {
            this.refillPointsPerTick = refillPointsPerTick;
            this.burstCapacityPoints = burstCapacityPoints;
            this.maxPacketsPerTickPerRecipient = maxPacketsPerTickPerRecipient;
        }

        public static VanillaConfig defaultConfig() {
            return new VanillaConfig(
                    DEFAULT_REFILL_POINTS_PER_TICK,
                    DEFAULT_BURST_CAPACITY_POINTS,
                    DEFAULT_MAX_PACKETS_PER_TICK_PER_RECIPIENT
            );
        }

        public VanillaConfig normalized() {
            int refill = clamp(refillPointsPerTick(), 64, 8192);
            int burst = clamp(burstCapacityPoints(), refill, 16384);
            return new VanillaConfig(
                    refill,
                    burst,
                    clamp(maxPacketsPerTickPerRecipient(), 128, 8192)
            );
        }

        public int refillPointsPerTick() {
            return refillPointsPerTick == null ? DEFAULT_REFILL_POINTS_PER_TICK : refillPointsPerTick;
        }

        public int burstCapacityPoints() {
            return burstCapacityPoints == null ? DEFAULT_BURST_CAPACITY_POINTS : burstCapacityPoints;
        }

        public int maxPacketsPerTickPerRecipient() {
            return maxPacketsPerTickPerRecipient == null
                    ? DEFAULT_MAX_PACKETS_PER_TICK_PER_RECIPIENT
                    : maxPacketsPerTickPerRecipient;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof VanillaConfig other)) {
                return false;
            }
            return refillPointsPerTick() == other.refillPointsPerTick()
                    && burstCapacityPoints() == other.burstCapacityPoints()
                    && maxPacketsPerTickPerRecipient() == other.maxPacketsPerTickPerRecipient();
        }

        @Override
        public int hashCode() {
            return Objects.hash(refillPointsPerTick(), burstCapacityPoints(), maxPacketsPerTickPerRecipient());
        }
    }

    public static final class GcbConfig {
        private final Integer maxShapeInstructionsPerTick;

        public GcbConfig(Integer maxShapeInstructionsPerTick) {
            this.maxShapeInstructionsPerTick = maxShapeInstructionsPerTick;
        }

        public static GcbConfig defaultConfig() {
            return new GcbConfig(DEFAULT_MAX_SHAPE_INSTRUCTIONS_PER_TICK);
        }

        public GcbConfig normalized() {
            return new GcbConfig(clamp(maxShapeInstructionsPerTick(), 16, 512));
        }

        public int maxShapeInstructionsPerTick() {
            return maxShapeInstructionsPerTick == null
                    ? DEFAULT_MAX_SHAPE_INSTRUCTIONS_PER_TICK
                    : maxShapeInstructionsPerTick;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof GcbConfig other
                    && maxShapeInstructionsPerTick() == other.maxShapeInstructionsPerTick();
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(maxShapeInstructionsPerTick());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
