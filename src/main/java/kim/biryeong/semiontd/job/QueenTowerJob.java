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
                SemionText.mini("<gray>붉은 여왕과 카드병정이 적을 <red>약체화</red>하고 자이언트의 <dark_red>처형</dark_red>으로 마무리하는 빌더입니다.</gray>")));
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>붉은 여왕을 먼저 설치한 뒤 25 다이아 카드병정을 뽑고, 다음 카드도 관리창에서 미리 확인합니다.</gray>"),
                SemionText.mini("<gray>모든 병정은 주변 적을 원본 능력치의 절반까지 약화하며 직접 죽이지 못합니다. 약해진 적은 자이언트가 처형합니다.</gray>"),
                SemionText.mini("<gray>라인에 수직인 가로 5장으로 <light_purple>포커 족보</light_purple>를 완성하면 체력·공속·치유·축소가 함께 강해집니다.</gray>"),
                SemionText.mini("<gray>여왕의 원 안에서 병정이 교전하면 <gold>저놈의 목을 쳐라!</gold>가 2배로 차며, 자이언트가 라인 끝에서 몬스터 생성점으로 질주합니다.</gray>")
        );
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
