package kim.biryeong.semiontd.tower.legion;

import kim.biryeong.semiontd.entity.visual.ChickenVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.ParrotVisual;
import kim.biryeong.semiontd.entity.visual.SlimeVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.ChickenVariants;
import net.minecraft.world.entity.animal.Parrot;

import java.util.List;
import java.util.Set;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.*;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

public class LegionTowers {
    public static final TowerType T1_CHICKEN = tower(
            "t1_chicken",
            "닭 타워",
            60,
            30,
            6,
            4,
            15,
            20,
            ChickenVisual.builder().variant(ChickenVariants.DEFAULT).build(),
            List.of(
                    "<gray> 닭들은 늑구가 싫어서 동물 빌더에 들어가지 않기로 결심했다네요.</gray>",
                    "<green> 웨이브 방어 시작 시 자신의 체력과 공격력의 50%를 가진 분신을 1기 소환합니다.",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
            )
    );

    public static final TowerType T2_CHICKEN_TOWER = tower(
            "t2_legion_chicken_tower",
            "인싸 닭 타워",
            100,
            40,
            6,
            7,
            15,
            15,
            ChickenVisual.builder().variant(ChickenVariants.WARM).build(),
            List.of(
                    "<gray> 친구를 더 데려온 닭 타워 입니다. 친구를 많이 데려오려고 해서 그런지 모습이 바뀌었네요.</gray>",
                    "<green> 웨이브 방어 시작 시 자신의 체력과 공격력의 50%를 가진 분신을 1기 소환합니다.",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                    "<green> 공격 시 공객 대상 주위 0.75 블록 내의 대상에게 25% 스플래시 피해를 입힙니다."
            )
    );

    public static final TowerType T2_DPS_CHICKEN_TOWER = tower(
            "t2_dps_chicken_tower",
            "아찐 닭 타워",
            100,
            80,
            7,
            12,
            15,
            10,
            ChickenVisual.builder().variant(ChickenVariants.COLD).build(),
            List.of(
                    "<gray> 이 닭은 친구가 없어서 분신을 소환할 수 없답니다. </gray>",
                    "<green> 공격 시 0.75 블록에 75% 스플래시 피해를 입힙니다. </green>"
            )
    );

    public static final TowerType T1_SLIME_TOWER = tower(
            "t1_slime_tower",
            "슬라임 타워",
            50,
            50,
            2,
            3,
            20,
            30,
            SlimeVisual.builder().size(1).build(),
            List.of(
                    "<gray> 아무 빌더나 일단 취직하려 보려고 여기저기 이력서를 내다가 무리 빌더에 합류했습니다.</gray>",
                    "<green> 웨이브 시작 시 자신의 체력과 공격력 65%의 스텟을 가진 분신을 소환합니다. </green>",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
            )
    );

    public static final TowerType T2_SLIME_TOWER = tower(
            "t2_slime_tower",
            "슬라임 타워",
            85,
            80,
            2,
            6,
            20,
            40,
            SlimeVisual.builder().size(2).build(),
            List.of(
                    "<gray> 아무 빌더나 일단 취직하려 보려고 여기저기 이력서를 내다가 무리 빌더에 합류했습니다.</gray>",
                    "<green> 웨이브 시작 시 자신의 체력과 공격력 65%의 스텟을 가진 분신을 2체 소환합니다. </green>",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                    "<green> 매 초 체력이 3씩 재생합니다. </green>"
            )
    );

    public static final TowerType T1_PENGUIN = tower(
            "t1_penguin",
            "펭귄 타워",
            165,
            180,
            4,
            18,
            20,
            30,
            EntityVisual.builder(byId(EntityType.SALMON))
                    .blockbenchModel("semion-td:tower/penguin")
                    .build(),
            List.of(
                    "<gray> 시발 팽귄 살려내라 모장 개새끼들아 </gray>",
                    "<green> 웨이브 시작 시 자신의 체력과 공격력 65%의 스텟을 가진 분신을 2체 소환합니다. </green>",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
            )
    );

