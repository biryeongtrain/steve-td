package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.adversary.AdversaryBalance;
import kim.biryeong.semiontd.tower.adversary.AdversaryFoxTower;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates;
import kim.biryeong.semiontd.tower.adversary.AdversaryTeamEffects;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AdversaryTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "adversary_towers");

    public AdversaryTowerJob() {
        super(
                ID,
                Component.literal("히어로 빌더"),
                List.of(
                        SemionText.mini("<gold>히어로 여우는 4기까지 설치할 수 있습니다. 웨이브가 시작되면 숙적이 적으로 변합니다.</gold>"),
                        SemionText.mini("<gray>여우가 숙적을 직접 처치하면 전직 점수를 얻습니다. 인컴 적은 점수를 주지 않습니다.</gray>"),
                        SemionText.mini("<gray>전직 점수는 모든 여우가 공유하며, 같은 전직 계열은 한 번만 선택할 수 있습니다.</gray>"),
                        SemionText.mini("<aqua>첫 전직은 200 다이아, 최종 전직은 400 다이아입니다.</aqua>"),
                        SemionText.mini("<yellow>첫 전직 후 웨이브를 한 번 완료해야 최종 전직할 수 있습니다.</yellow>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!AdversaryTowers.isAdversaryTower(towerType)) {
            return false;
        }
        if (!AdversaryTowers.isFox(towerType)
                || !AdversaryTowers.matches(towerType, AdversaryTowers.FOX)
                || context == null) {
            return true;
        }
        int maximum = AdversaryBalance.globalInt("maxFoxTowers", AdversaryBalance.MAX_FOX_TOWERS);
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .filter(AdversaryTowers::isFox)
                        .count() < maximum)
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return AdversaryTowers.isAdversaryTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        AdversaryProgressStates.clear(context.player().uuid());
        var team = context.game().teams().get(context.player().teamId());
        if (team != null) {
            AdversaryTeamEffects.registerTeam(context.player().uuid(), team.laneGroup());
        }
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> AdversaryProgressStates.reconcileLane(context.player().uuid(), lane));
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> lane.towers().stream()
                        .filter(AdversaryFoxTower.class::isInstance)
                        .map(AdversaryFoxTower.class::cast)
                        .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                        .forEach(tower -> AdversaryProgressStates.state(context.player().uuid())
                                .recordCompletedWave(tower.foxId(), tower.form())));
    }

    @Override
    public void onEliminated(JobContext context) {
        AdversaryTeamEffects.unregisterPlayer(context.player().uuid());
        AdversaryProgressStates.clear(context.player().uuid());
    }
}
