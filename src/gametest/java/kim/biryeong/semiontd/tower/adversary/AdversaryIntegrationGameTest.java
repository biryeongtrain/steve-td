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
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
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
    public void supportFormsHealOwnedFoxesWhileOminousDebuffsTheTeamLane(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane sourceLane = testLane(context, BELL_OWNER, 1, 0);
        PlayerLane recipientLane = testLane(context, BEACON_OWNER, 2, 20);
        group.addLane(sourceLane);
        group.addLane(recipientLane);

        try {
            TowerBalanceRuntime.apply(teamEffectTestConfig(defaults));
            AdversaryFoxTower beacon = fox(BELL_OWNER, 1, position(context, 3, 2, 3));
            AdversaryFoxTower lowest = fox(BELL_OWNER, 1, position(context, 4, 2, 3));
            AdversaryFoxTower second = fox(BELL_OWNER, 1, position(context, 5, 2, 3));
            AdversaryFoxTower third = fox(BELL_OWNER, 1, position(context, 6, 2, 3));
            AdversaryFoxTower foreign = fox(BEACON_OWNER, 1, position(context, 7, 2, 3));
            AdversaryFoxTower ominous = fox(OMINOUS_OWNER, 2, position(context, 23, 2, 3));
            beacon.setForm(FoxForm.BEACON_KEEPER, sourceLane);
            ominous.setForm(FoxForm.OMINOUS_HEXER, recipientLane);
            TestTower recipient = new TestTower(
                    BEACON_OWNER,
                    TeamId.RED,
                    2,
                    position(context, 27, 2, 10)
            );
            sourceLane.addTower(beacon);
            sourceLane.addTower(lowest);
            sourceLane.addTower(second);
            sourceLane.addTower(third);
            sourceLane.addTower(foreign);
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
            SemionTowerEntity beaconEntity = towerEntity(context, beacon);
            SemionMonsterEntity monsterEntity = monsterEntity(context, monster);
            SemionMonsterEntity ownedRivalEntity = monsterEntity(context, ownedRival);
            recipientEntity.setNoAi(true);
            beaconEntity.setNoAi(true);
            monsterEntity.setNoAi(true);
            ownedRivalEntity.setNoAi(true);
            lowest.syncHealth(30.0);
            second.syncHealth(120.0);
            third.syncHealth(240.0);
            foreign.syncHealth(30.0);

            beacon.tick(sourceLane);
            ominous.tick(recipientLane);

            requireClose(
                    72.0,
                    lowest.health(),
                    "Beacon must heal the lowest-health-ratio owned fox for fourteen percent max health."
            );
            requireClose(
                    162.0,
                    second.health(),
                    "Beacon must heal up to two owned foxes."
            );
            requireClose(240.0, third.health(), "Beacon must stop after two targets.");
            requireClose(30.0, foreign.health(), "Beacon must not heal another player's fox.");
            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                    "Beacon must no longer grant team-wide damage."
            );
            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                    "Beacon must no longer grant team-wide attack speed."
            );
            requireClose(
                    0.0,
                    recipientEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS),
                    "Beacon must no longer grant team-wide max health."
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
            require(monsterEntity.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION) == 4,
                    "Team monster control must use the configured timed duration.");
            recipientLane.killTower(ominous);
            for (int tick = 0; tick < 4; tick++) {
                recipientEntity.aiStep();
                monsterEntity.aiStep();
            }

            requireClose(
                    0.0,
                    monsterEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Monster debuff must expire after the Ominous Hexer dies."
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
            require(monsterEntity.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION) == 0,
                    "Expired monster control must have no remaining duration.");
            context.succeed();
        } finally {
            group.closeRuntime();
            AdversaryTeamEffects.clearAllForTesting();
            AdversaryProgressStates.clearAllForTesting();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void replacementEvolutionPreservesLogicalFoxAndHealthRatio(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("adversary-live-form-transition");
        PlayerLane lane = testLane(context, owner, 1, 0);

        try {
            Map<String, Map<String, Double>> abilities = new LinkedHashMap<>();
            defaults.abilities().forEach((id, values) -> abilities.put(id, new LinkedHashMap<>(values)));
            abilities.get(AdversaryBalance.formConfigId(FoxForm.BELL_KEEPER)).put("maxHealth", 1_060.0);
            TowerBalanceRuntime.apply(new TowerBalanceConfig(
                    defaults.towers(),
                    defaults.upgradeCosts(),
                    abilities
            ));
            AdversaryProgressState progress = AdversaryProgressStates.state(owner);
            AdversaryFoxTower base = fox(owner, 1, position(context, 3, 2, 3));
            lane.addTower(base);
            UUID foxId = base.foxId();
            base.syncHealth(base.currentMaxHealth());

            progress.reconcileRivals(List.of(new RivalContribution(
                    stableUuid("adversary-live-form-phantom"),
                    RivalKind.PHANTOM,
                    14
            )));
            require(progress.canEvolve(foxId, FoxForm.BASE, FoxForm.BELL_KEEPER),
                    "Phantom score must unlock the Bell form.");
            TowerUpgradeOption option = new TowerUpgradeOption(
                    AdversaryTowers.typeFor(FoxForm.BELL_KEEPER).id(),
                    FoxForm.BELL_KEEPER.displayName(),
                    AdversaryTowers.resolvedTypeFor(FoxForm.BELL_KEEPER),
                    0L
            );
            AdversaryFoxTower fox = new AdversaryFoxTower(
                    option.targetType(),
                    owner,
                    TeamId.RED,
                    1,
                    base.originalPosition(),
                    base.position()
            );
            fox.copyFrom(base, 0L);
            require(lane.replaceTower(base, fox), "Evolution must replace the registered tower.");
            fox.onUpgradeCompleted(lane, base, option);
            SemionTowerEntity entity = towerEntity(context, fox);
            entity.setNoAi(true);

            require(fox.foxId().equals(foxId), "Evolution must keep the logical fox id.");
            require(fox.form() == FoxForm.BELL_KEEPER,
                    "The replacement must use the selected Bell form.");
            requireClose(FoxForm.BELL_KEEPER.maxHealth(), fox.currentMaxHealth(),
                    "Bell form must synchronize configured max health.");
            requireClose(1060.0, entity.getMaxHealth(),
                    "The live entity max-health attribute must exceed the vanilla 1024 cap.");
            requireClose(1.0, fox.health() / fox.currentMaxHealth(),
                    "Form synchronization must preserve the fox health ratio.");
            requireClose(1060.0, entity.getHealth(),
                    "The live entity must mirror logical health above the vanilla cap.");
            require(entity.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BELL),
                    "The live fox MAINHAND item must synchronize to Bell.");

            require(entity.hurtServer(context.getLevel(), entity.damageSources().generic(), 25.0F),
                    "Normal damage must reach the live Bell fox.");
            requireClose(1035.0, fox.health(),
                    "Normal damage must reduce logical health above the old cap.");
            requireClose(1035.0, entity.getHealth(),
                    "The entity must synchronize damage above the old cap.");

            require(entity.hurtServer(context.getLevel(), entity.damageSources().generic(), 25.0F),
                    "A second same-tick hit must not be blocked by virtual-health handling.");
            requireClose(1010.0, fox.health(),
                    "Same-tick normal damage must also reduce logical health.");
            requireClose(1010.0, entity.getHealth(),
                    "The second hit must remain synchronized across the old cap boundary.");

            entity.hurtIgnoringReductions(entity.damageSources().generic(), 990.0);
            requireClose(20.0, fox.health(),
                    "Defense-ignoring damage larger than entity HP must use logical HP.");
            requireClose(20.0, entity.getHealth(),
                    "The entity must survive when logical health survives the true hit.");
            requireClose(1040.0, fox.roundDamageTaken(),
                    "Damage statistics must include above-cap and true damage.");
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
    public void breezeChainUsesMagicDamageStatistics(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("adversary-breeze-magic");
        PlayerLane lane = testLane(context, owner, 1, 0);

        try {
            TowerBalanceRuntime.apply(defaults);
            AdversaryFoxTower fox = fox(owner, 1, position(context, 3, 2, 3));
            lane.addTower(fox);
            SemionTowerEntity source = towerEntity(context, fox);
            source.setNoAi(true);
            fox.setForm(FoxForm.BREEZE, lane);

            Vec3 center = source.position().add(3.0, 0.0, 0.0);
            SemionMonsterEntity primary = spawnMonster(context, lane, "breeze-primary", center);
            SemionMonsterEntity first = spawnMonster(context, lane, "breeze-first", center.add(0.0, 0.0, 0.5));
            SemionMonsterEntity second = spawnMonster(context, lane, "breeze-second", center.add(0.0, 0.0, 0.8));
            SemionMonsterEntity third = spawnMonster(context, lane, "breeze-third", center.add(0.0, 0.0, 1.1));
            SemionMonsterEntity fourth = spawnMonster(context, lane, "breeze-fourth", center.add(0.0, 0.0, 1.4));
            SemionMonsterEntity fifth = spawnMonster(context, lane, "breeze-fifth", center.add(0.0, 0.0, 1.7));
            primary.setNoAi(true);
            for (SemionMonsterEntity monster : List.of(first, second, third, fourth, fifth)) {
                monster.setNoAi(true);
            }

            fox.onAttackResolved(source, primary, 30.0, 30.0, 30.0, false);

            for (SemionMonsterEntity chained : List.of(first, second, third, fourth)) {
                requireClose(982.0, chained.runtimeMonster().health(),
                        "Breeze must deal sixty percent magic chain damage to four extra targets.");
            }
            requireClose(1_000.0, fifth.runtimeMonster().health(),
                    "Breeze chain must stop after four extra targets.");
            requireClose(72.0, fox.roundMagicDamageDealt(),
                    "All four Breeze chain hits must be recorded as magic.");
            requireClose(0.0, fox.roundPhysicalDamageDealt(),
                    "The manually resolved Breeze chain must not enter physical statistics.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Adversary Breeze magic chain failed: " + failure.getMessage()));
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void evolvedSplashAndFocusFireMitigationUseLiveTargets(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("adversary-evolved-sustain");
        PlayerLane lane = testLane(context, owner, 1, 0);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);

        try {
            TowerBalanceRuntime.apply(defaults);
            AdversaryFoxTower fox = fox(owner, 1, position(context, 3, 2, 3));
            lane.addTower(fox);
            SemionTowerEntity source = towerEntity(context, fox);
            source.setNoAi(true);
            fox.setForm(FoxForm.BEACON_KEEPER, lane);

            Vec3 center = source.position().add(3.0, 0.0, 0.0);
            SemionMonsterEntity primary = spawnMonster(context, lane, "evolved-primary", center);
            SemionMonsterEntity first = spawnMonster(context, lane, "evolved-first", center.add(0.0, 0.0, 0.4));
            SemionMonsterEntity second = spawnMonster(context, lane, "evolved-second", center.add(0.0, 0.0, -0.4));
            SemionMonsterEntity third = spawnMonster(context, lane, "evolved-third", center.add(0.0, 0.0, 0.8));
            SemionMonsterEntity fourth = spawnMonster(context, lane, "evolved-fourth", center.add(0.0, 0.0, -0.8));
            SemionMonsterEntity fifth = spawnMonster(context, lane, "evolved-fifth", center.add(0.0, 0.0, 1.0));
            for (SemionMonsterEntity monster : List.of(primary, first, second, third, fourth, fifth)) {
                monster.setNoAi(true);
            }
            for (SemionMonsterEntity monster : List.of(primary, first, second, third)) {
                monster.setTarget(source);
            }

            fox.onAttackResolved(source, primary, 90.0, 90.0, 90.0, false);

            List<SemionMonsterEntity> secondaries = List.of(first, second, third, fourth, fifth);
            require(secondaries.stream().filter(monster -> monster.runtimeMonster().health() < 1_000.0).count() == 3,
                    "Evolved splash must stop after three nearby targets.");
            requireClose(135.0, secondaries.stream()
                            .mapToDouble(monster -> 1_000.0 - monster.runtimeMonster().health())
                            .sum(),
                    "An evolved single-target form must deal fifty percent splash.");
            fox.setForm(FoxForm.BASE, lane);
            requireClose(88.0, fox.modifyIncomingDamage(source, null, 100.0),
                    "Four attackers targeting the fox must reduce incoming damage by twelve percent.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Adversary sustain combat failed: " + failure.getMessage()));
        } finally {
            group.closeRuntime();
            AdversaryProgressStates.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest
    public void maceSweepsTwoNearbyTargetsAndSculkRecoilStopsAtFortyPercent(GameTestHelper context) {
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

            requireClose(600.0, primary.runtimeMonster().health(),
                    "Mace must deal its full strike to the primary target.");
            requireClose(900.0, first.runtimeMonster().health(),
                    "Mace must sweep the nearest first target for 25% damage.");
            requireClose(900.0, second.runtimeMonster().health(),
                    "Mace must sweep the nearest second target for 25% damage.");
            requireClose(900.0, third.runtimeMonster().health(),
                    "Mace sweep must include nearby targets below the eight-target cap.");

            fox.setForm(FoxForm.SCULK_CORE, lane);
            fox.syncHealth(396.0);
            source.setHealth(396.0F);
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
            requireClose(320.0, fox.health(),
                    "Sculk recoil must stop exactly at forty percent health.");

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
            requireClose(320.0, fox.health(),
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
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("bellHealIntervalTicks", 1.0);
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("beaconHealIntervalTicks", 1.0);
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
