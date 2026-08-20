package kim.biryeong.semiontd.tower.queen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class QueenGameTest {
    @GameTest(maxTicks = 120)
    public void shrinkPreservesHealthAndGiantExecutesContactedEnemy(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("queen-runtime".getBytes(StandardCharsets.UTF_8));
        QueenStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        PlayerLane finalDefenseLane = testFinalDefenseLane(context);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        group.addLane(finalDefenseLane);
        QueenStates.begin(owner, group);
        prepareFloor(context);
        QueenTower queen = (QueenTower) ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3))));
        QueenCardTower card = (QueenCardTower) ProductionTowerCatalog.find(QueenTowers.RANDOM_CARD_SOLDIER.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(4, 2, 3))));
        try {
            card.assignCard(new QueenCard(QueenCard.Suit.DIAMOND, 7));
            lane.addTower(queen);
            lane.addTower(card);
            requireClose(60.0, queen.currentMaxHealth(), "The Queen must start with reduced early-game health.");
            Monster monster = new Monster("queen-target", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                    80.0, 0.0, 20.0, AttackKind.MELEE, "minecraft:zombie", 5L);
            SemionMonsterEntity target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            target.configureFrom(monster, lane.laneLayout());
            Vec3 spawn = lane.laneLayout().spawn();
            target.setPos(spawn.x, spawn.y, spawn.z);
            require(context.getLevel().addFreshEntity(target), "Target monster must spawn.");
            monster.markMinecraftEntitySpawned(target.getId(), spawn.x, spawn.y, spawn.z);
            lane.activeMonsters().add(monster);
            Monster nearbyMonster = new Monster("queen-nearby-target", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                    80.0, 0.0, 20.0, AttackKind.MELEE, "minecraft:zombie", 5L);
            SemionMonsterEntity nearby = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            nearby.configureFrom(nearbyMonster, lane.laneLayout());
            nearby.setPos(spawn.x + 1.0, spawn.y, spawn.z);
            require(context.getLevel().addFreshEntity(nearby), "Nearby target monster must spawn.");
            nearbyMonster.markMinecraftEntitySpawned(nearby.getId(), spawn.x + 1.0, spawn.y, spawn.z);
            lane.activeMonsters().add(nearbyMonster);
            Monster unweakenedMonster = new Monster("queen-unweakened-target", TeamId.RED, 1,
                    Optional.empty(), Optional.empty(), 80.0, 0.0, 20.0,
                    AttackKind.MELEE, "minecraft:zombie", 5L);
            unweakenedMonster.syncHealth(4.0);
            SemionMonsterEntity unweakened = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            unweakened.configureFrom(unweakenedMonster, lane.laneLayout());
            unweakened.setPos(spawn.x + 2.0, spawn.y, spawn.z);
            require(context.getLevel().addFreshEntity(unweakened), "Unweakened target monster must spawn.");
            unweakenedMonster.markMinecraftEntitySpawned(unweakened.getId(), spawn.x + 2.0, spawn.y, spawn.z);
            lane.activeMonsters().add(unweakenedMonster);
            SemionTowerEntity queenEntity = (SemionTowerEntity) context.getLevel().getEntity(queen.entityId().orElseThrow());
            require(queenEntity.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                            && queenEntity.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.GOLDEN_SWORD),
                    "The Queen must wear the red-and-gold equipment set.");
            ArmorStand queenEquipment = equipmentVisual(context, queenEntity);
            require(queenEquipment.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)
                            && queenEquipment.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                            && queenEquipment.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.GOLDEN_SWORD),
                    "The Queen equipment overlay must render the complete equipment set.");
            var player = context.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            require(queenEquipment.isMarker() && !queenEquipment.isPickable(),
                    "The Queen equipment overlay must not intercept player interaction.");
            require(queenEquipment.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                            && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                            && queenEquipment.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.GOLDEN_SWORD),
                    "Players must not take equipment from the Queen overlay.");

            monster.syncHealth(40.0);
            target.setHealth(40.0F);
            queen.onAttackResolved(queenEntity, target, 0.0, 0.0, 0.0, false);
            double factor = Math.pow(QueenBalance.shrinkFactorPerPoint(), QueenBalance.queenShrinkPoints());
            requireClose(80.0 * factor, monster.maxHealth(), "Queen shrink must reduce max health.");
            requireClose(40.0 * factor, monster.health(), "Queen shrink must preserve current health ratio.");
            requireClose(20.0 * factor, monster.attackDamage(), "Queen shrink must reduce attack damage.");
            require(monster.isAlive(), "Shrink must never kill its target directly.");
            require(queen.selectForcedAttackTarget(queenEntity, List.of(target, nearby)).orElseThrow() == target,
                    "The Queen must keep focusing a partially weakened target.");
            for (int hit = 1; hit < 2; hit++) {
                queen.onAttackResolved(queenEntity, target, 0.0, 0.0, 0.0, false);
            }
            require(QueenGiantRunner.hasRequiredVisualShrink(monster),
                    "Two early Queen attacks must make the target 20% smaller for Giant execution.");
            require(queen.selectForcedAttackTarget(queenEntity, List.of(target, nearby)).orElseThrow() == nearby,
                    "The Queen must switch targets after preparing one for execution.");
            SemionTowerEntity cardEntity = (SemionTowerEntity) context.getLevel().getEntity(card.entityId().orElseThrow());
            require(card.selectForcedAttackTarget(cardEntity, List.of(target, nearby)).orElseThrow() == nearby,
                    "Card soldiers must target an enemy that still needs shrink for Giant execution.");
            QueenShrink.apply(nearby, QueenBalance.cardShrinkPoints());
            QueenShrink.apply(unweakened, QueenBalance.cardShrinkPoints());
            requireClose(nearbyMonster.permanentStatScale(), unweakenedMonster.permanentStatScale(),
                    "Target-lock regression setup must give both enemies equal shrink.");
            cardEntity.recordCurrentAttackTarget(nearby);
            require(card.selectForcedAttackTarget(cardEntity, List.of(unweakened, nearby)).orElseThrow() == nearby,
                    "Card soldiers must keep their current target when splash creates an equal-shrink tie.");
            for (int hit = 0; hit < 200 && !QueenGiantRunner.hasRequiredVisualShrink(nearbyMonster); hit++) {
                QueenShrink.apply(nearby, QueenBalance.cardShrinkPoints());
            }
            require(card.selectForcedAttackTarget(cardEntity, List.of(nearby, unweakened)).orElseThrow() == unweakened,
                    "Card soldiers must switch only after the current target reaches execution shrink.");
            require(cardEntity.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE),
                    "Card soldiers must wear suit-colored leather armor.");
            ArmorStand cardEquipment = equipmentVisual(context, cardEntity);
            require(cardEquipment.isMarker()
                            && cardEquipment.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE),
                    "Card soldiers must render their suit-colored armor overlay.");
            card.onAttackResolved(cardEntity, target, 0.0, 0.0, 0.0, false);
            require(nearbyMonster.maxHealth() < 80.0,
                    "Every card suit must splash shrink to at least one nearby target.");
            for (int hit = 0; hit < 200; hit++) {
                QueenShrink.apply(target, QueenBalance.queenShrinkPoints());
            }
            requireClose(16.0, monster.maxHealth(), "Queen shrink must stop at the configured stat floor.");
            requireClose(8.0, monster.health(), "The shrink floor must preserve the current health ratio.");
            requireClose(4.0, monster.attackDamage(), "Queen shrink must stop at the configured attack floor.");
            requireClose(0.50, monster.visualScale(), "Queen shrink must preserve the separate visual floor.");
            double appliedPoints = QueenShrink.points(target);
            requireClose(Math.log(QueenBalance.minimumStatScale()) / Math.log(QueenBalance.shrinkFactorPerPoint()),
                    appliedPoints, "Only effective shrink points must be recorded.");
            require(!QueenShrink.apply(target, QueenBalance.queenShrinkPoints()),
                    "Shrink at the floor must report no state change.");
            requireClose(appliedPoints, QueenShrink.points(target),
                    "Rejected shrink must not increase the recorded points.");
            monster.syncHealth(4.0);
            target.setHealth(4.0F);

            QueenStates.PlayerState state = QueenStates.state(owner);
            state.addCharge(QueenBalance.giantChargeTicks());
            queen.markWaveStarted(1);
            queen.onWaveStarted(lane, 1);
            queen.tick(lane);
            require(state.runnerActive(), "A full gauge must dispatch a Giant.");
            Vec3 laneEnd = lane.laneLayout().waypoints().getFirst();
            require(state.runner().position().distanceToSqr(laneEnd) < 0.01,
                    "The Giant must begin at the player's lane end, not at the central boss position.");
            Vec3 direction = lane.laneLayout().spawn().subtract(laneEnd);
            float expectedYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            require(angleDifference(state.runner().yaw(), expectedYaw) < 0.1,
                    "The Giant must face the direction it is running.");
            for (int tick = 0; tick < 80; tick++) queen.tick(lane);
            require(!monster.isAlive(), "The Giant must execute a contacted enemy below its threshold.");
            require(unweakenedMonster.isAlive(),
                    "The Giant must not execute an enemy that is less than 20% smaller, even below its threshold.");
            requireClose(QueenBalance.giantInitialExecutionHealth()
                            + Math.max(QueenBalance.giantInitialExecutionHealth(), Math.min(16.0,
                                    QueenBalance.giantInitialExecutionHealth()
                                            * QueenBalance.giantGrowthTargetCapMultiplier()))
                                    * QueenBalance.giantExecutionGrowthRatio(), state.executionHealth(),
                    "A successful execution must use the bounded growth formula.");
            require(owner.equals(monster.lastHitPlayerId().orElse(null))
                            && monster.lastHitSourceKind() == KillSourceKind.TOWER,
                    "Giant executions must keep tower owner and kill-source attribution.");
            require(queen.roundPhysicalDamageDealt() > 0.0,
                    "Giant TRUE damage must be included in tower damage statistics.");
            require(nearbyMonster.isAlive(), "Enemies above the execution threshold must survive the Giant.");
            requireClose(QueenBalance.giantSlow(),
                    nearby.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Survivors must receive the configured movement slow.");
            requireClose(QueenBalance.giantSlow(),
                    nearby.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Survivors must receive the configured attack-speed slow.");

            state.endRunner();
            queen.resetForRound(lane);
            lane.moveTowersToFinalDefense();
            state.addCharge(QueenBalance.giantChargeTicks());
            queen.markWaveStarted(2);
            queen.onWaveStarted(lane, 2);
            requireClose(68.0, queen.currentMaxHealth(),
                    "The Queen must gain a fixed amount of maximum health each round.");
            requireClose(68.0, queen.health(), "Round health growth must heal the gained maximum health.");
            Vec3 boss = finalDefenseLane.laneLayout().bossPosition();
            group.spawnBossEntity(context.getLevel(), boss);
            double teamBossHealth = group.boss().health();
            queen.tick(lane);
            require(state.runnerActive() && state.runner().position().distanceToSqr(boss) < 0.01,
                    "Final-defense Giants must start at the boss.");
            Vec3 finalWaypoint = finalDefenseLane.laneLayout().waypoints().getLast();
            Vec3 finalDirection = finalWaypoint.subtract(boss);
            float expectedFinalYaw = (float) Math.toDegrees(Math.atan2(-finalDirection.x, finalDirection.z));
            require(angleDifference(state.runner().yaw(), expectedFinalYaw) < 0.1,
                    "Final-defense Giants must face back along lane 5.");
            Vec3 lastRunnerPosition = state.runner().position();
            for (int tick = 0; tick < 200 && state.runnerActive(); tick++) {
                lastRunnerPosition = state.runner().position();
                queen.tick(lane);
            }
            require(!state.runnerActive(), "The final-defense Giant must finish its lane 5 run.");
            require(lastRunnerPosition.distanceTo(finalDefenseLane.laneLayout().spawn())
                            <= QueenBalance.giantSpeed() + 0.01,
                    "The final-defense Giant must run to the end of lane 5.");
            require(group.boss().isAlive(), "The final-defense Giant must not kill its own team's boss.");
            requireClose(teamBossHealth, group.boss().health(),
                    "The final-defense Giant must not damage its own team's boss.");
            context.succeed();
        } finally {
            group.closeRuntime();
            QueenStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 20)
    public void giantSpawnsOnMidLaneWithoutPersonalWaypoints(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("queen-mid-lane".getBytes(StandardCharsets.UTF_8));
        QueenStates.clear(owner);
        PlayerLane lane = testMidLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        QueenStates.begin(owner, group);
        prepareFloor(context);
        QueenTower queen = (QueenTower) ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow()
                .create(owner, TeamId.RED, 5, GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3))));
        try {
            lane.addTower(queen);
            lane.activeMonsters().add(new Monster("queen-mid-lane-target", TeamId.RED, 1,
                    Optional.empty(), Optional.empty(), 80.0, 0.0, 20.0,
                    AttackKind.MELEE, "minecraft:zombie", 5L));
            QueenStates.PlayerState state = QueenStates.state(owner);
            state.addCharge(QueenBalance.giantChargeTicks());
            queen.markWaveStarted(1);
            queen.onWaveStarted(lane, 1);

            queen.tick(lane);

            require(state.runnerActive(), "The Giant must spawn on lane 5 without personal waypoints.");
            require(state.runner().position().distanceToSqr(lane.laneLayout().waypoints().getLast()) < 0.01,
                    "The lane 5 Giant must start at the boss-side end of the shared path.");
            context.succeed();
        } finally {
            group.closeRuntime();
            QueenStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 20)
    public void equipmentOverlayUsesTheTowerInterpolationAndPose(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("queen-equipment-sync".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        QueenTower queen = (QueenTower) ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3))));
        try {
            lane.addTower(queen);
            SemionTowerEntity source = towerEntity(context, queen);
            ArmorStand equipment = equipmentVisual(context, source);
            require(SemionEntityTypes.TOWER.updateInterval() == EntityType.ARMOR_STAND.updateInterval(),
                    "Tower and equipment-overlay packets must use the same interpolation interval.");
            Vec3 shiftedPosition = source.position().add(0.75, 0.25, -0.5);
            source.teleportTo(shiftedPosition.x, shiftedPosition.y, shiftedPosition.z);
            source.setYRot(15.0F);
            source.setXRot(-25.0F);
            source.yBodyRot = 35.0F;
            source.setYHeadRot(55.0F);
            queen.tick(lane);
            require(equipment.position().distanceToSqr(shiftedPosition) < 0.0001,
                    "The equipment overlay must follow the tower position exactly.");
            requireClose(source.yBodyRot, equipment.getYRot(), "Equipment yaw must match the tower body.");
            requireClose(source.yBodyRot, equipment.yBodyRot, "Equipment body rotation must match the tower body.");
            requireClose(source.getXRot(), equipment.getHeadPose().x(), "Equipment head pitch must match the tower.");
            requireClose(20.0, equipment.getHeadPose().y(), "Equipment head yaw must follow the tower head.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void cardSplashHonorsNormalAndSpadeTargetCaps(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("queen-splash".getBytes(StandardCharsets.UTF_8));
        QueenStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        QueenCardTower card = (QueenCardTower) ProductionTowerCatalog.find(QueenTowers.RANDOM_CARD_SOLDIER.id())
                .orElseThrow().create(owner, TeamId.RED, 1,
                        GridPosition.from(context.absolutePos(new BlockPos(4, 2, 3))));
        try {
            card.assignCard(new QueenCard(QueenCard.Suit.DIAMOND, 7));
            lane.addTower(card);
            assertShrinkCount(context, lane, card, 1, 1);
            assertShrinkCount(context, lane, card, 3, 3);
            assertShrinkCount(context, lane, card, 8, 6);

            card.assignCard(new QueenCard(QueenCard.Suit.SPADE, 7));
            assertShrinkCount(context, lane, card, 1, 1);
            assertShrinkCount(context, lane, card, 3, 3);
            assertShrinkCount(context, lane, card, 8, 6);
            context.succeed();
        } finally {
            group.closeRuntime();
            QueenStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 120)
    public void cardSupportDeathShrinkAndPokerSnapshotUseWaveState(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("queen-card-support".getBytes(StandardCharsets.UTF_8));
        QueenStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        QueenTower queen = createQueen(context, owner, new BlockPos(3, 2, 3));
        QueenCardTower heart = createCard(context, owner, new BlockPos(4, 2, 3), new QueenCard(QueenCard.Suit.HEART, 2));
        QueenCardTower club = createCard(context, owner, new BlockPos(5, 2, 3), new QueenCard(QueenCard.Suit.CLUB, 6));
        try {
            lane.addTower(queen);
            lane.addTower(heart);
            lane.addTower(club);
            queen.syncHealth(30.0);
            lane.markWaveStarted(1);
            for (int tick = 0; tick <= QueenBalance.heartHealIntervalTicks(); tick++) heart.tick(lane);
            requireClose(30.0 + QueenBalance.heartHealAmount(), queen.health(),
                    "Heart cards must heal damaged Queen-family towers.");

            SemionTowerEntity clubEntity = towerEntity(context, club);
            ArmorStand clubEquipment = equipmentVisual(context, clubEntity);
            requireClose(85.0, club.modifyIncomingDamage(clubEntity, clubEntity.damageSources().generic(), 100.0),
                    "Club cards must retain their configured damage reduction.");

            SpawnedTarget deathTarget = spawnTarget(context, lane, towerEntity(context, club).position(),
                    "queen-card-death");
            require(lane.killTower(club), "The card must die through the lane lifecycle.");
            require(clubEquipment.isRemoved(), "A dead card must remove its equipment overlay.");
            requireClose(Math.pow(QueenBalance.shrinkFactorPerPoint(), QueenBalance.cardDeathShrinkPoints()),
                    deathTarget.runtime().permanentStatScale(), "Card death must apply the configured shrink once.");
            deathTarget.entity().discard();
            lane.activeMonsters().remove(deathTarget.runtime());

            List<QueenCardTower> hand = List.of(
                    createCard(context, owner, new BlockPos(7, 2, 3), new QueenCard(QueenCard.Suit.HEART, 2)),
                    createCard(context, owner, new BlockPos(7, 2, 4), new QueenCard(QueenCard.Suit.DIAMOND, 2)),
                    createCard(context, owner, new BlockPos(7, 2, 5), new QueenCard(QueenCard.Suit.CLUB, 6)),
                    createCard(context, owner, new BlockPos(7, 2, 6), new QueenCard(QueenCard.Suit.SPADE, 8)),
                    createCard(context, owner, new BlockPos(7, 2, 7), new QueenCard(QueenCard.Suit.HEART, 10))
            );
            hand.forEach(lane::addTower);
            lane.markWaveStarted(2);
            QueenCardTower firstCard = hand.getFirst();
            double pairHealth = QueenBalance.cardMaxHealth(QueenCard.Suit.HEART)
                    * (1.0 + QueenBalance.handBonus(PokerHand.ONE_PAIR));
            requireClose(pairHealth, firstCard.currentMaxHealth(),
                    "Poker hands must increase card maximum health.");
            requireClose((QueenTowers.QUEEN.maxHealth() + QueenBalance.queenMaxHealthPerRound())
                            * (1.0 + QueenBalance.handBonus(PokerHand.ONE_PAIR)), queen.currentMaxHealth(),
                    "A completed hand must add its bonus to the Queen's maximum health once per wave.");
            require(firstCard.adjustAttackInterval(999) == 19,
                    "One pair must improve the Heart card attack interval.");
            require(towerEntity(context, firstCard).aggroPriority() == QueenBalance.cardAggro(QueenCard.Suit.HEART),
                    "The live card entity must use its suit role's aggro priority.");
            lane.markWaveStarted(3);
            requireClose((QueenTowers.QUEEN.maxHealth() + QueenBalance.queenMaxHealthPerRound() * 2.0)
                            * (1.0 + QueenBalance.handBonus(PokerHand.ONE_PAIR) * 2.0), queen.currentMaxHealth(),
                    "The same completed hand must add its bonus again on the next wave without duplicate snapshots.");
            firstCard.assignCard(new QueenCard(QueenCard.Suit.HEART, 3));
            requireClose(pairHealth, firstCard.currentMaxHealth(),
                    "Card changes during a wave must not change the snapshot.");
            lane.markWaveStarted(4);
            requireClose(QueenBalance.cardMaxHealth(QueenCard.Suit.HEART), firstCard.currentMaxHealth(),
                    "The next wave must capture the updated hand.");
            require(firstCard.adjustAttackInterval(999) == QueenBalance.cardInterval(QueenCard.Suit.HEART),
                    "The next wave must replace the previous attack-speed bonus.");
            requireClose((QueenTowers.QUEEN.maxHealth() + QueenBalance.queenMaxHealthPerRound() * 3.0)
                            * (1.0 + QueenBalance.handBonus(PokerHand.ONE_PAIR) * 2.0), queen.currentMaxHealth(),
                    "A high-card wave must preserve the accumulated Queen health bonus without adding more.");
            context.succeed();
        } finally {
            group.closeRuntime();
            QueenStates.clear(owner);
        }
    }

    private static QueenTower createQueen(GameTestHelper context, UUID owner, BlockPos relativePosition) {
        return (QueenTower) ProductionTowerCatalog.find(QueenTowers.QUEEN.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, GridPosition.from(context.absolutePos(relativePosition)));
    }

    private static QueenCardTower createCard(GameTestHelper context, UUID owner, BlockPos relativePosition,
                                              QueenCard value) {
        QueenCardTower card = (QueenCardTower) ProductionTowerCatalog.find(QueenTowers.RANDOM_CARD_SOLDIER.id())
                .orElseThrow().create(owner, TeamId.RED, 1, GridPosition.from(context.absolutePos(relativePosition)));
        card.assignCard(value);
        return card;
    }

    private static void assertShrinkCount(GameTestHelper context, PlayerLane lane, QueenCardTower card,
                                          int targetCount, int expectedShrunk) {
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        Vec3 center = lane.laneLayout().spawn();
        for (int index = 0; index < targetCount; index++) {
            targets.add(spawnTarget(context, lane, center.add(index * 0.2, 0.0, 0.0),
                    "queen-splash-" + targetCount + "-" + index));
        }
        card.onAttackResolved(towerEntity(context, card), targets.getFirst().entity(), 0.0, 0.0, 0.0, false);
        long actual = targets.stream().filter(target -> target.runtime().permanentStatScale() < 1.0).count();
        require(actual == expectedShrunk,
                card.card().orElseThrow().suit() + " must shrink " + expectedShrunk + "/" + targetCount
                        + " targets, but shrank " + actual + '.');
        targets.forEach(target -> target.entity().discard());
        lane.activeMonsters().removeAll(targets.stream().map(SpawnedTarget::runtime).toList());
    }

    private static SpawnedTarget spawnTarget(GameTestHelper context, PlayerLane lane, Vec3 position, String id) {
        Monster runtime = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                100.0, 0.0, 20.0, AttackKind.MELEE, "minecraft:zombie", 5L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Target monster must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, Tower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(((kim.biryeong.semiontd.tower.EntityBackedTower) tower)
                .entityId().orElseThrow());
    }

    private static ArmorStand equipmentVisual(GameTestHelper context, SemionTowerEntity source) {
        return context.getLevel().getEntitiesOfClass(ArmorStand.class, source.getBoundingBox().inflate(0.4),
                        visual -> visual.isInvisible() && visual.distanceToSqr(source) < 0.01).stream().findFirst()
                .orElseThrow(() -> new AssertionError("The equipment overlay must spawn with its tower."));
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                BlockBounds.of(context.absolutePos(new BlockPos(2, 2, 2)), context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7))),
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(18, 2, 18)))
                ),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                BlockBounds.of(min, max), List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))), 1);
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static PlayerLane testFinalDefenseLane(GameTestHelper context) {
        return testMidLane(context,
                UUID.nameUUIDFromBytes("queen-final-defense-lane".getBytes(StandardCharsets.UTF_8)));
    }

    private static PlayerLane testMidLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 2)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12)));
        LaneRegionLayout layout = new LaneRegionLayout(
                5,
                spawn,
                BlockBounds.of(BlockPos.containing(spawn), BlockPos.containing(spawn)),
                List.of(
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 7))),
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 10)))
                ),
                boss,
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))),
                0
        );
        return new PlayerLane(
                TeamId.RED,
                5,
                owner,
                context.getLevel(),
                layout
        );
    }

    private static void prepareFloor(GameTestHelper context) {
        for (int x = 0; x <= 14; x++) for (int z = 0; z <= 14; z++) {
            BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
            context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.001) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    private static double angleDifference(float first, float second) {
        return Math.abs(((first - second + 540.0) % 360.0) - 180.0);
    }

    private record SpawnedTarget(Monster runtime, SemionMonsterEntity entity) {}
}
