package kim.biryeong.semiontd.tower.thunder;

import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * The one number the 람쥐썬더 builder runs on.
 *
 * <p>Load factor is {@code consumption / generation}. Below {@link ThunderBalance#surplusFloor()}
 * every tower in the family gets the full surplus damage bonus; above 1.0 they all lose damage and
 * attack speed together, bottoming out at {@link ThunderBalance#shortageCeiling()}.
 *
 * <p>This is deliberately a flow, not a stock. Nothing is banked between waves — the snapshot only
 * ever describes the grid as it stands right now, which is what separates the family from the ocean
 * builder's stored water.
 */
public final class ThunderPower {
    private ThunderPower() {
    }

    /**
     * Immutable view of a player's grid.
     *
     * @param generation  total supply including the builder's base power
     * @param consumption total draw from towers that are plugged in
     * @param loadFactor  consumption divided by generation
     */
    public record Snapshot(double generation, double consumption, double loadFactor) {
        /** Headroom as a 0..1 fraction, where 1.0 means nothing is drawing power. */
        public double headroom() {
            if (generation <= 0.0) {
                return 0.0;
            }
            return Math.max(0.0, Math.min(1.0, 1.0 - loadFactor));
        }

        public boolean shortage() {
            return loadFactor > 1.0;
        }

        public boolean surplus() {
            return loadFactor < 1.0;
        }

        /** Damage scaling every powered tower in the family receives. */
        public double damageMultiplier() {
            double floor = ThunderBalance.surplusFloor();
            if (loadFactor <= floor) {
                return 1.0 + ThunderBalance.surplusDamageBonus();
            }
            if (loadFactor < 1.0) {
                // Linear fade from the full bonus at the floor down to neutral at 1.0.
                double progress = (loadFactor - floor) / (1.0 - floor);
                return 1.0 + ThunderBalance.surplusDamageBonus() * (1.0 - progress);
            }
            double ceiling = ThunderBalance.shortageCeiling();
            if (loadFactor >= ceiling) {
                return 1.0 - ThunderBalance.shortageDamagePenalty();
            }
            double progress = (loadFactor - 1.0) / (ceiling - 1.0);
            return 1.0 - ThunderBalance.shortageDamagePenalty() * progress;
        }

        /** Attack interval scaling. Only the shortage side slows towers down. */
        public double attackSpeedMultiplier() {
            if (loadFactor <= 1.0) {
                return 1.0;
            }
            double ceiling = ThunderBalance.shortageCeiling();
            double penalty = ThunderBalance.shortageAttackSpeedPenalty();
            if (loadFactor >= ceiling) {
                return 1.0 - penalty;
            }
            return 1.0 - penalty * ((loadFactor - 1.0) / (ceiling - 1.0));
        }
    }

    /** A grid with nothing built on it: base power only, nothing drawing. */
    public static Snapshot empty() {
        double generation = ThunderBalance.basePower();
        return new Snapshot(generation, 0.0, 0.0);
    }

    /**
     * Computes the grid for {@code playerId} from the towers currently on {@code lane}.
     *
     * <p>Recomputed on demand rather than cached: towers are placed, upgraded, sold and destroyed
     * mid-wave, and a stale snapshot would silently mis-scale every attack until the next rebuild.
     */
    public static Snapshot snapshot(UUID playerId, PlayerLane lane) {
        if (playerId == null || lane == null) {
            return empty();
        }
        double generation = ThunderBalance.basePower();
        double consumption = 0.0;

        for (Tower tower : lane.towers()) {
            if (!playerId.equals(tower.ownerPlayer()) || tower.isDestroyed(lane)) {
                continue;
            }
            TowerType type = tower.type();
            if (!ThunderTowers.isThunderTower(type)) {
                continue;
            }
            generation += generationOf(playerId, tower);
            consumption += ThunderBalance.powerDraw(type.id());
        }

        double loadFactor = generation <= 0.0 ? Double.MAX_VALUE : consumption / generation;
        return new Snapshot(generation, consumption, loadFactor);
    }

    /** What a single tower contributes to supply. */
    public static double generationOf(UUID playerId, Tower tower) {
        if (tower == null) {
            return 0.0;
        }
        TowerType type = tower.type();
        String id = type.id();

        if (ThunderTowers.isRod(type)) {
            if (ThunderBalance.isStormRod(id)) {
                double[] range = ThunderBalance.stormRange(id);
                double roll = ThunderStates.stormRoll(playerId);
                return range[0] + (range[1] - range[0]) * roll;
            }
            return ThunderBalance.powerOutput(id);
        }

        if (ThunderTowers.isInsulated(type)) {
            // Lost health is the fuel: an untouched tank supplies nothing.
            double max = tower.maxHealth();
            if (max <= 0.0) {
                return 0.0;
            }
            double lostFraction = Math.max(0.0, Math.min(1.0, 1.0 - (tower.health() / max)));
            return ThunderBalance.healthToPower(id) * lostFraction;
        }

        return 0.0;
    }
}
