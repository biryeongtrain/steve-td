package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;

public final class HyperCarryBuilders {
    private HyperCarryBuilders() { }

    public static boolean includes(TowerType type) {
        return type != null && JobRegistry.all().stream()
                .anyMatch(job -> job.isHyperCarry() && job.includesTowerInCatalog(type));
    }

    public static boolean isCore(TowerType type) {
        return includes(type) && (EndTowers.isBaseEndTower(type)
                || WarlockTowers.isWarlockCore(type) || DemonLordTowers.isDemonLordTower(type));
    }
}
