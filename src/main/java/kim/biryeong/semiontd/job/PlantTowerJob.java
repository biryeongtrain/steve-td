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
                List.of(SemionText.mini("<gray>지형을 바꾸고 그 위에만 식물을 심는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray><green>테라포밍 타워</green>로 라인 바닥을 자기 지형으로 바꾸는 빌더입니다.</gray>"),
                SemionText.mini("<gray>전투 타워는 <green>자기 계열 지형 위에만</green> 심고, 그 지형의 효과를 받습니다.</gray>"),
                SemionText.mini("<gray>한 칸에는 한 계열만 깔리므로 라인 자리를 나눠 써야 합니다.</gray>"),
                SemionText.mini("<yellow>잔디 회복·성장·정산 / 균사 취약·지뢰 / 사암 공속 약화·가시 / 회백토 사거리·치명타</yellow>"),
                SemionText.mini("<red>모든 식물은 뿌리를 내려 사거리 밖 적을 쫓아가지 않습니다.</red>")
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
