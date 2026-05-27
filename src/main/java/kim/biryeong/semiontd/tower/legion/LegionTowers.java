package kim.biryeong.semiontd.tower.legion;

import kim.biryeong.semiontd.entity.visual.ChickenVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.animal.ChickenVariants;

import java.util.List;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.*;

public class LegionTowers {
    public static final TowerType T1_CHICKEN = tower(
            "t1_chicken",
            "닭 타워",
            60,
            30,
            6,
            4,
            10,
            20,
            ChickenVisual.builder().variant(ChickenVariants.TEMPERATE).build(),
            List.of(
                    "<gray> 닭들은 늑대가 싫어서 동물 빌더에 들어가지 않기로 결심했습니다.</gray>",
                    "<green> 웨이브 방어 시작 시 자신의 체력과 공격력의 50%를 가진 분신을 1기 소환합니다.",
                    "<green> 분신은 "
            )
    );


}
