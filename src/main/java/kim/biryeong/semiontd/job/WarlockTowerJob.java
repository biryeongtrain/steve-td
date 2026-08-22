package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warlockText;

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
        super(ID, Component.literal("흑마법사"), List.of());
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>희생으로 성장해 원거리·근거리 중 선택합니다.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>" + awakeningKills() + "킬 후 최후 생존·저체력에서 " + warlockText("각성") + "합니다.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>핵심 타워는 1기, 각성은 라운드 끝에 해제됩니다.</gray>")
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
        return TowerBalanceRuntime.abilityInt(WarlockTowers.CONFIG_ID, "awakeningKills", 1250);
    }
}
