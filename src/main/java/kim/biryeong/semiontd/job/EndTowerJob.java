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
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ender_towers");

    public EndTowerJob() {
        super(
                ID,
                Component.literal("엔드 빌더"),
                List.of(
                        SemionText.mini("<gray>엔더 타워의 힘을 전달받아 핵심 엔더 드래곤을 성장시킵니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>아군 타워에게서 힘을 전달받아 엔더 드래곤을 성장시키는 빌더입니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>아군 엔더 타워에게서 10초에 걸쳐 힘을 전달받습니다.</gray>"),
                SemionText.mini("<gray>각 공급 타워 체력·공격력의 50%를 이번 라운드에 얻으며, 대상 스탯의 5%는 영구 누적됩니다.</gray>"),
                SemionText.mini("<gray>전달을 마친 타워는 사망하고 다음 라운드에 부활합니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>엔드 수정 계열은 엔더 드래곤의 공격력, 광역 공격, 공격 속도, 사거리를 강화합니다.</gray>"),
                SemionText.mini("<gray>셜커 계열은 엔더 드래곤의 체력, 생명력 흡수, 피해 감소, 재생을 강화합니다.</gray>")
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
