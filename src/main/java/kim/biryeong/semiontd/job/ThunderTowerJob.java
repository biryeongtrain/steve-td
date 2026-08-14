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
                List.of(SemionText.mini("<gray>전력 수급 균형으로 모든 타워의 성능이 동시에 정해지는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>모든 타워가 <yellow>전력</yellow>을 나눠 씁니다. 여유가 있으면 함께 강해지고, 모자라면 함께 약해집니다.</gray>"),
                SemionText.mini("<aqua>피뢰침은 공격하지 않고 전력만 생산하고, 절연 아르마딜로는 맞을수록 전력을 보탭니다.</aqua>"),
                SemionText.mini("<red>타워를 늘릴수록 소비가 늘어 기존 타워까지 약해집니다.</red>")
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
