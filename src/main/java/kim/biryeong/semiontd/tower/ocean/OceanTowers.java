package kim.biryeong.semiontd.tower.ocean;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.SalmonVisual;
import kim.biryeong.semiontd.entity.visual.TropicalFishVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

public final class OceanTowers {
    public static final TowerType T1_WATER = tower(
            "ocean_water_t1", "물 타워", 25, 70.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of(
                    "<gray>바다 전투 타워에 물을 공급하는 기본 시설입니다.</gray>",
                    "<green>웨이브 시작 시 {ability.supplyRadius:blocks} 안의 살아 있는 바다 전투 타워를 공급 대상으로 고정하고, 물 {ability.waveStartWater:number}을 즉시 공급합니다.</green>",
                    "<green>웨이브 중에는 고정된 대상에게 초당 물 {ability.waterPerSupply:number}을 계속 공급합니다.</green>"
            )
    );
    public static final TowerType T2_SPRING_WATER = tower(
            "ocean_water_t2", "샘물 타워", 60, 110.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of(
                    "<gray>더 많은 물을 공급하는 중급 시설입니다.</gray>",
                    "<green>웨이브 시작 시 {ability.supplyRadius:blocks} 안의 살아 있는 바다 전투 타워를 공급 대상으로 고정하고, 물 {ability.waveStartWater:number}을 즉시 공급합니다.</green>",
                    "<green>웨이브 중에는 고정된 대상에게 초당 물 {ability.waterPerSupply:number}을 계속 공급합니다.</green>"
            )
    );
    public static final TowerType T3_CURRENT = tower(
            "ocean_water_t3", "해류 타워", 150, 180.0, 0.0, 0.0, 20, -10,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of(
                    "<gray>가장 많은 물을 공급하는 최종 시설입니다.</gray>",
                    "<green>웨이브 시작 시 {ability.supplyRadius:blocks} 안의 살아 있는 바다 전투 타워를 공급 대상으로 고정하고, 물 {ability.waveStartWater:number}을 즉시 공급합니다.</green>",
                    "<green>웨이브 중에는 고정된 대상에게 초당 물 {ability.waterPerSupply:number}을 계속 공급합니다.</green>"
            )
    );

    public static final TowerType T1_PUFFERFISH = tower(
            "ocean_pufferfish_t1", "복어 타워", 40, 130.0, 2.4, 5.0, 16, 50,
            byId(EntityType.PUFFERFISH),
            List.of(
                    "<gray>짧은 사거리와 높은 어그로로 앞라인을 지키는 기본 탱커입니다.</gray>",
                    "<green>물을 보유한 동안 받는 피해가 {ability.damageReduction:percent} 감소합니다.</green>",
                    "<green>피격 시 {ability.transferCooldownTicks:seconds}마다 물 {ability.transferWaterCost:number}을 소모합니다. 실제로 받은 피해를 최대 {ability.transferCap:number}의 물로 바꿔 {ability.transferRadius:blocks} 안의 다른 바다 전투 타워에 나눠 줍니다. 복어·가디언·엘더 가디언은 공급 대상에서 제외됩니다.</green>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>"
            )
    );
    public static final TowerType T2_GUARDIAN = tower(
            "ocean_guardian_t2", "가디언 타워", 130, 230.0, 2.6, 9.0, 16, 70,
            EntityVisual.builder(byId(EntityType.GUARDIAN)).scale(0.9).build(),
            List.of(
                    "<gray>더 높은 체력과 어그로로 앞라인을 지키는 중급 탱커입니다.</gray>",
                    "<green>물을 보유한 동안 받는 피해가 {ability.damageReduction:percent} 감소합니다.</green>",
                    "<green>피격 시 {ability.transferCooldownTicks:seconds}마다 물 {ability.transferWaterCost:number}을 소모합니다. 실제로 받은 피해를 최대 {ability.transferCap:number}의 물로 바꿔 {ability.transferRadius:blocks} 안의 다른 바다 전투 타워에 나눠 줍니다. 복어·가디언·엘더 가디언은 공급 대상에서 제외됩니다.</green>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>"
            )
    );
    public static final TowerType T3_ELDER_GUARDIAN = tower(
            "ocean_elder_guardian_t3", "엘더 가디언 타워", 210, 450.0, 3.0, 16.0, 20, 110,
            EntityVisual.builder(byId(EntityType.ELDER_GUARDIAN)).scale(0.5).build(),
            List.of(
                    "<gray>압도적인 체력과 어그로로 앞라인을 버티는 최종 탱커입니다.</gray>",
                    "<green>물을 보유한 동안 받는 피해가 {ability.damageReduction:percent} 감소합니다.</green>",
                    "<green>피격 시 {ability.transferCooldownTicks:seconds}마다 물 {ability.transferWaterCost:number}을 소모합니다. 실제로 받은 피해를 최대 {ability.transferCap:number}의 물로 바꿔 {ability.transferRadius:blocks} 안의 다른 바다 전투 타워에 나눠 줍니다. 복어·가디언·엘더 가디언은 공급 대상에서 제외됩니다.</green>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>"
            )
    );

