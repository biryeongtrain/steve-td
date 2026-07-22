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
            15,
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
            80,
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
            130,
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
            80,
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
            130,
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
                "<gray>알로 소환되며, 라운드 시작 시 <dark_purple>아기 드래곤</dark_purple>으로 변합니다.</gray>",
                "<gray><red>최대 체력 {ability.ender_global.dragonEvolutionMaxHealth:integer}</red> 이상이면 <dark_purple>엔더 드래곤</dark_purple>으로 진화합니다.</gray>",
                "<gray><dark_purple>아기 드래곤</dark_purple> 크기는 <red>최대 체력 100</red>당 0.2씩 증가합니다.</gray>",
                "<gray>힘 전달 {ability.ender_global.absorptionDurationTicks:seconds} 후 타워 <dark_red>사망</dark_red>, <red>체력 {ability.ender_global.absorptionHealAmount:integer}</red> 회복합니다.</gray>",
                "<gray>전달 중 타워 당 <red>체력</red>을 초당 <green>+{ability.ender_global.transferHealingPerTower:integer} 재생</green>합니다.</gray>",
                "<gray><dark_red>공격력</dark_red>: 타워 공격력의 <dark_red>{ability.ender_global.roundDamageRatio:percent}</dark_red>를 임시 획득, <dark_red>{ability.ender_global.permanentDamageRatio:percent}</dark_red> 영구 누적</gray>",
                "<gray><red>체력</red>: 타워 체력의 <red>{ability.ender_global.roundHealthRatio:percent}</red>를 임시 획득, <red>{ability.ender_global.permanentHealthRatio:percent}</red> 영구 누적</gray>",
                "<gray><yellow>공격 범위</yellow>: 엔드 수정 <yellow>{ability.ender_global.endCrystalSplashThreshold1:integer} / {ability.ender_global.endCrystalSplashThreshold2:integer} / {ability.ender_global.endCrystalSplashThreshold3:integer} / {ability.ender_global.endCrystalSplashThreshold4:integer}</yellow>스택에서 <yellow>+1</yellow></gray>",
                "<gray><yellow>공격 속도</yellow>: 엔드 수정 <yellow>{ability.ender_global.endCrystalAttackIntervalEvery:integer}</yellow>스택마다 <yellow>-{ability.ender_global.attackIntervalReductionPerStep:integer}틱</yellow></gray>",
                "<gray><yellow>사거리</yellow>: 엔드 수정 <yellow>{ability.ender_global.endCrystalAttackRangeEvery:integer}</yellow>스택마다 <yellow>+{ability.ender_global.attackRangePerStep:blocks}</yellow></gray>",
                "<gray><dark_red>생명력 흡수</dark_red>: 셜커 <dark_red>{ability.ender_global.shulkerLifeStealEvery:integer}</dark_red>스택마다 <dark_red>+{ability.ender_global.lifeStealPerStep:percent}</dark_red></gray>",
                "<gray><blue>받는 피해 감소</blue>: 셜커 <blue>{ability.ender_global.shulkerReductionEvery:integer}</blue>스택마다 <blue>+{ability.ender_global.damageReductionPerStep:percent}</blue></gray>",
                "<gray><green>재생</green>: 셜커 <green>{ability.ender_global.shulkerRegenerationEvery:integer}</green>스택마다 초당 <green>+{ability.ender_global.regenerationPerStep:integer}</green></gray>",
                "<gray><dark_purple>엔더 드래곤</dark_purple> 진화 시 <dark_red>최종 피해</dark_red>: <dark_red>+{ability.ender_global.dragonFinalDamageBonus:percent}</dark_red> / <light_purple>저항</light_purple>: <light_purple>+{ability.ender_global.dragonIncomeDebuffResistance:percent}</light_purple></gray>"
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
