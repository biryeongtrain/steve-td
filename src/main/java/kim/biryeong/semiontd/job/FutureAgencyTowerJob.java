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
                SemionText.mini("<green><bold>시작</bold></green> <gray>도피자를 설치하고 미래기관 재건을 골라 전투 요원을 해금하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>살아남은 요원은 생존자 1기를 다음 웨이브에 합류시킵니다.</gray>"),
                SemionText.mini("<light_purple><bold>성장</bold></light_purple> <gray>정책을 고르고, 완막하면 다음 준비에 두 번 선택하세요.</gray>")));
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
    @Override public void onRoundEnded(JobContext context, int round) {
        FutureAgencyStates.state(context.player().uuid())
                .setNextSelectionLimit(context.game().hasClearedRound(context.player().uuid(), round) ? 2 : 1);
    }
    @Override public void onEliminated(JobContext context) {FutureAgencyStates.clear(context.player().uuid());}
}
