package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.plant.PlantCombatTower;
import kim.biryeong.semiontd.tower.plant.PlantMineTower;
import kim.biryeong.semiontd.tower.plant.PlantSoil;
import kim.biryeong.semiontd.tower.plant.PlantSoilStates;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PlantTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "plant_towers");

    public PlantTowerJob() {
        super(
                ID,
                Component.literal("식물 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>테라포밍 타워로 바닥을 바꾼 뒤 같은 지형의 식물을 심으세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>잔디, 균사, 사암, 회백토 중 원하는 효과에 맞춰 자리를 나누세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>식물은 자기 지형에만 놓을 수 있고 사거리 밖의 적을 쫓지 않습니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return PlantTowers.isPlantTower(towerType);
    }

    /**
     * 상점을 지형별로 나눕니다. 테라포밍 타워와 그 지형에 심는 전투 타워가 한 묶음이 됩니다.
     */
    @Override
    public String towerGroup(TowerType towerType) {
        PlantSoil soil = PlantTowers.soilOf(towerType);
        return soil == null ? null : soil.displayName();
    }

    @Override
    public void onMatchStarted(JobContext context) {
        PlantSoilStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        context.game().playerLane(context.player().uuid()).ifPresent(lane -> {
            long payout = lane.towers().stream()
                    .filter(tower -> tower.health() > 0.0)
                    .filter(PlantCombatTower.class::isInstance)
                    .map(PlantCombatTower.class::cast)
                    .filter(tower -> context.player().uuid().equals(tower.ownerPlayer()))
                    .mapToLong(PlantCombatTower::diamondPerWave)
                    .sum();
            if (payout > 0L) {
                context.player().economy().addMineral(payout);
            }
            decayMines(context, lane);
        });
    }

    /**
     * 균사 지뢰를 한 단계씩 삭힙니다. 붉은 버섯은 사라집니다.
     *
     * <p>지뢰는 폭발 한 번으로 소모되지 않는 대신 라운드마다 값을 치릅니다. 뒤틀린 버섯을 심으면
     * 세 라운드를 버티고, 붉은 버섯은 한 라운드짜리입니다.
     *
     * <p>여기서 도는 이유는 두 가지입니다. 라운드 경계를 아는 쪽이 타워가 아니라 직업이고,
     * {@code PlayerLane#resetForRound} 는 타워 목록을 그대로 순회하므로 그 안에서 타워를 지우거나
     * 갈아 끼울 수 없습니다. 이 훅은 정산({@code recordBuilderRoundResults}) 뒤, 준비 단계
     * 시작 전에 불려서 이번 라운드 전과가 기록된 뒤에 삭습니다.
     *
     * <p>삭은 타워는 {@code copyFrom} 을 쓰지 않고 새로 만듭니다. 판매가와 체력이 지금 티어 기준으로
     * 잡혀야 합니다 - 값을 물려받으면 붉은 버섯을 뒤틀린 버섯 값에 되팔 수 있습니다.
     */
    private static void decayMines(JobContext context, PlayerLane lane) {
        for (Tower tower : List.copyOf(lane.towers())) {
            if (!(tower instanceof PlantMineTower mine)
                    || !context.player().uuid().equals(mine.ownerPlayer())) {
                continue;
            }
            TowerType decayed = PlantTowers.previousMyceliumTier(mine.type());
            if (decayed == null) {
                lane.removeTower(mine);
                continue;
            }
            ProductionTowerCatalog.entry(decayed)
                    .map(entry -> entry.create(
                            mine.ownerPlayer(),
                            mine.teamId(),
                            mine.laneId(),
                            mine.originalPosition(),
                            mine.position()))
                    .ifPresent(replacement -> lane.replaceTower(mine, replacement));
        }
    }

    @Override
    public void onEliminated(JobContext context) {
        PlantSoilStates.clear(context.player().uuid());
    }
}
