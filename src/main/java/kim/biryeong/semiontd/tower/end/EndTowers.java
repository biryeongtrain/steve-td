package kim.biryeong.semiontd.tower.end;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.ShulkerVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

public final class EndTowers {
    public static final double PHANTOM_BASE_SCALE = 1.0;
    public static final double PHANTOM_SCALE_PER_100_MAX_HEALTH = 0.2;
    public static final EntityVisual DRAGON_EGG_VISUAL = BlockDisplayVisual.builder(Blocks.DRAGON_EGG.defaultBlockState())
            .build();
    public static final EntityVisual PHANTOM_VISUAL = EntityVisual.builder(byId(EntityType.PHANTOM))
            .scale(PHANTOM_BASE_SCALE)
            .build();
    public static final EntityVisual DRAGON_VISUAL = EntityVisual.builder(byId(EntityType.ENDER_DRAGON))
            .build();

    public static final TowerType BASE_END_TOWER = tower(
            "base_ender_dragon",
            "엔더 드래곤",
            0,
            200.0,
            5.0,
            10.0,
            20,
            100,
            DRAGON_EGG_VISUAL,
            List.of(
                    "<gray>엔더 드래곤이 부화하는 핵심 타워입니다.</gray>"
            )
    );

    public static final TowerType T1_ENDERMITE_TOWER = tower(
            "t1_endermite_tower",
            "엔더 마이트",
            50,
            50,
            0,
            10,
            20,
            10,
            byId(EntityType.ENDERMITE),
            List.of(
                    "<gray>공격력이 높은 엔더마이트 입니다.</gray>",
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
            )
    );

    public static final TowerType T2_ENDERMAN_TOWER = tower(
            "t2_enderman_tower",
            "엔더맨",
            100,
            50,
            0,
            15,
            20,
            10,
            byId(EntityType.ENDERMAN),
            List.of(
                    "<gray>공격력이 높은 엔더맨 입니다.</gray>",
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
            )
    );

    public static final TowerType T3_END_CRYSTAL_TOWER = tower(
            "t3_end_crystal_tower",
            "엔드 수정",
            150,
            50,
            0,
            20,
            20,
            10,
            byId(EntityType.END_CRYSTAL),
            List.of(
                    "<gray>공격력이 매우 높은 엔드 수정 입니다.</gray>",
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
            )
    );

    public static final TowerType T1_SHULKER_TOWER = tower(
            "t1_shulker_tower",
            "셜커",
            50,
            100,
            0,
            5,
            20,
            10,
            byId(EntityType.SHULKER),
            List.of(
                    "<gray>체력이 높은 셜커 입니다.</gray>",
                    "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
            )
    );

    public static final TowerType T2_SHULKER_TOWER = tower(
            "t2_shulker_tower",
            "견고한 셜커",
            100,
            150,
            0,
            5,
            20,
            10,
            ShulkerVisual.builder().color(DyeColor.PURPLE).build(),
            List.of(
                    "<gray>체력이 높은 견고한 셜커 입니다.</gray>",
                    "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
            )
    );

    public static final TowerType T3_SHULKER_TOWER = tower(
            "t3_shulker_tower",
            "완강한 셜커",
            150,
            200,
            0,
            5,
            20,
            10,
            ShulkerVisual.builder().color(DyeColor.BLACK).build(),
            List.of(
                    "<gray>체력이 매우 높은 완강한 셜커 입니다.</gray>",
                    "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
            )
    );



    private static final Set<String> ENDER_TOWER_IDS = Set.of(
            BASE_END_TOWER.id(),
            T1_ENDERMITE_TOWER.id(),
            T2_ENDERMAN_TOWER.id(),
            T3_END_CRYSTAL_TOWER.id(),
            T1_SHULKER_TOWER.id(),
            T2_SHULKER_TOWER.id(),
            T3_SHULKER_TOWER.id()
    );

    private static final Set<String> END_CRYSTAL_LINE_IDS = Set.of(
            T1_ENDERMITE_TOWER.id(), T2_ENDERMAN_TOWER.id(), T3_END_CRYSTAL_TOWER.id()
    );
    private static final Set<String> SHULKER_LINE_IDS = Set.of(
            T1_SHULKER_TOWER.id(), T2_SHULKER_TOWER.id(), T3_SHULKER_TOWER.id()
    );

