package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.mage.MageProphetTower;
import kim.biryeong.semiontd.tower.mage.MageStates;
import kim.biryeong.semiontd.tower.mage.MageTowerLifecycle;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.mage.MageWizardTower;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.summon.SummonRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MageTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "mage_towers");

    public MageTowerJob() {
        super(
                ID,
                Component.literal("마도사 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>마법핵을 먼저 놓고 마법사나 예언가를 배치하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>준비 단계에 타워를 우클릭해 주문이나 다음 웨이브의 인컴 예언을 고르세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>두 타워는 기본 공격을 하지 않고 마나가 없으면 주문도 멈춥니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!MageTowers.isMageTower(towerType)) {
            return false;
        }
        var prediction = MageTowers.predictionFor(towerType);
        if (context != null && prediction.isPresent() && SummonRegistry.find(prediction.orElseThrow()).isEmpty()) {
            return false;
        }
        if (context == null || !MageTowers.isCore(towerType)) {
            return true;
        }
        UUID owner = context.player().uuid();
        return context.game().playerLane(owner)
                .map(lane -> lane.towers().stream()
                        .noneMatch(tower -> owner.equals(tower.ownerPlayer()) && MageTowers.isCore(tower.type())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return MageTowers.isMageTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        MageStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        UUID owner = context.player().uuid();
        context.game().playerLane(owner).ifPresent(lane -> MageTowerLifecycle.finishRound(lane, owner));
    }

    @Override
    public void onEliminated(JobContext context) {
        MageStates.clear(context.player().uuid());
    }
}
