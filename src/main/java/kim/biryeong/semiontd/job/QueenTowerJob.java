package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.queen.QueenStates;
import kim.biryeong.semiontd.tower.queen.QueenTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class QueenTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "queen_towers");

    public QueenTowerJob() {
        super(ID, Component.literal("붉은 여왕 빌더"), List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>붉은 여왕을 먼저 놓은 뒤 카드병정을 뽑으세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>카드병정 5장을 가로로 모아 포커 족보를 만들고 적을 약화하세요.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>여왕과 병정은 적을 죽이지 못하므로 자이언트 처형이 필요합니다.</gray>")));
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType type) {
        if (!QueenTowers.isQueenTower(type)) return false;
        if (context == null) return true;
        PlayerLane lane = context.game().playerLane(context.player().uuid()).orElse(null);
        if (lane == null) return type.id().equals(QueenTowers.QUEEN.id());
        boolean hasQueen = lane.towers().stream().anyMatch(tower -> tower.type().id().equals(QueenTowers.QUEEN.id()));
        return type.id().equals(QueenTowers.QUEEN.id()) ? !hasQueen : hasQueen;
    }

    @Override public boolean includesTowerInCatalog(TowerType type) {return QueenTowers.isQueenTower(type);}

    @Override
    public void onMatchStarted(JobContext context) {
        UUID playerId = context.player().uuid();
        QueenStates.begin(playerId, context.game().teams().get(context.player().teamId()).laneGroup());
    }

    @Override public void onEliminated(JobContext context) {QueenStates.clear(context.player().uuid());}
}
