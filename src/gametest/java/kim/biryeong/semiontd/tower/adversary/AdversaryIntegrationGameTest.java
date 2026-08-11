package kim.biryeong.semiontd.tower.adversary;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.test.tower.TestTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AdversaryIntegrationGameTest {
    private static final UUID BELL_OWNER = stableUuid("adversary-team-bell");
    private static final UUID BEACON_OWNER = stableUuid("adversary-team-beacon");
    private static final UUID OMINOUS_OWNER = stableUuid("adversary-team-ominous");

    @GameTest
    public void teamFormsApplyStrongestEffectsAcrossLanesAndExpireAfterFoxesDie(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane sourceLane = testLane(context, BELL_OWNER, 1, 0);
        PlayerLane recipientLane = testLane(context, BEACON_OWNER, 2, 20);
        group.addLane(sourceLane);
        group.addLane(recipientLane);

        try {
            TowerBalanceRuntime.apply(teamEffectTestConfig(defaults));
            AdversaryProgressStates.state(BELL_OWNER).setCurrentForm(FoxForm.BELL_KEEPER);
            AdversaryProgressStates.state(BEACON_OWNER).setCurrentForm(FoxForm.BEACON_KEEPER);
            AdversaryProgressStates.state(OMINOUS_OWNER).setCurrentForm(FoxForm.OMINOUS_HEXER);

            AdversaryFoxTower bell = fox(BELL_OWNER, 1, position(context, 3, 2, 3));
            AdversaryFoxTower beacon = fox(BEACON_OWNER, 1, position(context, 5, 2, 3));
            AdversaryFoxTower ominous = fox(OMINOUS_OWNER, 2, position(context, 23, 2, 3));
            TestTower recipient = new TestTower(
                    BEACON_OWNER,
                    TeamId.RED,
                    2,
                    position(context, 27, 2, 10)
            );
            sourceLane.addTower(bell);
            sourceLane.addTower(beacon);
            recipientLane.addTower(ominous);
            recipientLane.addTower(recipient);

            AdversaryTeamEffects.registerTeam(BELL_OWNER, group);
            AdversaryTeamEffects.registerTeam(BEACON_OWNER, group);
            AdversaryTeamEffects.registerTeam(OMINOUS_OWNER, group);

            Monster monster = new Monster(
                    "adversary-team-effect-target",
                    TeamId.RED,
                    2,
                    Optional.empty(),
                    Optional.empty(),
                    100.0,
                    0.0,
                    10.0,
                    AttackKind.MELEE,
                    "minecraft:zombie",
                    0L
            );
            AdversaryRivalTower rivalTower = new AdversaryRivalTower(
                    AdversaryTowers.BREEZE_RIVAL,
                    OMINOUS_OWNER,
                    TeamId.RED,
                    2,
                    position(context, 25, 2, 3)
            );
            Monster ownedRival = rivalTower.createProxy(1);
            recipientLane.enqueueSummonedMonster(monster);
            recipientLane.enqueueSummonedMonster(ownedRival);
            recipientLane.tick(context.getLevel().getServer());
            recipientLane.tick(context.getLevel().getServer());

            SemionTowerEntity recipientEntity = towerEntity(context, recipient);
            SemionMonsterEntity monsterEntity = monsterEntity(context, monster);
            SemionMonsterEntity ownedRivalEntity = monsterEntity(context, ownedRival);
            recipientEntity.setNoAi(true);
            monsterEntity.setNoAi(true);
            ownedRivalEntity.setNoAi(true);

            requireClose(
                    AdversaryBalance.BEACON_TEAM_DAMAGE_BONUS,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                    "Beacon must replace, not stack with, Bell's team damage channel."
            );
            requireClose(
                    AdversaryBalance.BEACON_TEAM_ATTACK_SPEED_BONUS,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                    "Beacon attack speed must reach another player's tower."
            );
            requireClose(
                    AdversaryBalance.BEACON_TEAM_MAX_HEALTH_BONUS,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS),
                    "Beacon max health must reach another player's tower."
            );
            requireClose(
                    AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Ominous damage reduction must reach a monster targeting the team."
            );
            requireClose(
                    AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Ominous attack speed reduction must reach a monster targeting the team."
            );
            requireClose(
                    AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                    "Ominous vulnerability must reach a monster targeting the team."
            );
            requireClose(
                    0.0,
                    ownedRivalEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Ominous damage reduction must not weaken an owned rival."
            );
            requireClose(
                    0.0,
                    ownedRivalEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Ominous attack speed reduction must not weaken an owned rival."
            );
            requireClose(
                    0.0,
                    ownedRivalEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                    "Ominous vulnerability must not amplify damage against an owned rival."
            );
            require(recipientEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS) == 4,
                    "Team tower support must use the configured timed duration.");
            require(monsterEntity.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION) == 4,
                    "Team monster control must use the configured timed duration.");
            requireClose(52.5, recipient.currentMaxHealth(), "Beacon max-health effect must be live.");

            sourceLane.killTowers(List.of(bell, beacon));
            recipientLane.killTower(ominous);
            for (int tick = 0; tick < 4; tick++) {
                recipientEntity.aiStep();
                monsterEntity.aiStep();
            }

            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                    "Tower damage effect must expire after every source fox dies."
            );
            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS),
                    "Tower max-health effect must expire after every source fox dies."
            );
            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                    "Tower attack-speed effect must expire after every source fox dies."
            );
            requireClose(
                    0.0,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Monster debuff must expire after every source fox dies."
            );
            requireClose(
                    0.0,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Monster attack-speed debuff must expire after every source fox dies."
            );
            requireClose(
                    0.0,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                    "Monster vulnerability must expire after every source fox dies."
            );
            require(recipientEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS) == 0,
                    "Expired tower support must have no remaining duration.");
            require(monsterEntity.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION) == 0,
                    "Expired monster control must have no remaining duration.");
            requireClose(50.0, recipient.currentMaxHealth(), "Expired Beacon health must restore base max health.");
            context.succeed();
        } finally {
            group.closeRuntime();
            AdversaryTeamEffects.clearAllForTesting();
            AdversaryProgressStates.clearAllForTesting();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void preparationTransitionSynchronizesLiveFoxOnTick(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("adversary-live-form-transition");
        PlayerLane lane = testLane(context, owner, 1, 0);

        try {
            TowerBalanceRuntime.apply(defaults);
            AdversaryProgressState progress = AdversaryProgressStates.state(owner);
            AdversaryFoxTower fox = fox(owner, 1, position(context, 3, 2, 3));
            lane.addTower(fox);
            int entityId = fox.entityId().orElseThrow();
            SemionTowerEntity entity = towerEntity(context, fox);
            entity.setNoAi(true);

            lane.resetForRound();
            require(fox.form() == FoxForm.BASE, "Round reset must retain the pre-transition form.");
            require(entity.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STICK),
                    "Round reset must keep the live base held item synchronized.");

            progress.reconcileRivals(List.of(new RivalContribution(
                    stableUuid("adversary-live-form-phantom"),
                    RivalKind.PHANTOM,
                    14
            )));
            require(progress.pendingForm().orElseThrow() == FoxForm.BELL_KEEPER,
                    "Phantom score must queue the Bell form.");
            require(progress.applyPreparationTransition().orElseThrow().current()
                            == FoxForm.BELL_KEEPER,
                    "Preparation must commit the queued Bell form.");
            require(fox.form() == FoxForm.BASE,
                    "The live fox must wait for its next runtime tick before observing progression.");

            fox.syncHealth(fox.currentMaxHealth() * 0.90);
            entity.setHealth((float) fox.health());
            lane.tick(context.getLevel().getServer());

            require(fox.entityId().orElseThrow() == entityId,
                    "Form synchronization must keep the existing live fox entity.");
            require(fox.form() == FoxForm.BELL_KEEPER,
                    "The next live tick must synchronize the committed Bell form.");
            requireClose(FoxForm.BELL_KEEPER.maxHealth(), fox.currentMaxHealth(),
                    "Bell form must synchronize configured max health.");
            double entityHealthCapacity = entity.getMaxHealth();
            require(entityHealthCapacity > 0.0 && entityHealthCapacity <= fox.currentMaxHealth(),
                    "The live entity must expose a positive health capacity no larger than logical health.");
            requireClose(0.90, fox.health() / fox.currentMaxHealth(),
                    "Form synchronization must preserve the fox health ratio.");
            requireClose(Math.min(1125.0, entityHealthCapacity), entity.getHealth(),
                    "The live entity must mirror logical health up to its runtime attribute cap.");
            require(entity.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BELL),
                    "The live fox MAINHAND item must synchronize to Bell.");

            require(entity.hurtServer(context.getLevel(), entity.damageSources().generic(), 50.0F),
                    "Normal damage must reach the live Bell fox.");
            requireClose(1075.0, fox.health(),
                    "Normal damage must consume logical overflow before entity health.");
            requireClose(Math.min(1075.0, entityHealthCapacity), entity.getHealth(),
                    "The entity must remain capped while logical overflow absorbs damage.");

            require(entity.hurtServer(context.getLevel(), entity.damageSources().generic(), 25.0F),
                    "A second same-tick hit must not be blocked by virtual-health handling.");
            requireClose(1050.0, fox.health(),
                    "Same-tick normal damage must also consume logical overflow.");
            requireClose(Math.min(1050.0, entityHealthCapacity), entity.getHealth(),
                    "The second hit must preserve the runtime entity cap.");

            entity.hurtIgnoringReductions(entity.damageSources().generic(), 1030.0);
            requireClose(20.0, fox.health(),
                    "Defense-ignoring damage larger than entity HP must use logical HP.");
            requireClose(20.0, entity.getHealth(),
                    "The entity must survive when logical health survives the true hit.");
            requireClose(1105.0, fox.roundDamageTaken(),
                    "Damage statistics must include virtual and over-cap true damage.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Adversary live form transition failed: " + failure.getMessage()));
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void maceSweepsTwoNearbyTargetsAndSculkRecoilStopsAtTwentyPercent(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("adversary-high-ceiling-combat");
        PlayerLane lane = testLane(context, owner, 1, 0);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);

        try {
            TowerBalanceRuntime.apply(defaults);
            AdversaryFoxTower fox = fox(owner, 1, position(context, 3, 2, 3));
            lane.addTower(fox);
            SemionTowerEntity source = towerEntity(context, fox);
            source.setNoAi(true);
            fox.setForm(FoxForm.MACE_EXECUTIONER, lane);

            Vec3 center = source.position().add(3.0, 0.0, 0.0);
            SemionMonsterEntity primary = spawnMonster(context, lane, "mace-primary", center);
            SemionMonsterEntity first = spawnMonster(context, lane, "mace-first", center.add(0.0, 0.0, 0.5));
            SemionMonsterEntity second = spawnMonster(context, lane, "mace-second", center.add(0.0, 0.0, -0.5));
            SemionMonsterEntity third = spawnMonster(context, lane, "mace-third", center.add(0.0, 0.0, 1.0));
            primary.setNoAi(true);
            first.setNoAi(true);
            second.setNoAi(true);
            third.setNoAi(true);

            fox.onAttackResolved(source, primary, 0.0, 0.0, 0.0, false);
            for (int tick = 0; tick < AdversaryBalance.MACE_FOCUS_TICKS; tick++) {
                fox.tick(lane);
            }

            requireClose(500.0, primary.runtimeMonster().health(),
                    "Mace must deal its full strike to the primary target.");
            requireClose(875.0, first.runtimeMonster().health(),
                    "Mace must sweep the nearest first target for 25% damage.");
            requireClose(875.0, second.runtimeMonster().health(),
                    "Mace must sweep the nearest second target for 25% damage.");
            requireClose(1_000.0, third.runtimeMonster().health(),
                    "Mace sweep must stop after two secondary targets.");

            fox.setForm(FoxForm.SCULK_CORE, lane);
            fox.syncHealth(275.0);
            source.setHealth(275.0F);
            SemionMonsterEntity firstSculkTarget = spawnMonster(
                    context,
                    lane,
                    "sculk-first",
                    source.position().add(-3.0, 0.0, 0.0)
            );
            firstSculkTarget.setNoAi(true);
            fox.onAttackResolved(source, firstSculkTarget, 0.0, 0.0, 0.0, false);
            for (int tick = 0; tick < AdversaryBalance.SCULK_DETONATION_DELAY_TICKS; tick++) {
                fox.tick(lane);
            }
            requireClose(220.0, fox.health(),
                    "Sculk recoil must stop exactly at twenty percent health.");

            SemionMonsterEntity secondSculkTarget = spawnMonster(
                    context,
                    lane,
                    "sculk-second",
                    source.position().add(-3.0, 0.0, 0.0)
            );
            secondSculkTarget.setNoAi(true);
            fox.onAttackResolved(source, secondSculkTarget, 0.0, 0.0, 0.0, false);
            for (int tick = 0; tick < AdversaryBalance.SCULK_DETONATION_DELAY_TICKS; tick++) {
                fox.tick(lane);
            }
            requireClose(220.0, fox.health(),
                    "Further Sculk blasts must not consume health below the floor.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Adversary high-ceiling combat failed: " + failure.getMessage()));
        } finally {
            group.closeRuntime();
            AdversaryProgressStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    private static TowerBalanceConfig teamEffectTestConfig(TowerBalanceConfig defaults) {
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        defaults.abilities().forEach((id, values) -> abilities.put(id, new LinkedHashMap<>(values)));
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("teamEffectScanIntervalTicks", 1.0);
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("teamEffectDurationTicks", 4.0);
        return new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities);
    }

    private static AdversaryFoxTower fox(UUID owner, int laneId, GridPosition position) {
        return new AdversaryFoxTower(AdversaryTowers.FOX, owner, TeamId.RED, laneId, position);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, TestTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, AdversaryFoxTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static SemionMonsterEntity monsterEntity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static SemionMonsterEntity spawnMonster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            Vec3 position
    ) {
        Monster monster = new Monster(
                id,
                lane.teamId(),
                lane.laneId(),
                Optional.empty(),
                Optional.empty(),
                1_000.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static PlayerLane testLane(
            GameTestHelper context,
            UUID owner,
            int laneId,
            int xOffset
    ) {
        BlockPos min = context.absolutePos(new BlockPos(xOffset, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(xOffset + 10, 5, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(xOffset + 1, 2, 1)));
        Vec3 waypoint = Vec3.atCenterOf(context.absolutePos(new BlockPos(xOffset + 5, 2, 8)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(xOffset + 5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(
                laneId,
                spawn,
                List.of(waypoint),
                boss,
                BlockBounds.of(min, max),
                List.of(position(context, xOffset + 7, 2, 11))
        );
        return new PlayerLane(TeamId.RED, laneId, owner, context.getLevel(), layout);
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual + '.');
        }
    }
}
