package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class WarlockTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "warlock_towers");

    public WarlockTowerJob() {
        super(
                ID,
                Component.literal("흑마법사"),
                List.of(
                        SemionText.mini("<gray>아군 타워를 희생해 흑마법사 타워를 키우는 빌더입니다.</gray>"),
                        SemionText.mini("<red>흑마법사 타워는 한 라인에 하나만 운용할 수 있습니다.</red>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!WarlockTowers.isWarlockTower(towerType)) {
            return false;
        }
        if (!WarlockTowers.isWarlockCore(towerType) || !towerType.id().equals(WarlockTowers.BASE_WARLOCK_TOWER.id())) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(WarlockTowers::isWarlockCore))
                .orElse(true);
    }
}
