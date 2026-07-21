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
                        SemionText.mini("<gray>엔더 타워의 힘을 전달받아 핵심 엔더 드래곤을 성장시킵니다.</gray>"),
                        SemionText.mini("<red>그러나 성장 난이도가 매우 어렵습니다.</red>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>아군 타워에게서 힘을 전달받아 엔더 드래곤을 성장시키는 빌더입니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>아군 엔더 타워에게서 20초에 걸쳐 힘을 전달받습니다. 체력·공격력은 이번 라운드에 각각 최대 50%까지 증가하며, 대상 스탯의 5%는 영구 누적됩니다.</gray>"),
                SemionText.mini("<gray>전달을 마친 타워는 사망하고 다음 라운드에 부활합니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>엔드 수정 계열은 엔더 드래곤의 공격력, 광역 공격, 공격 속도를 강화합니다.</gray>"),
                SemionText.mini("<gray>셜커 계열은 엔더 드래곤의 체력, 생명력 흡수, 피해 감소를 강화합니다.</gray>"),
                SemionText.mini("<yellow>엔더 드래곤의 공격 주기는 최소 5틱까지 감소합니다.</yellow>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!EndTowers.isEnderTower(towerType)) {
            return false;
        }
        if (!EndTowers.isBaseEnderTower(towerType) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(EndTowers::isBaseEnderTower))
                .orElse(true);
    }
}
