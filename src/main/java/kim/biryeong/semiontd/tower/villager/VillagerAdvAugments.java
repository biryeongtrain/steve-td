package kim.biryeong.semiontd.tower.villager;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class VillagerAdvAugments {
    public static final String MENTOR = "job_villager_adv_towers_s";
    public static final String EARLY = "job_villager_adv_towers_g1";
    public static final String CONTEST = "job_villager_adv_towers_g2";
    public static final String GRADUATE = "job_villager_adv_towers_p";
    private static final TowerDataKey<Boolean> TOP_GRADUATE = TowerDataKey.of(id("graduate"), Boolean.class);
    private static final TowerDataKey<Long> NEXT_CONTEST = TowerDataKey.of(id("next_contest"), Long.class);

    private VillagerAdvAugments() {}

    public static long upgradeCost(Tower tower, long baseCost) {
        return eligible(tower) && tower.augmentSnapshot().has(EARLY)
                ? (long) Math.ceil(baseCost * (1.0 + tower.augmentSnapshot().parameter(EARLY, "upgradeCostBonus", .40)))
                : baseCost;
    }

    public static void startWave(PlayerLane lane) {
        long now = lane.arenaWorld() == null ? 0 : lane.arenaWorld().getGameTime();
        for (Tower tower : lane.towers()) {
            if (!eligible(tower)) continue;
            tower.removeData(TOP_GRADUATE);
            tower.setData(NEXT_CONTEST, now + interval(tower));
        }
    }

    public static void resetWave(PlayerLane lane) {
        for (Tower tower : lane.towers()) {
            tower.removeData(TOP_GRADUATE);
            tower.removeData(NEXT_CONTEST);
        }
    }

    public static void captureGraduate(PlayerLane lane) {
        List<Tower> candidates = lane.towers().stream().filter(VillagerAdvAugments::eligible)
                .filter(tower -> tower.health() > 0 && !role(tower).equals("allay")
                        && tower.type().damage() > 0 && tower.augmentSnapshot().has(GRADUATE))
                .sorted(byExperience()).toList();
        for (Tower tower : candidates) {
            tower.setData(TOP_GRADUATE, tower == candidates.getFirst());
            tower.onStateChanged(lane);
        }
    }

    public static double attackRange(Tower tower, double range) {
        if (!isGraduate(tower) || tower.attachedLane() == null) return range;
        PlayerLane lane = tower.attachedLane();
        var box = (tower.deployedAtFinalDefense() ? lane.finalDefensePathLane() : lane).laneLayout().defenseSearchBox(
                new Vec3(tower.position().x(), tower.position().y(), tower.position().z()), 0, 0);
        return Math.max(range, Math.sqrt(box.getXsize() * box.getXsize()
                + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) + 1.0);
    }

    public static boolean isGraduate(Tower tower) {
        return eligible(tower) && tower.augmentSnapshot().has(GRADUATE) && tower.getDataOrDefault(TOP_GRADUATE, false);
    }

    public static void tick(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || !lane.augmentSnapshot().has(CONTEST)) return;
        List<Tower> candidates = lane.towers().stream().filter(VillagerAdvAugments::eligible)
                .filter(tower -> tower.health() > 0).sorted(byExperience()).toList();
        Map<String, Tower> representatives = new LinkedHashMap<>();
        for (Tower tower : candidates) representatives.putIfAbsent(role(tower), tower);
        long now = lane.arenaWorld().getGameTime();
        for (Tower tower : candidates) {
            if (now < tower.getDataOrDefault(NEXT_CONTEST, Long.MAX_VALUE)) continue;
            tower.setData(NEXT_CONTEST, now + interval(tower));
            if (representatives.size() < (int) tower.augmentSnapshot().parameter(CONTEST, "requiredKinds", 3)
                    || representatives.get(role(tower)) != tower) continue;
            if (role(tower).equals("allay") || tower.type().damage() <= 0) {
                AugmentCombat.additionalAction(tower, lane);
            } else {
                SemionTowerEntity source = entity(tower, lane);
                if (source == null) continue;
                var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, "augment_contest"), source,
                        source.position(), source.attackRange(), Set.of(), source::isValidAttackTarget,
                        AreaVfxSpec.none()).nearestTargets(1);
                SemionTdApi.areaEffects().applyToMonsters(request, target -> {
                    AugmentCombat.additionalAttack(source, target, 1.0);
                    return AreaEffectOutcome.APPLIED;
                });
            }
        }
    }

    public static void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity primary, Tower.DamageResult result) {
        Tower tower = source == null ? null : source.runtimeTower();
        if (!AugmentCombat.allowsTriggers() || !isGraduate(tower) || primary == null || result.dealtDamage() <= 0) return;
        var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, "augment_graduate"), source,
                source.position(), source.attackRange(), Set.of(primary.getUUID()), source::isValidAttackTarget,
                AreaVfxSpec.none()).nearestTargets((int) tower.augmentSnapshot().parameter(GRADUATE, "extraTargets", 2));
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            AugmentCombat.additionalAttack(source, target, tower.augmentSnapshot().parameter(GRADUATE, "damageRatio", .60));
            return AreaEffectOutcome.APPLIED;
        });
    }

    public static List<String> runtimeDetails(Tower tower) {
        if (!eligible(tower)) return List.of();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (isGraduate(tower)) lines.add("수석 졸업: 레인 전체 사거리 / 추가 표적 "
                + (int) tower.augmentSnapshot().parameter(GRADUATE, "extraTargets", 2) + "기");
        if (tower.augmentSnapshot().has(CONTEST) && tower.attachedLane() != null
                && tower.attachedLane().towers().stream().filter(VillagerAdvAugments::eligible)
                .filter(other -> other.health() > 0 && role(other).equals(role(tower))).sorted(byExperience())
                .findFirst().orElse(null) == tower) lines.add("역할 대항전: 계열 대표");
        return lines;
    }

    private static Comparator<Tower> byExperience() {
        return Comparator.comparingDouble(VillagerAdvStates::experience).reversed().thenComparing(Tower::logicalId);
    }

    static String role(Tower tower) {
        String id = tower.type().id();
        if (id.contains("golem")) return "golem";
        if (id.contains("splash")) return "ranged";
        if (id.contains("cat")) return "cat";
        return "allay";
    }

    private static int interval(Tower tower) {
        return Math.max(1, (int) tower.augmentSnapshot().parameter(CONTEST, "intervalTicks", 120));
    }

    private static boolean eligible(Tower tower) {
        return tower != null && VillagerTowers.isAdvVillagerTower(tower.type());
    }

    private static SemionTowerEntity entity(Tower tower, PlayerLane lane) {
        return tower instanceof EntityBackedTower entityTower && entityTower.entityId().isPresent()
                && lane.arenaWorld().getEntity(entityTower.entityId().getAsInt()) instanceof SemionTowerEntity entity
                ? entity : null;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("semiontd", "villager_adv_augment/" + name);
    }
}
