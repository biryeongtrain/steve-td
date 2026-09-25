package kim.biryeong.semiontd.summon;

import java.util.Map;
import kim.biryeong.semiontd.config.SummonConfig;

/** H0 support values use separate keys so legacy siege cooldowns do not silently carry over. */
public record UtilitySupportProfile(double radius, double healing, double physicalShield, double magicShield,
        int maxTargets, int shieldDurationTicks, int cooldownTicks, int retryDelayTicks, boolean includesSelf,
        Priority priority, boolean physicalCanary) {
    public enum Priority { NEAREST, MISSING_HEALTH, HEALTH_RATIO }

    public UtilitySupportProfile {
        if (!Double.isFinite(radius) || radius <= 0.0 || !Double.isFinite(healing) || healing < 0.0
                || !Double.isFinite(physicalShield) || physicalShield < 0.0
                || !Double.isFinite(magicShield) || magicShield < 0.0 || maxTargets <= 0
                || shieldDurationTicks <= 0 || cooldownTicks <= 0 || retryDelayTicks <= 0) {
            throw new IllegalArgumentException("Utility support values must be finite and valid.");
        }
    }

    public static boolean supports(String id) {
        return switch (id) {
            case "guardian", "blaze", "ghast", "wither_skeleton", "warden" -> true;
            default -> false;
        };
    }

    public static UtilitySupportProfile from(SummonConfig.SummonDefinition definition) {
        String id = definition.id();
        Map<String, Double> values = definition.abilityValues();
        UtilitySupportProfile base = switch (id) {
            case "guardian" -> new UtilitySupportProfile(6, 0, 30, 0, 8, 80, 60, 20, false, Priority.NEAREST, false);
            case "blaze" -> new UtilitySupportProfile(6, 0, 0, 40, 8, 80, 80, 20, false, Priority.NEAREST, false);
            case "ghast" -> new UtilitySupportProfile(7, 45, 0, 0, 6, 80, 120, 20, false, Priority.MISSING_HEALTH, false);
            // H0: the initial shield duration matches the five-second support cooldown.
            case "wither_skeleton" -> new UtilitySupportProfile(6, 35, 25, 25, 3, 100, 100, 20, true, Priority.HEALTH_RATIO, false);
            case "warden" -> new UtilitySupportProfile(8, 0, 70, 70, 8, 100, 120, 20, true, Priority.NEAREST, false);
            default -> throw new IllegalArgumentException("Unknown utility summon: " + id);
        };
        return new UtilitySupportProfile(
                values.getOrDefault("supportRadius", base.radius), values.getOrDefault("supportHealAmount", base.healing),
                values.getOrDefault("physicalShield", base.physicalShield), values.getOrDefault("magicShield", base.magicShield),
                integer(values, "supportMaxTargets", base.maxTargets), integer(values, "shieldDurationTicks", base.shieldDurationTicks),
                integer(values, "supportCooldownTicks", base.cooldownTicks), integer(values, "supportRetryDelayTicks", base.retryDelayTicks),
                base.includesSelf, base.priority, id.equals("warden") && values.getOrDefault("physicalCanary", 0.0) == 1.0);
    }

    public static void validateValues(String id, Map<String, Double> values) {
        for (String key : new String[] { "supportMaxTargets", "shieldDurationTicks", "supportCooldownTicks", "supportRetryDelayTicks" }) {
            integer(values, key, 1);
        }
        for (String key : new String[] { "supportRadius", "supportHealAmount", "physicalShield", "magicShield" }) {
            Double value = values.get(key);
            if (value != null && (!Double.isFinite(value) || value < 0.0 || (key.equals("supportRadius") && value == 0.0))) {
                throw new IllegalArgumentException("Invalid utility value: " + key);
            }
        }
        Double canary = values.get("physicalCanary");
        if (canary != null && ((!canary.equals(0.0) && !canary.equals(1.0)) || (!id.equals("warden") && canary != 0.0))) {
            throw new IllegalArgumentException("Only Warden supports physicalCanary=1.");
        }
    }

    private static int integer(Map<String, Double> values, String key, int fallback) {
        double value = values.getOrDefault(key, (double) fallback);
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid utility integer: " + key);
        }
        return (int) value;
    }
}
