package kim.biryeong.semiontd.entity.monster.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class MonsterDefenseTargetScanTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void reachLimitedScanMatchesLaneWideScanIncludingTiesAndDominatedMonsters(GameTestHelper context) {
        BlockPos origin = context.absolutePos(new BlockPos(12, 3, 12));
        Vec3 start = Vec3.atBottomCenterOf(origin);
        var layout = new LaneRegionLayout(1, start, List.of(start.add(0, 0, 12)), start.add(0, 0, 18),
                BlockBounds.of(origin.offset(-10, -1, -10), origin.offset(18, 5, 18)),
                List.of(GridPosition.from(origin.offset(10, 0, 0))));
        var lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        int[][] towers = {{-3, -3, 10}, {3, -3, 10}, {-3, 3, 10}, {3, 3, 10}, {0, 6, 10}, {0, -6, 10},
                {6, 0, 40}, {-6, 0, 40}, {9, 9, 80}, {-9, 9, 5}, {2, 0, 10}, {-2, 0, 10}};
        for (int[] tower : towers) {
            lane.addTower(tower(lane, origin.offset(tower[0], 0, tower[1]), tower[2]));
        }
        SemionMonsterEntity monster = lane.spawnMonsterAt(monster(), start).orElseThrow();
        SemionMonsterEntity other = lane.spawnMonsterAt(monster(), start.add(4, 0, 4)).orElseThrow();
        monster.setNoAi(true);
        monster.setNoGravity(true);
        other.setNoAi(true);
        other.setNoGravity(true);
        var acquire = new AcquireLaneDefenseTargetGoal(monster);
        try {
            int found = compareGrid(context, monster, acquire, start);
            context.assertTrue(found > 100, "The grid must exercise real target choices: " + found);
            other.setDominatedFor(1);
            context.assertTrue(SemionMonsterEntity.anyDominated(), "A dominated monster must be tracked");
            compareGrid(context, monster, acquire, start);
            monster.setPos(other.position().add(1, 0, 0));
            monster.setTarget(null);
            acquire.start();
            context.assertTrue(monster.getTarget() == reference(monster), "A dominated monster stays a candidate");
            other.setDominatedFor(-1);
            compareGrid(context, monster, acquire, start);
            other.setDominatedFor(1);
            other.discard();
            lane.killTowers(lane.towers());
            monster.setPos(start);
            monster.setTarget(null);
            context.assertTrue(reference(monster) == null && !acquire.canUse(), "No living defender means no target");
            context.succeed();
        } finally {
            monster.discard();
            other.discard();
            lane.clearTowers();
        }
    }

    private static int compareGrid(GameTestHelper context, SemionMonsterEntity monster,
            AcquireLaneDefenseTargetGoal acquire, Vec3 start) {
        int found = 0;
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                monster.setPos(start.add(x * 0.5, 0, z * 0.5));
                monster.setTarget(null);
                LivingEntity expected = reference(monster);
                context.assertTrue(acquire.canUse() == (expected != null),
                        "Availability differs at " + x + "," + z);
                acquire.start();
                context.assertTrue(monster.getTarget() == expected, "Target differs at " + x + "," + z);
                if (expected != null) {
                    found++;
                }
            }
        }
        return found;
    }

    private static LivingEntity reference(SemionMonsterEntity monster) {
        AABB searchBox = monster.defenseSearchBox();
        List<LivingEntity> candidates = new ArrayList<>();
        for (var entity : monster.level().getEntities(monster, searchBox, entity -> entity instanceof LivingEntity living
                && entity instanceof LaneDefenseEntity defense
                && entity.isAlive()
                && defense.defendsLane(monster.runtimeMonster().targetLaneId())
                && defense.drawsAggro()
                && monster.canTargetDefense(living))) {
            if (!(entity instanceof SemionTowerEntity tower) || tower.runtimeTower() == null
                    || tower.runtimeTower().targetableByMonsters()) {
                candidates.add((LivingEntity) entity);
            }
        }
        Comparator<LivingEntity> priority = Comparator
                .comparingInt((LivingEntity entity) -> ((LaneDefenseEntity) entity).aggroPriority()).reversed()
                .thenComparingDouble(monster::distanceToSqr);
        LivingEntity baseline = candidates.stream().min(priority).orElse(null);
        if (baseline == null) {
            return null;
        }
        double distance = monster.distanceToSqr(baseline);
        return candidates.stream().filter(entity -> entity instanceof SemionTowerEntity tower
                        && tower.runtimeTower() != null
                        && (AugmentTowers.is(tower.runtimeTower().type(), AugmentTowers.FOLDING_BARRICADE)
                        || AugmentCombat.prefersEqualDistance(tower.runtimeTower()))
                        && Math.abs(monster.distanceToSqr(entity) - distance) < 1.0e-9)
                .min(priority).orElse(baseline);
    }

    private static Monster monster() {
        return new Monster("defense_scan", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10000, 0, 20, AttackKind.MELEE, "minecraft:zombie", 0L);
    }

    private static ProductionTower tower(PlayerLane lane, BlockPos position, int priority) {
        return new ProductionTower(TowerType.builder("defense_scan_" + priority, "Defense scan")
                .maxHealth(1000).damage(0).range(0).aggroPriority(priority).build(), lane.ownerPlayer(),
                lane.teamId(), lane.laneId(), GridPosition.from(position));
    }
}
