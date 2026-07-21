package kim.biryeong.semiontd.tower.animal;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.*;

import kim.biryeong.semiontd.entity.visual.FoxVisual;
import kim.biryeong.semiontd.entity.visual.PigVisual;
import kim.biryeong.semiontd.entity.visual.RabbitVisual;
import kim.biryeong.semiontd.entity.visual.WolfVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.PigVariants;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.wolf.WolfVariants;

import java.util.List;


public class AnimalTowers {
    // 돼지 타워
    public static final TowerType T1_PIG_TOWER = tower(
            "t1_pig_tower",
            "돼지 타워",
            40,
            80,
            2,
            5,
            20,
            40,
            PigVisual.builder().variant(PigVariants.DEFAULT).build(),
            List.of(
                    "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                    "<green> 자신의 다른 돼지 타워 당 체력을 10, 공격력을 2.5 얻습니다. </green>",
                    "<green> 최대 중첩 : 2스택"
            )
    );

    public static final TowerType T2_PIG_TOWER = tower(
            "t2_pig_tower",
            "돼지 타워",
            180,
            150,
            2,
            7,
            20,
            55,
            PigVisual.builder().variant(PigVariants.COLD).build(),
            List.of(
                    "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                    "<green> 자신의 다른 돼지 타워 당 체력을 25, 공격력을 5 얻습니다. </green>",
                    "<green> 최대 중첩 : 2스택",
                    "<green> 최대 중첩에 도달할 경우, 받는 피해를 10% 경감합니다. </green>"
            )
    );

    public static final TowerType T3_PIG_TOWER = tower(
            "t3_pig_tower",
            "돼지 타워",
            300,
            350,
            2,
            15,
            20,
            60,
            PigVisual.builder().variant(PigVariants.WARM).build(),
            List.of(
                    "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                    "<green> 자신의 다른 돼지 타워 당 체력을 90, 공격력을 15 얻습니다. </green>",
                    "<green> 최대 중첩 : 2스택",
                    "<green> 최대 중첩에 도달할 경우, 받는 피해를 30% 경감합니다. </green>",
                    "<green> 최대 중첩에 도달할 경우, 공격 시 대상 주위 1블록 적에게 50% 스플래시 피해를 줍니다.</green>"
            )
    );

    // 늑구타워
    public static final TowerType T1_WOLF_TOWER = tower(
            "t1_wolf_tower",
            "늑구 타워",
            50,
            50,
            6,
            5,
            20,
            5,
            WolfVisual.builder().variant(WolfVariants.DEFAULT).tame(true).build(),
            List.of("<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                    "<green> 자신의 다른 늑구 타워 당 공격력을 2, 공격 주기가 1.25 감소합니다.",
                    "<green> 최대 중첩 : 4회"
            )
    );

    public static final TowerType T2_WOLF_DPS_TOWER = tower(
            "t2_wolf_dps_tower",
            "재빠른 늑구 타워",
            110,
            70,
            6,
            10,
            20,
            5,
            WolfVisual.builder().variant(WolfVariants.SNOWY).tame(true).build(),
            List.of("<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                    "<green> 공격 시 1.25 범위 내의 적에게 50%의 스플래시 피해를 입힙니다.</green>",
                    "<green> 자신의 다른 늑구 타워 당 공격력을 5, 공격 주기가 1.25 감소합니다.",
                    "<green> 최대 중첩 : 4회 </green>",
                    "<green> 최대 중첩 시 타워의 공격 주기가 추가로 3 감소합니다. </green>"
            )
    );

    public static final TowerType T3_WOLF_DPS_TOWER = tower(
            "t3_wolf_dps_tower",
            "개빠른 늑구 타워",
            110,
            90,
            6,
            20,
            20,
            0,
            WolfVisual.builder().variant(WolfVariants.ASHEN).tame(true).build(),
            List.of("<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                    "<green> 공격 시 2 범위 내의 적에게 75%의 스플래시 피해를 입힙니다.</green>",
                    "<green> 자신의 다른 늑구 타워 당 공격력을 10, 공격 주기가 1.25 감소합니다.",
                    "<green> 최대 중첩 : 4회 </green>",
                    "<green> 최대 중첩 시 타워의 공격 주기가 추가로 5 감소합니다. </green>",
                    "<green> 최대 중첩 시 공격력이 추가로 5 증가합니다."
            )
    );
    // 토끼 타워
    public static final TowerType T1_RABBIT_TOWER = tower(
            "t1_rabbit_tower",
            "토끼 타워",
            50,
            40,
            7,
            5,
            15,
            -5,
            RabbitVisual.builder().variant(Rabbit.Variant.DEFAULT).build(),
            List.of(
                    "<gray> 깡총~ 토끼가 풀을 뜯으러 왔지!</gray>",
                    "<green> 자신이 가진 토끼 타워당 공격력이 2.5 증가합니다. </green>",
                    "<green> 최대 중첩 : 4회 </green>"
            )
    );


