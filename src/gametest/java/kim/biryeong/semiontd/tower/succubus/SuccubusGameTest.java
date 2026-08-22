package kim.biryeong.semiontd.tower.succubus;

import com.mojang.datafixers.util.Pair;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class SuccubusGameTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("succubus-gametest".getBytes(StandardCharsets.UTF_8));

    @GameTest
    public void thirdSleepExecutesAndWakeImmunityBlocksImmediateRestacking(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        SuccubusAbsorption.clear(OWNER);
        PlayerLane lane = testLane(context);
        SuccubusTower succubus = tower(SuccubusTowers.SUCCUBUS, position(context, 3, 2, 4));
        lane.addTower(succubus);
        SemionMonsterEntity target = spawnMonster(context, lane, "dream-target", position(context, 4, 2, 4));
        try {
            for (int sleep = 1; sleep <= 2; sleep++) {
                require(SuccubusDreams.add(target, lane, succubus, 10), "Ten stacks must put the target to sleep.");
                require(SuccubusDreams.isAsleep(target), "The target must enter dreamland.");
                for (int tick = 0; tick < SuccubusBalance.SLEEP_DURATION_TICKS; tick++) SuccubusDreams.tick(lane);
                require(!SuccubusDreams.isAsleep(target), "Five seconds must wake the target.");
                require(!SuccubusDreams.add(target, lane, succubus, 1), "Awakened immunity must block dream stacks.");
                for (int tick = 0; tick < SuccubusBalance.AWAKENED_IMMUNITY_TICKS; tick++) SuccubusDreams.tick(lane);
            }

            SuccubusDreams.add(target, lane, succubus, 10);
            require(!target.isAlive() && SuccubusDreams.sleepCount(target) == 0,
                    "The Succubus must execute and clear the third-sleep target.");
            require(SuccubusAbsorption.kills(OWNER) == 1, "Execution must absorb exactly once.");
            requireClose(0.30, SuccubusAbsorption.attack(OWNER), "Execution attack absorption");
            requireClose(10.0, SuccubusAbsorption.health(OWNER), "Execution health absorption");
            requireClose(170.0, succubus.currentMaxHealth(), "Absorbed maximum health");
            requireClose(170.0, succubus.health(), "Absorbed health must heal immediately");
            context.succeed();
        } finally {
            SuccubusDreams.clearLane(lane);
            SuccubusAbsorption.clear(OWNER);
        }
    }

    @GameTest
    public void onlyRecordedSuccubusBasicKillsAreAbsorbed(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        SuccubusAbsorption.clear(OWNER);
        PlayerLane lane = testLane(context);
        SuccubusTower succubus = tower(SuccubusTowers.SUCCUBUS, position(context, 3, 2, 4));
        lane.addTower(succubus);
        SemionTowerEntity source = (SemionTowerEntity) context.getLevel().getEntity(succubus.entityId().orElseThrow());
        try {
            SemionMonsterEntity unrecorded = spawnMonster(context, lane, "unrecorded", position(context, 5, 2, 4));
            var unrecordedResult = succubus.damageTargetResult(source, unrecorded, 2_000.0);
            require(unrecordedResult.killed(), "Setup damage must kill.");
            require(SuccubusAbsorption.kills(OWNER) == 0, "Unrecorded damage must not absorb.");

            SemionMonsterEntity direct = spawnMonster(context, lane, "direct", position(context, 6, 2, 4));
            var result = succubus.damageTargetResult(source, direct, 2_000.0);
            source.recordAttack(direct, 2_000.0, result.outgoingDamage(), result.dealtDamage(), result.killed());
            require(SuccubusAbsorption.kills(OWNER) == 1, "Recorded basic kill must absorb once.");
            requireClose(0.30, SuccubusAbsorption.attack(OWNER), "Basic kill attack absorption");
            requireClose(10.0, SuccubusAbsorption.health(OWNER), "Basic kill health absorption");
            succubus.resetForRound(lane);
            requireClose(170.0, succubus.currentMaxHealth(), "Absorption must persist across rounds");
            succubus.syncHealth(0.0);
            succubus.onRemoved(lane);
            SuccubusTower replacement = tower(SuccubusTowers.SUCCUBUS, position(context, 3, 2, 6));
            lane.addTower(replacement);
            requireClose(170.0, replacement.currentMaxHealth(), "Reinstalled Succubus must restore absorption");
            requireClose(170.0, replacement.health(), "Reinstalled Succubus must restore enhanced health");
            context.succeed();
        } finally {
            SuccubusDreams.clearLane(lane);
            SuccubusAbsorption.clear(OWNER);
        }
    }

    @GameTest
    public void sleepwalkerReductionUsesDreamingMonsterSourceOnly(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        SuccubusTower source = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 2, 2, 4));
        lane.addTower(source);
        SemionMonsterEntity attacker = spawnMonster(context, lane, "dream-attacker", position(context, 3, 2, 4));
        SuccubusDreams.add(attacker, lane, source, 1);
        try {
            assertReduction(context, lane, attacker, SuccubusTowers.SLEEPWALKER_T1, 0.10, 4);
            assertReduction(context, lane, attacker, SuccubusTowers.SLEEPWALKER_T2, 0.15, 5);
            assertReduction(context, lane, attacker, SuccubusTowers.SLEEPWALKER_T3, 0.20, 6);
            context.succeed();
        } finally {
            SuccubusDreams.clearLane(lane);
        }
    }

    @GameTest
    public void wakeSpreadsDreamBeforeMagicDamageAndCanChainWake(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        SuccubusTower source = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 1, 2, 4));
        SuccubusTower ally = tower(SuccubusTowers.SLEEPWALKER_T1, position(context, 4, 2, 6));
        lane.addTower(source);
        lane.addTower(ally);
        SemionMonsterEntity origin = spawnMonster(context, lane, "wake-origin", position(context, 4, 2, 4));
        SemionMonsterEntity nearby = spawnMonster(context, lane, "wake-nearby", position(context, 6, 2, 4));
        SemionMonsterEntity outside = spawnMonster(context, lane, "wake-outside", position(context, 8, 2, 4));
        try {
            SuccubusDreams.add(nearby, lane, source, 8);
            SuccubusDreams.add(origin, lane, source, 10);
            SuccubusDreams.onMonsterDamaged(origin, source, 400.0);

            requireClose(920.0, origin.getHealth(), "Origin wake damage");
            requireClose(920.0, nearby.getHealth(), "Nearby wake area damage");
            requireClose(1_000.0, outside.getHealth(), "Outside target exclusion");
            require(SuccubusDreams.isAsleep(nearby), "Spread stacks must sleep the nearby target before damage.");
            require(SuccubusDreams.stacks(ally) == 2, "Nearby allied towers must receive stacks.");
            requireClose(ally.currentMaxHealth(), ally.health(), "Allied towers must not receive wake area damage");

            SuccubusDreams.onMonsterDamaged(nearby, source, 320.0);
            require(!SuccubusDreams.isAsleep(nearby), "Area damage must count toward a chained wake.");
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal(throwable.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void wakeAreaKillDoesNotTriggerSuccubusAbsorption(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        SuccubusAbsorption.clear(OWNER);
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        SuccubusTower succubus = tower(SuccubusTowers.SUCCUBUS, position(context, 1, 2, 4));
        lane.addTower(succubus);
        SemionMonsterEntity origin = spawnMonster(context, lane, "absorb-origin", position(context, 4, 2, 4));
        SemionMonsterEntity victim = spawnMonster(context, lane, "absorb-victim", position(context, 6, 2, 4), 70.0);
        try {
            SuccubusDreams.add(origin, lane, succubus, 10);
            SuccubusDreams.onMonsterDamaged(origin, succubus, 400.0);
            require(!victim.isAlive(), "Wake area damage must kill the low-health target.");
            require(SuccubusAbsorption.kills(OWNER) == 0, "Wake area kills must not be absorbed.");
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal(throwable.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            SuccubusAbsorption.clear(OWNER);
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void wakeAreaDamageDoesNotRecursivelyPropagate(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        SuccubusTower source = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 1, 2, 4));
        lane.addTower(source);
        SemionMonsterEntity origin = spawnMonster(context, lane, "bounded-wake-origin", position(context, 2, 2, 4));
        SemionMonsterEntity chained = spawnMonster(context, lane, "bounded-wake-chained", position(context, 4, 2, 4));
        SemionMonsterEntity outsideInitialArea = spawnMonster(context, lane, "bounded-wake-outside", position(context, 6, 2, 4));
        try {
            SuccubusDreams.add(origin, lane, source, 10);
            SuccubusDreams.add(chained, lane, source, 10);
            SuccubusDreams.add(outsideInitialArea, lane, source, 10);
            SuccubusDreams.onMonsterDamaged(chained, source, 390.0);

            SuccubusDreams.onMonsterDamaged(origin, source, 400.0);

            require(!SuccubusDreams.isAsleep(chained), "Initial wake area damage should still awaken a threshold target.");
            requireClose(1_000.0, outsideInitialArea.getHealth(), "Nested wake must not emit another area-damage scan.");
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal(throwable.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void lullabyUsesSeparateAllyAndEnemyTargetCaps(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        SuccubusTower lullaby = tower(SuccubusTowers.LULLABY_T3, position(context, 5, 2, 7));
        lane.addTower(lullaby);
        for (int index = 0; index < 5; index++) {
            lane.addTower(tower(SuccubusTowers.DREAM_DUST_T1,
                    position(context, 3 + index % 3, 2, 5 + index / 3)));
        }
        for (int index = 0; index < 8; index++) {
            spawnMonster(context, lane, "lullaby-target-" + index,
                    position(context, 3 + index % 4, 2, 8 + index / 4));
        }
        try {
            require(lullaby.execute(lane), "Lullaby pulse must execute.");
            long dreamedTowers = lane.towers().stream().filter(tower -> SuccubusDreams.stacks(tower) > 0).count();
            long dreamedMonsters = lane.activeMonsters().stream()
                    .map(monster -> context.getLevel().getEntity(monster.minecraftEntityId()))
                    .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                    .filter(monster -> SuccubusDreams.stacks(monster) > 0).count();
            require(dreamedTowers == 4, "T3 Lullaby must target four allied towers.");
            require(dreamedMonsters == 7, "T3 Lullaby must target seven enemies.");
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal(throwable.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void succubusTowerCannotReceiveDreamStacksOrConsumeLullabyTargets(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        AreaEffectLaneIndex.register(lane);
        SuccubusTower succubus = tower(SuccubusTowers.SUCCUBUS, position(context, 3, 2, 4));
        SuccubusTower lullaby = tower(SuccubusTowers.LULLABY_T1, position(context, 4, 2, 4));
        lane.addTower(succubus);
        lane.addTower(lullaby);
        lane.addTower(tower(SuccubusTowers.DREAM_DUST_T1, position(context, 5, 2, 4)));
        lane.addTower(tower(SuccubusTowers.DREAM_DUST_T1, position(context, 6, 2, 4)));
        try {
            require(!SuccubusDreams.add(succubus, lane, lullaby, 10),
                    "The unique Succubus tower must reject direct dream stacks.");
            require(lullaby.execute(lane), "Lullaby pulse must execute.");
            require(SuccubusDreams.stacks(succubus) == 0 && !SuccubusDreams.isAsleep(succubus),
                    "The unique Succubus tower must remain outside dream state.");
            long dreamedTowers = lane.towers().stream().filter(tower -> SuccubusDreams.stacks(tower) > 0).count();
            require(dreamedTowers == 2, "Excluded Succubus must not consume a Lullaby ally target slot.");
            context.succeed();
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void dreamDamageBonusAppliesOnceToBasicAndMagicAbilityDamage(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        SuccubusTower source = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 2, 2, 4));
        SuccubusTower recipient = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 4, 2, 4));
        lane.addTower(source);
        lane.addTower(recipient);
        SemionTowerEntity recipientEntity = (SemionTowerEntity) context.getLevel()
                .getEntity(recipient.entityId().orElseThrow());
        SemionMonsterEntity basicTarget = spawnMonster(context, lane, "dream-basic", position(context, 5, 2, 4));
        SemionMonsterEntity magicTarget = spawnMonster(context, lane, "dream-magic", position(context, 6, 2, 4));
        SemionMonsterEntity resolvedTarget = spawnMonster(context, lane, "dream-resolved", position(context, 7, 2, 4));
        try {
            require(SuccubusDreams.add(recipient, lane, source, 1), "Dream stack must apply to the recipient.");
            double expectedBasic = recipient.type().damage() * 1.07;
            double basicDamage = recipientEntity.attackDamageAmount(basicTarget);
            requireClose(expectedBasic, basicDamage, "Dream basic damage preview");
            requireClose(expectedBasic, recipientEntity.damageTargetResult(basicTarget, basicDamage).outgoingDamage(),
                    "Dream basic damage must not double apply");
            requireClose(107.0,
                    recipient.damageTargetResult(recipientEntity, magicTarget, 100.0, DamageType.MAGIC).outgoingDamage(),
                    "Dream magic ability damage");
            requireClose(100.0,
                    recipient.damageResolvedTargetResult(recipientEntity, resolvedTarget, 100.0, DamageType.MAGIC)
                            .outgoingDamage(),
                    "Resolved damage must remain excluded");
            context.succeed();
        } finally {
            SuccubusDreams.clearLane(lane);
        }
    }

    @GameTest
    public void sleepingTargetsEmitHeadSmokeUntilStateCleanup(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        SuccubusTower source = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 2, 2, 4));
        SuccubusTower sleepingTower = tower(SuccubusTowers.SLEEPWALKER_T1, position(context, 4, 2, 4));
        lane.addTower(source);
        lane.addTower(sleepingTower);
        SemionMonsterEntity sleepingMonster = spawnMonster(context, lane, "smoke-target", position(context, 6, 2, 4));
        ArrayList<Vec3> smokeCenters = new ArrayList<>();
        SuccubusVfx.setSleepSmokeTestObserver(smokeCenters::add);
        try {
            SuccubusDreams.add(sleepingTower, lane, source, 10);
            SuccubusDreams.add(sleepingMonster, lane, source, 10);
            for (int tick = 0; tick < 10; tick++) SuccubusDreams.tick(lane);
            require(smokeCenters.size() == 2, "Each sleeping target must emit one smoke cycle per ten ticks.");
            require(smokeCenters.stream().anyMatch(center -> center.y > sleepingTower.position().y() + 1.0),
                    "Tower smoke must be placed above its bounding box.");
            require(smokeCenters.stream().anyMatch(center -> center.y > sleepingMonster.getY() + sleepingMonster.getBbHeight()),
                    "Monster smoke must be placed above its bounding box.");
            for (int tick = 10; tick < 40; tick++) SuccubusDreams.tick(lane);
            require(!SuccubusDreams.isAsleep(sleepingTower), "Allied tower sleep must end after two seconds.");
            require(SuccubusDreams.isAsleep(sleepingMonster), "Enemy sleep must still last five seconds.");
            require(smokeCenters.size() == 7, "Tower smoke must stop at its earlier wake time.");
            SuccubusDreams.clearLane(lane);
            for (int tick = 0; tick < 10; tick++) SuccubusDreams.tick(lane);
            require(smokeCenters.size() == 7, "Cleared sleep state must stop smoke immediately.");
            context.succeed();
        } finally {
            SuccubusVfx.setSleepSmokeTestObserver(null);
            SuccubusDreams.clearLane(lane);
        }
    }

    @GameTest
    public void dreamDustAllayHidesHeldItems(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        PlayerLane lane = testLane(context);
        SuccubusTower dreamDust = tower(SuccubusTowers.DREAM_DUST_T1, position(context, 3, 2, 4));
        lane.addTower(dreamDust);
        SemionTowerEntity entity = (SemionTowerEntity) context.getLevel()
                .getEntity(dreamDust.entityId().orElseThrow());

        List<Pair<EquipmentSlot, ItemStack>> visible = entity.getPolymerVisibleEquipment(
                List.of(Pair.of(EquipmentSlot.MAINHAND, new ItemStack(Items.COMPASS))),
                null
        );

        require(visible.getFirst().getSecond().isEmpty(), "Allay visuals must hide held items.");
        context.succeed();
    }

    private static void assertReduction(GameTestHelper context, PlayerLane lane, SemionMonsterEntity attacker,
                                        kim.biryeong.semiontd.tower.TowerType type, double reduction, int x) {
        SuccubusTower tower = tower(type, position(context, x, 2, 4));
        lane.addTower(tower);
        SemionTowerEntity entity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
        double reduced = tower.modifyIncomingDamage(entity, context.getLevel().damageSources().mobAttack(attacker), 100.0);
        double environmental = tower.modifyIncomingDamage(entity, context.getLevel().damageSources().fellOutOfWorld(), 100.0);
        requireClose(100.0 * (1.0 - reduction), reduced, type.id() + " reduction");
        requireClose(100.0, environmental, type.id() + " environment exclusion");
        int previousStacks = SuccubusDreams.stacks(attacker);
        tower.onDamaged(entity, context.getLevel().damageSources().mobAttack(attacker), 10.0, 100.0, 90.0);
        require(SuccubusDreams.stacks(attacker) == previousStacks + SuccubusTowers.tier(type),
                type.id() + " must return its tier's dream stacks.");
    }

    private static SuccubusTower tower(kim.biryeong.semiontd.tower.TowerType type, GridPosition position) {
        return new SuccubusTower(TowerBalanceRuntime.resolve(type), OWNER, TeamId.RED, 1, position, position);
    }

    private static SemionMonsterEntity spawnMonster(GameTestHelper context, PlayerLane lane, String id,
                                                     GridPosition position) {
        return spawnMonster(context, lane, id, position, 1_000.0);
    }

    private static SemionMonsterEntity spawnMonster(GameTestHelper context, PlayerLane lane, String id,
                                                     GridPosition position, double maxHealth) {
        Monster monster = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                maxHealth, 0.0, 10.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        require(context.getLevel().addFreshEntity(entity), "Monster must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static PlayerLane testLane(GameTestHelper context) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(10, 5, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn,
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 7)))), boss,
                BlockBounds.of(min, max), List.of(position(context, 8, 2, 11)));
        return new PlayerLane(TeamId.RED, 1, OWNER, context.getLevel(), layout);
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1.0E-6, message + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