    public static final TowerType T2_PENGUIN = tower(
            "t2_penguin_tower",
            "강화 땡컨타워",
            225,
            250,
            4,
            25,
            15,
            30,
            EntityVisual.builder(byId(EntityType.SALMON))
                    .blockbenchModel("semion-td:tower/penguin")
                    .build(),
            List.of(
                    "<gray> 살려내라고 땡컨</gray>",
                    "<green> 웨이브 시작 시 자신의 체력과 공격력 65%의 스텟을 가진 분신을 3체 소환합니다. </green>",
                    "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                    "<green> 공격 시 공격 대상 주위 0.75 범위의 적에게 60%의 피해를 입힙니다. </green>"
            )
    );

    public static final TowerType T1_PARROT_TOWER = tower(
            "t1_parrot_tower",
            "앵무 타워",
            150,
            80,
            8,
            15,
            20,
            15,
            ParrotVisual.builder().variant(Parrot.Variant.DEFAULT).build(),
            List.of(
                    "<gray>동물 빌더가 리워크될 때 실직당한 앵무 타워가 원한을 풀기위해 돌아왔습니다.</gray>",
                    "<green> 공격 시 마다 이 타워의 공격력과 공격속도가 10% 증가합니다. 최대 50% </green>"
            )
    );

    public static final TowerType T2_PARROT_TOWER = tower(
            "t2_parrot_tower",
            "앵무 타워",
            225,
            100,
            8,
            20,
            20,
            15,
            ParrotVisual.builder().variant(Parrot.Variant.GREEN).build(),
            List.of(
                    "<gray>동물 빌더가 리워크될 때 실직당한 앵무 타워가 원한을 풀기위해 돌아왔습니다.</gray>",
                    "<green> 공격 시 마다 이 타워의 공격력과 공격속도가 20% 증가합니다. 최대 100% </green>"
            )
    );

    public static final TowerType T1_GOAT_TOWER = tower(
            "t1_goat_tower",
            "염소 타워",
            70,
            70,
            5,
            0,
            100,
            5,
            EntityVisual.vanilla("minecraft:goat"),
            List.of(
                    "<gray>무리를 향해 나팔을 불어 사기를 끌어올리는 지원 타워입니다.</gray>",
                    "<green>근처 무리 타워의 피해량을 증가시킵니다.</green>",
                    "<green>분신은 추가로 피해 증가와 받는 피해 감소를 얻습니다.</green>"
            )
    );

    public static final TowerType T2_STRONG_GOAT_TOWER = tower(
            "t2_strong_goat_tower",
            "짱쌘 염소 타워",
            150,
            110,
            6,
            0,
            90,
            5,
            EntityVisual.vanilla("minecraft:goat"),
            List.of(
                    "<gray>더 크게 울리는 나팔로 무리의 진형을 단단하게 만듭니다.</gray>",
                    "<green>근처 무리 타워의 피해량을 증가시킵니다.</green>",
                    "<green>분신은 추가로 피해 증가와 받는 피해 감소를 얻습니다.</green>"
            )
    );

    public static final TowerType T3_EXTREME_GOAT_TOWER = tower(
            "t3_extreme_goat_tower",
            "개쌘 염소 타워",
            250,
            160,
            7,
            0,
            80,
            5,
            EntityVisual.vanilla("minecraft:goat"),
            List.of(
                    "<gray>전장의 선두에서 무리를 밀어붙이는 최종 지원 타워입니다.</gray>",
                    "<green>근처 무리 타워의 피해량을 증가시킵니다.</green>",
                    "<green>분신은 추가로 피해 증가와 받는 피해 감소를 얻습니다.</green>"
            )
    );