    static {
        List<String> dragonDescription = List.of(
                "<gray>알로 소환되며, 라운드 시작 시 팬텀으로 변합니다.</gray>",
                "<gray>최대 체력이 <yellow>{ability.end_global.dragonEvolutionMaxHealth:integer}</yellow> 이상이면 엔더 드래곤으로 진화합니다.</gray>",
                "<gray>팬텀 크기는 1.0부터 최대 체력 100당 0.2 증가합니다.</gray>",
                "<white>힘 전달 <green>{ability.end_global.absorptionDurationTicks:seconds} 후 전달 타워 사망 / 체력 {ability.end_global.absorptionHealAmount:integer} 회복</green></white>",
                "<white>엔더 드래곤 성장: 체력 <color:#ff8080>셜커 계열 타워 체력의 {ability.end_global.permanentHealthRatio:percent} 영구 누적</color> / 공격력 <red>엔드 수정 계열 타워 공격력의 {ability.end_global.permanentDamageRatio:percent} 영구 누적</red></white>",
                "<white>공격 속도 <yellow>엔드 수정 {ability.end_global.endCrystalAttackIntervalEvery:integer}스택마다 -{ability.end_global.attackIntervalReductionPerStep:integer}틱 (최소 {ability.end_global.minimumAttackIntervalTicks:integer}틱) / 이번 라운드 전달 타워 {ability.end_global.roundAbsorptionAttackIntervalEvery:integer}기마다 -{ability.end_global.roundAbsorptionAttackIntervalReductionTicks:integer}틱</yellow></white>",
                "<white>공격 범위 <yellow>엔드 수정 {ability.end_global.endCrystalSplashEvery:integer}스택마다 +{ability.end_global.splashRadiusPerStep:blocks} (최대 {ability.end_global.splashRadiusCap:blocks})</yellow> / 사거리 <yellow>엔드 수정 {ability.end_global.endCrystalAttackRangeEvery:integer}스택마다 +{ability.end_global.attackRangePerStep:blocks} (최대 +{ability.end_global.attackRangeCap:blocks})</yellow></white>",
                "<white>받는 피해 감소 <blue>셜커 {ability.end_global.shulkerReductionEvery:integer}스택마다 +{ability.end_global.damageReductionPerStep:percent} (최대 {ability.end_global.damageReductionCap:percent})</blue></white>",
                "<white>생명력 흡수 <red>셜커 {ability.end_global.shulkerLifeStealEvery:integer}스택마다 +{ability.end_global.lifeStealPerStep:percent} (최대 {ability.end_global.lifeStealCap:percent})</red> / 재생 <green>셜커 {ability.end_global.shulkerRegenerationEvery:integer}스택마다 초당 +{ability.end_global.regenerationPerStep:integer} (최대 {ability.end_global.regenerationCap:integer})</green></white>",
                "<white>최대 체력 {ability.end_global.dragonEvolutionMaxHealth:integer} 이상: 최종 피해 <red>+{ability.end_global.dragonFinalDamageBonus:percent}</red> / 저항 <light_purple>{ability.end_global.dragonIncomeDebuffResistance:percent}</light_purple></white>"
        );
        TowerDescriptionRegistry.registerTemplate(BASE_END_TOWER, dragonDescription);
        TowerDescriptionRegistry.registerTemplate(T1_ENDERMITE_TOWER, List.of(
                "<gray>공격력이 높은 엔더마이트 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_ENDERMAN_TOWER, List.of(
                "<gray>공격력이 높은 엔더맨 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_END_CRYSTAL_TOWER, List.of(
                "<gray>공격력이 매우 높은 엔드 수정 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격 능력을 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_SHULKER_TOWER, List.of(
                "<gray>체력이 높은 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_SHULKER_TOWER, List.of(
                "<gray>체력이 높은 견고한 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_SHULKER_TOWER, List.of(
                "<gray>체력이 매우 높은 완강한 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 내구력을 강화합니다.</green>"
        ));
    }

    private EndTowers() {
    }

    public static boolean isEndTower(TowerType type) {
        return type != null && ENDER_TOWER_IDS.contains(type.id());
    }

    public static boolean isBaseEndTower(TowerType type) {
        return type != null && type.id().equals(BASE_END_TOWER.id());
    }

    public static boolean isEndCrystalLine(TowerType type) {
        return type != null && END_CRYSTAL_LINE_IDS.contains(type.id());
    }

    public static boolean isShulkerLine(TowerType type) {
        return type != null && SHULKER_LINE_IDS.contains(type.id());
    }

    public static boolean isAbsorbableTower(TowerType type) {
        return isEndCrystalLine(type) || isShulkerLine(type);
    }

    public static int absorptionTier(TowerType type) {
        return ProductionTowerCatalog.entry(type)
                .map(ProductionTowerCatalog.CatalogEntry::tier)
                .orElse(0);
    }

    public static double phantomScaleForMaxHealth(double maxHealth) {
        double scale = PHANTOM_BASE_SCALE + Math.max(0.0, maxHealth) / 100.0 * PHANTOM_SCALE_PER_100_MAX_HEALTH;
        return Math.min(5.0, scale);
    }

}
