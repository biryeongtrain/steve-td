package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warningText;
import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warlockText;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.warlock.WarlockAwakeningProgress;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
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
                        SemionText.mini("<gray>아군을 희생하며 영구적으로 강해지고, 원거리 또는 근거리 흑마법사를 선택해 마지막까지 살아남아 " + warlockText("각성") + "하는 빌더입니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(SemionText.mini("<gray>아군을 희생하며 영구적으로 강해지고, 원거리 또는 근거리 흑마법사를 선택한 뒤 마지막까지 살아남아 " + warlockText("각성") + "하는 빌더입니다.</gray>"));
        lines.add(SemionText.mini("<gray>" + warlockText("원거리") + "는 누적 흡수로 생명력 흡수와 광역 범위를 키우는 장기 성장형입니다.</gray>"));
        lines.add(SemionText.mini("<gray>" + warlockText("근거리") + "는 라운드 흡수로 공격 속도와 근접 폭발력을 끌어올리는 최후 생존형입니다.</gray>"));
        lines.add(SemionText.mini("<gray>" + awakeningKills() + "킬에 도달하면 " + warlockText("각성") + "을 습득하고, 최후 생존·저체력 조건을 만족하면 각성합니다.</gray>"));
        lines.add(SemionText.mini("<gray>원거리는 회복·재생, 근거리는 피해·이동 속도를 얻으며 각성은 라운드 종료 시 해제됩니다.</gray>"));
        lines.add(SemionText.mini(warningText(warlockText("흑마법사") + " 타워는 한 라인에 하나만 운용할 수 있습니다.")));
        return List.copyOf(lines);
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

    @Override
    public void onMatchStarted(JobContext context) {
        WarlockAwakeningProgress.clear(context.player().uuid());
    }

    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        if (!WarlockAwakeningProgress.recordKill(context.player().uuid())) {
            return;
        }
        context.game().playerLane(context.player().uuid())
                .ifPresent(lane -> WarlockTower.onAwakeningUnlocked(lane, context.player().uuid()));
    }

    @Override
    public void onEliminated(JobContext context) {
        WarlockAwakeningProgress.clear(context.player().uuid());
    }

    private static int awakeningKills() {
        return TowerBalanceRuntime.abilityInt(WarlockTowers.CONFIG_ID, "awakeningKills", 1350);
    }
}
