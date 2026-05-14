package kim.biryeong.semiontd.tower;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductionTowerCatalog {
    public static final TowerType VILLAGER_CROSSBOW_POST = new TowerType(
            "villager_crossbow_post", "주민 방패 초소", TowerCategory.DIRECT,
            95, 150.0, 7.0, 6.0, 24, 35, "minecraft:villager"
    );
    public static final TowerType VILLAGER_BELL_MORTAR = new TowerType(
            "villager_bell_mortar", "주민 종 폭격대", TowerCategory.DIRECT,
            105, 82.0, 10.5, 10.0, 26, 5, "minecraft:villager"
    );
    public static final TowerType VILLAGER_EMERALD_LENS = new TowerType(
            "villager_emerald_lens", "주민 에메랄드 저격 렌즈", TowerCategory.DIRECT,
            110, 68.0, 15.0, 18.0, 24, -10, "minecraft:villager"
    );

    public static final TowerType UNDEAD_BONE_SPITTER = new TowerType(
            "undead_bone_spitter", "언데드 뼈 파편 투척기", TowerCategory.DIRECT,
            100, 72.0, 9.5, 8.0, 18, 0, "minecraft:skeleton"
    );
    public static final TowerType UNDEAD_GRAVE_BOMBARD = new TowerType(
            "undead_grave_bombard", "언데드 묘지 방벽", TowerCategory.DIRECT,
            100, 165.0, 6.5, 5.5, 26, 38, "minecraft:zombie"
    );
    public static final TowerType UNDEAD_SOUL_REAPER = new TowerType(
            "undead_soul_reaper", "언데드 영혼 처형자", TowerCategory.DIRECT,
            115, 78.0, 13.5, 19.0, 25, -5, "minecraft:wither_skeleton"
    );

    public static final TowerType BEAST_WOLF_DEN = new TowerType(
            "beast_wolf_den", "늑대 무리 소굴", TowerCategory.DIRECT,
            95, 74.0, 8.5, 7.0, 15, 5, "minecraft:wolf"
    );
    public static final TowerType BEAST_BOAR_CRASHER = new TowerType(
            "beast_boar_crasher", "멧돼지 방벽", TowerCategory.DIRECT,
            105, 175.0, 6.5, 6.5, 24, 42, "minecraft:pig"
    );
    public static final TowerType BEAST_HAWK_ROOST = new TowerType(
            "beast_hawk_roost", "매 사냥 둥지", TowerCategory.DIRECT,
            115, 64.0, 16.0, 17.0, 21, -12, "minecraft:parrot"
    );

    private static final Map<String, CatalogEntry> ENTRIES = new LinkedHashMap<>();

    static {
        registerTree(
                VILLAGER_CROSSBOW_POST,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 0.75, 0.25, 10, 0.035, 0.0, true, false, 0.0, 0.0),
                bastionBranch("militia_net", "민병 방벽", "emerald_sentry", "에메랄드 수호대", "minecraft:iron_golem", 90, 210),
                tempoBranch("bolt_gallery", "수비대 집결소", "royal_bolt_bastion", "왕실 방벽 성채", "minecraft:villager", 115, 260)
        );
        registerTree(
                VILLAGER_BELL_MORTAR,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 2.85, 0.58, 8, 0.035, 0.0, true, false, 0.0, 0.0),
                splashBranch("foundry_chorus", "종 주조 합창대", "grand_bell_foundry", "대종 주조소", "minecraft:iron_golem", 135, 310),
                tempoBranch("guild_auditor", "길드 감사관", "emerald_treasury", "에메랄드 회계청", "minecraft:villager", 125, 290)
        );
        registerTree(
                VILLAGER_EMERALD_LENS,
                new ProductionTowerBehavior(TowerFaction.VILLAGER, "Emerald", 0.35, 0.18, 7, 0.095, 0.0, false, true, 0.0, 0.0),
                rangeBranch("prism_observatory", "프리즘 저격 관측소", "emerald_astrarium", "에메랄드 천문 포대", "minecraft:villager", 150, 340),
                focusBranch("market_ward", "시장 처형 렌즈", "city_guardian_lens", "도시 수호 렌즈", "minecraft:iron_golem", 145, 330)
        );

        registerTree(
                UNDEAD_BONE_SPITTER,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 2.65, 0.55, 5, 0.035, 0.0, true, false, 1.5, 0.35),
                splashBranch("shard_swarm", "뼈 파편 무리", "skeleton_storm", "해골 폭풍 무리", "minecraft:skeleton", 95, 225),
                focusBranch("marrow_sniper", "골수 저격수", "reaper_marrow_cannon", "사신 골수포", "minecraft:skeleton", 120, 275)
        );
        registerTree(
                UNDEAD_GRAVE_BOMBARD,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 0.85, 0.28, 7, 0.025, 0.0, true, false, 1.0, 0.25),
                bastionBranch("plague_burial", "역병 방벽", "plague_catacomb", "역병 카타콤 방벽", "minecraft:zombie", 140, 320),
                deathBranch("ossuary_mortar", "납골 방어진", "colossal_ossuary", "거대 납골 요새", "minecraft:zombie", 145, 335)
        );
        registerTree(
                UNDEAD_SOUL_REAPER,
                new ProductionTowerBehavior(TowerFaction.UNDEAD, "Decay", 0.45, 0.22, 6, 0.08, 0.0, false, true, 1.0, 0.25),
                focusBranch("void_scythe", "공허 낫꾼", "void_reaper", "공허 수확자", "minecraft:wither_skeleton", 165, 360),
                rangeBranch("soul_lantern", "영혼 등불", "wraith_beacon", "망령 등대", "minecraft:wither_skeleton", 155, 345)
        );

        registerTree(
                BEAST_WOLF_DEN,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 2.25, 0.5, 7, 0.0, 0.035, true, false, 0.0, 0.0),
                tempoBranch("alpha_pack", "우두머리 무리", "frenzy_alpha_den", "광란 우두머리 굴", "minecraft:wolf", 90, 215),
                splashBranch("frost_fang", "서리 송곳니", "white_fang_den", "백색 송곳니 굴", "minecraft:wolf", 105, 240)
        );
        registerTree(
                BEAST_BOAR_CRASHER,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 0.9, 0.3, 8, 0.018, 0.02, true, false, 0.0, 0.0),
                tempoBranch("stampede_pack", "돌진 방벽", "ravager_stampede", "파괴 돌진대", "minecraft:pig", 140, 325),
                bastionBranch("iron_tusk_guard", "철엄니 방벽", "iron_tusk_bastion", "철엄니 보루", "minecraft:pig", 130, 310)
        );
        registerTree(
                BEAST_HAWK_ROOST,
                new ProductionTowerBehavior(TowerFaction.BEAST, "Rage", 0.45, 0.2, 7, 0.03, 0.035, true, true, 0.0, 0.0),
                rangeBranch("storm_hawk", "폭풍 매 둥지", "thunder_hawk_sanctum", "천둥 매 성소", "minecraft:parrot", 135, 315),
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
                stage.entityTypeId(),
                upgradeOptions
        );
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
