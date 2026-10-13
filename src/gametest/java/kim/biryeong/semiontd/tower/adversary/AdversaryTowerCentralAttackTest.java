package kim.biryeong.semiontd.tower.adversary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AdversaryTowerCentralAttackTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void breezeMagicChainsReachOtherLanesOnlyAfterFinalDefenseMovement(GameTestHelper context) {
        verify(context, FoxForm.BREEZE, 0.60, true);
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 120)
    public void shieldBearerSplashReachesOtherLanesAfterFinalDefenseMovement(GameTestHelper context) {
        verify(context, FoxForm.SHIELD_BEARER, 0.50, false);
    }

    private static void verify(GameTestHelper context, FoxForm form, double ratio, boolean magic) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        BlockPos origin = context.absolutePos(new BlockPos(10, 3, 10));
        Vec3 start = Vec3.atCenterOf(origin);
        LaneRegionLayout layout = new LaneRegionLayout(1, start, List.of(start.add(0, 0, 12)),
                start.add(0, 0, 16), BlockBounds.of(origin.offset(-8, -1, -8), origin.offset(16, 5, 18)),
                List.of(GridPosition.from(origin.offset(10, 0, 0))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        PlayerLane other = new PlayerLane(TeamId.RED, 2, UUID.randomUUID(), context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        AreaEffectLaneIndex.register(other);
        var fox = new AdversaryFoxTower(AdversaryTowers.typeFor(form), owner, TeamId.RED, 1, GridPosition.from(origin));
        try {
            lane.addTower(fox);
            var source = (SemionTowerEntity) context.getLevel().getEntity(fox.entityId().orElseThrow());
            source.setNoAi(true);
            source.setNoGravity(true);
            var primary = spawn(lane, source.position().add(1, 0, 0));
            var own = spawn(lane, source.position().add(1.5, 0, 0));
            var cross = spawn(other, source.position().add(2, 0, 0));
            primary.runtimeMonster().syncLaneProgress(0.95);
            var goal = new TowerAttackMonsterGoal(source);
            double damage = source.attackDamageAmount(primary);
            goal.tick();
            close(context, damage * ratio, 10000 - own.runtimeMonster().health(), "Own-lane secondary damage before movement");
            close(context, 10000, cross.runtimeMonster().health(), "Other lane is excluded before movement");
            double range = source.attackRange();
            int interval = source.attackIntervalTicks();
            Vec3 before = source.position();
            lane.moveTowersToFinalDefense();
            context.assertTrue(fox.deployedAtFinalDefense() && source.deployedAtFinalDefense(), "Final defense flags are synchronized");
            Vec3 shift = source.position().subtract(before);
            for (var target : List.of(primary, own, cross)) target.setPos(target.position().add(shift));
            close(context, range, source.attackRange(), "Form range survives movement");
            close(context, interval, source.attackIntervalTicks(), "Attack interval survives movement");
            context.assertTrue(fox.form() == form, "Evolution survives movement");
            double priorPrimary = primary.runtimeMonster().health();
            goal.tick();
            close(context, priorPrimary, primary.runtimeMonster().health(), "Movement preserves remaining attack cooldown");
            double priorMagic = fox.roundMagicDamageDealt();
            for (int tick = 1; tick < interval; tick++) goal.tick();
            close(context, damage * ratio, 10000 - cross.runtimeMonster().health(), "Central secondary reaches a monster owned by another lane");
            if (magic) close(context, 2 * damage * ratio, fox.roundMagicDamageDealt() - priorMagic, "Breeze secondaries remain magic damage");
            Vec3 central = source.position();
            lane.moveTowersToFinalDefense();
            context.assertTrue(source.position().equals(central), "Repeated movement is idempotent");
            cross.setPos(source.position().add(range + 5, 0, 0));
            double crossBefore = cross.runtimeMonster().health();
            for (int tick = 0; tick < interval; tick++) goal.tick();
            close(context, crossBefore, cross.runtimeMonster().health(), "Out-of-range central targets remain excluded");
            context.succeed();
        } finally {
            for (var targetLane : List.of(lane, other)) {
                for (var monster : List.copyOf(targetLane.activeMonsters())) {
                    var entity = context.getLevel().getEntity(monster.minecraftEntityId());
                    if (entity != null) entity.discard();
                }
                targetLane.activeMonsters().clear();
            }
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            AreaEffectLaneIndex.unregister(other);
            AdversaryProgressStates.clear(owner);
        }
    }

    private static SemionMonsterEntity spawn(PlayerLane lane, Vec3 position) {
        var monster = new Monster("central-fox-" + UUID.randomUUID(), lane.teamId(), lane.laneId(),
                Optional.empty(), Optional.empty(), 10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0L);
        var entity = lane.spawnMonsterAt(monster, position).orElseThrow();
        entity.setNoAi(true);
        entity.setNoGravity(true);
        return entity;
    }

    private static void close(GameTestHelper context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.01, message + ": expected " + expected + ", actual " + actual);
    }
}
