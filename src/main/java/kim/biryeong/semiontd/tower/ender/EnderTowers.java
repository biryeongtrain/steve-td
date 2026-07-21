package kim.biryeong.semiontd.tower.ender;

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

public final class EnderTowers {
    public static final double PHANTOM_BASE_SCALE = 1.0;
    public static final double PHANTOM_SCALE_PER_100_MAX_HEALTH = 0.2;
    public static final EntityVisual DRAGON_EGG_VISUAL = BlockDisplayVisual.builder(Blocks.DRAGON_EGG.defaultBlockState())
            .build();
    public static final EntityVisual PHANTOM_VISUAL = EntityVisual.builder(byId(EntityType.PHANTOM))
            .scale(PHANTOM_BASE_SCALE)
            .build();
    public static final EntityVisual DRAGON_VISUAL = EntityVisual.builder(byId(EntityType.ENDER_DRAGON))
            .build();

    public static final TowerType BASE_ENDER_TOWER = tower(
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
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
            )
    );

    public static final TowerType T2_ENDERMAN_TOWER = tower(
            "t2_enderman_tower",
            "엔더맨",
            125,
            50,
            0,
            15,
            20,
            10,
            byId(EntityType.ENDERMAN),
            List.of(
                    "<gray>공격력이 높은 엔더맨 입니다.</gray>",
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
            )
    );

    public static final TowerType T3_END_CRYSTAL_TOWER = tower(
            "t3_end_crystal_tower",
            "엔드 수정",
            200,
            50,
            0,
            20,
            20,
            10,
            byId(EntityType.END_CRYSTAL),
            List.of(
                    "<gray>공격력이 매우 높은 엔드 수정 입니다.</gray>",
                    "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
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
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
            )
    );

    public static final TowerType T2_SHULKER_TOWER = tower(
            "t2_shulker_tower",
            "견고한 셜커",
            125,
            150,
            0,
            5,
            20,
            10,
            ShulkerVisual.builder().color(DyeColor.PURPLE).build(),
            List.of(
                    "<gray>체력이 높은 견고한 셜커 입니다.</gray>",
                    "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
            )
    );

    public static final TowerType T3_SHULKER_TOWER = tower(
            "t3_shulker_tower",
            "완강한 셜커",
            200,
            200,
            0,
            5,
            20,
            10,
            ShulkerVisual.builder().color(DyeColor.BLACK).build(),
            List.of(
                    "<gray>체력이 매우 높은 완강한 셜커 입니다.</gray>",
                    "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                    "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
            )
    );



    private static final Set<String> ENDER_TOWER_IDS = Set.of(
            BASE_ENDER_TOWER.id(),
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
                "<gray>최대 체력이 <yellow>{ability.ender_global.dragonEvolutionMaxHealth:integer}</yellow> 이상이면 엔더 드래곤으로 진화합니다.</gray>",
                "<gray>팬텀 크기는 1.0부터 최대 체력 100당 0.2 증가합니다.</gray>",
                "<green>타워는 {ability.ender_global.absorptionDurationTicks:seconds} 동안 힘을 전달하고 사망합니다.</green>",
                "<green>엔드 수정 계열: 타워 공격력 {ability.ender_global.permanentDamageRatio:percent} 영구 누적</green>",
                "<green>엔드 수정 계열 누적 {ability.ender_global.endCrystalSplashEvery:integer}스택마다 광역 공격 반경 +{ability.ender_global.splashRadiusPerStep:blocks} (최대 {ability.ender_global.splashRadiusCap:blocks})</green>",
                "<green>엔드 수정 계열 누적 {ability.ender_global.endCrystalAttackIntervalEvery:integer}스택마다 공격 주기 -{ability.ender_global.attackIntervalReductionPerStep:integer}틱 (최소 {ability.ender_global.minimumAttackIntervalTicks:integer}틱)</green>",
                "<green>셜커 계열: 타워 체력 {ability.ender_global.permanentHealthRatio:percent} 영구 누적</green>",
                "<green>셜커 계열 누적 {ability.ender_global.shulkerLifeStealEvery:integer}스택마다 생명력 흡수 +{ability.ender_global.lifeStealPerStep:percent} (최대 {ability.ender_global.lifeStealCap:percent})</green>",
                "<green>셜커 계열 누적 {ability.ender_global.shulkerReductionEvery:integer}스택마다 받는 피해 -{ability.ender_global.damageReductionPerStep:percent} (최대 {ability.ender_global.damageReductionCap:percent} 감소)</green>"
        );
        TowerDescriptionRegistry.registerTemplate(BASE_ENDER_TOWER, dragonDescription);
        TowerDescriptionRegistry.registerTemplate(T1_ENDERMITE_TOWER, List.of(
                "<gray>공격력이 높은 엔더마이트 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_ENDERMAN_TOWER, List.of(
                "<gray>공격력이 높은 엔더맨 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_END_CRYSTAL_TOWER, List.of(
                "<gray>공격력이 매우 높은 엔드 수정 입니다.</gray>",
                "<green>공격을 하지 않지만, 엔드 수정 계열의 힘 전달을 완료하면 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_SHULKER_TOWER, List.of(
                "<gray>체력이 높은 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_SHULKER_TOWER, List.of(
                "<gray>체력이 높은 견고한 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_SHULKER_TOWER, List.of(
                "<gray>체력이 매우 높은 완강한 셜커 입니다.</gray>",
                "<yellow>받는 피해가 {ability.damageReduction:percent} 감소합니다.</yellow>",
                "<green>공격을 하지 않지만, 셜커 계열의 힘 전달을 완료하면 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</green>"
        ));
    }

    private EnderTowers() {
    }

    public static boolean isEnderTower(TowerType type) {
        return type != null && ENDER_TOWER_IDS.contains(type.id());
    }

    public static boolean isBaseEnderTower(TowerType type) {
        return type != null && type.id().equals(BASE_ENDER_TOWER.id());
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
