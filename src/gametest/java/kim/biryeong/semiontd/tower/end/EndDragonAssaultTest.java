package kim.biryeong.semiontd.tower.end;

import java.util.Optional;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerCoreAugmentFixture;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

public final class EndDragonAssaultTest extends TowerCoreAugmentFixture implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void hatchChargesInPlaceBeforeTeleportingToTheRear(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower core = dragon(fixture);
            Vec3 hatch = fixture.entity(core).position();
            tick(core, fixture, 1);
            require(core.assaultPhase() == EndDragonAssault.Phase.CHARGING,
                    "Dragon evolution must immediately enter charging at the hatch location.");
            requireClose(0, fixture.entity(core).position().distanceTo(hatch),
                    "The dragon must charge where it hatched, before teleporting rearward.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void chargeWaitsSixtyTicksAndRushDamagesOnceWithIncomeOnlyStun(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower core = dragon(fixture);
            SemionTowerEntity source = fixture.entity(core);
            source.setNoAi(false);
            advance(core, fixture, EndDragonAssault.Phase.CHARGING);
            Vec3 middle = rear(fixture).lerp(front(fixture), .5);
            SemionMonsterEntity income = target(fixture, middle, true, 1);
            SemionMonsterEntity wave = target(fixture, middle.add(.1, 0, 0), false, 1);
            SemionMonsterEntity immune = target(fixture, middle.add(.2, 0, 0), true, 1);
            immune.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 1, 1000);
            SemionMonsterEntity otherLane = target(fixture, middle, true, 2);
            SemionMonsterEntity outside = target(fixture, rear(fixture).add(2, 0, 0), true, 1);
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .5, 1000);
            double expected = core.resolveBasicAttackOutgoingDamage(source, income, source.attackDamageAmount(income));
            tick(core, fixture, 59);
            require(core.assaultPhase() == EndDragonAssault.Phase.CHARGING, "The dragon must charge for the full three seconds.");
            requireClose(10000, income.runtimeMonster().health(), "Charging cannot deal rush damage.");
            tick(core, fixture, 1);
            require(core.assaultPhase() == EndDragonAssault.Phase.RUSHING, "The sixtieth charge tick arms the rush.");
            requireClose(0, source.getXRot(), "Charging faces horizontally along the lane.");
            advance(core, fixture, EndDragonAssault.Phase.EXITING);
            requireClose(expected, 10000 - income.runtimeMonster().health(), "One swept rush applies current physical damage at 100% once.");
            requireClose(expected, 10000 - wave.runtimeMonster().health(), "Wave enemies also receive the physical rush hit.");
            requireClose(200, income.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN), "Actually damaged income monsters receive exactly ten seconds of stun.");
            requireClose(0, fixture.lane.laneLayout().progressAt(income.position()), "A short lane clamps knockback at spawn and cannot hit the same income twice.");
            requireClose(middle.y, income.getY(), "Knockback preserves the victim height.");
            require(!wave.isStunned(), "The requested income stun does not stun natural wave monsters.");
            require(!immune.isStunned(), "Zero actual damage must not stun.");
            requireClose(10000, immune.runtimeMonster().health(), "The rush honors damage immunity.");
            requireClose(10000, otherLane.runtimeMonster().health(), "The rush respects the defending lane.");
            requireClose(10000, outside.runtimeMonster().health(), "The rush respects its swept width.");
            requireClose(expected * 2, core.roundPhysicalDamageDealt(), "Rush damage is attributed as physical exactly once per victim.");
            requireClose(0, core.roundMagicDamageDealt(), "The rush does not create hidden magic hits.");
            advance(core, fixture, EndDragonAssault.Phase.SPENT);
            require(!source.isNoAi(), "The original AI setting is restored after returning.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void breathAtLanePlusTenBurnsTenTimesUsingCurrentDamageAndThenStops(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower core = dragon(fixture);
            SemionTowerEntity source = fixture.entity(core);
            advance(core, fixture, EndDragonAssault.Phase.BREATHING);
            requireClose(Math.floor(fixture.lane.laneLayout().spawn().y) + 10, source.getY(), "The breath begins exactly ten blocks above the actual lane floor.");
            requireClose(0, source.position().distanceTo(geometry(fixture).airborneRear(Math.floor(fixture.lane.laneLayout().spawn().y), 10)), "Breath begins five blocks behind the rear and ten above the floor.");
            SemionMonsterEntity victim = target(fixture, rear(fixture).add(geometry(fixture).direction().scale(.1)), false, 1);
            tick(core, fixture, 1);
            requireClose(10000, victim.runtimeMonster().health(), "Breath contact starts a burn without an extra immediate hit.");
            require(source.getXRot() > 0, "The fixed elevated dragon must aim diagonally down at the lane.");
            victim.setPos(front(fixture).add(20, 0, 0));
            tick(core, fixture, 19);
            requireClose(10000, victim.runtimeMonster().health(), "The first burn waits twenty ticks after contact.");
            double first = core.resolveBasicAttackOutgoingDamage(source, victim, source.attackDamageAmount(victim) * .25);
            tick(core, fixture, 1);
            requireClose(first, 10000 - victim.runtimeMonster().health(), "The first pulse is exactly 25% current damage.");
            source.applyTimedEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, 40, 1000);
            double later = core.resolveBasicAttackOutgoingDamage(source, victim, source.attackDamageAmount(victim) * .25);
            tick(core, fixture, 180);
            requireClose(first + 9 * later, 10000 - victim.runtimeMonster().health(), "Ten pulses use current damage, continue outside the beam and do not stack from repeated contact.");
            requireClose(first + 9 * later, core.roundMagicDamageDealt(), "All burn pulses are magic damage attributed to the dragon.");
            tick(core, fixture, 40);
            requireClose(first + 9 * later, 10000 - victim.runtimeMonster().health(), "The burn expires after exactly ten seconds.");
            require(core.assaultPhase() == EndDragonAssault.Phase.SPENT, "The dragon returns and consumes this round's use.");
            var bounds = fixture.lane.laneLayout().laneArea();
            double centerX = (bounds.min().getX() + bounds.max().getX() + 1.0) / 2;
            double centerZ = (bounds.min().getZ() + bounds.max().getZ() + 1.0) / 2;
            require(Math.abs(source.getX() - centerX) <= .5 && Math.abs(source.getZ() - centerZ) <= .5,
                    "The final position is a central buildable lane cell.");
            requireClose(Math.floor(fixture.lane.laneLayout().spawn().y) + 1, source.getY(),
                    "The return preserves the normal dragon height above the actual lane floor.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void oneUseSurvivesRepeatedWaveNotificationAndRefreshThenResetsNextRound(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower core = dragon(fixture);
            advance(core, fixture, EndDragonAssault.Phase.SPENT);
            core.onWaveStarted(fixture.lane, 5);
            core.refreshType(core.type(), fixture.lane);
            tick(core, fixture, 500);
            require(core.assaultPhase() == EndDragonAssault.Phase.SPENT, "There is no cooldown or automatic same-round repeat.");
            require(fixture.lane.towers().size() == 1, "The replaced augment never creates a twin.");
            EndTower replacement = new EndTower(core.type(), fixture.owner, TeamId.RED, 1, fixture.position(0));
            replacement.copyFrom(core, 0);
            require(replacement.assaultPhase() == EndDragonAssault.Phase.SPENT, "Runtime copying preserves the consumed use.");
            core.resetForRound(fixture.lane);
            require(core.assaultPhase() == EndDragonAssault.Phase.READY, "Round end clears the old flight and use.");
            core.onWaveStarted(fixture.lane, 6);
            advance(core, fixture, EndDragonAssault.Phase.CHARGING);
            require(core.controlsAssaultFlight(), "The next round has exactly one fresh flight.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void deathStopsFlightButAppliedBurnFinishesAndRoundResetClearsPendingBurn(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower core = dragon(fixture);
            advance(core, fixture, EndDragonAssault.Phase.BREATHING);
            SemionMonsterEntity victim = target(fixture, rear(fixture).add(geometry(fixture).direction().scale(.1)), true, 1);
            SemionTowerEntity source = fixture.entity(core);
            double pulse = core.resolveBasicAttackOutgoingDamage(source, victim, source.attackDamageAmount(victim) * .25);
            tick(core, fixture, 1);
            fixture.lane.killTower(core);
            require(!core.controlsAssaultFlight(), "Combat death cancels movement immediately.");
            tick(core, fixture, 200);
            requireClose(pulse * 10, 10000 - victim.runtimeMonster().health(), "An applied ten-second burn survives the source's combat death.");
            core.resetForRound(fixture.lane);
            core.onWaveStarted(fixture.lane, 6);
            advance(core, fixture, EndDragonAssault.Phase.BREATHING);
            victim.setPos(rear(fixture).add(geometry(fixture).direction().scale(.1)));
            double before = victim.runtimeMonster().health();
            tick(core, fixture, 1);
            core.resetForRound(fixture.lane);
            tick(core, fixture, 300);
            requireClose(before, victim.runtimeMonster().health(), "Round reset clears pending burn ticks.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void phantomAndMissingAugmentCannotStartAndSuppressedTriggersDoNotConsumeUse(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.ASSAULT)) {
            EndTower phantom = fixture.end(EndTowers.BASE_END_TOWER);
            phantom.onWaveStarted(fixture.lane, 5);
            tick(phantom, fixture, 100);
            require(phantom.state() == EndTowerState.PHANTOM && phantom.assaultPhase() == EndDragonAssault.Phase.READY,
                    "A phantom waits for dragon evolution and creates no copy.");
            fixture.lane.removeTower(phantom);
            EndTower core = dragon(fixture);
            AugmentCombat.runWithoutTriggers(() -> tick(core, fixture, 100));
            require(core.assaultPhase() == EndDragonAssault.Phase.READY, "Suppressed augment actions cannot consume the round's use.");
            advance(core, fixture, EndDragonAssault.Phase.CHARGING);
            core.resetForRound(fixture.lane);
            require(fixture.entity(core).isNoAi(), "Cancellation restores the fixture's prior AI state.");
        }
        try (Fixture fixture = new Fixture(context)) {
            EndTower core = dragon(fixture);
            tick(core, fixture, 200);
            require(core.assaultPhase() == EndDragonAssault.Phase.READY, "A dragon without the card cannot fly an assault.");
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane")
    public void knockbackTraversesTwentyBlocksOfBentPathAndStopsBeforeWalls(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context)) {
            Vec3 origin = fixture.lane.laneLayout().spawn();
            var layout = new kim.biryeong.semiontd.map.LaneRegionLayout(1, origin,
                    java.util.List.of(origin.add(11, 0, 0), origin.add(11, 0, 11)), origin.add(0, 0, 11),
                    fixture.lane.laneLayout().laneArea(), java.util.List.of());
            var lane = new kim.biryeong.semiontd.game.PlayerLane(TeamId.RED, 1, fixture.owner, context.getLevel(), layout);
            SemionMonsterEntity victim = target(fixture, layout.positionAt(.9).add(0, 1, 0), true, 1);
            double expectedProgress = .9 - 20 / layout.pathLength();
            EndDragonAssault.knockBack(lane, victim, 20);
            requireClose(expectedProgress, layout.progressAt(victim.position()), "Twenty blocks must follow the bent lane rather than cutting across its corner.");
            requireClose(expectedProgress, victim.runtimeMonster().laneProgress(), "Knockback also rewinds tracked lane progress.");
            requireClose(origin.y + 1, victim.getY(), "A bent path cannot change victim height.");
            victim.discard();

            var straight = new kim.biryeong.semiontd.map.LaneRegionLayout(1, origin,
                    java.util.List.of(origin.add(11, 0, 0)), origin.add(11, 0, 0),
                    fixture.lane.laneLayout().laneArea(), java.util.List.of());
            var straightLane = new kim.biryeong.semiontd.game.PlayerLane(TeamId.RED, 1, fixture.owner, context.getLevel(), straight);
            SemionMonsterEntity blocked = target(fixture, origin.add(10, 1, 0), true, 1);
            var wall = net.minecraft.core.BlockPos.containing(origin.add(5, 1, 0));
            var firstBlock = context.getLevel().getBlockState(wall);
            var secondBlock = context.getLevel().getBlockState(wall.above());
            try {
                context.getLevel().setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                context.getLevel().setBlockAndUpdate(wall.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                EndDragonAssault.knockBack(straightLane, blocked, 20);
                require(blocked.getX() >= wall.getX() + 1, "The swept victim body must stop before a solid wall.");
                require(blocked.getX() < origin.x + 10, "Clear space before the wall still permits partial knockback.");
                requireClose(origin.y + 1, blocked.getY(), "Collision cannot launch the victim upward.");
            } finally {
                context.getLevel().setBlockAndUpdate(wall, firstBlock);
                context.getLevel().setBlockAndUpdate(wall.above(), secondBlock);
            }
            context.succeed();
        }
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 230)
    public void rushStunExpiresAtExactlyTwoHundredServerTicks(GameTestHelper context) {
        Fixture fixture = new Fixture(context, EndAugments.ASSAULT);
        EndTower core = dragon(fixture);
        advance(core, fixture, EndDragonAssault.Phase.CHARGING);
        SemionMonsterEntity victim = target(fixture, rear(fixture).lerp(front(fixture), .5), true, 1);
        advance(core, fixture, EndDragonAssault.Phase.EXITING);
        requireClose(200, victim.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN), "The rush installs two hundred stun ticks.");
        context.startSequence()
                .thenIdle(199)
                .thenExecute(() -> check(context, fixture, () -> require(victim.isStunned(), "Stun remains before the final tick.")))
                .thenIdle(1)
                .thenExecute(() -> check(context, fixture, () -> {
                    require(!victim.isStunned(), "Stun expires at exactly ten seconds of server time.");
                    fixture.close();
                }))
                .thenSucceed();
    }

    private static void check(GameTestHelper context, Fixture fixture, Runnable assertion) {
        try {assertion.run();}
        catch (RuntimeException | Error failure) {
            fixture.close();
            context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
        }
    }

    private static EndTower dragon(Fixture fixture) {
        var bounds = fixture.lane.laneLayout().laneArea();
        int floorBlockY = (int) Math.floor(fixture.lane.laneLayout().spawn().y) - 1;
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                fixture.lane.arenaWorld().setBlockAndUpdate(new net.minecraft.core.BlockPos(x, floorBlockY, z),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        TowerType base = EndTowers.BASE_END_TOWER;
        TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                base.description(), base.visual(), base.upgradeOptions());
        EndTower core = fixture.end(giant);
        core.onWaveStarted(fixture.lane, 5);
        return core;
    }

    private static void advance(EndTower core, Fixture fixture, EndDragonAssault.Phase phase) {
        for (int i = 0; i < 1000 && core.assaultPhase() != phase; i++) {core.tick(fixture.lane);}
        require(core.assaultPhase() == phase, "Expected flight phase " + phase + ", got " + core.assaultPhase());
    }

    private static void tick(EndTower core, Fixture fixture, int ticks) {
        for (int i = 0; i < ticks; i++) {core.tick(fixture.lane);}
    }

    private static EndDragonAssaultGeometry geometry(Fixture fixture) {
        return EndDragonAssaultGeometry.from(fixture.lane.laneLayout(), Math.floor(fixture.lane.laneLayout().spawn().y));
    }

    private static Vec3 rear(Fixture fixture) {return geometry(fixture).rear();}

    private static Vec3 front(Fixture fixture) {return geometry(fixture).front();}

    private static SemionMonsterEntity target(Fixture fixture, Vec3 position, boolean income, int laneId) {
        SemionMonsterEntity target = fixture.target(position, 10000);
        fixture.lane.activeMonsters().remove(target.runtimeMonster());
        Monster monster = new Monster("assault_target", TeamId.RED, laneId, Optional.empty(),
                income ? Optional.of(TeamId.BLUE) : Optional.empty(), 10000, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
        monster.setOrigin(income ? MonsterOrigin.NORMAL_PAID : MonsterOrigin.NATURAL_WAVE);
        target.configureFrom(monster, fixture.lane.laneLayout());
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(position);
        monster.markMinecraftEntitySpawned(target.getId(), position.x, position.y, position.z);
        fixture.lane.activeMonsters().add(monster);
        return target;
    }
}
