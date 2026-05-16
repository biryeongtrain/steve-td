package kim.biryeong.semiontd.tower;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductionTowerCatalog {
    public static final TowerType VILLAGER_CROSSBOW_POST = new TowerType(
            "villager_armor_tower", "주민 탱커 타워", TowerCategory.DIRECT,
            95, 150.0, 7.0, 4, 15, 35,
            List.of("높은 체력과 어그로로 라인을 붙잡는 전방형 타워입니다.", "공격할수록 Emerald 중첩으로 피해량이 점진적으로 오릅니다."),
            "minecraft:villager"
    );
    public static final TowerType VILLAGER_BELL_MORTAR = new TowerType(
            "villager_bell_mortar", "주민 스플래쉬 타워", TowerCategory.DIRECT,
            105, 82.0, 10.5, 7, 17, 5,
            List.of("넓은 스플래시로 몰려오는 몬스터를 정리하는 라인 클리어 타워입니다.", "중첩이 쌓이면 광역 피해가 안정적으로 누적됩니다."),
            "minecraft:villager"
    );
    public static final TowerType VILLAGER_EMERALD_LENS = new TowerType(
            "villager_emerald_lens", "주민 저격 타워", TowerCategory.DIRECT,
            110, 68.0, 15.0, 13, 15, -10,
            List.of("긴 사거리와 높은 단일 피해로 후방에서 핵심 타겟을 압박합니다.", "처치 시 중첩을 얻어 보스와 고체력 몹 대응력이 좋아집니다."),
            "minecraft:villager"
    );

    public static final TowerType UNDEAD_BONE_SPITTER = new TowerType(
            "undead_bone_spitter", "언데드 폭발 타워", TowerCategory.DIRECT,
            100, 72.0, 9.5, 6, 12, 0,
            List.of("빠른 공격과 폭발 스플래시로 물량을 갉아먹는 타워입니다.", "Decay 특성으로 처치 주변에 추가 피해를 남깁니다."),
            "minecraft:skeleton"
    );
    public static final TowerType UNDEAD_GRAVE_BOMBARD = new TowerType(
            "undead_grave_bombard", "언데드 방어 타워", TowerCategory.DIRECT,
            100, 165.0, 6.5, 4, 17, 38,
            List.of("튼튼하고 어그로가 높아 최전방을 버티는 방어형 타워입니다.", "근접 라인에서 몬스터를 붙잡고 처치 폭발로 보조 피해를 냅니다."),
            "minecraft:zombie"
    );
    public static final TowerType UNDEAD_SOUL_REAPER = new TowerType(
            "undead_soul_reaper", "언데드 저격 타워", TowerCategory.DIRECT,
            115, 78.0, 13.5, 13, 16, -5,
            List.of("높은 단일 피해와 처치 기반 Decay로 강한 몬스터를 마무리합니다.", "사거리 분기와 저격 분기 모두 후반 대응력이 좋습니다."),
            "minecraft:wither_skeleton"
    );

    public static final TowerType BEAST_WOLF_DEN = new TowerType(
            "beast_wolf_den", "댕댕이 타워(원거리)", TowerCategory.DIRECT,
            95, 74.0, 8.5, 5, 10, 5,
            List.of("빠른 공격으로 Rage 중첩을 쌓아 공속을 끌어올리는 원거리 타워입니다.", "쉬지 않고 때릴수록 성능이 좋아지지만 오래 쉬면 중첩이 줄어듭니다."),
            "minecraft:wolf"
    );
    public static final TowerType BEAST_BOAR_CRASHER = new TowerType(
            "beast_boar_crasher", "돼지 타워", TowerCategory.DIRECT,
            105, 175.0, 6.5, 5, 15, 42,
            List.of("높은 체력과 어그로로 전선을 세우는 동물 진영 방어 타워입니다.", "Rage 중첩으로 피해와 공속이 함께 성장합니다."),
            "minecraft:pig"
    );
    public static final TowerType BEAST_HAWK_ROOST = new TowerType(
            "beast_hawk_roost", "앵무새 저격 타워", TowerCategory.DIRECT,
            115, 64.0, 16.0, 12, 14, -12,
            List.of("매우 긴 사거리로 라인 전체를 견제하는 저격형 타워입니다.", "명중과 처치 모두 Rage 중첩을 올려 후반 화력이 커집니다."),
            "minecraft:parrot"
    );

    private static final Map<String, CatalogEntry> ENTRIES = new LinkedHashMap<>();

    static {
        registerTree(
                VILLAGER_CROSSBOW_POST,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 0.75, 0.25, 10, 0.035, 0.0, true, false, 0.0, 0.0),
                bastionBranch("villager_tanker_t2", "주민 탱커 특화 타워", "villager_tanker_t3", "개쌉탱커", "minecraft:iron_golem", 90, 210),
                tempoBranch("villager_melee_dealer_t2", "주민 근딜 타워", "villager_melee_dealer_t2", "개쌉 근딜러", "minecraft:villager", 115, 260)
        );
        registerTree(
                VILLAGER_BELL_MORTAR,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 2.85, 0.58, 8, 0.035, 0.0, true, false, 0.0, 0.0),
                splashBranch("villager_lane_clear_t2", "주민 라인클리어 타워", "villager_lane_clear_t2", "라클 타워", "minecraft:iron_golem", 135, 310),
                tempoBranch("villager_balance_t2", "주민 밸런스 타워", "villager_balance_t2", "개쌉 밸런스 타워", "minecraft:villager", 125, 290)
        );
        registerTree(
                VILLAGER_EMERALD_LENS,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 0.35, 0.18, 7, 0.095, 0.0, false, true, 0.0, 0.0),
                rangeBranch("villager_range_t2", "사거리 타워", "villager_range_t2", "개쌉 사거리 타워", "minecraft:villager", 150, 340),
                focusBranch("villager_sniper_t2", "저격 타워", "villager_sniper_t3", "개쌉 저거리 타워", "minecraft:iron_golem", 145, 330)
        );

        registerTree(
                UNDEAD_BONE_SPITTER,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 2.65, 0.55, 5, 0.035, 0.0, true, false, 1.5, 0.35),
                splashBranch("undead_lane_clear_t2", "스플 타워", "undead_lane_clear_t3", "개씹 스플타워", "minecraft:skeleton", 95, 225),
                focusBranch("undead_balance_t2", "밸런스 타워", "undead_balance_t3", "개씹 밸런스 타워", "minecraft:skeleton", 120, 275)
        );
        registerTree(
                UNDEAD_GRAVE_BOMBARD,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 0.85, 0.28, 7, 0.025, 0.0, true, false, 1.0, 0.25),
                bastionBranch("undead_tanker_t2", "탱 타워", "undead_tanker_t2", "극탱 타워", "minecraft:zombie", 140, 320),
                deathBranch("undead_melee_t2", "근딜 타워", "undead_melee_t3", "근딜 타워", "minecraft:zombie", 145, 335)
        );
        registerTree(
                UNDEAD_SOUL_REAPER,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 0.45, 0.22, 6, 0.08, 0.0, false, true, 1.0, 0.25),
                focusBranch("undead_sniper_t2", "저격 타워", "undead_sniper_t3", "강화 저격 타워", "minecraft:wither_skeleton", 165, 360),
                rangeBranch("undead_range_t2", "사거리 타워", "undead_range_t3", "강화 사거리 타워", "minecraft:wither_skeleton", 155, 345)
        );

        registerTree(
                BEAST_WOLF_DEN,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 2.25, 0.5, 7, 0.0, 0.035, true, false, 0.0, 0.0),
                tempoBranch("beast_balance_t2", "공속 타워", "beast_balance_t3", "강화 공속 타워", "minecraft:wolf", 90, 215),
                splashBranch("beast_splash_t2", "라클 타워", "white_fang_den", "강화 라클 타워", "minecraft:wolf", 105, 240)
        );
        registerTree(
                BEAST_BOAR_CRASHER,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 0.9, 0.3, 8, 0.018, 0.02, true, false, 0.0, 0.0),
                tempoBranch("beast_melee_dps_t2", "근딜 타워", "beast_melee_dps_t2", "강화 근딜 타워", "minecraft:pig", 140, 325),
                bastionBranch("beast_tanker_t2", "탱 타워", "beast_tanker_t2", "강화 탱 타워", "minecraft:pig", 130, 310)
        );
        registerTree(
                BEAST_HAWK_ROOST,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 0.45, 0.2, 7, 0.03, 0.035, true, true, 0.0, 0.0),
                rangeBranch("beast_range_t2", "사거리 타워", "beast_range_t3", "천둥 매 성소", "minecraft:parrot", 135, 315),
                tempoBranch("dive_hunter", "급강하 사냥대", "hawk_command_roost", "매 사령탑", "minecraft:parrot", 125, 300)
        );
    }

    private ProductionTowerCatalog() {
    }

    public static Optional<CatalogEntry> find(String towerId) {
        return Optional.ofNullable(ENTRIES.get(towerId));
    }

    public static Collection<CatalogEntry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static List<CatalogEntry> forFaction(TowerFaction faction) {
        return ENTRIES.values().stream()
                .filter(entry -> entry.behavior().faction() == faction)
                .toList();
    }

    public static Optional<ProductionTowerBehavior> behavior(TowerType type) {
        return type == null ? Optional.empty() : find(type.id()).map(CatalogEntry::behavior);
    }

    private static void registerTree(TowerType starter, ProductionTowerBehavior behavior, UpgradeBranch left, UpgradeBranch right) {
        TowerType leftUltimate = stageType(starter, left.tierThree(), left.tierTwo().upgradeCost() + left.tierThree().upgradeCost(), List.of());
        TowerType rightUltimate = stageType(starter, right.tierThree(), right.tierTwo().upgradeCost() + right.tierThree().upgradeCost(), List.of());

        TowerType leftTierTwo = stageType(
                starter,
                left.tierTwo(),
                left.tierTwo().upgradeCost(),
                List.of(upgrade(left.tierThree(), leftUltimate))
        );
        TowerType rightTierTwo = stageType(
                starter,
                right.tierTwo(),
                right.tierTwo().upgradeCost(),
                List.of(upgrade(right.tierThree(), rightUltimate))
        );
        TowerType starterWithUpgrades = copyWithUpgrades(starter, List.of(upgrade(left.tierTwo(), leftTierTwo), upgrade(right.tierTwo(), rightTierTwo)));

        register(starterWithUpgrades, behavior, 1);
        register(leftTierTwo, tune(behavior, left.tierTwo().tuning()), 2);
        register(leftUltimate, tune(behavior, left.tierThree().tuning()), 3);
        register(rightTierTwo, tune(behavior, right.tierTwo().tuning()), 2);
        register(rightUltimate, tune(behavior, right.tierThree().tuning()), 3);
    }

    private static TowerUpgradeOption upgrade(UpgradeStage stage, TowerType target) {
        return new TowerUpgradeOption(stage.suffix(), stage.displayName(), target.id(), stage.upgradeCost());
    }

    private static TowerType copyWithUpgrades(TowerType source, List<TowerUpgradeOption> upgradeOptions) {
        return new TowerType(
                source.id(),
                source.displayName(),
                source.category(),
                source.mineralCost(),
                source.maxHealth(),
                source.range(),
                source.damage(),
                source.attackIntervalTicks(),
                source.aggroPriority(),
                source.description(),
                source.entityTypeId(),
                source.blockbenchModelId(),
                upgradeOptions
        );
    }

    private static TowerType stageType(TowerType starter, UpgradeStage stage, long extraCost, List<TowerUpgradeOption> upgradeOptions) {
        return new TowerType(
                starter.id() + "_" + stage.suffix(),
                starter.displayName() + " - " + stage.displayName(),
                starter.category(),
                starter.mineralCost() + extraCost,
                starter.maxHealth() * stage.healthMultiplier(),
                starter.range() + stage.rangeBonus(),
                starter.damage() * stage.damageMultiplier(),
                Math.max(4, (int) Math.round(starter.attackIntervalTicks() * stage.attackIntervalMultiplier())),
                starter.aggroPriority() + stage.aggroPriorityBonus(),
                stageDescription(stage),
                stage.entityTypeId(),
                null,
                upgradeOptions
        );
    }

    private static List<String> stageDescription(UpgradeStage stage) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(stage.displayName() + " 분기로 업그레이드한 타워입니다.");
        if (stage.healthMultiplier() > 1.45) {
            lines.add("체력과 어그로가 크게 올라 전방 유지력이 좋습니다.");
        } else if (stage.rangeBonus() >= 3.0) {
            lines.add("사거리가 크게 늘어나 후방 배치에서도 라인에 개입합니다.");
        } else if (stage.attackIntervalMultiplier() <= 0.72) {
            lines.add("공격 주기가 짧아져 중첩과 지속 피해를 빠르게 쌓습니다.");
        } else if (stage.damageMultiplier() >= 1.5) {
            lines.add("단일 피해가 크게 올라 고체력 타겟 처리에 강합니다.");
        } else {
            lines.add("기본 성능과 특성 수치가 고르게 강화됩니다.");
        }
        return lines;
    }

    private static ProductionTowerBehavior tune(ProductionTowerBehavior base, BehaviorTuning tuning) {
        return new ProductionTowerBehavior(
                base.faction(),
                base.mechanicName(),
                base.splashRadius() * tuning.splashMultiplier() + tuning.splashBonus(),
                base.splashDamageMultiplier() + tuning.splashDamageBonus(),
                base.maxStacks() + tuning.stackBonus(),
                base.damagePerStack() + tuning.damagePerStackBonus(),
                base.attackSpeedPerStack() + tuning.attackSpeedPerStackBonus(),
                base.stackOnHit() || tuning.stackOnHit(),
                base.stackOnKill() || tuning.stackOnKill(),
                base.killSplashRadius() + tuning.killSplashRadiusBonus(),
                base.killSplashDamageMultiplier() + tuning.killSplashDamageBonus()
        );
    }

    private static UpgradeBranch splashBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.35, 0.5, 1.25, 0.92, 5, new BehaviorTuning(1.25, 0.35, 0.12, 1, 0.01, 0.002, 0.35, 0.10, true, false)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 1.9, 1.25, 1.75, 0.78, 14, new BehaviorTuning(1.75, 0.85, 0.24, 3, 0.02, 0.006, 0.8, 0.22, true, false))
        );
    }

    private static UpgradeBranch focusBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.25, 2.0, 1.55, 0.9, 11, new BehaviorTuning(0.9, 0.1, 0.04, 2, 0.018, 0.002, 0.15, 0.04, false, true)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 1.7, 4.0, 2.45, 0.72, 24, new BehaviorTuning(1.05, 0.3, 0.10, 4, 0.035, 0.006, 0.35, 0.10, false, true))
        );
    }

    private static UpgradeBranch tempoBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.2, 1.0, 1.2, 0.72, 7, new BehaviorTuning(1.1, 0.2, 0.08, 2, 0.01, 0.012, 0.2, 0.06, true, false)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 1.55, 2.0, 1.65, 0.55, 18, new BehaviorTuning(1.35, 0.5, 0.16, 4, 0.02, 0.02, 0.45, 0.14, true, false))
        );
    }

    private static UpgradeBranch rangeBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.15, 3.0, 1.35, 0.88, 10, new BehaviorTuning(1.0, 0.15, 0.06, 1, 0.012, 0.004, 0.25, 0.06, true, true)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 1.55, 6.0, 2.0, 0.68, 26, new BehaviorTuning(1.25, 0.45, 0.14, 3, 0.026, 0.01, 0.55, 0.14, true, true))
        );
    }

    private static UpgradeBranch deathBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.3, 0.75, 1.3, 0.9, 8, new BehaviorTuning(1.2, 0.25, 0.10, 1, 0.012, 0.002, 1.0, 0.25, true, true)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 1.85, 1.75, 1.85, 0.75, 20, new BehaviorTuning(1.55, 0.7, 0.22, 3, 0.024, 0.006, 2.0, 0.45, true, true))
        );
    }

    private static UpgradeBranch bastionBranch(
            String tierTwoSuffix,
            String tierTwoDisplayName,
            String ultimateSuffix,
            String ultimateDisplayName,
            String entityTypeId,
            long tierTwoCost,
            long ultimateCost
    ) {
        return new UpgradeBranch(
                new UpgradeStage(tierTwoSuffix, tierTwoDisplayName, entityTypeId, tierTwoCost, 1.65, -0.5, 1.2, 0.95, 22, new BehaviorTuning(1.25, 0.45, 0.08, 1, 0.008, 0.006, 0.25, 0.08, true, false)),
                new UpgradeStage(ultimateSuffix, ultimateDisplayName, entityTypeId, ultimateCost, 2.5, 0.5, 1.75, 0.82, 40, new BehaviorTuning(1.65, 0.9, 0.18, 3, 0.018, 0.012, 0.6, 0.18, true, false))
        );
    }

    private static void register(TowerType type, ProductionTowerBehavior behavior, int tier) {
        ENTRIES.put(type.id(), new CatalogEntry(type, behavior, tier));
    }

    private record UpgradeBranch(UpgradeStage tierTwo, UpgradeStage tierThree) {
    }

    private record UpgradeStage(
            String suffix,
            String displayName,
            String entityTypeId,
            long upgradeCost,
            double healthMultiplier,
            double rangeBonus,
            double damageMultiplier,
            double attackIntervalMultiplier,
            int aggroPriorityBonus,
            BehaviorTuning tuning
    ) {
    }

    private record BehaviorTuning(
            double splashMultiplier,
            double splashBonus,
            double splashDamageBonus,
            int stackBonus,
            double damagePerStackBonus,
            double attackSpeedPerStackBonus,
            double killSplashRadiusBonus,
            double killSplashDamageBonus,
            boolean stackOnHit,
            boolean stackOnKill
    ) {
    }

    public record CatalogEntry(TowerType type, ProductionTowerBehavior behavior, int tier) {
        public boolean starter() {
            return tier == 1;
        }
    }
}
