package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressState;
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
                Component.literal("대적자 빌더"),
                List.of(
                        SemionText.mini("<gray><gold>여우 한 마리</gold>와 <red>숙적</red>을 함께 운용합니다.</gray>"),
                        SemionText.mini("<gray>웨이브마다 적으로 변한 숙적을 여우가 처치하면 <aqua>새로운 형태</aqua>로 전직합니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gold>여우는 한 마리만 설치할 수 있습니다.</gold>"),
                SemionText.mini("<gray>숙적은 타워 슬롯을 차지하며, 웨이브가 시작되면 설치한 자리에서 적으로 변합니다.</gray>"),
                SemionText.mini("<gray>숙적을 여우가 직접 처치하면 종류에 맞는 <yellow>전직 점수</yellow>를 얻습니다. 강화 숙적은 2점을 줍니다.</gray>"),
                SemionText.mini("<gray>점수를 채우면 <green>다음 준비 단계</green>에 한 단계 전직합니다. 인컴 적은 점수를 주지 않습니다.</gray>"),
                SemionText.mini("<aqua>첫 전직은 질풍 여우, 종지기 여우, 추적자 여우, 메아리 여우 중 하나이며 각 계열에서 최종 형태 2종으로 갈립니다.</aqua>"),
                SemionText.mini("<red>숙적을 판매하면 그 숙적에게서 얻은 점수가 사라집니다. 점수가 부족하면 여우는 강등되지만 전직 계열은 유지됩니다.</red>"),
                SemionText.mini("<yellow>여우를 판매해도 전직 상태와 점수는 유지됩니다.</yellow>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!AdversaryTowers.isAdversaryTower(towerType)) {
            return false;
        }
        if (!AdversaryTowers.isFox(towerType) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(AdversaryTowers::isFox))
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
        AdversaryProgressStates.state(context.player().uuid()).applyPreparationTransition();
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        AdversaryProgressState state = AdversaryProgressStates.state(context.player().uuid());
        context.game().playerLane(context.player().uuid())
                .filter(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .anyMatch(AdversaryTowers::isFox))
                .ifPresent(lane -> state.recordCompletedWave());
    }

    @Override
    public void onEliminated(JobContext context) {
        AdversaryTeamEffects.unregisterPlayer(context.player().uuid());
        AdversaryProgressStates.clear(context.player().uuid());
    }
}
