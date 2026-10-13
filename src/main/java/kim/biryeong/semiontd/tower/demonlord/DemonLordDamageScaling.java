package kim.biryeong.semiontd.tower.demonlord;

import java.util.Locale;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.tower.LogarithmicScaling;

public final class DemonLordDamageScaling {
    private DemonLordDamageScaling() {
    }

    public static double apply(double damage, DamageType type) {
        return apply(damage, type, threshold(type), scale(type));
    }

    static double apply(double damage, DamageType type, double threshold, double scale) {
        if (!Double.isFinite(damage) || damage <= 0.0) {
            return 0.0;
        }
        if (type == DamageType.TRUE) {
            return damage;
        }
        return LogarithmicScaling.logarithmicBonus(damage, threshold, scale);
    }

    static double threshold(DamageType type) {
        return TowerBalanceRuntime.ability(DemonLordTowers.GLOBAL_CONFIG_ID,
                type == DamageType.MAGIC ? "magicDamageThreshold" : "physicalDamageThreshold", 100.0);
    }

    static double scale(DamageType type) {
        return TowerBalanceRuntime.ability(DemonLordTowers.GLOBAL_CONFIG_ID,
                type == DamageType.MAGIC ? "magicDamageScale" : "physicalDamageScale",
                type == DamageType.MAGIC ? 50.0 : 100.0);
    }

    static String description() {
        return String.format(Locale.ROOT,
                "1회 피해 점감(임계값/스케일, 피해 포인트): 물리 %.1f/%.1f · 마법 %.1f/%.1f",
                threshold(DamageType.PHYSICAL), scale(DamageType.PHYSICAL),
                threshold(DamageType.MAGIC), scale(DamageType.MAGIC));
    }
}
