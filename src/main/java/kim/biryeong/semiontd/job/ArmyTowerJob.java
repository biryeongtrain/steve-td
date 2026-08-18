package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.army.ArmyTower;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ArmyTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "army");

    public ArmyTowerJob() {
        super(
                ID,
                Component.literal("군대 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>전투 타워를 먼저 놓고 같은 종류의 신병을 계속 보충하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>웨이브에 참가한 고참은 공격력이 낮아지지만 주변 후임을 강화합니다.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>복무를 마친 타워는 자동 전역하고, 남긴 훈장은 모든 군대 타워를 영구 강화합니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return ArmyTowers.isArmyTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return ArmyTowers.isArmyTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        ArmyStates.clear(context.player().uuid());
    }

    /**
     * Discharges everyone who has served their time.
     *
     * <p>Runs here rather than inside the tower because crediting the payout needs the player's
     * economy, and a tower only ever sees its lane. Done between rounds so a tower never vanishes
     * mid-wave, which would read as the tower having been destroyed.
     */
    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid()).ifPresent(lane -> {
            List<ArmyTower> towers = lane.towers().stream()
                    .filter(ArmyTower.class::isInstance)
                    .map(ArmyTower.class::cast)
                    .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                    .toList();
            towers.forEach(tower -> tower.completeServiceWave(lane));

            List<ArmyTower> due = towers.stream()
                    .filter(ArmyTower::dischargePending)
                    .toList();

            long payout = 0L;
            for (ArmyTower tower : due) {
                long refund = tower.sellRefundAmount();
                tower.showDebugVfx(lane, ArmyTower.DebugVfx.DISCHARGE);
                if (!lane.removeTower(tower)) {
                    continue;
                }
                payout += refund;
                tower.completeDischarge(lane);
            }
            if (payout > 0L) {
                context.player().economy().addMineral(payout);
            }
        });
    }

    @Override
    public void onEliminated(JobContext context) {
        ArmyStates.clear(context.player().uuid());
    }
}
