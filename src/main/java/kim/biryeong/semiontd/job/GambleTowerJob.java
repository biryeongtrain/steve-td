package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.gamble.GambleRoundEffects;
import kim.biryeong.semiontd.tower.gamble.GambleSpectatorRewards;
import kim.biryeong.semiontd.tower.gamble.GambleTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class GambleTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SemionTd.MOD_ID, "gamble_towers"
    );

    public GambleTowerJob() {
        super(ID, Component.literal("겜블 빌더"), List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>도박꾼으로 적을 막고 주사위 타워와 슬롯머신으로 지원하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>주사위 타워는 범위 안 전투 타워를, 슬롯머신은 가장 강한 도박꾼 하나를 지원합니다.</gray>"),
                SemionText.mini("<light_purple><bold>성장</bold></light_purple> <gray>도박 점수의 양극단에 도달해 도박왕 또는 어둠의 도박왕으로 전직하세요.</gray>")
        ));
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return GambleTowers.isGambleTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return GambleTowers.isGambleTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        GambleSpectatorRewards.closeRound(context.player().uuid());
        clear(context);
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        GambleSpectatorRewards.openRound(context.player().uuid(), context.player().economy());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        clear(context);
        GambleSpectatorRewards.closeRound(context.player().uuid());
    }

    private static void clear(JobContext context) {
        kim.biryeong.semiontd.ui.GambleRevealService.clear(context.player().uuid());
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> GambleRoundEffects.clearAll(lane, context.player().uuid()));
    }
}
