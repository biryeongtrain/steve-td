package kim.biryeong.semiontd.tower.villager;

import java.util.Comparator;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import net.minecraft.resources.ResourceLocation;

/** Survival augments use the original growth counter; inherited stacks never become permanent. */
public final class VillagerAugments {
    public static final String INHERITANCE = "job_villager_towers_s";
    public static final String LONGEVITY = "job_villager_towers_g1";
    public static final String GIANT = "job_villager_towers_p";
    private static final TowerDataKey<Integer> INHERITED = key("inherited", Integer.class);
    private static final TowerDataKey<Integer> ATTACKS = key("extra_attacks", Integer.class);
    private static final TowerDataKey<Boolean> INHERITANCE_USED = key("inheritance_used", Boolean.class);

    private VillagerAugments() {}

    private static <T> TowerDataKey<T> key(String name, Class<T> type) {
        return TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("semiontd", "villager_augment/" + name), type);
    }

    public static boolean grows(Tower tower) {
        return tower instanceof VillagerThornTower || tower instanceof VillagerSplashTower;
    }

    public static int permanentStacks(Tower tower) {
        if (tower instanceof VillagerThornTower thorn) {return thorn.survivalStacks();}
        if (tower instanceof VillagerSplashTower splash) {return splash.survivalStacks();}
        return 0;
    }

    static int totalStacks(Tower tower) {
        return permanentStacks(tower) + tower.getDataOrDefault(INHERITED, 0);
    }

    public static void onSelected(PlayerLane lane, AugmentConfig config) {
        if (lane == null) {return;}
        int count = (int) config.parameter(LONGEVITY, "initialStacks", 3);
        for (Tower tower : lane.towers()) {
            if (tower instanceof VillagerThornTower thorn) {thorn.addSurvivalStacks(count);}
            if (tower instanceof VillagerSplashTower splash) {splash.addSurvivalStacks(count);}
            if (grows(tower)) {tower.onStateChanged(lane);}
        }
    }

    static void waveStarted(Tower tower) {
        tower.removeData(INHERITANCE_USED);
        tower.removeData(INHERITED);
        tower.setData(ATTACKS, tower.augmentSnapshot().has(LONGEVITY)
                ? Math.min(permanentStacks(tower), (int) tower.augmentSnapshot().parameter(LONGEVITY, "maxExtraAttacks", 5)) : 0);
    }

    static void roundEnded(Tower tower) {
        tower.removeData(INHERITED);
        tower.removeData(ATTACKS);
    }

    static void onDeath(Tower dead, PlayerLane lane) {
        if (lane == null || !dead.augmentSnapshot().has(INHERITANCE)
                || dead.getDataOrDefault(INHERITANCE_USED, false)) {return;}
        lane.towers().stream().filter(VillagerAugments::grows).forEach(t -> t.setData(INHERITANCE_USED, true));
        int amount = (int) Math.floor(permanentStacks(dead)
                * dead.augmentSnapshot().parameter(INHERITANCE, "inheritRatio", .5));
        lane.towers().stream().filter(t -> t != dead && t.health() > 0 && sameFamily(dead, t))
                .min(Comparator.<Tower>comparingDouble(t -> distanceSquared(dead, t)).thenComparing(Tower::logicalId))
                .ifPresent(target -> {
                    int cap = TowerBalanceRuntime.abilityInt(target.type().id(), "maxSurvivalStacks")
                            * (int) dead.augmentSnapshot().parameter(INHERITANCE, "capMultiplier", 3);
                    double healthRatio = target.health() / Math.max(1, target.currentMaxHealth());
                    target.setData(INHERITED, Math.min(Math.max(0, cap - permanentStacks(target)), amount));
                    target.syncHealth(target.currentMaxHealth() * healthRatio);
                    target.onStateChanged(lane);
                });
    }

    static boolean sameFamily(Tower first, Tower second) {
        return first instanceof VillagerThornTower && second instanceof VillagerThornTower
                || first instanceof VillagerSplashTower && second instanceof VillagerSplashTower;
    }

    private static double distanceSquared(Tower first, Tower second) {
        return Math.pow(first.position().x() - second.position().x(), 2)
                + Math.pow(first.position().y() - second.position().y(), 2)
                + Math.pow(first.position().z() - second.position().z(), 2);
    }

    static void attackResolved(Tower tower, SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        if (!AugmentCombat.allowsTriggers() || damage <= 0 || target == null || !target.isAlive()
                || tower.getDataOrDefault(ATTACKS, 0) <= 0) {return;}
        tower.setData(ATTACKS, tower.getDataOrDefault(ATTACKS, 0) - 1);
        AugmentCombat.additionalAttack(source, target, 1);
    }

    static String detail(Tower tower) {
        return "임시 생존 스택 " + tower.getDataOrDefault(INHERITED, 0)
                + " · 장수 만세 추가 공격 " + tower.getDataOrDefault(ATTACKS, 0);
    }

    public static boolean giantTarget(Tower tower) {
        return tower != null && VillagerTowers.matches(tower.type(), VillagerTowers.T3_GOLEM_TOWER);
    }

    static boolean isGiant(Tower tower) {
        UUID target = tower.augmentSnapshot().choice(GIANT).primaryTargetId();
        return tower.augmentSnapshot().has(GIANT) && tower.logicalId().equals(target) && giantTarget(tower);
    }
}
