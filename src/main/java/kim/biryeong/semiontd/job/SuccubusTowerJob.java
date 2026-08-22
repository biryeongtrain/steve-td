package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.succubus.SuccubusAbsorption;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SuccubusTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "succubus");

    public SuccubusTowerJob() {
        super(ID, Component.literal("서큐버스 빌더"), List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>꿈가루와 몽유 타워로 적에게 꿈을 쌓으세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>아군은 꿈으로 강해지지만 10스택이 되면 잠듭니다.</gray>"),
                SemionText.mini("<yellow><bold>성장</bold></yellow> <gray>서큐버스는 세 번째로 잠든 적을 처형합니다.</gray>")
        ));
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!SuccubusTowers.isSuccubusTower(towerType)) return false;
        if (context == null || !SuccubusTowers.isSuccubus(towerType)) return true;
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream().noneMatch(tower ->
                        SuccubusTowers.isSuccubus(tower.type()) && tower.health() > 0.0))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return SuccubusTowers.isSuccubusTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {clear(context);}
    @Override
    public void onEliminated(JobContext context) {clear(context);}
    @Override
    public void onMatchClosed(JobContext context) {clear(context);}

    private static void clear(JobContext context) {
        SuccubusDreams.clearPlayer(context.player().uuid());
        SuccubusAbsorption.clear(context.player().uuid());
    }
}
