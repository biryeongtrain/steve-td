package kim.biryeong.semiontd.tower.undead;

import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.EntityType;

import java.util.List;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.*;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

public class UndeadTowers {
    // 좀비 타워. 탱커
    public static final TowerType T1_ZOMBIE_TOWER = tower(
      "t1_zombie_tower",
      "좀비 타워",
            95,
            100,
            2,
            5,
            15,
            40,
            byId(EntityType.ZOMBIE),
            List.of(
                    "<gray> 지속 교전을 통한 탱킹에 주력된 타워입니다.</gray>",
                    "<green> 입힌 피해의 20%를 회복합니다.",
                    "<green> 적 처치 시 5초간 공격력이 2 증가합니다. </green>",
                    "<red> 이 수치는 중첩되지 않습니다. </red>"
            )
    );

    public static final TowerType T2_ZOMBIE_TOWER = tower(
            "t2_zombie_tower",
            "허스크 타워",
            180,
            150,
            3,
            8,
            15,
            50,
            byId(EntityType.HUSK),
            List.of(
                    "<gray> 든든한 탱킹 타워입니다.</gray>",
                    "<green> 입힌 피해의 30%를 회복합니다. </green>",
                    "<green> 피해를 입을 경우 2의 범위 내에 있는 적에게 공격력 만큼 피해를 입힙니다. </green>",
                    "<green> 8초 쿨타임이 존재하며, 적중한 적 마다 체력을 2 회복합니다. </green>",
                    "<green> 피격 시 5초간 공격력이 2 증가합니다. </green>",
                    "<red> 이 수치는 중첩되지 않습니다. </red>"
            )
    );

    public static final TowerType T3_ZOMBIE_TOWER = tower(
            "t3_zombie_tower",
            "드라운드 타워",
            350,
            250,
            3,
            10,
            15,
            50,
            byId(EntityType.DROWNED),
            List.of(
                    "<green> 매 라운드마다 단 한번, 치명적인 피해를 입을 경우 3초간 생존합니다.</green>",
                    "<green> 입힌 피해의 30%를 회복합니다. </green>",
                    "<green> 피해를 입을 경우 2의 범위 내에 있는 적에게 공격력 만큼 피해를 입힙니다. </green>",
                    "<green> 8초 쿨타임이 존재하며, 적중한 적 마다 체력을 2 회복합니다. </green>",
                    "<red> 이 수치는 중첩되지 않습니다. </red>",
                    "<green> 피격 시 5초간 공격력이 3 증가합니다. </green>"
            )
    );

    // 스켈타워
    public static final TowerType T1_SKELETON_TOWER = tower(
            "t1_skeleton_tower",
            "뼈 타워",
            75,
            50,
            4,
            5,
            12,
            10,
            byId(EntityType.SKELETON),
            List.of(
                    "<gray> 원거리 또는 근거리 특화 업그레이드가 가능한 타워입니다. </gray>",
                    "<gray> 근거리로 변환이 되는 업그레이드도 있으니 채용할거면 앞에 두는 거 추천 </gray>"
            )
    );



    // 언데드 동물 타워
}
