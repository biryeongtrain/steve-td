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
                        SemionText.mini("<green><bold>시작</bold></green> <gray>흑마법사를 먼저 놓고, 희생시킬 아군 타워를 주변에 배치하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>아군을 흡수해 흑마법사의 체력과 공격 능력을 키우세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>흑마법사는 라인마다 1기만 운용할 수 있습니다.</gray>")
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

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return WarlockTowers.isWarlockTower(towerType);
    }
}
