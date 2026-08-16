package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class IllagerTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_towers");

    public IllagerTowerJob() {
        super(
                ID,
                Component.literal("우민 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>우민을 배치해 적을 처치하고 습격 게이지를 채우세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>표식이 있는 적을 처치하거나 우민 타워가 쓰러져도 게이지를 얻습니다.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>습격은 발동한 라운드가 끝나면 종료됩니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return IllagerTowers.isIllagerTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        IllagerRaidStates.onRoundStarted(context);
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }
}
