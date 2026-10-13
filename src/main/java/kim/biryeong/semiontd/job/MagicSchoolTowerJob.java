package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MagicSchoolTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school");

    public MagicSchoolTowerJob() {
        super(ID, Component.literal("마법학교 빌더"), List.of(
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>호그와트에서 커리큘럼을 업그레이드하여 새로운 주문과 능력을 해금하고, 마법사를 강화하세요.</gray>"),
                SemionText.mini("<gray>마법사가 사용할 주문을 직접 지정하여 몹을 방어하세요. 티어가 높아질수록 사용할 수 있는 주문이 많아집니다.</gray>"),
                SemionText.mini("<gray>마법사는 전투에 참여하거나 커리큘럼의 효과로 주문 숙련도를 얻을 수 있으며, 숙련도가 쌓일 수록 강해집니다. 최대 숙련도에서 다음 티어로 강화할 수 있습니다.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>커리큘럼은 라운드당 한 번만 업그레이드 할 수 있습니다.</gray>"),
                SemionText.mini("<gray>'기숙사 배정 모자'를 해금하지 않으면 T2 마법사로 강화할 수 없습니다.</gray>")
        ));
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!MagicSchoolTowers.isMagicSchoolTower(towerType)) {
            return false;
        }
        if (!MagicSchoolTowers.HOGWARTS.id().equals(towerType.id()) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid())
                .map(lane -> lane.towers().stream().noneMatch(tower ->
                        tower.ownerPlayer().equals(context.player().uuid())
                                && MagicSchoolTowers.isHogwarts(tower.type())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return MagicSchoolTowers.isMagicSchoolTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }
}
