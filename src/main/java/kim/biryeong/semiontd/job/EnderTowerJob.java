package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ender.EnderTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EnderTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ender_towers");

    public EnderTowerJob() {
        super(
                ID,
                Component.literal("엔드 빌더"),
                List.of(
                        SemionText.mini("<gray>엔더 타워를 흡수해 드래곤을 성장시키는 고난도 빌더입니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>10초간 힘을 흡수해 라운드 체력·공격력을 최대 50% 얻고, 공급 스탯의 5%를 영구 누적합니다.</gray>"),
                SemionText.mini("<gray>엔드 수정은 공격력·광역·공속, 셜커는 체력·흡혈·피해 감소를 강화합니다.</gray>"),
                SemionText.mini("<gray>진행도는 라운드·업그레이드에 유지되고 사망·판매 시 초기화됩니다. 완료된 타워는 다음 라운드에 부활합니다.</gray>"),
                SemionText.mini("<yellow>영구 공격력 +60, 드래곤 피해 +25%·사거리 +2, 엔더 스택 공격 주기 최소 5틱.</yellow>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!EnderTowers.isEnderTower(towerType)) {
            return false;
        }
        if (!EnderTowers.isBaseEnderTower(towerType) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream()
                        .map(Tower::type)
                        .noneMatch(EnderTowers::isBaseEnderTower))
                .orElse(true);
    }
}
