package kim.biryeong.semiontd.tower.gamble;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GambleGameTest {
    private static final List<TimedEffectType> SUPPORT_EFFECTS = List.of(
            TimedEffectType.TOWER_FLAT_RANGE_BONUS,
            TimedEffectType.TOWER_FLAT_RANGE_REDUCTION,
            TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND,
            TimedEffectType.TOWER_HEALTH_LOSS_PER_SECOND,
            TimedEffectType.TOWER_FLAT_DAMAGE_BONUS,
            TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION,
            TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS,
            TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION
    );

    @GameTest(maxTicks = 80)
    public void flatSupportStatsAndRegenerationApplyToTheRuntimeTower(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-flat-support-owner");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        GamblerTower target = gambler(owner, floor(context, 4, 2, 4));
        ResourceLocation rangeSource = supportTestSource("range");
        ResourceLocation regenerationSource = supportTestSource("regeneration");
        ResourceLocation damageSource = supportTestSource("damage");
        ResourceLocation healthSource = supportTestSource("health");
        try {
            lane.addTower(target);
            SemionTowerEntity entity = entity(lane, target);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_RANGE_BONUS, rangeSource, 0.5);
            entity.setPersistentEffect(TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND, regenerationSource, 5.0);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, damageSource, 5.0);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS, healthSource, 50.0);

            require(close(entity.attackRange(), 7.0), "A range roll must add exactly 0.5 blocks.");
            require(close(entity.attackDamageAmount(null), 15.0), "A damage roll must add exactly 5 damage.");
            require(close(target.currentMaxHealth(), 160.0), "A max-health roll must add exactly 50 health.");
            target.syncHealth(100.0);
            entity.setHealth(100.0F);

            context.runAfterDelay(20, () -> {
                try {
                    require(target.health() >= 104.75 && target.health() <= 105.25,
                            "Five health per second must heal about five health over twenty ticks: "
                                    + target.health());
                    entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_RANGE_BONUS, rangeSource, 0.0);
                    entity.setPersistentEffect(TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND, regenerationSource, 0.0);
                    entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, damageSource, 0.0);
                    entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS, healthSource, 0.0);
                    require(close(entity.attackRange(), 6.5), "Removing support must restore base range.");
                    require(close(entity.attackDamageAmount(null), 10.0), "Removing support must restore base damage.");
                    require(close(target.currentMaxHealth(), 110.0), "Removing support must restore base max health.");
                    context.succeed();
                } catch (Throwable failure) {
                    context.fail(Component.literal("Gamble flat support GameTest failed: "
                            + failure.getClass().getName() + ": " + failure.getMessage()));
                } finally {
                    group.closeRuntime();
                    TowerBalanceRuntime.apply(defaults);
                }
            });
        } catch (Throwable failure) {
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
            context.fail(Component.literal("Gamble flat support setup failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        }
    }

    @GameTest(maxTicks = 80)
    public void negativeSupportUsesFixedStatsAndHealthLossIsNonlethal(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-negative-support-owner");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        GamblerTower target = gambler(owner, floor(context, 4, 2, 4));
        try {
            lane.addTower(target);
            SemionTowerEntity entity = entity(lane, target);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_RANGE_REDUCTION,
                    supportTestSource("range-loss"), 0.25);
            entity.setPersistentEffect(TimedEffectType.TOWER_HEALTH_LOSS_PER_SECOND,
                    supportTestSource("health-loss"), 1.0);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION,
                    supportTestSource("damage-loss"), 2.5);
            entity.setPersistentEffect(TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION,
                    supportTestSource("max-health-loss"), 25.0);
            require(close(entity.attackRange(), 6.25), "Range weakening must subtract exactly 0.25 blocks.");
            require(close(entity.attackDamageAmount(null), 7.5), "Damage weakening must subtract exactly 2.5.");
            require(close(target.currentMaxHealth(), 85.0), "Max-health weakening must subtract exactly 25.");
            target.syncHealth(1.5);
            entity.setHealth(1.5F);
            context.runAfterDelay(20, () -> {
                try {
                    require(close(target.health(), 1.0),
                            "Health loss must stop at one health instead of killing the supported tower.");
                    context.succeed();
                } catch (Throwable failure) {
                    context.fail(Component.literal("Gamble negative support GameTest failed: "
                            + failure.getClass().getName() + ": " + failure.getMessage()));
                } finally {
                    group.closeRuntime();
                    TowerBalanceRuntime.apply(defaults);
                }
            });
        } catch (Throwable failure) {
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
            context.fail(Component.literal("Gamble negative support setup failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        }
    }

    @GameTest(maxTicks = 80)
    public void spectatorsChooseTheHighestScoreAndLimitThreeLinksPerGambler(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-spectator-cap-owner");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        GamblerTower strongest = gambler(owner, floor(context, 5, 2, 6));
        strongest.setData(GamblerTower.STATE, GambleState.EMPTY.recordStat(
                GambleStat.DAMAGE, 50, 10, 100, "strongest"));
        GamblerTower runnerUp = gambler(owner, floor(context, 7, 2, 6));
        runnerUp.setData(GamblerTower.STATE, GambleState.EMPTY.recordStat(
                GambleStat.DAMAGE, 25, 10, 50, "runner-up"));
        List<GambleSupportTower> spectators = List.of(
                support(GambleTowers.SPECTATOR_T3, owner, floor(context, 3, 2, 3)),
                support(GambleTowers.SPECTATOR_T3, owner, floor(context, 4, 2, 3)),
                support(GambleTowers.SPECTATOR_T3, owner, floor(context, 5, 2, 3)),
                support(GambleTowers.SPECTATOR_T3, owner, floor(context, 6, 2, 3))
        );
        try {
            lane.addTower(strongest);
            lane.addTower(runnerUp);
            spectators.forEach(lane::addTower);
            for (GambleSupportTower spectator : spectators) {
                require(close(spectator.currentMaxHealth(), 10.0),
                        "Every spectator tier must stay at ten health.");
                SemionTowerEntity source = entity(lane, spectator);
                require(GambleRoundEffects.assignSpectator(
                        lane, owner, GambleRoundEffects.sourceId(spectator), source, 20.0).isPresent(),
                        "Every spectator must find an available owned gambler.");
            }
            require(GambleRoundEffects.spectatorLinkCount(lane, owner, strongest.originalPosition()) == 3,
                    "Exactly three spectators must occupy the strongest gambler.");
            require(GambleRoundEffects.spectatorLinkCount(lane, owner, runnerUp.originalPosition()) == 1,
                    "The fourth spectator must fall back to the next-highest gamble score.");
            GambleRoundEffects.clearAll(lane, owner);
            require(GambleRoundEffects.spectatorLinkCount(lane, owner, strongest.originalPosition()) == 0
                            && GambleRoundEffects.spectatorLinkCount(
                            lane, owner, runnerUp.originalPosition()) == 0,
                    "Round cleanup must release every spectator assignment.");
            context.succeed();
        } finally {
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120)
    public void supportRollsAreOwnerFilteredStackBySourceAndLiveUntilRoundCleanup(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-support-owner");
        UUID otherOwner = stableUuid("gamble-support-other");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);

        GambleSupportTower dice = support(GambleTowers.DICE_T3, owner, floor(context, 3, 2, 3));
        GambleSupportTower spectator = support(GambleTowers.SPECTATOR_T3, owner, floor(context, 5, 2, 3));
        GamblerTower target = gambler(owner, floor(context, 4, 2, 5));
        GamblerTower foreign = gambler(otherOwner, floor(context, 4, 2, 4));
        try {
            lane.addTower(dice);
            lane.addTower(spectator);
            lane.addTower(target);
            lane.addTower(foreign);
            lane.markWaveStarted(1);

            require(dice.affectedTargets() == 1 && spectator.affectedTargets() == 1,
                    "Dice must support owned combat towers while spectators support only the owned gambler.");
            require(dice.linkedTargets() == 1 && spectator.linkedTargets() == 1,
                    "Every affected tower must have a visible connection from its support tower.");
            require(sum(dice.lastRollCounts()) == 1 && sum(spectator.lastRollCounts()) == 1,
                    "Each support tower must roll exactly one face per round, regardless of target count.");
            require(java.util.Arrays.stream(spectator.lastRollCounts()).sum() == 1,
                    "Every spectator tier must use the same one-through-six die.");

            var diceSource = GambleRoundEffects.sourceId(dice);
            var spectatorSource = GambleRoundEffects.sourceId(spectator);
            SemionTowerEntity diceEntity = entity(lane, dice);
            SemionTowerEntity spectatorEntity = entity(lane, spectator);
            SemionTowerEntity targetEntity = entity(lane, target);
            SemionTowerEntity foreignEntity = entity(lane, foreign);
            require(close(diceEntity.attackRange(), 0.0) && close(spectatorEntity.attackRange(), 0.0),
                    "Support entities must have no combat range and therefore never attack.");
            require(GambleRollLabels.hasVisibleLabel(lane, owner, dice)
                            && GambleRollLabels.hasVisibleLabel(lane, owner, spectator),
                    "Each support tower must display its round face above itself.");
            require(GambleRollLabels.count(lane, owner) == 2,
                    "The lane must keep one face label for each support tower.");
            require(sourceCount(targetEntity, diceSource) == dice.activeEffects().size()
                            && sourceCount(targetEntity, spectatorSource) == spectator.activeEffects().size(),
                    "The target must retain every independently sourced stat result from each support.");
            require(sourceCount(diceEntity, diceSource) == 0 && sourceCount(spectatorEntity, spectatorSource) == 0,
                    "Support towers must exclude themselves from their own roll.");
            require(sourceCount(diceEntity, spectatorSource) == 0
                            && sourceCount(spectatorEntity, diceSource) == 0,
                    "Support towers must never support each other and accidentally gain combat stats.");
            require(sourceCount(foreignEntity, diceSource) == 0 && sourceCount(foreignEntity, spectatorSource) == 0,
                    "A tower with another owner must never receive gamble support.");

            dice.onLaneCleared(lane);
            spectator.onLaneCleared(lane);
            require(GambleRollLabels.count(lane, owner) == 0,
                    "Floating faces must disappear as soon as the owner's lane is cleared.");
            require(sourceCount(targetEntity, diceSource) == dice.activeEffects().size()
                            && sourceCount(targetEntity, spectatorSource) == spectator.activeEffects().size(),
                    "Clearing the lane must hide the faces without ending surviving support effects early.");

            require(lane.killTower(dice), "The dice tower must be destroyable for persistence coverage.");
            require(sourceCount(targetEntity, diceSource) == 0
                            && sourceCount(targetEntity, spectatorSource) == spectator.activeEffects().size(),
                    "A destroyed support tower must immediately remove only its own result.");
            GambleRoundEffects.clearAll(lane, owner);
            require(GambleRollLabels.count(lane, owner) == 0,
                    "Round cleanup must remove every floating face label.");
            require(sourceCount(targetEntity, diceSource) == 0 && sourceCount(targetEntity, spectatorSource) == 0,
                    "Round cleanup must remove every gamble source exactly.");

            lane.resetForRound();
            lane.markWaveStarted(2);
            SemionTowerEntity nextTarget = entity(lane, target);
            require(sourceCount(nextTarget, diceSource) == dice.activeEffects().size()
                            && sourceCount(nextTarget, spectatorSource) == spectator.activeEffects().size(),
                    "The next round must produce fresh effects after the destroyed support respawns.");
            GambleRoundEffects.clearAll(lane, owner);
            require(sourceCount(nextTarget, diceSource) == 0 && sourceCount(nextTarget, spectatorSource) == 0,
                    "Elimination cleanup must share the exact round cleanup behavior.");
            context.succeed();
        } finally {
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120)
    public void rolledStatePreservesHealthRatioAndBasicSplashDamage(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        UUID owner = stableUuid("gamble-combat-owner");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        GamblerTower original = gambler(owner, floor(context, 4, 2, 3));
        SemionMonsterEntity primary = null;
        SemionMonsterEntity secondary = null;
        SemionMonsterEntity splashTarget = null;
        try {
            lane.addTower(original);
            GambleState upgradedState = new GambleState(
                    50.0, 35.0, 0.5, 0.5,
                    120.0, Set.of(), 4, "능력치 테스트"
            );
            original.setData(GamblerTower.STATE, upgradedState);
            original.syncMaxHealth(160.0, false);
            original.syncHealth(80.0);
            entity(lane, original).setHealth(80.0F);

            GamblerTower replacement = gambler(owner, original.position());
            replacement.copyFrom(original, 0);
            require(lane.replaceTower(original, replacement), "Self-upgrade replacement must succeed.");
            require(close(replacement.currentMaxHealth(), 160.0) && close(replacement.health(), 80.0),
                    "A fixed max-health upgrade must preserve the exact 50% health ratio.");
            require(close(replacement.adjustAttackRange(6.5), 7.0),
                    "The range result must add the rolled amount to the base range.");
            require(close(replacement.modifyAttackDamage(null, null, 10.0), 45.0),
                    "The damage result must add the rolled amount to the base damage.");
            require(close(replacement.splashRadius(), 2.5),
                    "The basic splash radius must remain fixed despite legacy rolled state.");

            SemionTowerEntity source = entity(lane, replacement);
            ResourceLocation supportDamage = supportTestSource("grown-damage-composition");
            source.setPersistentEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, supportDamage, 2.5);
            require(close(source.attackDamageAmount(null), 47.5),
                    "Fixed support damage must be added after the gambler's +35 growth without being multiplied.");
            source.setPersistentEffect(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS, supportDamage, 0.0);
            primary = spawnTarget(context, lane, source.position().add(0.0, 0.0, 2.0), "gamble-primary");
            splashTarget = spawnTarget(context, lane, primary.position().add(0.5, 0.0, 0.0), "gamble-splash");
            secondary = spawnTarget(context, lane, primary.position().add(2.75, 0.0, 0.0), "gamble-secondary");
            replacement.onAttackResolved(source, primary, 100.0, 100.0, 100.0, false);
            require(close(splashTarget.runtimeMonster().health(), 40.0),
                    "Every basic attack must deal 60% finalized damage inside the fixed splash radius.");
            require(close(secondary.runtimeMonster().health(), 100.0),
                    "A target outside the basic splash radius must not take splash damage.");
            context.succeed();
        } finally {
            if (primary != null) primary.discard();
            if (secondary != null) secondary.discard();
            if (splashTarget != null) splashTarget.discard();
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120)
    public void scoreThresholdPromotionsKeepStateAndEquipFinalFormItems(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-promotion-owner");
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        prepareFloor(context);
        GamblerTower kingCandidate = gambler(owner, floor(context, 4, 2, 3));
        GamblerTower darkCandidate = gambler(owner, floor(context, 8, 2, 3));
        try {
            lane.addTower(kingCandidate);
            lane.addTower(darkCandidate);
            GambleState kingState = new GambleState(
                    50.0, 5.0, 0.5, 0.0,
                    400.0, Set.of(GambleAbility.LOSS_INSURANCE), 12, "도박왕 전직 테스트"
            );
            GambleState darkState = new GambleState(
                    -20.0, -2.0, -0.5, 0.0,
                    -200.0, Set.of(), 4, "어둠의 도박왕 전직 테스트"
            );
            kingCandidate.setData(GamblerTower.STATE, kingState);
            darkCandidate.setData(GamblerTower.STATE, darkState);
            TowerUpgradeOption bet = ProductionTowerCatalog.upgrade(
                    GambleTowers.GAMBLER, GambleBet.ODD.upgradeId()).orElseThrow(() ->
                    new IllegalStateException("The gambler odd-bet upgrade is missing after catalog reload."));

            kingCandidate.onUpgradeCompleted(lane, kingCandidate, bet);
            darkCandidate.onUpgradeCompleted(lane, darkCandidate, bet);

            Tower kingTower = lane.towerAt(kingCandidate.position());
            Tower darkTower = lane.towerAt(darkCandidate.position());
            require(kingTower instanceof GamblerTower && kingTower.type().id().equals(GambleTowers.KING.id()),
                    "A +400 score gambler must become the Gamble King.");
            require(darkTower instanceof GamblerTower
                            && darkTower.type().id().equals(GambleTowers.DARK_KING.id()),
                    "A -200 score gambler must become the Dark Gamble King.");
            GamblerTower king = (GamblerTower) kingTower;
            GamblerTower dark = (GamblerTower) darkTower;
            require(king.state().equals(kingState) && dark.state().equals(darkState),
                    "Promotion must preserve every gamble stat, score, bet count, result, and ability.");
            require(close(king.currentMaxHealth(), 450.0) && close(dark.currentMaxHealth(), 420.0),
                    "Promoted base health must retain the previous fixed gamble deltas.");
            require(close(king.splashRadius(), 3.0) && close(dark.splashRadius(), 3.25),
                    "Final forms must gain their configured splash-radius bonuses.");
            require(entity(lane, king).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.DIAMOND),
                    "The Gamble King must hold a diamond.");
            require(entity(lane, dark).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.NETHERITE_INGOT),
                    "The Dark Gamble King must hold a netherite ingot.");
            context.succeed();
        } finally {
            group.closeRuntime();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 120)
    public void forcedMatchCloseClearsEconomyBeforeTheSamePlayerStartsAgain(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        UUID owner = stableUuid("gamble-close-lifecycle-owner");
        SemionGame first = null;
        SemionGame second = null;
        try {
            first = startedGambleGame(context, owner, "gamble-first-match");
            var firstPlayer = first.players().get(owner);
            firstPlayer.job().orElseThrow().onRoundStarted(new JobContext(first, firstPlayer), 1);
            var firstEconomy = firstPlayer.economy();
            long firstDiamond = firstEconomy.diamond();
            require(GambleSpectatorRewards.hasActiveEconomy(owner),
                    "Starting a gamble round must register the active economy.");

            first.close();
            first = null;
            require(!GambleSpectatorRewards.hasActiveEconomy(owner),
                    "Forced match close must remove the active gamble economy.");

            second = startedGambleGame(context, owner, "gamble-second-match");
            var secondPlayer = second.players().get(owner);
            secondPlayer.job().orElseThrow().onRoundStarted(new JobContext(second, secondPlayer), 1);
            long secondDiamond = secondPlayer.economy().diamond();
            require(GambleSpectatorRewards.awardFaceSix(
                    owner, GambleTowers.SPECTATOR_T1, 6) == 5,
                    "The second match must register a fresh economy for the same UUID.");
            require(firstEconomy.diamond() == firstDiamond,
                    "The closed first match economy must never receive the second match reward.");
            require(secondPlayer.economy().diamond() == secondDiamond + 5,
                    "The second match economy must receive the face-six reward exactly once.");

            second.close();
            second = null;
            require(!GambleSpectatorRewards.hasActiveEconomy(owner),
                    "Closing the second match must leave no active gamble economy.");
            context.succeed();
        } finally {
            if (first != null) first.close();
            if (second != null) second.close();
            GambleSpectatorRewards.closeRound(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 80)
    public void maximumScoreClosesEveryBetBeforeChargingDiamonds(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        UUID owner = stableUuid("gamble-score-cap-owner");
        SemionGame game = startedGambleGame(context, owner, "gamble-score-cap");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            GridPosition position = emptyPosition(lane);
            GamblerTower gambler = new GamblerTower(
                    TowerBalanceRuntime.resolve(GambleTowers.GAMBLER), owner, TeamId.RED, 1,
                    position, position);
            gambler.setData(GamblerTower.STATE, new GambleState(
                    10_000.0, 1_000.0, 100.0, 0.0,
                    500.0, Set.of(GambleAbility.LOSS_INSURANCE), 99, "최대 점수"
            ));
            lane.addTower(gambler);
            game.players().get(owner).economy().addMineral(1_000);
            long before = game.players().get(owner).economy().mineral();

            require(ProductionTowerService.availableUpgrades(game, owner, position).isEmpty(),
                    "All three bet buttons must disappear at the maximum score.");
            require(ProductionTowerService.upgradeTower(
                            game, owner, position, GambleBet.ODD.upgradeId())
                            == TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                    "A direct upgrade request must be rejected after the buttons close.");
            require(game.players().get(owner).economy().mineral() == before,
                    "Rejected capped gambling must not charge diamonds.");
            context.succeed();
        } finally {
            game.close();
            TowerBalanceRuntime.apply(defaults);
        }
    }

    @GameTest(maxTicks = 80)
    public void balanceReloadReclampsAndPromotesExistingGamblerWithoutCharging(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig legacy = withGambleScores(defaults, 1_000.0, 2_000.0);
        ProductionTowerCatalogs.reloadBuiltIns(legacy);
        UUID owner = stableUuid("gamble-reload-cap-owner");
        SemionGame game = startedGambleGame(context, owner, "gamble-reload-cap");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            GridPosition position = emptyPosition(lane);
            GamblerTower gambler = new GamblerTower(
                    TowerBalanceRuntime.resolve(GambleTowers.GAMBLER), owner, TeamId.RED, 1,
                    position, position);
            gambler.setData(GamblerTower.STATE, new GambleState(
                    4_000.0, 400.0, 40.0, 20.0,
                    600.0, Set.of(GambleAbility.LOSS_INSURANCE), 30, "이전 상한"
            ));
            lane.addTower(gambler);
            gambler.syncHealth(gambler.currentMaxHealth() * 0.5);
            game.players().get(owner).economy().addMineral(1_000);
            long before = game.players().get(owner).economy().mineral();

            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            game.refreshProductionTowerTypes();

            Tower refreshed = lane.towerAt(position);
            require(refreshed instanceof GamblerTower
                            && refreshed.type().id().equals(GambleTowers.KING.id()),
                    "Lowering the promotion threshold must promote an existing eligible gambler.");
            GamblerTower king = (GamblerTower) refreshed;
            require(close(king.state().cumulativeScore(), 500.0),
                    "Reload must clamp the displayed score to the new maximum.");
            require(close(king.state().maxHealthDelta(), 2_500.0)
                            && close(king.state().damageDelta(), 250.0)
                            && close(king.state().rangeDelta(), 25.0),
                    "Reload must clamp every stored positive stat to the new score-equivalent maximum.");
            require(close(king.health(), king.currentMaxHealth() * 0.5),
                    "Reload and promotion must preserve the current health ratio.");
            require(king.runtimeDetailLines().stream().anyMatch(line -> line.contains("+500.0 / +500.0")),
                    "Runtime details must immediately show the reloaded cap.");
            require(ProductionTowerService.availableUpgrades(game, owner, position).isEmpty(),
                    "Reload must immediately close every gamble button at the new cap.");
            require(ProductionTowerService.upgradeTower(
                            game, owner, position, GambleBet.TWO_DICE.upgradeId())
                            == TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                    "A direct bet request must be rejected after a cap-lowering reload.");
            require(game.players().get(owner).economy().mineral() == before,
                    "A rejected post-reload bet must not charge diamonds.");
            context.succeed();
        } finally {
            game.close();
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
        }
    }

    private static GambleSupportTower support(kim.biryeong.semiontd.tower.TowerType type,
                                               UUID owner, GridPosition position) {
        return new GambleSupportTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, position, position);
    }

    private static GamblerTower gambler(UUID owner, GridPosition position) {
        return new GamblerTower(TowerBalanceRuntime.resolve(GambleTowers.GAMBLER),
                owner, TeamId.RED, 1, position, position);
    }

    private static SemionGame startedGambleGame(
            GameTestHelper context, UUID owner, String playerName
    ) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, kim.biryeong.semiontd.job.GambleTowerJob.ID),
                "Gamble job selection must succeed.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, playerName, TeamId.RED, 1)),
                        Set.of(), 1
                )
        ), "Gamble test game must start.");
        return game;
    }

    private static GridPosition emptyPosition(PlayerLane lane) {
        var bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.min().getY(), z);
                GridPosition position = GridPosition.from(candidate);
                if (lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(position)) {
                    return position;
                }
            }
        }
        throw new AssertionError("No empty Gamble tower position was found.");
    }

    private static SemionMonsterEntity spawnTarget(
            GameTestHelper context, PlayerLane lane, Vec3 position, String id
    ) {
        Monster runtime = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(),
                100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Gamble target must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return entity;
    }

    private static int sourceCount(SemionTowerEntity entity, net.minecraft.resources.ResourceLocation source) {
        return (int) SUPPORT_EFFECTS.stream().filter(type -> entity.hasTimedEffectSource(type, source)).count();
    }

    private static ResourceLocation supportTestSource(String path) {
        return ResourceLocation.fromNamespaceAndPath("semion-td", "gamble/test/" + path);
    }

    private static int sum(int[] values) {
        return java.util.Arrays.stream(values).sum();
    }

    private static SemionTowerEntity entity(PlayerLane lane, Tower tower) {
        return GambleRoundEffects.towerEntity(tower, lane).orElseThrow(() ->
                new IllegalStateException("The runtime entity is missing for tower " + tower.type().id()
                        + " at " + tower.position() + "."));
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static GridPosition floor(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void prepareFloor(GameTestHelper context) {
        for (int x = 1; x <= 12; x++) {
            for (int z = 1; z <= 12; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 2, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static TowerBalanceConfig withGambleScores(
            TowerBalanceConfig defaults, double kingPromotionScore, double maxGambleScore
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> gamble = new LinkedHashMap<>(abilities.get(GambleBalance.GLOBAL_ID));
        gamble.put("kingPromotionScore", kingPromotionScore);
        gamble.put("maxGambleScore", maxGambleScore);
        abilities.put(GambleBalance.GLOBAL_ID, gamble);
        return new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.0001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
