package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.body.BodyTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BodyTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "body");

    public BodyTowerJob() {
        super(
                ID,
                Component.literal("신체 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>심장을 설치한 뒤 필요한 기관을 배치하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>심장이 박동할 때 다른 모든 신체 타워가 한 번씩 행동합니다.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>심장은 플레이어마다 하나만 설치할 수 있습니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!BodyTowers.isBodyTower(towerType)) {
            return false;
        }
        if (!towerType.id().equals(BodyTowers.HEART_T1.id()) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(BodyTowers::isHeart))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return BodyTowers.isBodyTower(towerType);
    }
}
