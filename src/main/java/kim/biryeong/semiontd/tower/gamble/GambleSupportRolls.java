package kim.biryeong.semiontd.tower.gamble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.util.RandomSource;

public final class GambleSupportRolls {
    private GambleSupportRolls() {
    }

    public static List<GambleSupportEffect> roll(TowerType type, int face, RandomSource random) {
        return roll(type, face, random, 0.0);
    }

    public static int rollFace(int minimum, RandomSource random, boolean twice) {
        int first = minimum + random.nextInt(7 - minimum);
        return twice ? Math.max(first, minimum + random.nextInt(7 - minimum)) : first;
    }

    public static List<GambleSupportEffect> roll(
            TowerType type, int face, RandomSource random, double insuranceRatio
    ) {
        if (type == null || random == null) {
            return List.of();
        }
        ArrayList<GambleSupportStat> order = new ArrayList<>(Arrays.asList(GambleSupportStat.values()));
        for (int index = order.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            GambleSupportStat previous = order.get(index);
            order.set(index, order.get(swap));
            order.set(swap, previous);
        }
        ArrayList<GambleSupportEffect> effects = new ArrayList<>(resolve(
                GambleTowers.isSpectator(type), face, GambleBalance.supportPowerMultiplier(type), order));
        if (face <= 2 && insuranceRatio > 0.0) {
            resolve(GambleTowers.isSpectator(type), 7 - face, GambleBalance.supportPowerMultiplier(type), order)
                    .forEach(effect -> effects.add(new GambleSupportEffect(
                            effect.stat(), effect.positive(), effect.magnitude() * insuranceRatio)));
        }
        return List.copyOf(effects);
    }

    static List<GambleSupportEffect> resolve(
            boolean spectator,
            int face,
            double powerMultiplier,
            List<GambleSupportStat> randomizedStats
    ) {
        RollPlan plan = plan(spectator, face);
        List<GambleSupportStat> order = normalizedOrder(randomizedStats);
        double tierMultiplier = plan.positive() ? Math.max(0.0, powerMultiplier) : 1.0;
        ArrayList<GambleSupportEffect> effects = new ArrayList<>(plan.statCount());
        for (int index = 0; index < plan.statCount(); index++) {
            GambleSupportStat stat = order.get(index);
            double magnitude = GambleBalance.supportUnit(stat, plan.positive())
                    * plan.faceMultiplier() * tierMultiplier;
            effects.add(new GambleSupportEffect(stat, plan.positive(), magnitude));
        }
        return List.copyOf(effects);
    }

    static RollPlan plan(boolean spectator, int face) {
        if (face < 1 || face > 6) {
            throw new IllegalArgumentException("Support die must be between 1 and 6: " + face);
        }
        if (spectator) {
            return switch (face) {
                case 1 -> new RollPlan(false, 2, 2.0);
                case 2 -> new RollPlan(false, 2, 1.0);
                case 3 -> new RollPlan(true, 2, 1.0);
                case 4 -> new RollPlan(true, 2, 1.5);
                case 5 -> new RollPlan(true, 4, 1.75);
                case 6 -> new RollPlan(true, 4, 2.25);
                default -> throw new IllegalStateException("Unreachable support face");
            };
        }
        return switch (face) {
            case 1 -> new RollPlan(false, 1, 2.0);
            case 2 -> new RollPlan(false, 1, 1.0);
            case 3 -> new RollPlan(true, 1, 1.0);
            case 4 -> new RollPlan(true, 1, 1.5);
            case 5 -> new RollPlan(true, 2, 1.75);
            case 6 -> new RollPlan(true, 2, 2.25);
            default -> throw new IllegalStateException("Unreachable support face");
        };
    }

    private static List<GambleSupportStat> normalizedOrder(List<GambleSupportStat> randomizedStats) {
        if (randomizedStats == null || randomizedStats.size() != GambleSupportStat.values().length
                || randomizedStats.stream().distinct().count() != GambleSupportStat.values().length) {
            throw new IllegalArgumentException("Support stat order must contain every stat exactly once.");
        }
        return List.copyOf(randomizedStats);
    }

    record RollPlan(boolean positive, int statCount, double faceMultiplier) {
    }
}
