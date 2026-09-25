package kim.biryeong.semiontd.tower.frost;

import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.resources.ResourceLocation;

/** 몬스터에 귀속되는 한기·냉매 상태와 미래 아이스브레이크용 해동 진입점. */
public final class FrostMonsterStates {
    private static final MonsterDataKey<java.util.UUID> REFRIGERANT_OWNER = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_refrigerant_owner"), java.util.UUID.class);
    private static final MonsterDataKey<Double> CHILL = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_chill"),
            Double.class
    );
    private static final MonsterDataKey<Boolean> REFRIGERATED = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_refrigerated"),
            Boolean.class
    );
    private static final ResourceLocation REFRIGERANT_EFFECT_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "frost_refrigerant");

    private FrostMonsterStates() {
    }

    public static ChillResult applyChill(SemionMonsterEntity target) {
        return applyChill(target, FrostBalance.chillPerHit());
    }

    public static ChillResult applyChill(SemionMonsterEntity target, double amount) {
        if (target == null || !target.isAlive() || target.runtimeMonster() == null) {
            return ChillResult.NONE;
        }
        Monster monster = target.runtimeMonster();
        double previous = chill(monster);
        if (isRefrigerated(monster)) {
            syncRefrigerantEffects(target);
            return new ChillResult(previous, previous, false, true);
        }

        double threshold = Math.max(0.000001, FrostBalance.chillThreshold());
        double current = Math.min(threshold, previous + Math.max(0.0, amount));
        monster.setData(CHILL, current);
        boolean becameRefrigerated = current >= threshold;
        if (becameRefrigerated) {
            monster.setData(REFRIGERATED, true);
            syncRefrigerantEffects(target);
        }
        return new ChillResult(previous, current, becameRefrigerated, becameRefrigerated);
    }

    public static ChillResult applyChill(SemionTowerEntity source, SemionMonsterEntity target, double amount) {
        ChillResult result = applyChill(target, amount);
        if (result.becameRefrigerated() && source != null && source.ownerPlayer() != null) {
            target.runtimeMonster().setData(REFRIGERANT_OWNER, source.ownerPlayer());
        }
        FrostAugments.onChill(source, target, result);
        return result;
    }

    static java.util.UUID refrigerantOwner(Monster monster) {
        return monster == null ? null : monster.getData(REFRIGERANT_OWNER).orElse(null);
    }

    public static double chill(Monster monster) {
        return monster == null ? 0.0 : monster.getData(CHILL).orElse(0.0);
    }

    public static boolean isRefrigerated(Monster monster) {
        return monster != null && monster.getData(REFRIGERATED).orElse(false);
    }

    /**
     * 냉매를 제거하고 최대 체력 비례 피해량을 반환합니다. 실제 피해 적용은 아이스브레이크
     * 타워가 공유 피해 파이프라인으로 처리해야 합니다.
     */
    public static ThawResult thaw(SemionMonsterEntity target) {
        if (target == null || target.runtimeMonster() == null || !isRefrigerated(target.runtimeMonster())) {
            return ThawResult.NONE;
        }
        Monster monster = target.runtimeMonster();
        double damage = Math.max(0.0, monster.maxHealth() * FrostBalance.thawMaxHealthDamage());
        FrostAugments.removeRefrigerant(monster);
        monster.removeData(CHILL);
        monster.removeData(REFRIGERATED);
        monster.removeData(REFRIGERANT_OWNER);
        clearRefrigerantEffects(target);
        return new ThawResult(true, damage);
    }

    private static void syncRefrigerantEffects(SemionMonsterEntity target) {
        target.setPersistentEffect(
                TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION,
                REFRIGERANT_EFFECT_SOURCE,
                FrostBalance.refrigerantDamageReduction()
        );
        target.setPersistentEffect(
                TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION,
                REFRIGERANT_EFFECT_SOURCE,
                FrostBalance.refrigerantAttackSpeedReduction()
        );
    }

    private static void clearRefrigerantEffects(SemionMonsterEntity target) {
        target.setPersistentEffect(
                TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION,
                REFRIGERANT_EFFECT_SOURCE,
                0.0
        );
        target.setPersistentEffect(
                TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION,
                REFRIGERANT_EFFECT_SOURCE,
                0.0
        );
    }

    public record ChillResult(
            double previousChill,
            double currentChill,
            boolean becameRefrigerated,
            boolean refrigerated
    ) {
        public static final ChillResult NONE = new ChillResult(0.0, 0.0, false, false);
    }

    public record ThawResult(boolean thawed, double damage) {
        public static final ThawResult NONE = new ThawResult(false, 0.0);
    }
}