    public static final TowerType T2_RABBIT_TOWER = tower(
      "t2_rabbit_tower",
      "토끼 타워",
      180,
      55,
      7,
      8,
      15,
      -5,
      RabbitVisual.builder().variant(Rabbit.Variant.WHITE_SPLOTCHED).build(),
            List.of(
                    "<green> 자신이 가진 토끼 타워당 공격력이 6.25 증가합니다. </green>",
                    "<green> 최대 중첩 : 4회 </green>",
                    "<green> 최대 중첩 시 공격 주기가 5틱 감소합니다. </green>"
            )
    );

    public static final TowerType T3_RABBIT_TOWER = tower(
            "t3_rabbit_tower",
            "토끼 타워",
            300,
            70,
            7,
            10,
            13,
            -5,
            RabbitVisual.builder().variant(Rabbit.Variant.GOLD).build(),
            List.of(
                    "<green> 자신이 가진 토끼 타워당 공격력이 12.5 증가합니다. </green>",
                    "<green> 최대 중첩 : 4회 </green>",
                    "<green> 최대 중첩 시 공격 주기가 5틱 감소합니다. </green>",
                    "<green> 최대 중첩 시 사거리 내 대상에게 추가 피해 +200%를 줍니다. </green>"
            )
    );

    public static final TowerType T1_FOX_TOWER = tower(
            "t1_fox_tower",
            "사냥 여우 타워",
            60,
            45,
            7,
            12,
            20,
            5,
            FoxVisual.builder().variant(Fox.Variant.DEFAULT).build(),
            List.of(
                    "<gray> 낮은 체력의 적을 노리는 동물 빌더의 마무리 타워입니다. </gray>",
                    "<green> 체력이 낮은 사거리 내 몬스터를 우선 공격하고 추가 피해를 줍니다. </green>",
                    "<green> 사거리 내 몬스터 사망 시 공격력이 증가합니다. </green>",
                    "<green> 같은 여우 타워가 많을수록 처형 기준과 추가 피해가 증가합니다. </green>"
            )
    );

    public static final TowerType T2_FOX_TOWER = tower(
            "t2_fox_tower",
            "붉은 여우 타워",
            170,
            60,
            7,
            20,
            15,
            5,
            FoxVisual.builder().variant(Fox.Variant.DEFAULT).build(),
            List.of(
                    "<gray> 더 이른 체력 구간부터 적을 마무리하는 여우 타워입니다. </gray>",
                    "<green> 처형 대상에게 더 큰 추가 피해를 줍니다. </green>",
                    "<green> 사거리 내 몬스터 사망 시 공격력이 증가합니다. </green>",
                    "<green> 같은 여우 타워가 많을수록 처형 기준과 추가 피해가 증가합니다. </green>"
            )
    );

    public static final TowerType T3_FOX_TOWER = tower(
            "t3_fox_tower",
            "설원 여우 타워",
            320,
            80,
            8,
            30,
            10,
            5,
            FoxVisual.builder().variant(Fox.Variant.SNOW).build(),
            List.of(
                    "<gray> 후반 누수를 정리하는 설원의 사냥꾼입니다. </gray>",
                    "<green> 체력이 낮은 사거리 내 몬스터를 우선 공격하고 큰 추가 피해를 줍니다. </green>",
                    "<green> 사거리 내 몬스터 사망 시 공격력이 증가합니다. </green>",
                    "<green> 같은 여우 타워가 많을수록 더 높은 체력의 적도 처형 대상으로 봅니다. </green>"
            )
    );

    // 양 타워

