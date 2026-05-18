package kim.biryeong.semiontd.tower.villager;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

public final class VillagerTowers {
    // 멍청이 타워
    public static final TowerType T1_SPLASH_TOWER = tower(
            "villager_splash_t1",
            "주민 원거리 기본 타워",
            75,
            40,
            5.5,
            5,
            20,
            0,
            VillagerVisual.builder().profession(VillagerProfession.NITWIT).build(),
            List.of("<gray>기본 주민 원거리 타워입니다.</gray>")
    );

    public static final TowerType T2_LIBRARIAN_TOWER = tower(
            "villager_splash_t2",
            "사서 타워",
            110,
            60,
            7,
            8,
            18,
            5,
            VillagerVisual.builder().profession(VillagerProfession.LIBRARIAN).build(),
            List.of(
                    "<gray>무난한 초반 타워입니다.<gray>",
                    "<green>+ 생존한 라운드마다 피해, 공격 속도가 5% 씩 증가합니다. 최대 30% </green>",
                    "<green>+ 스플래시가 존재합니다. 0.5블록"
                    )
    );

    public static final TowerType T3_CLERIC_TOWER = tower(
            "villager_splash_t3",
            "성직자 타워",
            180,
            80,
            7,
            10,
            15,
            10,
            VillagerVisual.builder().profession(VillagerProfession.CLERIC).build(),
            List.of(
                    "<gray> 초중반용 스플래시 타워입니다. </green>",
                    "<green> + 생존한 라운드마다 피해, 공격 속도가 7.5% 씩 증가합니다. 최대 45%",
                    "<green> + 스플래시가 존재합니다. 0.75블록 </green>",
                    "<green> + 3번 공격 시 1번 추가로 공격을 가합니다.</green>"
                    )
    );

    // 골렘 타워

    public static final TowerType T1_GOLEM_TOWER = tower(
            "t1_golem_tower",
            "눈 골렘 타워",
            100,
            80,
            2,
            5,
            20,
            35,
            byId(EntityType.SNOW_GOLEM),
            List.of("<gray>주민 타워의 탱킹을 담당하는 트리입니다.</gray>")
    );

    public static final TowerType T2_GOLEM_TOWER = tower(
            "t2_golem_tower",
            "라마 타워",
            180,
            120,
            2,
            8,
            20,
            50,
            byId(EntityType.TRADER_LLAMA),
            List.of("<gray>구리 골렘이 있는 줄 알고 만들다가 없는거 알고 급하게 바꾼 타워입니다.</gray>",
                    "<green> + 피격 시 3 범위 적에게 8 데미지를 입힙니다. (쿨타임 : 5초)</green>",
                    "<green> + 생존한 라운드 마다 체력이 5% 증가합니다. (최대 : 25%)"
                    )
    );

    public static final TowerType T3_GOLEM_TOWER = tower(
            "t3_golem_tower",
            "철 골렘 타워",
            350,
            180,
            3,
            10,
            20,
            80,
            byId(EntityType.IRON_GOLEM),
            List.of(
                    "<green> + 피격 시 4 범위 적에게 10 데미지를 입힙니다. ( 쿨타임 : 4초 ) </green>",
                    "<green> + 생존한 라운드 마다 체력이 10% 증가합니다. ( 최대 : 50 % ) "
            )
    );


    // 알레이 타워
    public static final TowerType T1_ALLAY_TOWER = tower(
            "t1_allay_tower",
            "알레이 타워",
            120,
            40,
            5,
            2,
            15,
            -5,
            byId(EntityType.ALLAY),
            List.of(
                    "<gray> 팀 지원형 타워입니다.</gray>",
                    "<green> + 5초마다 주위 2블록에 있는 타워의 체력을 10 회복시킵니다. (회복 받은 대상은 5초간 회복받지 않음)</green>"
            )
    );

    public static final TowerType T2_ALLAY_TOWER = tower(
            "t2_allay_tower",
            "알레이 타워(강함)",
            200,
            50,
            5,
            4,
            15,
            -5,
            byId(EntityType.ALLAY),
            List.of(
                    "<green> + 5초마다 주위 3블록에 있는 타워의 체력을 20 회복시킵니다. (회복 받은 대상은 5초간 회복받지 않음)"            )
    );

    public static final TowerType T2_WEAPON_SMITH_TOWER = tower(
            "t2_weapon_smith_tower",
            "대장장이 타워",
            250,
            50,
            5,
            5,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.WEAPONSMITH).build(),
            List.of(
                    "<green> + 5초마다 주위 2블록에 있는 타워의 공격력을 3초간 10% 증가시킵니다. (버프 받은 대상은 5초간 같은 버프를 적용받지 않음) </green>"
            )
    );

    public static final TowerType T3_ARMORER_TOWER = tower(
            "t3_armorer_tower",
            "갑옷 제조인 타워",
            300,
            70,
            7,
            10,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.ARMORER).build(),
            List.of(
                    "<green> + 5초마다 주위 3블록에 있는 타워의 체력을 25 회복시킵니다. 또한 3초간 받는 피해를 10% 감소합니다. </green>",
                    "<red> 효과를 받은 대상은 같은 타워 종류의 효과를 5초간 받을 수 없습니다. </red>"
            )

    );

    public static final TowerType T3_WEAPON_SMITH_TOWER = tower(
            "t3_weapon_smith_tower",
            "강화 대장장이 타워",
            350,
            60,
            5,
            7,
            15,
            -5,
            VillagerVisual.builder().profession(VillagerProfession.WEAPONSMITH).type(VillagerType.SAVANNA).build(),
            List.of(
                    "<green> + 5초마다 주위 3블록 이내에 있는 타워를 3초동안 공격력과 공격속도를 15% 증가시킵니다.</green>",
                    "<red> 이 효과를 받은 타워는 5초동안 같은 효과를 받을 수 없습니다.</red>"
            )
    );


    private static String byId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private VillagerTowers() {
    }
}
