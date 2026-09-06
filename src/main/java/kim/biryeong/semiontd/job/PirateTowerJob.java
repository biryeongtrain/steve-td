package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.pirate.PirateTower;
import kim.biryeong.semiontd.tower.pirate.PirateStates;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PirateTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "pirate");
    public PirateTowerJob() { super(ID, Component.literal("해적 빌더"), List.of(SemionText.mini("<gold>시작 재테크</gold> <gray>준비 시간에 다이아를 소비해 제독과 조타수를 강화합니다.</gray>"), SemionText.mini("<aqua>운영 수입</aqua> <gray>갑판원·뱃사공·보물상자로 수익을 순환시킵니다.</gray>"), SemionText.mini("<yellow>연계</yellow> <gray>판매 반환과 보물상자 효과를 조합해 다이아 순환을 설계합니다.</gray>"))); }
    @Override public boolean canUseTower(JobContext context, TowerType type) {
        if (!PirateTowers.isPirateTower(type) || !PirateTowers.matches(type, PirateTowers.ADMIRAL) || context == null) return PirateTowers.isPirateTower(type);
        return context.game().playerLane(context.player().uuid()).map(lane -> lane.towers().stream()
                .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                .noneMatch(tower -> PirateTowers.isAdmiral(tower.type()))).orElse(true);
    }
    @Override public boolean includesTowerInCatalog(TowerType type) { return PirateTowers.isPirateTower(type); }
    @Override public void onMatchStarted(JobContext context) { PirateStates.open(context.game(), context.player()); }
    @Override public void onRoundStarted(JobContext context, int round) { PirateStates.startRound(context.player().uuid()); }
    @Override public void onRoundEnded(JobContext context, int round) { context.game().playerLane(context.player().uuid()).ifPresent(lane -> List.copyOf(lane.towers()).stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast).filter(tower -> context.player().uuid().equals(tower.ownerPlayer())).forEach(tower -> tower.onRoundEnded(lane, round))); }
    @Override public void onEliminated(JobContext context) { PirateStates.close(context.player().uuid()); }
    @Override public void onMatchClosed(JobContext context) { PirateStates.close(context.player().uuid()); }
}
