package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EndTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "end_towers");

    public EndTowerJob() {
        super(
                ID,
                Component.literal("엔드 빌더"),
                List.of(
                        SemionText.mini("<gray>타워를 설치해 엔더 드래곤을</gray>"),
                        SemionText.mini("<gray>성장시키는 빌더입니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>아군 타워의 체력과 공격력을</gray>"),
                SemionText.mini("<gray>10초에 걸쳐 힘을 받습니다.</gray>"),
                SemionText.mini("<gray>50%는 일시적으로 증가하고,</gray>"),
                SemionText.mini("<gray>5%는 영구적으로 누적됩니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>엔드 수정 계열은 공격 능력을,</gray>"),
                SemionText.mini("<gray>셜커 계열은 내구력을 강화합니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>엔더 드래곤으로 진화하면</gray>"),
                SemionText.mini("<gray>추가 능력을 획득합니다.</gray>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!EndTowers.isEndTower(towerType)) {
            return false;
        }
        if (!EndTowers.isBaseEndTower(towerType) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(EndTowers::isBaseEndTower))
                .orElse(true);
    }
}
