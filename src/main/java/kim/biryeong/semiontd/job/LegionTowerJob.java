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
                        SemionText.mini("<gray>다른 빌더보다 성능은 떨어지지만, 물량으로 상대하는 빌더입니다..</gray>"),
                        SemionText.mini("<red>테스트 중</red>")
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
}
