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
                        SemionText.mini("<gray><gold>여우 한 마리</gold>와 웨이브마다 적이 되는 <red>숙적</red>을 운용합니다.</gray>"),
                        SemionText.mini("<gray>여우로 숙적을 처치해 <aqua>네 갈래 진화 루트</aqua>를 완성합니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gold><bold>전직</bold></gold><gray> — 여우가 설치된 </gray><red>숙적</red><gray>을 막타하면 점수를 얻고, </gray><green>다음 준비 단계</green><gray>에 자동 전직합니다.</gray>"),
                SemionText.mini("<aqua>처치한 숙적의 종류와 누적 점수</aqua><gray>에 따라 네 전직 루트 중 하나로 갈립니다. 준비 단계당 최대 1단계입니다.</gray>"),
                SemionText.mini("<yellow>루트</yellow><gray> — 빠른 저비용 / 팀 지원 / 대상 특화 / 느린 초고점</gray>"),
                SemionText.mini("<dark_gray>인컴 몬스터 처치는 전직에 영향을 주지 않습니다.</dark_gray>"),
                SemionText.mini("<red><bold>위험</bold></red><gray> — 숙적은 슬롯을 차지하고 웨이브 시작 시 적으로 변합니다. 강화 숙적 처치는 2점을 줍니다.</gray>"),
                SemionText.mini("<red><bold>강등</bold></red><gray> — 숙적 판매 시 그 개체의 누적 점수가 빠집니다. 요구량 미달이면 여우는 강등되지만 선택한 루트는 유지됩니다.</gray>"),
                SemionText.mini("<yellow>여우는 한 마리만 설치할 수 있으며 판매 후 다시 설치해도 진행도를 유지합니다.</yellow>")
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