    static {
        TowerDescriptionRegistry.registerTemplate(T1_PIG_TOWER, List.of(
                "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                "<green> 자신의 다른 돼지 타워 당 체력을 {ability.healthPerStack:number}, 공격력을 {ability.damagePerStack:number} 얻습니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}스택"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PIG_TOWER, List.of(
                "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                "<green> 자신의 다른 돼지 타워 당 체력을 {ability.healthPerStack:number}, 공격력을 {ability.damagePerStack:number} 얻습니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}스택",
                "<green> 최대 중첩에 도달할 경우, 받는 피해를 {ability.damageReduction:percent} 경감합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_PIG_TOWER, List.of(
                "<gray> 동물 빌더의 탱킹 타워입니다. </gray>",
                "<green> 자신의 다른 돼지 타워 당 체력을 {ability.healthPerStack:number}, 공격력을 {ability.damagePerStack:number} 얻습니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}스택",
                "<green> 최대 중첩에 도달할 경우, 받는 피해를 {ability.damageReduction:percent} 경감합니다. </green>",
                "<green> 최대 중첩에 도달할 경우, 공격 시 대상 주위 {ability.splashRadius:blocks} 적에게 {ability.splashDamageRatio:percent} 스플래시 피해를 줍니다.</green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_WOLF_TOWER, List.of(
                "<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                "<green> 자신의 다른 늑구 타워 당 공격력을 {ability.damagePerStack:number}, 공격 주기가 {ability.intervalReductionPerStack:number} 감소합니다.",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_WOLF_DPS_TOWER, List.of(
                "<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                "<green> 공격 시 {ability.splashRadius:blocks} 범위 내의 적에게 {ability.splashDamageRatio:percent}의 스플래시 피해를 입힙니다.</green>",
                "<green> 자신의 다른 늑구 타워 당 공격력을 {ability.damagePerStack:number}, 공격 주기가 {ability.intervalReductionPerStack:number} 감소합니다.",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회 </green>",
                "<green> 최대 중첩 시 타워의 공격 주기가 추가로 {ability.maxStackExtraIntervalReduction:integer} 감소합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_WOLF_DPS_TOWER, List.of(
                "<gray> 대전에서 탈출한 늑구가 여기로 왔네요. </gray>",
                "<green> 공격 시 {ability.splashRadius:blocks} 범위 내의 적에게 {ability.splashDamageRatio:percent}의 스플래시 피해를 입힙니다.</green>",
                "<green> 자신의 다른 늑구 타워 당 공격력을 {ability.damagePerStack:number}, 공격 주기가 {ability.intervalReductionPerStack:number} 감소합니다.",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회 </green>",
                "<green> 최대 중첩 시 타워의 공격 주기가 추가로 {ability.maxStackExtraIntervalReduction:integer} 감소합니다. </green>",
                "<green> 최대 중첩 시 공격력이 추가로 {ability.maxStackDamageBonus:number} 증가합니다."
        ));
        TowerDescriptionRegistry.registerTemplate(T1_RABBIT_TOWER, List.of(
                "<gray> 깡총~ 토끼가 풀을 뜯으러 왔지!</gray>",
                "<green> 자신이 가진 토끼 타워당 공격력이 {ability.damagePerStack:number} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회 </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_RABBIT_TOWER, List.of(
                "<green> 자신이 가진 토끼 타워당 공격력이 {ability.damagePerStack:number} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회 </green>",
                "<green> 최대 중첩 시 공격 주기가 {ability.maxStackExtraIntervalReduction:integer}틱 감소합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_RABBIT_TOWER, List.of(
                "<green> 자신이 가진 토끼 타워당 공격력이 {ability.damagePerStack:number} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회 </green>",
                "<green> 최대 중첩 시 공격 주기가 {ability.maxStackExtraIntervalReduction:integer}틱 감소합니다. </green>",
                "<green> 최대 중첩 시 사거리 내 대상에게 추가 피해 +{ability.extraAttackDamageRatio:percent}를 줍니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_FOX_TOWER, List.of(
                "<gray> 낮은 체력의 적을 노리는 동물 빌더의 마무리 타워입니다. </gray>",
                "<green> 체력이 {ability.executeHealthThreshold:percent} 이하인 사거리 내 몬스터를 우선 공격합니다. </green>",
                "<green> 처형 대상에게 {ability.executeDamageBonusRatio:percent} 추가 피해를 줍니다. </green>",
                "<green> 사거리 내 몬스터 사망 시 공격력이 {ability.killBonusDamage:number} 증가합니다. 최대 {ability.killBonusDamageCap:number}까지 증가합니다. </green>",
                "<green> 같은 여우 타워마다 처형 기준이 {ability.executeThresholdPerStack:percent}, 추가 피해가 {ability.executeDamageBonusPerStack:percent} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회, 처형 기준 최대 : {ability.maxExecuteHealthThreshold:percent} </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_FOX_TOWER, List.of(
                "<gray> 더 이른 체력 구간부터 적을 마무리하는 여우 타워입니다. </gray>",
                "<green> 체력이 {ability.executeHealthThreshold:percent} 이하인 사거리 내 몬스터를 우선 공격합니다. </green>",
                "<green> 처형 대상에게 {ability.executeDamageBonusRatio:percent} 추가 피해를 줍니다. </green>",
                "<green> 사거리 내 몬스터 사망 시 공격력이 {ability.killBonusDamage:number} 증가합니다. 최대 {ability.killBonusDamageCap:number}까지 증가합니다. </green>",
                "<green> 같은 여우 타워마다 처형 기준이 {ability.executeThresholdPerStack:percent}, 추가 피해가 {ability.executeDamageBonusPerStack:percent} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회, 처형 기준 최대 : {ability.maxExecuteHealthThreshold:percent} </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_FOX_TOWER, List.of(
                "<gray> 후반 누수를 정리하는 설원의 사냥꾼입니다. </gray>",
                "<green> 체력이 {ability.executeHealthThreshold:percent} 이하인 사거리 내 몬스터를 우선 공격합니다. </green>",
                "<green> 처형 대상에게 {ability.executeDamageBonusRatio:percent} 추가 피해를 줍니다. </green>",
                "<green> 사거리 내 몬스터 사망 시 공격력이 {ability.killBonusDamage:number} 증가합니다. 최대 {ability.killBonusDamageCap:number}까지 증가합니다. </green>",
                "<green> 같은 여우 타워마다 처형 기준이 {ability.executeThresholdPerStack:percent}, 추가 피해가 {ability.executeDamageBonusPerStack:percent} 증가합니다. </green>",
                "<green> 최대 중첩 : {ability.maxStacks:integer}회, 처형 기준 최대 : {ability.maxExecuteHealthThreshold:percent} </green>"
        ));
    }
}
