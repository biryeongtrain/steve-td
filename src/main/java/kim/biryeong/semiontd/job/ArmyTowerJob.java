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
                List.of(SemionText.mini("<gray>짬이 차면 공격을 덜 하고 대신 후임을 강하게 만드는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>전투 타워는 웨이브를 넘길 때마다 <yellow>진급</yellow>합니다. 계급이 오르면 공격력이 줄고 대신 반경 안의 <green>후임</green>을 강화합니다.</gray>"),
                SemionText.mini("<aqua>고참 1기는 후임 2기부터 이득입니다. 신병을 계속 넣어 피라미드를 유지해야 합니다.</aqua>"),
                SemionText.mini("<red>짬 13에 자동 전역합니다. 전역하면 <yellow>훈장</yellow>이 남아 라인 전체가 영구히 강해집니다.</red>")
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
            List<ArmyTower> due = lane.towers().stream()
                    .filter(ArmyTower.class::isInstance)
                    .map(ArmyTower.class::cast)
                    .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                    .filter(ArmyTower::dischargePending)
                    .toList();

            long payout = 0L;
            for (ArmyTower tower : due) {
                long refund = tower.sellRefundAmount();
                if (!lane.removeTower(tower)) {
                    continue;
                }
                payout += refund;
                // onRemoved awards the medal; the lane reference is still valid at this point.
                tower.onRemoved(lane);
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
