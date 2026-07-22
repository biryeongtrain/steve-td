package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OceanTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ocean");

    public OceanTowerJob() {
        super(
                ID,
                Component.literal("바다 빌더"),
                List.of(SemionText.mini("<gray>물을 저장하고 소비해 전투력이 성장하는 물고기 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>물 타워가 반경 2블록의 바다 타워에 물을 공급합니다.</gray>"),
                SemionText.mini("<gray>물은 라운드가 끝나도 유지되며 많을수록 공격력이 증가합니다.</gray>"),
                SemionText.mini("<red>물이 없으면 능력을 사용할 수 없고 공격력·공격 속도가 크게 감소합니다.</red>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return OceanTowers.isOceanTower(towerType);
    }
}
