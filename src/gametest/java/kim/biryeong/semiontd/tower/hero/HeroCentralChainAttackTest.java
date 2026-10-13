package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class HeroCentralChainAttackTest {
    @GameTest
    public void staffRetainsChainsAfterCentralDefenseMovement(GameTestHelper context) {
        verifyMovement(context, HeroWeapon.STAFF, new double[]{0.60, 0.35});
    }

    @GameTest
    public void longbowRetainsExtraShotsAfterCentralDefenseMovement(GameTestHelper context) {
        verifyMovement(context, HeroWeapon.LONGBOW, new double[]{0.65, 0.40});
    }

    private static void verifyMovement(GameTestHelper context, HeroWeapon weapon, double[] ratios) {
        UUID owner = UUID.randomUUID();
        BlockPos origin = context.absolutePos(new BlockPos(2, 32, 2));
        Vec3 spawn = Vec3.atCenterOf(origin);
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(spawn.add(10, 0, 0)), spawn.add(4, 0, 4),
                BlockBounds.of(origin.offset(-12, -1, -12), origin.offset(12, 5, 12)),
                List.of(GridPosition.from(origin.offset(0, 0, 1))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        List<SemionMonsterEntity> targets = new ArrayList<>();
        GridPosition position = GridPosition.from(origin);
        HeroPartyState state = HeroPartyStates.state(owner);
        state.addWeapon(weapon);
        state.equip(weapon);
        state.upgradeWeapon(weapon);
        HeroTower hero = new HeroTower(HeroPartyTowers.HERO, owner, TeamId.RED, 1, position, position);
        try {
            lane.addTower(hero);
            SemionTowerEntity source = (SemionTowerEntity) context.getLevel().getEntity(hero.entityId().orElseThrow());
            require(source != null, "Hero must be indexed before testing attacks");
            source.setNoAi(true);
            source.setNoGravity(true);
            double range = source.attackRange();
            int interval = source.attackIntervalTicks();
            targets.add(monster(lane, source.position().add(1, 0, 0)));
            targets.getFirst().runtimeMonster().syncLaneProgress(0.1);
            targets.add(monster(lane, source.position().add(range - 0.35, 0, 0)));
            targets.add(monster(lane, source.position().add(range - 0.05, 0, 0)));
            targets.add(monster(lane, source.position().add(0, 0, range - 0.01)));
            TowerAttackMonsterGoal goal = new TowerAttackMonsterGoal(source);
            verifyAttack(goal, source, targets, ratios, 1, "lane");
            Vec3 beforeMove = source.position();
            lane.moveTowersToFinalDefense();
            require(hero.deployedAtFinalDefense() && source.deployedAtFinalDefense(), "Movement must synchronize central defense");
            Vec3 displacement = source.position().subtract(beforeMove);
            for (SemionMonsterEntity target : targets) {
                target.setPos(target.position().add(displacement));
            }
            close(range, source.attackRange(), "Movement preserves weapon range");
            close(interval, source.attackIntervalTicks(), "Movement preserves cooldown duration");
            require(state.equippedWeapon() == weapon && state.weaponLevel(weapon) == 1, "Movement preserves weapon and upgrade");
            double primaryHealth = targets.getFirst().runtimeMonster().health();
            goal.tick();
            close(primaryHealth, targets.getFirst().runtimeMonster().health(), "Movement must not reset attack cooldown");
            verifyAttack(goal, source, targets, ratios, interval - 1, "central defense");
            Vec3 centralPosition = source.position();
            lane.moveTowersToFinalDefense();
            require(centralPosition.equals(source.position()), "Repeated central movement is idempotent");
            verifyAttack(goal, source, targets, ratios, interval, "repeated central movement");
            targets.get(2).setPos(source.position().add(range + 0.01, 0, 0));
            targets.get(3).setHealth(0);
            verifyAttack(goal, source, targets, new double[]{ratios[0], 0}, interval, "one eligible extra target");
            targets.get(1).setPos(source.position().add(range + 0.01, 0, 0));
            verifyAttack(goal, source, targets, new double[]{0, 0}, interval, "no eligible extra targets");
            context.succeed();
        } finally {
            for (SemionMonsterEntity target : targets) {
                target.discard();
            }
            lane.removeTower(hero);
            HeroPartyStates.clear(owner);
        }
    }

    private static void verifyAttack(TowerAttackMonsterGoal goal, SemionTowerEntity source,
                                     List<SemionMonsterEntity> targets, double[] ratios, int ticks, String phase) {
        double[] before = targets.stream().mapToDouble(target -> target.runtimeMonster().health()).toArray();
        double attempted = source.attackDamageAmount(targets.getFirst());
        double[] expected = new double[]{
                source.runtimeTower().resolveBasicAttackOutgoingDamage(source, targets.getFirst(), attempted),
                source.runtimeTower().resolveBasicAttackOutgoingDamage(source, targets.get(1), attempted * ratios[0]),
                source.runtimeTower().resolveBasicAttackOutgoingDamage(source, targets.get(2), attempted * ratios[1]),
                0
        };
        for (int tick = 0; tick < ticks; tick++) {
            goal.tick();
        }
        for (int index = 0; index < targets.size(); index++) {
            close(expected[index], before[index] - targets.get(index).runtimeMonster().health(),
                    phase + " target " + index + " damage");
        }
    }

    private static SemionMonsterEntity monster(PlayerLane lane, Vec3 position) {
        Monster monster = new Monster("hero_chain_" + UUID.randomUUID(), TeamId.RED, 1,
                Optional.empty(), Optional.empty(), 10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(lane.arenaWorld().addFreshEntity(entity), "Chain target must spawn");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        return entity;
    }

    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.01) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
