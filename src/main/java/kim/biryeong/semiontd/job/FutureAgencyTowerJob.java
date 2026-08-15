package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyStates;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class FutureAgencyTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "future_agency_towers");

    public FutureAgencyTowerJob() {
        super(ID, Component.literal("미래기관 빌더"), List.of(
                SemionText.mini("<gray><gold>정책</gold>과 원본당 1기의 <aqua>생존 이월</aqua>로 성장하는 후반 왕귀형 빌더입니다.</gray>")));
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray><aqua>150 다이아</aqua> 도피자에게 <gold>미래기관 재건</gold>을 선택하면 5급 전투·제압·방호 요원이 해금됩니다.</gray>"),
                SemionText.mini("<gray>살아남은 설치 원본은 위치·체력을 잇는 <aqua>연결 생존자</aqua>를 최대 1기 유지합니다.</gray>"),
                SemionText.mini("<gray>매 준비 단계에 무작위 <gold>정책 3개</gold>가 나오며 그중 하나만 무료로 선택합니다.</gray>"),
                SemionText.mini("<gray>최고 지휘자·정책 10회·세 역할 1급·1500 다이아를 완성하면 <red>세계 구원</red>으로 이월을 끝내고 중앙전에 참가합니다.</gray>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!FutureAgencyTowers.isFutureAgencyTower(towerType)) return false;
        if (context == null) return true;
        UUID owner = context.player().uuid();
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(owner);
        if (FutureAgencyTowers.isLeader(towerType)) {
            if (!towerType.id().equals(FutureAgencyTowers.ESCAPEE.id())) return true;
            return context.game().playerLane(owner).map(lane -> lane.towers().stream()
                    .noneMatch(tower -> owner.equals(tower.ownerPlayer()) && FutureAgencyTowers.isLeader(tower.type())))
                    .orElse(true);
        }
        return state.reconstructed();
    }

    @Override public boolean includesTowerInCatalog(TowerType type) {return FutureAgencyTowers.isFutureAgencyTower(type);}
    @Override public void onMatchStarted(JobContext context) {FutureAgencyStates.clear(context.player().uuid());}
    @Override public void onRoundStarted(JobContext context, int round) {FutureAgencyStates.state(context.player().uuid()).openRound(round);}
    @Override public void onEliminated(JobContext context) {FutureAgencyStates.clear(context.player().uuid());}
}
