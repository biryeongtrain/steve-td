package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class VillagerAdvTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_adv_towers");

    public VillagerAdvTowerJob() {
        super(
                ID,
                Component.literal("주민 ADV 빌더"),
                List.of()
        );
    }

    @Override
    public List<Component> description() {
        String reputationMax = number(TowerBalanceRuntime.villagerAdv().resolvedReputationMax());
        return List.of(
                SemionText.mini("<gray>주민타워 리워크 테스트를 위한 빌더입니다.</gray>"),
                Component.empty(),
                SemionText.mini("<green>경험치</green> : <gray>주민타워는 고유한 자원인 경험치를 가지고 있습니다. 경험치는 매 라운드 시작 시 각 타워 수와 타워들의 티어에 비례해 증가합니다. </gray><red><bold>주민들은 강화하기 위해 경험치를 소모하며, 가진 경험치에 따라 추가 보너스를 획득합니다. </bold></red>"),
                Component.empty(),
                SemionText.mini("<blue>평판</blue> : <gray>평판은 주민 빌더 전체에게 적용되는 버프입니다. 웨이브를 성공적으로 방어할 경우 ( 자신의 라인에서 적을 모두 처치할 경우 ) 웨이브 라운드만큼 평판을 얻습니다. 최대 <yellow>" + reputationMax + "</yellow> 입니다. 각 수치에 따라 모든 타워가 보너스 스텟을 얻습니다.</gray>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return VillagerTowers.isAdvVillagerTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        VillagerAdvStates.clear(context.player().uuid());
    }

    private static String number(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
    }
}