    public static final TowerType T1_BEE_TOWER = tower(
            "t1_bee_tower",
            "정찰 벌 타워",
            80,
            28,
            7,
            4,
            18,
            0,
            EntityVisual.vanilla("minecraft:bee"),
            List.of(
                    "<gray> 무리 빌더의 정찰 벌떼가 독침으로 오래 버티는 적을 갉아먹습니다. </gray>",
                    "<green> 공격 시 대상에게 독침 스택을 부여합니다. </green>"
            )
    );

    public static final TowerType T2_BEE_TOWER = tower(
            "t2_bee_tower",
            "벌떼 타워",
            160,
            36,
            7,
            6,
            17,
            0,
            EntityVisual.vanilla("minecraft:bee"),
            List.of(
                    "<gray> 더 큰 무리로 독침 스택을 오래 유지하는 벌떼 타워입니다. </gray>",
                    "<green> 높은 독 스택으로 탱커를 압박합니다. </green>"
            )
    );

    public static final TowerType T3_BEE_TOWER = tower(
            "t3_bee_tower",
            "여왕벌 타워",
            310,
            48,
            8,
            8,
            16,
            0,
            EntityVisual.vanilla("minecraft:bee"),
            List.of(
                    "<gray> 여왕벌이 무리를 이끌어 장기전에 독 피해를 누적시킵니다. </gray>",
                    "<green> 더 많은 독침 스택으로 보스와 고체력 몬스터를 압박합니다. </green>"
            )
    );

    public static final TowerType ILLUSION_TOWER = tower(
            "illusion_tower",
            "환술 타워",
            960,
            500,
            2,
            10,
            20,
            1557,
            byId(EntityType.ILLUSIONER),
            List.of(
                    "<red><bold> 이 유닛은 최대 1기만 설치할 수 있습니다. </red></bold>",
                    "<green> 이 타워가 사망할 때, 플레이어의 모든 타워가 자신의 체력, 공격력을 65% 스텟을 가진 분신 1체를 소환합니다. </green>",
                    "<red><bold> 이 타워는 최 우선적으로 공격받습니다. </red></bold>"
            )
    );

    private static final Set<String> LEGION_TOWER_IDS = Set.of(
            T1_CHICKEN.id(),
            T2_CHICKEN_TOWER.id(),
            T2_DPS_CHICKEN_TOWER.id(),
            T1_SLIME_TOWER.id(),
            T2_SLIME_TOWER.id(),
            T1_PENGUIN.id(),
            T2_PENGUIN.id(),
            T1_PARROT_TOWER.id(),
            T2_PARROT_TOWER.id(),
            T1_GOAT_TOWER.id(),
            T2_STRONG_GOAT_TOWER.id(),
            T3_EXTREME_GOAT_TOWER.id(),
//            T1_BEE_TOWER.id(),
//            T2_BEE_TOWER.id(),
//            T3_BEE_TOWER.id(),
            ILLUSION_TOWER.id()
    );

    public static boolean isLegionTower(TowerType towerType) {
        return towerType != null && LEGION_TOWER_IDS.contains(towerType.id());
    }

