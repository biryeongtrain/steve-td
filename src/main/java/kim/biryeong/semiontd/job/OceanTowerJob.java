package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
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
                List.of()
        );
    }

    @Override
    public List<Component> description() {
        String supplyRadius = format(
                TowerBalanceRuntime.ability(OceanTowers.T1_WATER.id(), "supplyRadius"),
                "number"
        );
        return List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>물 타워를 먼저 놓고 " + supplyRadius + "칸 안에 전투 타워를 배치하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>전투 타워가 저장한 물이 많을수록 공격력이 높아집니다.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>물이 마르면 능력이 멈추고 타워가 피해를 받습니다.</gray>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return OceanTowers.isOceanTower(towerType);
    }
}
