package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class EndTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            SemionTd.MOD_ID,
            "end_towers"
    );

    public EndTowerJob() {
        super(
                ID,
                Component.literal("엔드 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>무료 엔더 드래곤을 먼저 놓고 셜커와 엔더마이트를 추가하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>셜커는 체력을, 엔더마이트 계열은 공격력을 보내 드래곤을 성장시킵니다.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>성장 준비가 길어 숙련자에게 권장합니다.</gray>")
                )
        );
    }

    @Override
    public boolean isHyperCarry() {
        return true;
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!includesTowerInCatalog(towerType)) {
            return false;
        }
        if (!EndTowers.isBaseEndTower(towerType) || context == null) {
            return true;
        }
        UUID owner = context.player().uuid();
        return context.game().playerLane(owner)
                .map(lane -> lane.towers().stream()
                        .noneMatch(tower -> owner.equals(tower.ownerPlayer())
                                && EndTowers.isBaseEndTower(tower.type())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return EndTowers.isEndTower(towerType);
    }
}