    static {
        TowerDescriptionRegistry.registerTemplate(T1_CHICKEN, List.of(
                "<gray> 닭들은 늑구가 싫어서 동물 빌더에 들어가지 않기로 결심했다네요.</gray>",
                "<green> 웨이브 방어 시작 시 자신의 체력과 공격력의 {ability.cloneHealthRatio:percent}를 가진 분신을 {ability.cloneCount:integer}기 소환합니다.",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_CHICKEN_TOWER, List.of(
                "<gray> 친구를 더 데려온 닭 타워 입니다. 친구를 많이 데려오려고 해서 그런지 모습이 바뀌었네요.</gray>",
                "<green> 웨이브 방어 시작 시 자신의 체력과 공격력의 {ability.cloneHealthRatio:percent}를 가진 분신을 {ability.cloneCount:integer}기 소환합니다.",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                "<green> 공격 시 공격 대상 주위 {ability.splashRadius:blocks} 내의 대상에게 {ability.splashDamageRatio:percent} 스플래시 피해를 입힙니다."
        ));
        TowerDescriptionRegistry.registerTemplate(T2_DPS_CHICKEN_TOWER, List.of(
                "<gray> 이 닭은 친구가 없어서 분신을 소환할 수 없답니다. </gray>",
                "<green> 공격 시 {ability.splashRadius:blocks}에 {ability.splashDamageRatio:percent} 스플래시 피해를 입힙니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_SLIME_TOWER, List.of(
                "<gray> 아무 빌더나 일단 취직하려 보려고 여기저기 이력서를 내다가 무리 빌더에 합류했습니다.</gray>",
                "<green> 웨이브 시작 시 자신의 체력과 공격력 {ability.cloneHealthRatio:percent}의 스텟을 가진 분신을 {ability.cloneCount:integer}체 소환합니다. </green>",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_SLIME_TOWER, List.of(
                "<gray> 아무 빌더나 일단 취직하려 보려고 여기저기 이력서를 내다가 무리 빌더에 합류했습니다.</gray>",
                "<green> 웨이브 시작 시 자신의 체력과 공격력 {ability.cloneHealthRatio:percent}의 스텟을 가진 분신을 {ability.cloneCount:integer}체 소환합니다. </green>",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                "<green> 매 초 체력이 {ability.regenAmount:number}씩 재생합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_PENGUIN, List.of(
                "<gray> 시발 팽귄 살려내라 모장 개새끼들아 </gray>",
                "<green> 웨이브 시작 시 자신의 체력과 공격력 {ability.cloneHealthRatio:percent}의 스텟을 가진 분신을 {ability.cloneCount:integer}체 소환합니다. </green>",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PENGUIN, List.of(
                "<gray> 살려내라고 땡컨</gray>",
                "<green> 웨이브 시작 시 자신의 체력과 공격력 {ability.cloneHealthRatio:percent}의 스텟을 가진 분신을 {ability.cloneCount:integer}체 소환합니다. </green>",
                "<green> 분신은 본체 타워보다 공격 우선순위가 높습니다. </green>",
                "<green> 공격 시 공격 대상 주위 {ability.splashRadius:blocks} 범위의 적에게 {ability.splashDamageRatio:percent}의 피해를 입힙니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_PARROT_TOWER, List.of(
                "<gray>동물 빌더가 리워크될 때 실직당한 앵무 타워가 원한을 풀기위해 돌아왔습니다.</gray>",
                "<green> 공격 시 마다 이 타워의 공격력과 공격속도가 {ability.attackStackBonus:percent} 증가합니다. 최대 {ability.attackStackBonus*ability.maxAttackStacks:percent} </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_PARROT_TOWER, List.of(
                "<gray>동물 빌더가 리워크될 때 실직당한 앵무 타워가 원한을 풀기위해 돌아왔습니다.</gray>",
                "<green> 공격 시 마다 이 타워의 공격력과 공격속도가 {ability.attackStackBonus:percent} 증가합니다. 최대 {ability.attackStackBonus*ability.maxAttackStacks:percent} </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_GOAT_TOWER, List.of(
                "<gray>월드컵에서 강렬한 성적을 가지고 무리타워에 합류한 GOAT입니다.</gray>",
                "<green>반경 {ability.radius:blocks} 내 무리 타워의 피해량을 {ability.damageBonus:percent} 증가시킵니다.</green>",
                "<green>분신은 위 효과 대신 피해량 {ability.cloneDamageBonus:percent} 증가와 받는 피해 {ability.cloneDamageReduction:percent} 감소를 받습니다.</green>",
                "<gray>염소 버프는 최대 {ability.maxStacks:integer}회 중첩됩니다.</gray>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_STRONG_GOAT_TOWER, List.of(
                "<gray>월드컵에서 강렬한 성적을 가지고 무리타워에 합류한 GOAT입니다.</gray>",
                "<green>반경 {ability.radius:blocks} 내 무리 타워의 피해량을 {ability.damageBonus:percent} 증가시킵니다.</green>",
                "<green>분신은 위 효과 대신 피해량 {ability.cloneDamageBonus:percent} 증가와 받는 피해 {ability.cloneDamageReduction:percent} 감소를 받습니다.</green>",
                "<gray>염소 버프는 최대 {ability.maxStacks:integer}회 중첩됩니다.</gray>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_EXTREME_GOAT_TOWER, List.of(
                "<gray>월드컵에서 강렬한 성적을 가지고 무리타워에 합류한 GOAT입니다.</gray>",
                "<green>반경 {ability.radius:blocks} 내 무리 타워의 피해량을 {ability.damageBonus:percent} 증가시킵니다.</green>",
                "<green>분신은 위 효과 대신 피해량 {ability.cloneDamageBonus:percent} 증가와 받는 피해 {ability.cloneDamageReduction:percent} 감소를 받습니다.</green>",
                "<gray>염소 버프는 최대 {ability.maxStacks:integer}회 중첩됩니다.</gray>"
        ));
        TowerDescriptionRegistry.registerTemplate(T1_BEE_TOWER, List.of(
                "<gray> 야꿀벌. </gray>",
                "<green> 공격 시 {ability.poisonDurationTicks:seconds} 동안 유지되는 독침을 부여합니다. </green>",
                "<green> 독침은 {ability.poisonTickIntervalTicks:seconds}마다 스택당 {ability.poisonDamagePerStack:number} 피해를 줍니다. </green>",
                "<green> 독침은 최대 {ability.maxPoisonStacks:integer}스택까지 중첩됩니다. </green>",
                "<green> 같은 벌 타워마다 스택당 피해가 {ability.poisonDamagePerSwarmStack:number}, 최대 독침이 {ability.poisonStacksPerSwarmStack:integer} 증가합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T2_BEE_TOWER, List.of(
                "<green> 공격 시 {ability.poisonDurationTicks:seconds} 동안 유지되는 독침을 부여합니다. </green>",
                "<green> 독침은 {ability.poisonTickIntervalTicks:seconds}마다 스택당 {ability.poisonDamagePerStack:number} 피해를 줍니다. </green>",
                "<green> 독침은 최대 {ability.maxPoisonStacks:integer}스택까지 중첩됩니다. </green>",
                "<green> 같은 벌 타워마다 스택당 피해가 {ability.poisonDamagePerSwarmStack:number}, 최대 독침이 {ability.poisonStacksPerSwarmStack:integer} 증가합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(T3_BEE_TOWER, List.of(
                "<green> 공격 시 {ability.poisonDurationTicks:seconds} 동안 유지되는 독침을 부여합니다. </green>",
                "<green> 독침은 {ability.poisonTickIntervalTicks:seconds}마다 스택당 {ability.poisonDamagePerStack:number} 피해를 줍니다. </green>",
                "<green> 독침은 최대 {ability.maxPoisonStacks:integer}스택까지 중첩됩니다. </green>",
                "<green> 같은 벌 타워마다 스택당 피해가 {ability.poisonDamagePerSwarmStack:number}, 최대 독침이 {ability.poisonStacksPerSwarmStack:integer} 증가합니다. </green>"
        ));
        TowerDescriptionRegistry.registerTemplate(ILLUSION_TOWER, List.of(
                "<red><bold> 이 유닛은 최대 1기만 설치할 수 있습니다. </red></bold>",
                "<green> 이 타워가 사망할 때, 플레이어의 모든 타워가 자신의 체력, 공격력을 {ability.cloneHealthRatio:percent} 스텟을 가진 분신 {ability.cloneCount:integer}체를 소환합니다. </green>",
                "<red><bold> 이 타워는 최 우선적으로 공격받습니다. </red></bold>"
        ));
    }
}