    public static final TowerType T1_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t1", "열대어 타워", 40, 55.0, 0.0, 0.0, 100, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.KOB)
                    .baseColor(DyeColor.LIGHT_BLUE)
                    .patternColor(DyeColor.WHITE)
                    .build().withScale(0.7),
            List.of(
                    "<gray>주변 바다 전투 타워의 공격력과 공격 속도를 높이는 기본 지원 타워입니다.</gray>",
                    "<green>{ability.supportIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.supportRadius:blocks} 안의 아군을 {ability.buffDurationTicks:seconds} 동안 강화합니다.</green>",
                    "<green>강화된 타워는 공격력이 {ability.damageBonus:percent}, 공격 속도가 {ability.attackSpeedBonus:percent} 증가합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 공격력·공격 속도 증가량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );
    public static final TowerType T2_LARGE_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t2", "큰 열대어 타워", 110, 85.0, 0.0, 0.0, 90, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.SUNSTREAK)
                    .baseColor(DyeColor.CYAN)
                    .patternColor(DyeColor.BLUE)
                    .build(),
            List.of(
                    "<gray>더 자주 강한 전투 버프를 제공하는 중급 지원 타워입니다.</gray>",
                    "<green>{ability.supportIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.supportRadius:blocks} 안의 아군을 {ability.buffDurationTicks:seconds} 동안 강화합니다.</green>",
                    "<green>강화된 타워는 공격력이 {ability.damageBonus:percent}, 공격 속도가 {ability.attackSpeedBonus:percent} 증가합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 공격력·공격 속도 증가량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );
    public static final TowerType T3_GIANT_TROPICAL_FISH = tower(
            "ocean_tropical_fish_t3", "거대 열대어 타워", 190, 130.0, 0.0, 0.0, 80, -5,
            TropicalFishVisual.builder()
                    .pattern(TropicalFish.Pattern.BETTY)
                    .baseColor(DyeColor.BLUE)
                    .patternColor(DyeColor.LIGHT_BLUE)
                    .build().withScale(1.2),
            List.of(
                    "<gray>가장 빠르고 강한 전투 버프를 제공하는 최종 지원 타워입니다.</gray>",
                    "<green>{ability.supportIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.supportRadius:blocks} 안의 아군을 {ability.buffDurationTicks:seconds} 동안 강화합니다.</green>",
                    "<green>강화된 타워는 공격력이 {ability.damageBonus:percent}, 공격 속도가 {ability.attackSpeedBonus:percent} 증가합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 공격력·공격 속도 증가량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );

    public static final TowerType T1_SQUID = tower(
            "ocean_squid_t1", "오징어 타워", 50, 60.0, 0.0, 0.0, 100, -5,
            EntityVisual.builder(byId(EntityType.SQUID)).scale(0.75).build(),
            List.of(
                    "<gray>주변의 피해 입은 바다 전투 타워를 회복시키는 기본 지원 타워입니다.</gray>",
                    "<green>웨이브 중 {ability.healIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.healRadius:blocks} 안의 다른 바다 전투 타워를 각각 {ability.healAmount:number} 회복시킵니다.</green>",
                    "<green>범위 안에 회복할 대상이 없거나 물이 부족하면 물을 소모하지 않고 대기합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 치유량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );
    public static final TowerType T2_GLOW_SQUID = tower(
            "ocean_glow_squid_t2", "발광 오징어 타워", 120, 90.0, 0.0, 0.0, 90, -5,
            EntityVisual.builder(byId(EntityType.GLOW_SQUID)).scale(0.9).build(),
            List.of(
                    "<gray>더 넓은 범위를 더 자주 회복시키는 중급 지원 타워입니다.</gray>",
                    "<green>웨이브 중 {ability.healIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.healRadius:blocks} 안의 다른 바다 전투 타워를 각각 {ability.healAmount:number} 회복시킵니다.</green>",
                    "<green>범위 안에 회복할 대상이 없거나 물이 부족하면 물을 소모하지 않고 대기합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 치유량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );
    public static final TowerType T3_DOLPHIN = tower(
            "ocean_dolphin_t3", "돌고래 타워", 210, 140.0, 0.0, 0.0, 80, -5,
            EntityVisual.builder(byId(EntityType.DOLPHIN)).build(),
            List.of(
                    "<gray>넓은 범위에 강한 회복을 빠르게 제공하는 최종 지원 타워입니다.</gray>",
                    "<green>웨이브 중 {ability.healIntervalTicks:seconds}마다 물 {ability.abilityWaterCost:number}을 소모해 {ability.healRadius:blocks} 안의 다른 바다 전투 타워를 각각 {ability.healAmount:number} 회복시킵니다.</green>",
                    "<green>범위 안에 회복할 대상이 없거나 물이 부족하면 물을 소모하지 않고 대기합니다.</green>",
                    "<aqua>물 {ability.ocean_global.empoweredAbilityWaterThreshold:number} 이상이면 물을 {ability.ocean_global.empoweredAbilityWaterCostMultiplier:number}배 소모하고 치유량이 {ability.ocean_global.empoweredAbilityEffectMultiplier:number}배가 됩니다.</aqua>"
            )
    );

    public static final TowerType T1_SALMON = tower(
            "ocean_salmon_t1", "연어 타워", 45, 55.0, 6.5, 6.0, 16, 0,
            SalmonVisual.builder().size(Salmon.Variant.SMALL).build().withScale(0.75),
            List.of(
                    "<gray>기본 공격이 대상 주변까지 번지는 광역 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>물 {ability.splashWaterCost:number}을 추가로 소모할 수 있으면 대상 주변 {ability.splashRadius:blocks}에 공격 피해의 {ability.splashDamageRatio:percent}를 줍니다.</green>"
            )
    );
    public static final TowerType T2_LARGE_SALMON = tower(
            "ocean_salmon_t2", "큰 연어 타워", 100, 80.0, 7.5, 13.0, 15, 0,
            SalmonVisual.builder().size(Salmon.Variant.MEDIUM).build(),
            List.of(
                    "<gray>더 넓고 강한 범위 피해를 주는 중급 광역 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>물 {ability.splashWaterCost:number}을 추가로 소모할 수 있으면 대상 주변 {ability.splashRadius:blocks}에 공격 피해의 {ability.splashDamageRatio:percent}를 줍니다.</green>"
            )
    );
    public static final TowerType T3_GIANT_SALMON = tower(
            "ocean_salmon_t3", "거대 연어 타워", 200, 115.0, 8.5, 24.0, 14, 0,
            SalmonVisual.builder().size(Salmon.Variant.LARGE).build().withScale(1.2),
            List.of(
                    "<gray>넓은 범위를 높은 피해로 쓸어버리는 최종 광역 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>물 {ability.splashWaterCost:number}을 추가로 소모할 수 있으면 대상 주변 {ability.splashRadius:blocks}에 공격 피해의 {ability.splashDamageRatio:percent}를 줍니다.</green>"
            )
    );

    public static final TowerType T1_COD = tower(
            "ocean_cod_t1", "대구 타워", 45, 55.0, 8.0, 6.0, 20, 0,
            EntityVisual.builder(byId(EntityType.COD)).scale(0.7).build(),
            List.of(
                    "<gray>현재 체력이 가장 높은 적을 우선 공격하는 단일 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>인컴/소환 적 공격 시 물 {ability.incomeWaterCost:number}을 추가로 소모할 수 있으면, 저장한 물에 따른 공격력 증가 효과가 {ability.ocean_global.incomeCoefficientMultiplier:number}배가 됩니다.</green>"
            )
    );
    public static final TowerType T2_LARGE_COD = tower(
            "ocean_cod_t2", "큰 대구 타워", 100, 80.0, 11.0, 16.0, 15, 0,
            EntityVisual.builder(byId(EntityType.COD)).build(),
            List.of(
                    "<gray>현재 체력이 가장 높은 적을 더 빠르게 처리하는 중급 단일 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>인컴/소환 적 공격 시 물 {ability.incomeWaterCost:number}을 추가로 소모할 수 있으면, 저장한 물에 따른 공격력 증가 효과가 {ability.ocean_global.incomeCoefficientMultiplier:number}배가 됩니다.</green>"
            )
    );
    public static final TowerType T3_GIANT_COD = tower(
            "ocean_cod_t3", "거대 대구 타워", 210, 115.0, 12.0, 40.0, 12, 0,
            EntityVisual.builder(byId(EntityType.COD)).scale(1.2).build(),
            List.of(
                    "<gray>높은 체력의 적과 인컴/소환 적을 강하게 압박하는 최종 단일 공격 타워입니다.</gray>",
                    "<green>공격할 때 물 {ability.attackWaterCost:number}을 소모하며, 저장한 물이 많을수록 공격력이 증가합니다.</green>",
                    "<green>인컴/소환 적 공격 시 물 {ability.incomeWaterCost:number}을 추가로 소모할 수 있으면, 저장한 물에 따른 공격력 증가 효과가 {ability.ocean_global.incomeCoefficientMultiplier:number}배가 됩니다.</green>"
            )
    );

    private static final Set<String> WATER_IDS = Set.of(T1_WATER.id(), T2_SPRING_WATER.id(), T3_CURRENT.id());
    private static final Set<String> TANK_IDS = Set.of(T1_PUFFERFISH.id(), T2_GUARDIAN.id(), T3_ELDER_GUARDIAN.id());
    private static final Set<String> SUPPORT_IDS = Set.of(T1_TROPICAL_FISH.id(), T2_LARGE_TROPICAL_FISH.id(), T3_GIANT_TROPICAL_FISH.id());
    private static final Set<String> HEALER_IDS = Set.of(T1_SQUID.id(), T2_GLOW_SQUID.id(), T3_DOLPHIN.id());
    private static final Set<String> SPLASH_IDS = Set.of(T1_SALMON.id(), T2_LARGE_SALMON.id(), T3_GIANT_SALMON.id());
    private static final Set<String> HUNTER_IDS = Set.of(T1_COD.id(), T2_LARGE_COD.id(), T3_GIANT_COD.id());

    static {
        List.of(
                T1_WATER, T2_SPRING_WATER, T3_CURRENT,
                T1_PUFFERFISH, T2_GUARDIAN, T3_ELDER_GUARDIAN,
                T1_TROPICAL_FISH, T2_LARGE_TROPICAL_FISH, T3_GIANT_TROPICAL_FISH,
                T1_SQUID, T2_GLOW_SQUID, T3_DOLPHIN,
                T1_SALMON, T2_LARGE_SALMON, T3_GIANT_SALMON,
                T1_COD, T2_LARGE_COD, T3_GIANT_COD
        ).forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private OceanTowers() {
    }

    public static boolean isOceanTower(TowerType type) {
        return type != null && (isWaterTower(type) || isCombatTower(type));
    }

    public static boolean isCombatTower(TowerType type) {
        return type != null && (isTank(type) || isSupport(type) || isHealer(type) || isSplash(type) || isHunter(type));
    }

    public static boolean isWaterTower(TowerType type) {
        return type != null && WATER_IDS.contains(type.id());
    }

    public static boolean isTank(TowerType type) {
        return type != null && TANK_IDS.contains(type.id());
    }

    public static boolean isSupport(TowerType type) {
        return type != null && SUPPORT_IDS.contains(type.id());
    }

    public static boolean isHealer(TowerType type) {
        return type != null && HEALER_IDS.contains(type.id());
    }

    public static boolean isSplash(TowerType type) {
        return type != null && SPLASH_IDS.contains(type.id());
    }

    public static boolean isHunter(TowerType type) {
        return type != null && HUNTER_IDS.contains(type.id());
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 1;
        }
        return type.id().endsWith("_t3") ? 3 : type.id().endsWith("_t2") ? 2 : 1;
    }
}
