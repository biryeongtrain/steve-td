package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.thunder.ThunderStates;
import kim.biryeong.semiontd.tower.thunder.ThunderTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ThunderTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "thunder");

    public ThunderTowerJob() {
        super(
                ID,
                Component.literal("람쥐썬더 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>피뢰침으로 발전량을 확보한 뒤 전투 타워를 놓으세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>화면의 발전량과 소비량을 보며 전력 여유를 유지하세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>소비가 발전을 넘으면 이미 놓은 타워도 함께 약해집니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return ThunderTowers.isThunderTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return ThunderTowers.isThunderTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        ThunderStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        // One shared roll per wave: a thunderstorm covers the whole lane, so every storm rod the
        // player owns reports the same output rather than each rolling independently.
        ThunderStates.rollStorm(context.player().uuid(), round);
    }

    @Override
    public void onEliminated(JobContext context) {
        ThunderStates.clear(context.player().uuid());
    }
}
