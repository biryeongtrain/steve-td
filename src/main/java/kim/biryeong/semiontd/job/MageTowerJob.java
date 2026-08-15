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
                List.of(SemionText.mini("<gray><aqua>마나</aqua>를 모아 주문과 <light_purple>예언</light_purple>으로 인컴을 요격하는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>준비 단계에 주문을 선택하면 마나가 있는 동안 매 웨이브 반복 시전합니다. 예언만 라운드 종료 시 초기화됩니다.</gray>"),
                SemionText.mini("<gray>현재 <aqua>마나</aqua>는 화면 위 보스바에서 확인합니다.</gray>"),
                SemionText.mini("<gray>라운드마다 살아 있는 마법핵은 <aqua>50</aqua>, 쉬는 마법사는 <aqua>8</aqua>, 예언가는 <aqua>15</aqua> 마나를 생산합니다.</gray>"),
                SemionText.mini("<gray>정확한 <light_purple>예언</light_purple>은 인컴을 즉사시키고 많은 <aqua>마나</aqua>를 충전합니다.</gray>"),
                SemionText.mini("<gold>초급→중급→대마법사</gold><gray> 순으로 자동 진화하며 시전 횟수가 쌓일수록 주문 피해가 강해집니다.</gray>"),
                SemionText.mini("<gold>고차원 주문</gold><gray>은 비싸지만 라인 전체를 공격할 수 있습니다.</gray>")
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
