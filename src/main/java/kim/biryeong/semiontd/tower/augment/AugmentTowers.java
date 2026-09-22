package kim.biryeong.semiontd.tower.augment;

import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;

/** Neutral, augment-gated entries: intentionally not owned by a fabricated builder. */
public final class AugmentTowers {
    public static final TowerType FOLDING_BARRICADE = tower("folding_barricade", "방벽", 160, 300, 0, 0, 20, "minecraft:iron_golem", DamageType.PHYSICAL);
    public static final TowerType PULSE_RELAY = tower("pulse_relay", "결속 타워", 220, 160, 6, 0, 20, "minecraft:allay", DamageType.MAGIC);
    public static final TowerType BARRIER_CORE = tower("barrier_core", "수호자", 0, 1000, 6, 0, 20, "minecraft:iron_golem", DamageType.PHYSICAL);
    public static final TowerType GIANT_HUNTER = tower("giant_hunter", "공성 전차", 0, 320, 9, 70, 60, "minecraft:pillager", DamageType.PHYSICAL);
    public static final TowerType EMERGENCY_BELL = tower("emergency_bell", "수호천사", 120, 100, 6, 0, 20, "minecraft:allay", DamageType.MAGIC);
    public static final TowerType CAPACITOR_POST = tower("capacitor_post", "전지 타워", 140, 180, 5, 35, 20, "minecraft:blaze", DamageType.MAGIC);
    public static final TowerType AMBUSH_WORKSHOP = tower("ambush_workshop", "지뢰 생성기", 220, 180, 6, 0, 5, "minecraft:shulker", DamageType.PHYSICAL);
    public static final TowerType STARLIGHT_COCOON = tower("starlight_cocoon", "별빛 고치", 0, 260, 5, 0, 30, "minecraft:shulker", DamageType.MAGIC);
    public static final TowerType ORDNANCE_FACTORY = tower("ordnance_factory", "인컴 대포", 0, 300, 8, 20, 20, "minecraft:iron_golem", DamageType.PHYSICAL);
    private static final List<TowerType> ALL = List.of(FOLDING_BARRICADE, PULSE_RELAY, BARRIER_CORE, GIANT_HUNTER,
            EMERGENCY_BELL, CAPACITOR_POST, AMBUSH_WORKSHOP, STARLIGHT_COCOON, ORDNANCE_FACTORY);
    private static final Map<String, String> AUGMENTS = Map.of(
            FOLDING_BARRICADE.id(), "semiontd:folding_barricade_blueprint",
            PULSE_RELAY.id(), "semiontd:pulse_relay_blueprint", BARRIER_CORE.id(), "semiontd:barrier_core_call",
            GIANT_HUNTER.id(), "semiontd:giant_hunter_call", EMERGENCY_BELL.id(), "semiontd:emergency_bell_blueprint",
            CAPACITOR_POST.id(), "semiontd:capacitor_post_blueprint", AMBUSH_WORKSHOP.id(), "semiontd:ambush_workshop_blueprint",
            STARLIGHT_COCOON.id(), "semiontd:starlight_cocoon_call", ORDNANCE_FACTORY.id(), "semiontd:ordnance_factory_call");

    private AugmentTowers() {}

    private static TowerType tower(String id, String name, long cost, double health, double range, double damage,
            int interval, String visual, DamageType damageType) {
        return new TowerType("augment_" + id, name, TowerCategory.DIRECT, cost, health, range, damage, interval,
                0,
                List.of("증강 전용 · 외부 회복/전투 버프/복제 불가", "R5 T1 → R15 T2 → R25 T3 자동 강화 · 일반 승급 없음", "파괴 시 긍정적인 사망 효과 없음"),
                EntityVisual.vanilla(visual), List.of(), damageType);
    }

    public static List<TowerType> all() { return ALL; }
    public static int tierForRound(int round) { return round >= 25 ? 3 : round >= 15 ? 2 : 1; }
    public static String augmentId(TowerType type) { return type == null ? null : AUGMENTS.get(type.id()); }
    public static boolean isAugment(TowerType type) { return augmentId(type) != null; }
    public static boolean is(TowerType type, String id) { return type != null && type.id().equals(id); }
    public static boolean is(TowerType type, TowerType expected) { return type != null && type.id().equals(expected.id()); }
    public static boolean isFreeCall(TowerType type) { String id = augmentId(type); return id != null && id.endsWith("_call"); }
    public static int slots(TowerType type) { return is(type, BARRIER_CORE) || is(type, STARLIGHT_COCOON) ? 2 : 1; }
    public static int placementLimit(TowerType type) { return is(type, CAPACITOR_POST) ? 2 : 1; }

    public static void register() {
        for (TowerType type : ALL) {
            ProductionTowerCatalog.TowerFactory factory = is(type, GIANT_HUNTER) || is(type, CAPACITOR_POST)
                    || is(type, STARLIGHT_COCOON) || is(type, ORDNANCE_FACTORY)
                    ? OffensiveAugmentTower::new : AugmentTower::new;
            ProductionTowerCatalog.registerAugment(TowerBalanceRuntime.resolve(type), factory, augmentId(type));
        }
    }
}
