package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AncientCityTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ancient_city");

    public AncientCityTowerJob() {
        super(
                ID,
                Component.literal("고대 도시 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>첫 고대 도시 타워를 놓아 그 자리에서 스컬크 영토를 만드세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>적을 처치해 영토를 넓히고 타워를 스컬크 위에 배치하세요.</gray>"),
                        SemionText.mini("<gold><bold>연계</bold></gold> <gray>감지체가 표식을 남긴 적은 고대 도시 마법에 더 큰 피해를 받습니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return AncientCityTowers.isAncientCityTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        AncientCityStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        AncientCityStates.onRoundStarted(context.player().uuid(), round);
    }

    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        AncientCityStates.onMonsterKilled(context, monster);
    }

    @Override
    public void onEliminated(JobContext context) {
        AncientCityStates.clear(context.player().uuid());
    }
}
