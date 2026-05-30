package kim.biryeong.semiontd.tower.animaladv;

import kim.biryeong.semiontd.entity.visual.FoxVisual;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.animal.Fox;

import java.util.List;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

public class AnimalADVTowers {
    public static final TowerType T1_FOX_TOWER = tower(
            "t1_adv_fox_tower",
            "여우 타워",
            40,
            40,
            6,
            4,
            20,
            20,
            FoxVisual.builder().variant(Fox.Variant.DEFAULT).build(),
            List.of(
                    "<gray> mojang plz more add fox variants </gray>",
                    "<green>> 여우 우두머리 타워는 여우 타워들의 공격력과 공격속도를 100% 증가시킵니다.",
                    "<gray> 초반용 타워입니다. 약하다고 ㅈㄹ ㄴ "
            )
    );

    public static final TowerType T2_FOX_TOWER = tower(
            "t2_adv_fox_tower",
            "강화 여우 타워",
            40,
            50,
            6,
            6,
            20,
            20,
            FoxVisual.builder().variant(Fox.Variant.DEFAULT).build(),
            List.of(
                    "<gray> mojang plz more add fox variants </gray>",
                    "<green>> 여우 우두머리 타워는 여우 타워들의 모든 능력치를 50% 증가시킵니다.",
                    "<gray> 초반용 타워입니다. 약하다고 ㅈㄹ ㄴ ",
                    "<green> 공격 시 0.75 범위의 대상에게 30%의 스플래시 피해를 입힙니다."
            )
    );
//
//    public static final TowerType FOX_BOSS_TOWER = tower(
//
//    )
}
