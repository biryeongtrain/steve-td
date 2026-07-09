package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class VillagerTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_towers");

    public VillagerTowerJob() {
        super(
                ID,
                Component.literal("주민 빌더"),
                List.of(
                        SemionText.mini("<gray>초반부터 최대한의 타워를 생존시켜 강해지는 형태의 빌더입니다.</gray>"),
                        SemionText.mini("<gray>각 타워를 업그레이드해야 생존 및 처치 보너스를 받기 때문에 업그레이드를 우선하는 것을 권장합니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return canUseVillagerTower(towerType);
    }

    public static boolean canUseVillagerTower(TowerType towerType) {
        return VillagerTowers.isBaseVillagerTower(towerType);
    }
}
