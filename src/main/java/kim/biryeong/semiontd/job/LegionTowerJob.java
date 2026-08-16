package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LegionTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "legion_towers");

    public LegionTowerJob() {
        super(
                ID,
                Component.literal("무리 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>같은 계열 타워를 여러 기 배치해 무리를 만드세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>분신과 지원 타워를 더해 본체와 소환물을 함께 강화하세요.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!LegionTowers.isLegionTower(towerType)) {
            return false;
        }
        if (!towerType.id().equals(LegionTowers.ILLUSION_TOWER.id())) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(type -> type.id().equals(LegionTowers.ILLUSION_TOWER.id())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return LegionTowers.isLegionTower(towerType);
    }
}
