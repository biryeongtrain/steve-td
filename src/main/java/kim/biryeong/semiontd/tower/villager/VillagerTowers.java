package kim.biryeong.semiontd.tower.villager;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerTowers {
    public static final TowerType T1_SPLASH_TOWER = tower(
            "villager_splash_t1",
            "주민 원거리 기본 타워",
            70,
            40,
            5.5,
            5,
            15,
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
            7,
            15,
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
            9,
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



    private VillagerTowers() {
    }
}
