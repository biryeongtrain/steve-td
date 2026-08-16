package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.plant.PlantCombatTower;
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
        });
    }

    @Override
    public void onEliminated(JobContext context) {
        PlantSoilStates.clear(context.player().uuid());
    }
}
