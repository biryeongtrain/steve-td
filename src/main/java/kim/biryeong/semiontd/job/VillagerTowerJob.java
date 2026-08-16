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
                        SemionText.mini("<green><bold>시작</bold></green> <gray>타워 수를 늘리기보다 배치한 타워부터 업그레이드하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>업그레이드한 타워를 오래 살리고 처치에 참여시켜 보너스를 쌓으세요.</gray>")
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
