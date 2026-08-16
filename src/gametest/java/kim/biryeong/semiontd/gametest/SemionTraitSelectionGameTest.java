package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSelectionSnapshot;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.tower.undead.UndeadDrownedTower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class SemionTraitSelectionGameTest {
    @GameTest
    public void traitSelectionLocksBuiltInLoadoutsBeforeStarting(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("trait-enabled-red");
        UUID blueId = playerId("trait-enabled-blue");
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = syntheticGame(context, economy);
        SemionGameManager manager = managerWithActiveGame(game);
        ParticipantSelectionPlan plan = twoPlayerPlan(redId, "trait-enabled-red", blueId, "trait-enabled-blue");

        if (!assertEquals(context, SemionGameManager.StartCountdownResult.SCHEDULED, manager.scheduleStart(server, plan), "Manager should schedule trait selection.")) {
            return;
        }
        if (!assertTrue(context, manager.traitsEnabled(), "Trait feature should be enabled.")) {
            return;
        }
        if (!assertTrue(context, manager.traitSelectionActive(), "Built-in traits should open the trait-selection phase.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.SELECTED,
                manager.selectTrait(server, redId, TraitSlot.PRIMARY, BuiltInTraits.MOBILIZATION_GRANT_ID),
                "Red player should select Gold Spoon as primary.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.DUPLICATE_TRAIT,
                manager.selectTrait(server, redId, TraitSlot.SECONDARY, BuiltInTraits.MOBILIZATION_GRANT_ID),
                "The same non-none trait should not fill both slots.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.SELECTED,
                manager.selectTrait(server, redId, TraitSlot.SECONDARY, BuiltInTraits.RAPID_DEPLOYMENT_ID),
                "Red player should select Rapid Deployment as secondary.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.SELECTED,
                manager.selectTrait(server, blueId, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID),
                "None should be valid for the primary slot.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.SELECTED,
                manager.selectTrait(server, blueId, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID),
                "None should be valid for the secondary slot.")) {
            return;
        }
        TraitLoadout expectedRedLoadout = new TraitLoadout(
                BuiltInTraits.MOBILIZATION_GRANT_ID,
                BuiltInTraits.RAPID_DEPLOYMENT_ID
        );
        if (!assertTrue(context, !manager.traitSelectionActive(), "Completing all loadouts should close trait selection.")) {
            return;
        }
        if (!assertTrue(context, manager.startCountdownActive(), "Completing all loadouts should begin the normal countdown.")) {
            return;
        }
        if (!assertEquals(context, expectedRedLoadout, manager.traitLoadoutOrDefault(redId), "Pending countdown should preserve the selected loadout.")) {
            return;
        }

        for (int i = 0; i < SemionGameManager.START_COUNTDOWN_TICKS; i++) {
            manager.tick(server);
        }
        if (!assertTrue(context, game.rosterLocked(), "Game should start after the normal countdown without waiting for trait selection.")) {
            return;
        }
        if (!assertEquals(context, expectedRedLoadout, game.players().get(redId).traitLoadout(), "The selected loadout should be locked into the match.")) {
            return;
        }
        if (!assertEquals(
                context,
                economy.startingDiamond() + TraitEffects.startingMineralBonus(expectedRedLoadout),
                game.players().get(redId).economy().diamond(),
                "Gold Spoon should add its configured starting diamond."
        )) {
            return;
        }
        PlayerLane lane = game.teams().get(TeamId.RED).laneGroup().lane(1).orElseThrow();
        GridPosition anchor = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        TestTower first = new TestTower(TestTowerTypes.TEST_DIRECT, redId, TeamId.RED, 1, anchor);
        TestTower second = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                redId,
                TeamId.RED,
                1,
                new GridPosition(anchor.x() + 1, anchor.y(), anchor.z())
        );
        TestTower different = new TestTower(
                TestTowerTypes.TEST_GUARD,
                redId,
                TeamId.RED,
                1,
                new GridPosition(anchor.x() + 2, anchor.y(), anchor.z())
        );
        lane.addTower(first);
        lane.addTower(second);
        lane.addTower(different);

        SemionTowerEntity firstEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(first.entityId().orElseThrow());
        TraitLoadout roundTraits = new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.DIVERSITY_ID
        );
        lane.assignTraitLoadout(roundTraits);
        if (!assertDoubleEquals(context, 0.0, firstEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), "Round traits should wait for wave start.")) {
            return;
        }
        lane.markWaveStarted(2);
        double firstWaveBonus = TraitEffects.sameTypeDamageBonus(roundTraits, lane, first)
                + TraitEffects.diversityDamageBonus(roundTraits, lane, first);
        if (!assertDoubleEquals(context, firstWaveBonus,
                firstEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Round traits should use the configured same-type and diversity bonuses.")) {
            return;
        }
        if (!assertTrue(context, firstEntity.hasPersistentEffect(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), "Round trait damage should be persistent.")) {
            return;
        }

        TestTower thirdSame = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                redId,
                TeamId.RED,
                1,
                new GridPosition(anchor.x() + 3, anchor.y(), anchor.z())
        );
        lane.addTower(thirdSame);
        SemionTowerEntity thirdEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(thirdSame.entityId().orElseThrow());
        if (!assertDoubleEquals(context, 0.0, thirdEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS), "A mid-wave tower should wait for the next snapshot.")) {
            return;
        }
        if (!assertDoubleEquals(context, firstWaveBonus,
                firstEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "The current wave snapshot should remain unchanged.")) {
            return;
        }
        lane.markWaveStarted(3);
        double nextWaveBonus = TraitEffects.sameTypeDamageBonus(roundTraits, lane, first)
                + TraitEffects.diversityDamageBonus(roundTraits, lane, first);
        if (!assertDoubleEquals(context, nextWaveBonus,
                firstEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "The next wave should count the updated tower snapshot.")) {
            return;
        }
        if (!assertDoubleEquals(context, nextWaveBonus,
                thirdEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "The next wave snapshot should include the new tower.")) {
            return;
        }

        TraitLoadout fortitude = new TraitLoadout(BuiltInTraits.FORTITUDE_ID, BuiltInTraits.NONE_ID);
        lane.assignTraitLoadout(fortitude);
        double fortifiedMaxHealth = first.type().maxHealth()
                * (1.0 + TraitEffects.towerMaxHealthBonus(fortitude, first));
        if (!assertDoubleEquals(context, fortifiedMaxHealth, first.currentMaxHealth(),
                "Fortitude should add its configured max health to a normal tower.")) {
            return;
        }
        if (!assertDoubleEquals(context, fortifiedMaxHealth, first.health(),
                "Attaching Fortitude should heal the granted max-health amount.")) {
            return;
        }

        TraitLoadout doubleEdgedIncome = new TraitLoadout(
                BuiltInTraits.DOUBLE_EDGED_SWORD_ID,
                BuiltInTraits.INTERCEPTION_DOCTRINE_ID
        );
        lane.assignTraitLoadout(doubleEdgedIncome);
        double outgoingBonus = TraitEffects.doubleEdgedOutgoingDamageBonus(doubleEdgedIncome);
        double incomingBonus = TraitEffects.doubleEdgedIncomingDamageBonus(doubleEdgedIncome);
        double incomeBonus = TraitEffects.incomeTargetDamageBonus(doubleEdgedIncome);
        Monster incomeMonster = new Monster(
                "trait-income-target",
                TeamId.RED,
                1,
                Optional.of(redId),
                Optional.of(TeamId.BLUE),
                1_000.0,
                0.0,
                10.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        if (!assertDoubleEquals(context, 100.0 * (1.0 + outgoingBonus), firstEntity.applyTraitOutgoingDamage(null, 100.0), "Target-independent preview should include final damage.")) {
            return;
        }
        double expectedIncomeDamage = 100.0 * (1.0 + incomeBonus) * (1.0 + outgoingBonus);
        if (!assertDoubleEquals(context, expectedIncomeDamage, firstEntity.applyTraitOutgoingDamage(incomeMonster, 100.0), "Income and final damage should preserve their multiplication order.")) {
            return;
        }
        if (!assertDoubleEquals(context, 100.0 * (1.0 + incomingBonus), firstEntity.applyTraitIncomingDamage(100.0), "Incoming trait damage should use the visible persistent effect.")) {
            return;
        }
        double baseDisplayedDamage = firstEntity.attackDamageAmount(null);
        if (!assertDoubleEquals(
                context,
                baseDisplayedDamage * (1.0 + outgoingBonus),
                firstEntity.applyTraitOutgoingDamage(null, baseDisplayedDamage),
                "Right-click damage should include target-independent trait damage."
        )) {
            return;
        }
        SemionMonsterEntity incomeEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
        incomeEntity.configureFrom(incomeMonster, lane.laneLayout());
        incomeEntity.setPos(firstEntity.position().add(1.0, 0.0, 0.0));
        lane.arenaWorld().addFreshEntity(incomeEntity);
        first.damageTarget(firstEntity, incomeEntity, 100.0);
        if (!assertDoubleEquals(context, 1_000.0 - expectedIncomeDamage, incomeMonster.health(), "The common damage path should apply trait damage exactly once.")) {
            return;
        }

        first.recordPlacementEconomy(100L, 1);
        first.markWaveStarted(1);
        TraitLoadout rapidDeployment = new TraitLoadout(
                BuiltInTraits.NONE_ID,
                BuiltInTraits.RAPID_DEPLOYMENT_ID
        );
        lane.assignTraitLoadout(rapidDeployment);
        if (!assertEquals(context, Math.round(100L * TraitEffects.sellRefundRate(rapidDeployment, true)),
                first.sellRefundAmount(), "Secondary Rapid Deployment should use its configured refund rate.")) {
            return;
        }
        TraitLoadout openingSalvo = new TraitLoadout(BuiltInTraits.OPENING_SALVO_ID, BuiltInTraits.NONE_ID);
        lane.assignTraitLoadout(openingSalvo);
        lane.markWaveStarted(4);
        if (!assertDoubleEquals(
                context,
                TraitEffects.openingAttackSpeedBonus(openingSalvo),
                firstEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Speedrunner should apply its configured attack-speed effect at wave start."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void doubleEdgedSwordDoesNotBypassDrownedLastStand(GameTestHelper context) {
        UUID ownerId = playerId("double-edged-drowned");
        UUID blueId = playerId("double-edged-drowned-blue");
        SemionGame game = syntheticGame(context, EconomyConfig.defaultConfig());
        TraitLoadout doubleEdged = new TraitLoadout(
                BuiltInTraits.DOUBLE_EDGED_SWORD_ID,
                BuiltInTraits.NONE_ID
        );
        if (!assertTrue(
                context,
                game.start(
                        context.getLevel().getServer(),
                        twoPlayerPlan(ownerId, "double-edged-drowned", blueId, "double-edged-drowned-blue"),
                        new TraitSelectionSnapshot(Map.of(ownerId, doubleEdged, blueId, TraitLoadout.none()))
                ),
                "Double-Edged Sword Drowned fixture should start."
        )) {
            return;
        }
        PlayerLane lane = game.teams().get(TeamId.RED).laneGroup().lane(1).orElseThrow();
        GridPosition anchor = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        UndeadDrownedTower drowned = new UndeadDrownedTower(
                UndeadTowers.T3_ZOMBIE_TOWER,
                ownerId,
                TeamId.RED,
                1,
                anchor
        );
        lane.addTower(drowned);

        SemionTowerEntity entity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(drowned.entityId().orElseThrow());
        float lethalDamage = entity.getHealth();
        context.hurt(entity, entity.damageSources().generic(), lethalDamage);
        if (!assertDoubleEquals(context, 1.0, entity.getHealth(), "Drowned Last Stand should survive lethal Double-Edged Sword damage at one health.")) {
            return;
        }

        context.hurt(entity, entity.damageSources().generic(), lethalDamage);
        if (!assertDoubleEquals(context, 1.0, entity.getHealth(), "Drowned should ignore damage during Last Stand even with Double-Edged Sword.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void transcendenceBuffsLivingTowersAndClonesAtConfiguredDelay(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("transcendence-red");
        UUID blueId = playerId("transcendence-blue");
        SemionGame game = syntheticGame(context, EconomyConfig.defaultConfig());
        TraitLoadout transcendence = new TraitLoadout(
                BuiltInTraits.TRANSCENDENCE_ID,
                BuiltInTraits.NONE_ID
        );
        TraitLoadout secondaryTranscendence = new TraitLoadout(
                BuiltInTraits.NONE_ID,
                BuiltInTraits.TRANSCENDENCE_ID
        );
        if (!assertTrue(
                context,
                game.start(
                        server,
                        twoPlayerPlan(redId, "transcendence-red", blueId, "transcendence-blue"),
                        new TraitSelectionSnapshot(Map.of(
                                redId, transcendence,
                                blueId, secondaryTranscendence
                        ))
                ),
                "Trait fixture game should start."
        )) {
            return;
        }

        PlayerLane lane = game.teams().get(TeamId.RED).laneGroup().lane(1).orElseThrow();
        PlayerLane blueLane = game.teams().get(TeamId.BLUE).laneGroup().lane(1).orElseThrow();
        GridPosition anchor = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        GridPosition blueAnchor = blueLane.laneLayout().finalDefenseTowerSlots().getFirst();
        TestTower living = new TestTower(TestTowerTypes.TEST_DIRECT, redId, TeamId.RED, 1, anchor);
        TestTower secondary = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                blueId,
                TeamId.BLUE,
                1,
                blueAnchor
        );
        TestTower dead = new TestTower(
                TestTowerTypes.TEST_GUARD,
                redId,
                TeamId.RED,
                1,
                new GridPosition(anchor.x() + 1, anchor.y(), anchor.z())
        );
        lane.addTower(living);
        lane.addTower(dead);
        blueLane.addTower(secondary);
        dead.syncHealth(0.0);

        TestTower cloneRuntime = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                redId,
                TeamId.RED,
                1,
                new GridPosition(anchor.x() + 2, anchor.y(), anchor.z())
        );
        cloneRuntime.attachToLane(lane, lane.traitLoadout());
        SemionTowerEntity clone = new SemionTowerEntity(SemionEntityTypes.TOWER, lane.arenaWorld());
        clone.configure(cloneRuntime, lane.laneLayout());
        clone.markIllusionClone();
        clone.setPos(anchor.x() + 2.5, anchor.y() + 1.0, anchor.z() + 0.5);
        lane.arenaWorld().addFreshEntity(clone);

        SemionTowerEntity livingEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(living.entityId().orElseThrow());
        SemionTowerEntity deadEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(dead.entityId().orElseThrow());
        SemionTowerEntity secondaryEntity = (SemionTowerEntity) blueLane.arenaWorld()
                .getEntity(secondary.entityId().orElseThrow());
        int activationDelayTicks = TraitEffects.transcendenceActivationDelayTicks();
        double primaryBonus = TraitEffects.transcendenceDamageBonus(transcendence);
        double secondaryBonus = TraitEffects.transcendenceDamageBonus(secondaryTranscendence);
        lane.moveTowersToFinalDefense();
        blueLane.moveTowersToFinalDefense();
        lane.tick(server, null, Map.of(), MonsterScalingConfig.defaultConfig(), activationDelayTicks - 1);
        blueLane.tick(server, null, Map.of(), MonsterScalingConfig.defaultConfig(), activationDelayTicks - 1);
        if (!assertDoubleEquals(
                context,
                0.0,
                livingEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Transcendence should not activate before its configured delay."
        )) {
            return;
        }

        lane.tick(server, null, Map.of(), MonsterScalingConfig.defaultConfig(), activationDelayTicks);
        blueLane.tick(server, null, Map.of(), MonsterScalingConfig.defaultConfig(), activationDelayTicks);
        if (!assertDoubleEquals(
                context,
                primaryBonus,
                livingEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "A living final-defense tower should gain configured trait damage."
        ) || !assertDoubleEquals(
                context,
                primaryBonus,
                clone.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "A living illusion clone should gain Transcendence."
        ) || !assertDoubleEquals(
                context,
                0.0,
                deadEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "A dead tower should not gain Transcendence."
        ) || !assertDoubleEquals(
                context,
                100.0 * (1.0 + primaryBonus),
                livingEntity.applyTraitOutgoingDamage(null, 100.0),
                "Transcendence should use the shared trait-damage path."
        ) || !assertDoubleEquals(
                context,
                secondaryBonus,
                secondaryEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Secondary Transcendence should grant half the configured trait damage."
        ) || !assertDoubleEquals(
                context,
                100.0 * (1.0 + secondaryBonus),
                secondaryEntity.applyTraitOutgoingDamage(null, 100.0),
                "Secondary Transcendence should use the shared trait-damage path."
        )) {
            return;
        }
        lane.clearTranscendence();
        blueLane.clearTranscendence();
        if (!assertDoubleEquals(
                context,
                0.0,
                livingEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Defense end should remove Transcendence from registered towers."
        ) || !assertDoubleEquals(
                context,
                0.0,
                clone.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Defense end should remove Transcendence from clones."
        ) || !assertDoubleEquals(
                context,
                0.0,
                secondaryEntity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS),
                "Defense end should remove secondary Transcendence."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void traitSelectionCanBeDisabledByConfig(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("trait-config-disabled-red");
        UUID blueId = playerId("trait-config-disabled-blue");
        SemionGame game = syntheticGame(context, EconomyConfig.defaultConfig());
        SemionGameManager manager = managerWithActiveGame(game);
        manager.configureTraits(new TraitSelectionConfig(false, 45));

        if (!assertEquals(context, SemionGameManager.StartCountdownResult.SCHEDULED,
                manager.scheduleStart(server, twoPlayerPlan(redId, "trait-config-disabled-red", blueId, "trait-config-disabled-blue")),
                "Disabled traits should schedule the normal countdown.")) {
            return;
        }
        if (!assertTrue(context, !manager.traitsEnabled(), "Trait config should disable the feature.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.DISABLED,
                manager.selectTrait(server, redId, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID),
                "Disabled traits should reject selection requests.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void traitCommandsRemainParseableWithoutConfiguredTraits(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        if (!assertTrue(context, dispatcher.getRoot().getChild("특성") != null, "Expected /특성 alias to remain registered.")) {
            return;
        }
        var source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of(
                "특성",
                "semiontd trait",
                "semiontd trait current",
                "semiontd trait list",
                "semiontd trait ui primary",
                "semiontd trait ui secondary",
                "특성 ui primary",
                "semiontd trait select primary none",
                "semiontd trait select secondary none"
        )) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected disabled trait command to parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void traitCommandTreeRegistersKoreanAlias(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        if (!assertTrue(context, dispatcher.getRoot().getChild("특성") != null, "Expected /특성 alias to be registered.")) {
            return;
        }
        var source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of(
                "특성",
                "semiontd trait",
                "semiontd trait current",
                "semiontd trait list",
                "semiontd trait ui primary",
                "semiontd trait ui secondary",
                "특성 ui primary",
                "semiontd trait select primary none",
                "semiontd trait select secondary none"
        )) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected command to parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void startedGameReportsAppliedTraitLoadout(GameTestHelper context) {
        UUID playerId = playerId("applied-trait-display");
        SemionGame game = syntheticGame(context, EconomyConfig.defaultConfig());
        TraitLoadout applied = new TraitLoadout(
                BuiltInTraits.MOBILIZATION_GRANT_ID,
                BuiltInTraits.RAPID_DEPLOYMENT_ID
        );
        SemionPlayer player = new SemionPlayer(
                playerId,
                "applied-trait-display",
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        player.assignTraitLoadout(applied);
        game.players().put(playerId, player);

        if (!assertEquals(context, applied, game.selectedTraitLoadoutOrDefault(playerId),
                "Started games should report the loadout actually assigned to the player.")) {
            return;
        }
        context.succeed();
    }

    private static SemionGame syntheticGame(GameTestHelper context, EconomyConfig economyConfig) {
        return new SemionGame(economyConfig, WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
    }

    private static SemionGameManager managerWithActiveGame(SemionGame game) {
        SemionGameManager manager = new SemionGameManager();
        try {
            var field = SemionGameManager.class.getDeclaredField("activeGame");
            field.setAccessible(true);
            field.set(manager, game);
            return manager;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to set active game for trait selection test.", exception);
        }
    }

    private static ParticipantSelectionPlan twoPlayerPlan(UUID redId, String redName, UUID blueId, String blueName) {
        return new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, redName, TeamId.RED, 1),
                        new AssignedParticipant(blueId, blueName, TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
    }

    private static UUID playerId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }

    private static boolean assertDoubleEquals(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.000_001) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
