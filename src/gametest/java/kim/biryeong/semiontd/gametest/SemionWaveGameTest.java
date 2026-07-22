package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SemionWaveGameTest {
    @GameTest
    public void roundRobinWaveUsesConfiguredIntervalAndCombatStats(GameTestHelper context) {
        PlayerLane lane = lane(context, "wave-runtime");
        WaveMonsterEntry tank = entry("tank", AttackKind.MELEE, 2, 45.0, 0.9, 3.0, 20);
        WaveMonsterEntry ranged = entry("ranged", AttackKind.RANGED, 1, 0.0, 0.75, 9.0, 24);

        lane.enqueueWave(List.of(tank, ranged), WaveSpawnMode.ROUND_ROBIN, 3);
        lane.tick(context.getLevel().getServer());
        assertIds(lane, "tank");

        lane.tick(context.getLevel().getServer());
        lane.tick(context.getLevel().getServer());
        assertIds(lane, "tank");

        lane.tick(context.getLevel().getServer());
        assertIds(lane, "tank", "ranged");

        Monster runtime = lane.activeMonsters().get(1);
        if (!(context.getLevel().getEntity(runtime.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
            throw new AssertionError("Configured ranged wave entity was not spawned.");
        }
        assertClose(0.75, entity.movementSpeedMultiplier(), "movement speed multiplier");
        assertClose(9.0, entity.attackRange(), "attack range");
        if (entity.attackIntervalTicks() != 24) {
            throw new AssertionError("Expected attack interval 24, got " + entity.attackIntervalTicks());
        }
        context.succeed();
    }

    @GameTest
    public void generalTowerTargetsHigherPriorityWaveMonster(GameTestHelper context) {
        PlayerLane lane = lane(context, "wave-priority");
        BlockPos towerBlock = context.absolutePos(new BlockPos(1, 1, 2));
        TestTower tower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                GridPosition.from(towerBlock)
        );
        lane.addTower(tower);
        lane.enqueueWave(
                List.of(
                        entry("backline", AttackKind.RANGED, 1, 0.0, 1.0, 6.0, 13),
                        entry("tank", AttackKind.MELEE, 1, 45.0, 1.0, 2.5, 13)
                ),
                WaveSpawnMode.ROUND_ROBIN,
                1
        );
        lane.tick(context.getLevel().getServer());
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(5, () -> {
            if (!(context.getLevel().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity towerEntity)) {
                throw new AssertionError("Test tower entity was not spawned.");
            }
            SemionMonsterEntity target = towerEntity.currentAttackTarget();
            if (target == null || target.runtimeMonster() == null || !"tank".equals(target.runtimeMonster().id())) {
                throw new AssertionError("General tower should target the higher-priority tank.");
            }
            context.succeed();
        });
    }

    @GameTest
    public void forcedFinalDefenseCapsMonsterAttackRangeAtTwoBlocks(GameTestHelper context) {
        PlayerLane lane = lane(context, "wave-final-defense-range");
        WaveMonsterEntry artillery = entry("artillery", AttackKind.RANGED, 1, 0.0, 0.7, 11.0, 24);

        lane.enqueueWave(List.of(artillery), WaveSpawnMode.SEQUENTIAL, 1);
        lane.tick(context.getLevel().getServer());
        Monster monster = lane.activeMonsters().getFirst();
        assertClose(11.0, monster.attackRange(), "line attack range");

        lane.forceFinalDefense();

        if (!monster.inFinalDefenseCombat()) {
            throw new AssertionError("Wave timeout should mark the monster as final-defense combat.");
        }
        assertClose(Monster.FINAL_DEFENSE_ATTACK_RANGE, monster.attackRange(), "forced final-defense attack range");
        context.succeed();
    }

    @GameTest
    public void monsterDefenseSearchUsesEightBlockMinimumAndConfiguredAttackRange(GameTestHelper context) {
        PlayerLane lane = lane(context, "monster-defense-search-range");
        Monster runtimeMonster = Monster.fromWaveEntry(
                entry("artillery-search", AttackKind.RANGED, 1, 0.0, 1.0, 11.0, 24),
                TeamId.RED,
                1
        );
        SemionMonsterEntity monster = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        monster.configureFrom(runtimeMonster, lane.laneLayout());
        Vec3 origin = lane.laneLayout().positionAt(0.4);
        monster.setPos(origin);
        context.getLevel().addFreshEntity(monster);

        TowerType towerType = new TowerType("search-target", "Search Target", TowerCategory.DIRECT, 0, 100.0, 2.0, 0.0, 20, 0);
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(new TestTower(towerType, lane.ownerPlayer(), TeamId.RED, 1, GridPosition.from(BlockPos.containing(origin))), lane.laneLayout());
        tower.setPos(origin.add(10.5, 0.0, 0.0));
        context.getLevel().addFreshEntity(tower);

        assertClose(11.0, monster.defenseTargetSearchRange(), "artillery defense search range");
        if (!monster.canTargetDefense(tower)) {
            throw new AssertionError("An artillery monster should find a tower inside its configured attack range.");
        }
        tower.setPos(origin.add(11.5, 0.0, 0.0));
        if (monster.canTargetDefense(tower)) {
            throw new AssertionError("An artillery monster should not find a tower beyond its configured attack range.");
        }

        runtimeMonster.enterFinalDefenseCombat();
        assertClose(8.0, monster.defenseTargetSearchRange(), "final-defense minimum search range");
        tower.setPos(origin.add(7.5, 0.0, 0.0));
        if (!monster.canTargetDefense(tower)) {
            throw new AssertionError("A final-defense monster should find a tower inside eight blocks.");
        }
        tower.setPos(origin.add(8.5, 0.0, 0.0));
        if (monster.canTargetDefense(tower)) {
            throw new AssertionError("A final-defense monster should not find a tower beyond eight blocks.");
        }
        context.succeed();
    }

    @GameTest
    public void forcedFinalDefenseSpreadsMonstersAcrossDistinctPositions(GameTestHelper context) {
        PlayerLane lane = lane(context, "forced-final-defense-spread");
        lane.enqueueWave(
                List.of(entry("spread", AttackKind.MELEE, 10, 0.0, 1.0, 2.5, 13)),
                WaveSpawnMode.SEQUENTIAL,
                1
        );
        lane.forceFinalDefense();
        Set<Vec3> positions = lane.activeMonsters().stream()
                .map(monster -> context.getLevel().getEntity(monster.minecraftEntityId()))
                .map(entity -> entity == null ? null : entity.position())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (positions.size() != 10) {
            throw new AssertionError("Forced final defense should spread ten monsters across ten positions, got " + positions.size());
        }
        if (lane.activeMonsters().stream().anyMatch(monster -> !monster.inFinalDefenseCombat())) {
            throw new AssertionError("Every forced monster should enter final-defense combat.");
        }
        context.succeed();
    }

    @GameTest
    public void teamFinalDefenseSlotsKeepEarlierLaneStableAndAvoidDuplicates(GameTestHelper context) {
        GameArena arena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
        PlayerLane firstLane = lane(context, arena, "slot-first", 1);
        PlayerLane secondLane = lane(context, arena, "slot-second", 2);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(firstLane);
        group.addLane(secondLane);

        TowerType meleeType = new TowerType("slot-melee", "Slot Melee", TowerCategory.DIRECT, 0, 100.0, 3.0, 0.0, 20, 0);
        TowerType rangedType = new TowerType("slot-ranged", "Slot Ranged", TowerCategory.DIRECT, 0, 100.0, 6.0, 0.0, 20, 0);
        TestTower melee = new TestTower(meleeType, firstLane.ownerPlayer(), TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(1, 1, 2))));
        TestTower ranged = new TestTower(rangedType, secondLane.ownerPlayer(), TeamId.RED, 2, GridPosition.from(context.absolutePos(new BlockPos(2, 1, 2))));
        firstLane.addTower(melee);
        secondLane.addTower(ranged);
        secondLane.enqueueWave(List.of(entry("slot-blocker", AttackKind.MELEE, 1, 0.0, 1.0, 2.5, 13)), WaveSpawnMode.SEQUENTIAL, 1);

        group.tick(context.getLevel().getServer());
        GridPosition firstAssignedPosition = melee.position();
        int firstEntityId = melee.entityId().orElseThrow();
        if (!melee.deployedAtFinalDefense() || ranged.deployedAtFinalDefense()) {
            throw new AssertionError("Only the cleared first lane should deploy on the first tick.");
        }

        secondLane.disableMonsters();
        group.tick(context.getLevel().getServer());

        if (!ranged.deployedAtFinalDefense()) {
            throw new AssertionError("The second lane should deploy after it clears.");
        }
        if (!firstAssignedPosition.equals(melee.position()) || firstEntityId != melee.entityId().orElseThrow()) {
            throw new AssertionError("An earlier final-defense tower must not be reassigned or respawned.");
        }
        if (melee.position().equals(ranged.position())) {
            throw new AssertionError("Different lanes should share one final-defense slot occupancy pool.");
        }
        Vec3 bossPosition = firstLane.laneLayout().bossPosition();
        double meleeDistance = bossPosition.distanceTo(new Vec3(melee.position().x() + 0.5, melee.position().y(), melee.position().z() + 0.5));
        double rangedDistance = bossPosition.distanceTo(new Vec3(ranged.position().x() + 0.5, ranged.position().y(), ranged.position().z() + 0.5));
        if (meleeDistance <= rangedDistance) {
            throw new AssertionError("Melee towers should deploy farther from the boss than ranged towers.");
        }
        context.succeed();
    }

    @GameTest
    public void unloadedLivingMonsterEntityIsRestoredWithoutClearingLane(GameTestHelper context) {
        PlayerLane lane = lane(context, "monster-unload-restore");
        lane.enqueueWave(List.of(entry("restore", AttackKind.MELEE, 1, 0.0, 1.0, 2.5, 13)), WaveSpawnMode.SEQUENTIAL, 1);
        lane.tick(context.getLevel().getServer());
        Monster runtimeMonster = lane.activeMonsters().getFirst();
        int previousEntityId = runtimeMonster.minecraftEntityId();
        SemionMonsterEntity entity = (SemionMonsterEntity) context.getLevel().getEntity(previousEntityId);
        runtimeMonster.syncLaneProgress(0.4);
        entity.setPos(lane.laneLayout().positionAt(0.4));
        entity.remove(RemovalReason.UNLOADED_TO_CHUNK);

        lane.tick(context.getLevel().getServer());

        if (lane.clearedThisRound() || lane.activeMonsters().size() != 1) {
            throw new AssertionError("An unloaded living monster must keep the lane active.");
        }
        if (runtimeMonster.minecraftEntityId() == previousEntityId
                || !(context.getLevel().getEntity(runtimeMonster.minecraftEntityId()) instanceof SemionMonsterEntity)) {
            throw new AssertionError("An unloaded living monster should be restored with a new entity.");
        }
        assertClose(0.4, runtimeMonster.laneProgress(), "restored lane progress");
        context.succeed();
    }

    @GameTest
    public void destroyingAllLaneTowersCapsWaveAndIncomeAttackRange(GameTestHelper context) {
        PlayerLane lane = lane(context, "broken-lane-range");
        BlockPos towerBlock = context.absolutePos(new BlockPos(1, 1, 2));
        TestTower tower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                GridPosition.from(towerBlock)
        );
        lane.addTower(tower);
        lane.enqueueWave(List.of(entry("wave-artillery", AttackKind.RANGED, 1, 0.0, 0.7, 11.0, 24)), WaveSpawnMode.SEQUENTIAL, 1);
        Monster incomeMonster = new Monster(
                "income-ranged",
                TeamId.RED,
                1,
                java.util.Optional.of(UUID.nameUUIDFromBytes("income-owner".getBytes())),
                java.util.Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                1.0,
                AttackKind.RANGED,
                "minecraft:pillager",
                0
        );
        lane.enqueueSummonedMonster(incomeMonster);
        lane.tick(context.getLevel().getServer());

        Monster waveMonster = lane.activeMonsters().stream()
                .filter(monster -> "wave-artillery".equals(monster.id()))
                .findFirst()
                .orElseThrow();
        assertClose(11.0, waveMonster.attackRange(), "wave line attack range");
        assertClose(6.0, incomeMonster.attackRange(), "income line attack range");

        if (!(context.getLevel().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity towerEntity)) {
            throw new AssertionError("Lane tower entity should be available before destruction.");
        }
        towerEntity.setHealth(0.0F);
        lane.tick(context.getLevel().getServer());

        if (!waveMonster.inFinalDefenseCombat() || !incomeMonster.inFinalDefenseCombat()) {
            throw new AssertionError("Wave and income monsters should enter final-defense combat after all lane towers die.");
        }
        assertClose(Monster.FINAL_DEFENSE_ATTACK_RANGE, waveMonster.attackRange(), "wave final-defense attack range");
        assertClose(Monster.FINAL_DEFENSE_ATTACK_RANGE, incomeMonster.attackRange(), "income final-defense attack range");
        context.succeed();
    }

    @GameTest
    public void reachingBossNaturallyCapsMonsterAttackRangeAtTwoBlocks(GameTestHelper context) {
        PlayerLane lane = lane(context, "natural-final-defense-range");
        WaveMonsterEntry artillery = entry("natural-artillery", AttackKind.RANGED, 1, 0.0, 0.95, 11.0, 24);
        Monster runtimeMonster = Monster.fromWaveEntry(artillery, TeamId.RED, 1);
        SemionMonsterEntity monsterEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        monsterEntity.configureFrom(runtimeMonster, lane.laneLayout());
        monsterEntity.setPos(lane.laneLayout().bossPosition());
        context.getLevel().addFreshEntity(monsterEntity);

        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        boss.setPos(lane.laneLayout().bossPosition());
        context.getLevel().addFreshEntity(boss);

        SemionBossEntity distantBoss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        distantBoss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        distantBoss.setPos(lane.laneLayout().bossPosition().add(8.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(distantBoss);

        LaneFollowGoal goal = new LaneFollowGoal(monsterEntity, 1.0);
        goal.start();
        goal.tick();

        if (monsterEntity.getTarget() != boss) {
            throw new AssertionError("A monster at the end of its lane should target the team boss.");
        }
        if (!runtimeMonster.inFinalDefenseCombat()) {
            throw new AssertionError("Natural lane completion should enter final-defense combat.");
        }
        assertClose(2.0, runtimeMonster.attackRange(), "natural final-defense attack range");
        assertClose(2.0, SemionBossEntity.FINAL_DEFENSE_ENGAGEMENT_RANGE, "boss engagement range");
        context.succeed();
    }

    @GameTest
    public void targetingFinalDefenseTowerBeforeBossCapsAttackRangeImmediately(GameTestHelper context) {
        PlayerLane lane = lane(context, "final-defense-tower-interception");
        WaveMonsterEntry artillery = new WaveMonsterEntry(
                "intercepted-artillery",
                100.0,
                4.0,
                1.0,
                AttackKind.RANGED,
                "minecraft:skeleton",
                null,
                MonsterDimensions.DEFAULT,
                0,
                1,
                0.0,
                1.0,
                11.0,
                24
        );
        Monster runtimeMonster = Monster.fromWaveEntry(artillery, TeamId.RED, 1);
        SemionMonsterEntity monsterEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        monsterEntity.configureFrom(runtimeMonster, lane.laneLayout());
        Vec3 finalDefensePosition = lane.laneLayout().bossPosition();
        monsterEntity.setPos(finalDefensePosition.add(0.0, 0.0, -3.0));
        context.getLevel().addFreshEntity(monsterEntity);

        GridPosition finalDefenseBlock = GridPosition.from(BlockPos.containing(finalDefensePosition.add(0.0, -1.0, 0.0)));
        TestTower finalDefenseTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                finalDefenseBlock
        );
        finalDefenseTower.moveToFinalDefense(lane, finalDefenseBlock);
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(finalDefenseTower, lane.laneLayout());
        towerEntity.setPos(finalDefensePosition);
        context.getLevel().addFreshEntity(towerEntity);

        AcquireLaneDefenseTargetGoal targetGoal = new AcquireLaneDefenseTargetGoal(monsterEntity);
        if (!targetGoal.canUse()) {
            context.fail(Component.literal("A ranged monster should detect the final-defense tower before reaching the boss."));
            return;
        }
        targetGoal.start();

        if (monsterEntity.getTarget() != towerEntity || !runtimeMonster.inFinalDefenseCombat()) {
            context.fail(Component.literal("Targeting a final-defense tower should immediately enter final-defense combat."));
            return;
        }
        if (Math.abs(runtimeMonster.attackRange() - Monster.FINAL_DEFENSE_ATTACK_RANGE) > 0.0001) {
            context.fail(Component.literal("Targeting final defense should cap attack range to "
                    + Monster.FINAL_DEFENSE_ATTACK_RANGE + ", got " + runtimeMonster.attackRange() + "."));
            return;
        }

        float towerHealth = towerEntity.getHealth();
        new MonsterAttackTargetGoal(monsterEntity, 1.1).tick();
        if (towerEntity.getHealth() != towerHealth || !monsterEntity.getMoveControl().hasWanted()) {
            context.fail(Component.literal("The monster should approach the tower instead of attacking beyond two blocks. health="
                    + towerEntity.getHealth() + "/" + towerHealth
                    + ", moveWanted=" + monsterEntity.getMoveControl().hasWanted()
                    + ", distance=" + Math.sqrt(monsterEntity.distanceToSqr(towerEntity))
                    + ", canTarget=" + monsterEntity.canTargetDefense(towerEntity)));
            return;
        }
        context.succeed();
    }

    @GameTest
    public void siegeAbilityRespectsFinalDefenseRangeAndProgress(GameTestHelper context) {
        PlayerLane lane = lane(context, "siege-final-defense-range");
        Monster runtimeMonster = Monster.fromWaveEntry(
                entry("siege-artillery", AttackKind.RANGED, 1, 0.0, 1.0, 11.0, 24),
                TeamId.RED,
                1
        );
        runtimeMonster.enterFinalDefenseCombat();
        runtimeMonster.syncLaneProgress(0.64);

        Vec3 towerPosition = lane.laneLayout().bossPosition();
        SemionMonsterEntity monsterEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        monsterEntity.configureFrom(runtimeMonster, lane.laneLayout());
        monsterEntity.setPos(towerPosition.add(7.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(monsterEntity);

        GridPosition towerBlock = GridPosition.from(BlockPos.containing(towerPosition.add(0.0, -1.0, 0.0)));
        TestTower tower = new TestTower(TestTowerTypes.TEST_DIRECT, lane.ownerPlayer(), TeamId.RED, 1, towerBlock);
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(tower, lane.laneLayout());
        towerEntity.setPos(towerPosition);
        context.getLevel().addFreshEntity(towerEntity);
        monsterEntity.setTarget(towerEntity);

        float initialHealth = towerEntity.getHealth();
        new SiegeTrueDamageGoal(monsterEntity, 10.0, 20, 1, 0.65).tick();
        if (towerEntity.getHealth() != initialHealth) {
            throw new AssertionError("A final-defense siege monster must not deal damage beyond two blocks.");
        }

        monsterEntity.setPos(towerPosition.add(1.0, 0.0, 0.0));
        new SiegeTrueDamageGoal(monsterEntity, 10.0, 20, 1, 0.65).tick();
        if (towerEntity.getHealth() != initialHealth) {
            throw new AssertionError("A siege ability must wait for its configured progress threshold.");
        }

        runtimeMonster.syncLaneProgress(0.65);
        new SiegeTrueDamageGoal(monsterEntity, 10.0, 20, 1, 0.65).tick();
        if (towerEntity.getHealth() >= initialHealth) {
            throw new AssertionError("A siege ability should work inside two blocks after its progress threshold.");
        }
        context.succeed();
    }

    @GameTest
    public void reachingFinalDefenseProgressCapsMonsterAttackRange(GameTestHelper context) {
        Monster runtimeMonster = Monster.fromWaveEntry(
                entry("area-artillery", AttackKind.RANGED, 1, 0.0, 1.0, 11.0, 24),
                TeamId.RED,
                1
        );
        runtimeMonster.syncLaneProgress(Monster.FINAL_DEFENSE_PROGRESS);

        assertClose(Monster.FINAL_DEFENSE_ATTACK_RANGE, runtimeMonster.attackRange(), "final-defense progress attack range");
        if (!runtimeMonster.inFinalDefenseCombat()) {
            throw new AssertionError("Reaching final-defense progress should enter final-defense combat.");
        }
        context.succeed();
    }

    @GameTest
    public void preNotifiedDestroyedTowerStillBreaksLaneDefense(GameTestHelper context) {
        PlayerLane lane = lane(context, "pre-notified-destroyed-tower");
        BlockPos towerBlock = context.absolutePos(new BlockPos(1, 1, 2));
        TestTower tower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                GridPosition.from(towerBlock)
        );
        lane.addTower(tower);
        lane.enqueueWave(
                List.of(entry("pre-notified-artillery", AttackKind.RANGED, 1, 0.0, 1.0, 11.0, 24)),
                WaveSpawnMode.SEQUENTIAL,
                1
        );
        lane.tick(context.getLevel().getServer());
        Monster monster = lane.activeMonsters().getFirst();

        if (!lane.killTower(tower)) {
            throw new AssertionError("The last lane tower should be killed by the test setup.");
        }
        lane.tick(context.getLevel().getServer());

        if (!monster.inFinalDefenseCombat()) {
            throw new AssertionError("A pre-notified destroyed tower should still break lane defense.");
        }
        assertClose(Monster.FINAL_DEFENSE_ATTACK_RANGE, monster.attackRange(), "pre-notified final-defense attack range");
        context.succeed();
    }

    @GameTest
    public void destroyedEndCoreStopsAbsorbing(GameTestHelper context) {
        PlayerLane lane = lane(context, "destroyed-end-core");
        EndTower core = new EndTower(
                EndTowers.BASE_END_TOWER,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(new BlockPos(4, 1, 1)))
        );
        EndTower source = new EndTower(
                EndTowers.T1_ENDERMITE_TOWER,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(new BlockPos(1, 1, 1)))
        );
        lane.addTower(core);
        lane.addTower(source);
        core.onWaveStarted(lane, 1);

        SemionTowerEntity coreEntity = (SemionTowerEntity) context.getLevel().getEntity(core.entityId().orElseThrow());
        coreEntity.setHealth(0.0F);
        lane.tick(context.getLevel().getServer());

        assertClose(0.0, core.roundDamageBonus(), "destroyed core round damage bonus");
        assertClose(0.0, core.permanentDamageBonus(), "destroyed core permanent damage bonus");
        if (source.health() <= 0.0) {
            throw new AssertionError("A destroyed End core must not finish absorbing feeder towers.");
        }
        context.succeed();
    }

    @GameTest
    public void endTransferDoesNotSpawnEndCrystals(GameTestHelper context) {
        PlayerLane lane = lane(context, "end-transfer-enchant-particles");
        GridPosition sourcePosition = GridPosition.from(context.absolutePos(new BlockPos(1, 1, 1)));
        GridPosition dragonPosition = GridPosition.from(context.absolutePos(new BlockPos(4, 1, 1)));
        EndTower egg = new EndTower(
                EndTowers.BASE_END_TOWER,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                dragonPosition
        );
        EndTower source = new EndTower(
                EndTowers.T1_ENDERMITE_TOWER,
                lane.ownerPlayer(),
                TeamId.RED,
                1,
                sourcePosition
        );
        lane.addTower(egg);
        lane.addTower(source);
        int coreEntityId = egg.entityId().orElseThrow();
        egg.onWaveStarted(lane, 1);

        for (int tick = 0; tick < 100; tick++) {
            egg.tick(lane);
        }
        EndTower dragon = egg;
        if (!lane.towers().contains(dragon)
                || dragon.state() != kim.biryeong.semiontd.tower.end.EndTowerState.PHANTOM
                || dragon.entityId().isEmpty()
                || dragon.entityId().getAsInt() != coreEntityId) {
            throw new AssertionError("Hatching should switch the existing core tower and entity from EGG to PHANTOM state.");
        }
        dragon.tick(lane);

        AABB beamArea = new AABB(
                sourcePosition.x() - 1.0,
                sourcePosition.y(),
                sourcePosition.z() - 1.0,
                dragonPosition.x() + 2.0,
                dragonPosition.y() + 4.0,
                dragonPosition.z() + 2.0
        );
        if (!context.getLevel().getEntitiesOfClass(EndCrystal.class, beamArea).isEmpty()) {
            throw new AssertionError("An active End transfer must not spawn a visible End Crystal entity.");
        }

        for (int tick = 0; tick < 99; tick++) {
            dragon.tick(lane);
        }

        if (!context.getLevel().getEntitiesOfClass(EndCrystal.class, beamArea).isEmpty()) {
            throw new AssertionError("A completed End transfer must not leave an End Crystal entity behind.");
        }
        if (!lane.towers().contains(source) || source.health() > 0.0) {
            throw new AssertionError("A completed transfer should kill its source without selling or removing it.");
        }

        lane.resetForRound();
        if (!lane.towers().contains(source)
                || source.health() <= 0.0
                || source.entityId().isEmpty()
                || context.getLevel().getEntity(source.entityId().getAsInt()) == null) {
            throw new AssertionError("A source killed by completed transfer should return on the next round.");
        }
        context.succeed();
    }

    private static PlayerLane lane(GameTestHelper context, String seed) {
        GameArena arena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
        return lane(context, arena, seed, 1);
    }

    private static PlayerLane lane(GameTestHelper context, GameArena arena, String seed, int laneId) {
        return new PlayerLane(
                TeamId.RED,
                laneId,
                UUID.nameUUIDFromBytes(seed.getBytes()),
                context.getLevel(),
                arena.lane(TeamId.RED, laneId).orElseThrow()
        );
    }

    private static WaveMonsterEntry entry(
            String id,
            AttackKind attackKind,
            int count,
            double targetPriority,
            double movementSpeedMultiplier,
            double attackRange,
            int attackIntervalTicks
    ) {
        return new WaveMonsterEntry(
                id,
                100.0,
                0.0,
                1.0,
                attackKind,
                attackKind == AttackKind.RANGED ? "minecraft:skeleton" : "minecraft:husk",
                null,
                MonsterDimensions.DEFAULT,
                0,
                count,
                targetPriority,
                movementSpeedMultiplier,
                attackRange,
                attackIntervalTicks
        );
    }

    private static void assertIds(PlayerLane lane, String... expected) {
        List<String> actual = lane.activeMonsters().stream().map(Monster::id).toList();
        if (!actual.equals(List.of(expected))) {
            throw new AssertionError("Expected wave order " + List.of(expected) + ", got " + actual);
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError("Expected " + label + " " + expected + ", got " + actual);
        }
    }
}
