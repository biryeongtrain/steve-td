package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class VillagerAdvTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_adv_towers");

    public VillagerAdvTowerJob() {
        super(
                ID,
                Component.literal("주민 ADV 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>주민을 놓으면 라운드마다 타워 수와 등급에 따라 경험치를 얻습니다.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>경험치로 개별 주민을 강화하고 내 라인의 적을 모두 막아 평판을 올리세요.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return VillagerTowers.isAdvVillagerTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }
}
