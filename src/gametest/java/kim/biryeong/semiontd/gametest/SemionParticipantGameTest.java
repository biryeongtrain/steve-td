package kim.biryeong.semiontd.gametest;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.faboslav.friendsandfoes.common.entity.MoobloomEntity;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideIndicatorService;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.goal.BossAttackLaneMonsterGoal;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntityState;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisualApplierRegistry;
import kim.biryeong.semiontd.entity.visual.MoobloomVisual;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.mixin.accessor.MoobloomAccessor;
import kim.biryeong.semiontd.entity.visual.SlimeVisual;
import kim.biryeong.semiontd.mixin.accessor.SlimeAccessor;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.PlayerTeleportTransitions;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerProtectionService;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.StartPlacement;
import kim.biryeong.semiontd.game.StartCandidate;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.game.TeamSizeBalancePolicy;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.game.VanillaTeamBridge;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.LegionTowerJob;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.job.ResonanceTowerJob;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicResourcePack;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.music.SemionMusicTrack;
import kim.biryeong.semiontd.placeholder.SemionPlaceholders;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileMatchResultRepository;
import kim.biryeong.semiontd.persistence.SemionPersistenceBackendType;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.animal.AnimalTowerCatalogs;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.FoxTower;
import kim.biryeong.semiontd.tower.animal.PigTower;
import kim.biryeong.semiontd.tower.animal.RabbitTower;
import kim.biryeong.semiontd.tower.animal.WolfTower;
import kim.biryeong.semiontd.tower.illager.IllagerTower;
import kim.biryeong.semiontd.tower.illager.IllagerMarks;
import kim.biryeong.semiontd.tower.illager.IllagerRaidState;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowerCatalogs;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.undead.UndeadAnimalTower;
import kim.biryeong.semiontd.tower.undead.UndeadDrownedTower;
import kim.biryeong.semiontd.tower.undead.UndeadHuskTower;
import kim.biryeong.semiontd.tower.undead.UndeadMeleeSkeletonTower;
import kim.biryeong.semiontd.tower.undead.UndeadRangedSkeletonTower;
import kim.biryeong.semiontd.tower.undead.UndeadTowerCatalogs;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.undead.UndeadZombieTower;
import kim.biryeong.semiontd.tower.legion.BeeTower;
import kim.biryeong.semiontd.tower.legion.IllusionCloneSpawnQueue;
import kim.biryeong.semiontd.tower.legion.IllusionProfile;
import kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower;
import kim.biryeong.semiontd.tower.legion.IllusionSummonerTower;
import kim.biryeong.semiontd.tower.legion.LegionGlobalIllusionTower;
import kim.biryeong.semiontd.tower.legion.LegionGoatTower;
import kim.biryeong.semiontd.tower.legion.LegionParrotTower;
import kim.biryeong.semiontd.tower.legion.LegionSlimeTower;
import kim.biryeong.semiontd.tower.legion.LegionTowerCatalogs;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.nether.NetherTower;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTower;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.ocean.OceanWaterTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.resonance.ResonanceTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.villager.AllayTower;
import kim.biryeong.semiontd.tower.villager.AntiTankerCatTower;
import kim.biryeong.semiontd.tower.villager.LaneClearCatTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowerCatalogs;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockSacrificeTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSelectionSnapshot;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionDisplayHudService;
import kim.biryeong.semiontd.ui.SemionHudTextService;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public final class SemionParticipantGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void teleportTransitionPreservesYawAndPitch(GameTestHelper context) {
        TeleportTransition transition = PlayerTeleportTransitions.preservingRotation(
                context.getLevel(),
                new Vec3(1.0, 2.0, 3.0),
                Vec3.ZERO,
                135.0F,
                -30.0F
        );

        if (!assertEquals(context, 135.0F, transition.yRot(), "Teleport should preserve head yaw as yRot.")) {
            return;
        }
        if (!assertEquals(context, -30.0F, transition.xRot(), "Teleport should preserve pitch as xRot.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void moobloomTowerSpawnsRealMoobloomOverlayVisual(GameTestHelper context) {
        UUID playerId = UUID.nameUUIDFromBytes("gametest-moobloom-visual".getBytes(StandardCharsets.UTF_8));
        BlockPos anchor = context.absolutePos(BlockPos.ZERO);
        TowerType type = new TowerType(
                "moobloom_visual_probe",
                "Moobloom Visual Probe",
                TowerCategory.DIRECT,
                0,
                50.0,
                4.0,
                1.0,
                20,
                0,
                MoobloomVisual.builder().variant("dandelion").build(),
                List.of()
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(new TestTower(type, playerId, TeamId.RED, 1, GridPosition.from(anchor)), null);
        towerEntity.setNoAi(true);
        towerEntity.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        context.getLevel().addFreshEntity(towerEntity);

        context.runAfterDelay(2, () -> {
            List<MoobloomEntity> visuals = context.getLevel().getEntitiesOfClass(
                    MoobloomEntity.class,
                    new AABB(anchor).inflate(2.0)
            );
            if (!assertEquals(context, 1, visuals.size(), "Moobloom tower should spawn one real Moobloom visual overlay entity.")) {
                return;
            }
            MoobloomEntity visual = visuals.getFirst();
            if (!assertClose(context, 0.75, towerEntity.getScale(), "Moobloom tower should use a shorter server collision box.")) {
                return;
            }
            if (!assertClose(context, 1.35, towerEntity.getBbHeight(), "Moobloom tower collision height should match its visual height.")) {
                return;
            }
            if (!assertEquals(context, "dandelion", visual.getEntityData().get(MoobloomAccessor.semiontd$dataVariant()), "Moobloom visual should carry the tower variant for the Polymer patch.")) {
                return;
            }
            if (!assertTrue(context, towerEntity.ownsMoobloomVisualEntity(visual), "Moobloom visual should be linked back to the tower for right-click UI resolution.")) {
                return;
            }
            if (!assertClose(context, towerEntity.getX(), visual.getX(), "Moobloom visual X should stay on the tower hitbox anchor.")) {
                return;
            }
            if (!assertClose(context, towerEntity.getY(), visual.getY(), "Moobloom visual Y should stay on the tower hitbox anchor.")) {
                return;
            }
            if (!assertClose(context, towerEntity.getZ(), visual.getZ(), "Moobloom visual Z should stay on the tower hitbox anchor.")) {
                return;
            }
            if (!assertTrue(context, visual.isNoAi() && visual.isInvulnerable() && visual.noPhysics, "Moobloom visual should be passive cosmetic state only.")) {
                return;
            }
            towerEntity.discard();
            context.runAfterDelay(1, () -> {
                List<MoobloomEntity> remaining = context.getLevel().getEntitiesOfClass(
                        MoobloomEntity.class,
                        new AABB(anchor).inflate(2.0)
                );
                if (!assertTrue(context, remaining.isEmpty(), "Removing the tower should also remove its Moobloom visual overlay entity.")) {
                    return;
                }
                context.succeed();
            });
        });
    }

    @GameTest
    public void moobloomTowerSkipsStaticVisualTeleportResync(GameTestHelper context) {
        UUID playerId = UUID.nameUUIDFromBytes("gametest-moobloom-visual-static-sync".getBytes(StandardCharsets.UTF_8));
        BlockPos anchor = context.absolutePos(BlockPos.ZERO);
        TowerType type = new TowerType(
                "moobloom_visual_static_sync_probe",
                "Moobloom Visual Static Sync Probe",
                TowerCategory.DIRECT,
                0,
                50.0,
                4.0,
                1.0,
                20,
                0,
                MoobloomVisual.builder().variant("sunflower").build(),
                List.of()
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(new TestTower(type, playerId, TeamId.RED, 1, GridPosition.from(anchor)), null);
        towerEntity.setNoAi(true);
        towerEntity.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        context.getLevel().addFreshEntity(towerEntity);

        context.runAfterDelay(2, () -> {
            List<MoobloomEntity> visuals = context.getLevel().getEntitiesOfClass(
                    MoobloomEntity.class,
                    new AABB(anchor).inflate(3.0)
            );
            if (!assertEquals(context, 1, visuals.size(), "Moobloom tower should spawn one visual before static sync check.")) {
                return;
            }
            MoobloomEntity visual = visuals.getFirst();
            double shiftedX = visual.getX() + 0.75;
            visual.teleportTo(shiftedX, visual.getY(), visual.getZ());

            context.runAfterDelay(2, () -> {
                if (!assertClose(context, shiftedX, visual.getX(), "Static Moobloom visual should not be teleported again while the owning tower has not moved.")) {
                    return;
                }
                towerEntity.teleportTo(towerEntity.getX() + 1.0, towerEntity.getY(), towerEntity.getZ());
                context.runAfterDelay(1, () -> {
                    if (!assertClose(context, towerEntity.getX(), visual.getX(), "Moobloom visual should resync when the owning tower position changes.")) {
                        return;
                    }
                    towerEntity.discard();
                    context.succeed();
                });
            });
        });
    }

    private static kim.biryeong.semiontd.map.GameArena testArena(GameTestHelper context) {
        return SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(net.minecraft.core.BlockPos.ZERO));
    }

    @GameTest
    public void crossLaneFinalDefenseWaveRewardReductionAppliesInRuntimeEconomyService(GameTestHelper context) {
        UUID playerId = UUID.nameUUIDFromBytes("gametest-cross-lane-final-defense-killer".getBytes(StandardCharsets.UTF_8));
        SemionPlayer player = new SemionPlayer(
                playerId,
                "killer",
                TeamId.BLUE,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        Monster monster = new Monster(
                "gametest-blue-lane-two-final-defense-wave",
                TeamId.BLUE,
                2,
                Optional.empty(),
                Optional.empty(),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                10L
        );
        monster.syncLaneProgress(0.90);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        var snapshot = player.matchStats().snapshot(player.economy().income());
        if (!assertEquals(context, 204L, player.economy().diamond(), "Cross-lane final-defense wave kill should pay 40% of 10 diamond reward.")) {
            return;
        }
        if (!assertEquals(context, 4L, snapshot.assistClearDiamondGain(), "Assist clear diamond gain should record paid reduced reward.")) {
            return;
        }
        if (!assertEquals(context, 25.0, snapshot.assistClearThreat(), "Assist clear threat should preserve full monster threat.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void testModeSelectsOneVersusOne(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("alpha"),
                candidate("beta"),
                candidate("gamma"),
                candidate("delta"),
                candidate("epsilon")
        ), MatchMode.TEST);

        if (!assertPresent(context, plan, "Expected a selection plan for test mode with 5 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 2, value.activePlayerCount(), "Test mode should select exactly 2 active players.")) {
            return;
        }
        if (!assertEquals(context, 2, value.activeTeamCount(), "Test mode should activate exactly 2 teams.")) {
            return;
        }
        if (!assertEquals(context, 3, value.spectatorCount(), "Remaining players should be spectators in test mode.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 1, TeamId.BLUE, 1))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModePrefersThreeBalancedTeamsFromNine(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 9 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 9, value.activePlayerCount(), "9 players should use all 9 active players.")) {
            return;
        }
        if (!assertEquals(context, 3, value.activeTeamCount(), "9 players should produce 3 balanced active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "9 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 3, TeamId.BLUE, 3, TeamId.GREEN, 3))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsThreeBalancedTeamsFromTwelve(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9"),
                candidate("p10"),
                candidate("p11"),
                candidate("p12")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 12 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 12, value.activePlayerCount(), "12 players should use all 12 active players.")) {
            return;
        }
        if (!assertEquals(context, 3, value.activeTeamCount(), "12 players should produce 3 active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "12 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4, TeamId.GREEN, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsFourBalancedTeamsFromSixteen(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9"),
                candidate("p10"),
                candidate("p11"),
                candidate("p12"),
                candidate("p13"),
                candidate("p14"),
                candidate("p15"),
                candidate("p16")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 16 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 16, value.activePlayerCount(), "16 players should use all 16 active players.")) {
            return;
        }
        if (!assertEquals(context, 4, value.activeTeamCount(), "16 players should produce 4 active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "16 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4, TeamId.GREEN, 4, TeamId.YELLOW, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsFourVersusFourFromEight(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 8 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsThreeVersusThreeFromSix(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 6 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 3, TeamId.BLUE, 3))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeFallsBackToTwoVersusTwoFromFour(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 4 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 2, TeamId.BLUE, 2))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeRejectsOneVersusOneStyleRoster(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3")
        ), MatchMode.NORMAL);

        if (!assertTrue(context, plan.isEmpty(), "Normal mode should reject rosters that force a one-player team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionOnlyCountsReadyPlayers(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(
                        candidate("ready-1"),
                        candidate("ready-2"),
                        candidate("ready-fill-1"),
                        candidate("ready-fill-2"),
                        candidate("not-ready")
                ),
                Set.of(
                        stableUuid("ready-1"),
                        stableUuid("ready-2"),
                        stableUuid("ready-fill-1"),
                        stableUuid("ready-fill-2")
                ),
                MatchMode.NORMAL
        );

        if (!assertPresent(context, plan, "Expected normal mode to start from the four ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 4, value.activePlayerCount(), "Only ready players should be counted as active candidates.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "Not-ready online players should not become match spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 2, TeamId.BLUE, 2))) {
            return;
        }
        if (!assertTrue(context, value.activeParticipants().stream().noneMatch(participant -> participant.uuid().equals(stableUuid("not-ready"))), "Not-ready players should not be assigned to an active lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeCreatesFifthTeamAboveTwentyPlayers(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> candidate("overflow-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(candidates, readyPlayerIds, MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected normal mode to select from 21 ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 21, value.activePlayerCount(), "21 players should all enter the match.")) {
            return;
        }
        if (!assertEquals(context, 5, value.activeTeamCount(), "21 players should create a fifth active team.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "21 ready players should not become spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 5, TeamId.BLUE, 4, TeamId.GREEN, 4, TeamId.YELLOW, 4, TeamId.PURPLE, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionCapsActivePlayersAtTwentyFive(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(index -> candidate("overflow-cap-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(candidates, readyPlayerIds, MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected normal mode to select from 26 ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 25, value.activePlayerCount(), "Only 25 players should enter the match.")) {
            return;
        }
        if (!assertEquals(context, 1, value.spectatorCount(), "Ready players above 25 should become spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 5, TeamId.BLUE, 5, TeamId.GREEN, 5, TeamId.YELLOW, 5, TeamId.PURPLE, 5))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionPrioritizesPreviousSpectators(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(index -> candidate("priority-overflow-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> previousSpectatorIds = Set.of(
                stableUuid("priority-overflow-21"),
                stableUuid("priority-overflow-22"),
                stableUuid("priority-overflow-23"),
                stableUuid("priority-overflow-24"),
                stableUuid("priority-overflow-25")
        );

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                candidates,
                readyPlayerIds,
                MatchMode.NORMAL,
                previousSpectatorIds
        );

        if (!assertPresent(context, plan, "Expected normal mode to select with previous spectator priority.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 25, value.activePlayerCount(), "Only 25 players should enter the match.")) {
            return;
        }
        if (!assertEquals(context, 5, value.spectatorCount(), "Ready players above 25 should become spectators.")) {
            return;
        }
        if (!assertTrue(
                context,
                previousSpectatorIds.stream().allMatch(priorityId -> value.activeParticipants().stream()
                        .anyMatch(participant -> participant.uuid().equals(priorityId))),
                "Previous spectators should be selected as active participants first."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionBalancesTeamsByDisplayElo(GameTestHelper context) {
        StartCandidate strongest = candidate("elo-balance-strongest", 2000);
        StartCandidate strong = candidate("elo-balance-strong", 1900);
        StartCandidate weak = candidate("elo-balance-weak", 1000);
        StartCandidate weakest = candidate("elo-balance-weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL
        );
        if (!assertPresent(context, plan, "Expected ELO-balanced participant selection plan.")) {
            return;
        }

        Map<TeamId, Integer> teamElo = plan.get().activeParticipants().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        java.util.stream.Collectors.summingInt(participant -> eloFor(
                                participant.uuid(),
                                strongest,
                                strong,
                                weak,
                                weakest
                        ))
                ));
        if (!assertEquals(context, 2900, teamElo.get(TeamId.RED), "Red team should combine high and low ELO players.")) {
            return;
        }
        if (!assertEquals(context, 2900, teamElo.get(TeamId.BLUE), "Blue team should combine high and low ELO players.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionCanDisableDisplayEloBalancing(GameTestHelper context) {
        StartCandidate strongest = candidate("elo-disabled-strongest", 2000);
        StartCandidate strong = candidate("elo-disabled-strong", 1900);
        StartCandidate weak = candidate("elo-disabled-weak", 1000);
        StartCandidate weakest = candidate("elo-disabled-weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL,
                Set.of(),
                false,
                new Random(0)
        );
        if (!assertPresent(context, plan, "Expected participant selection plan with ELO matchmaking disabled.")) {
            return;
        }

        Map<TeamId, Integer> teamElo = plan.get().activeParticipants().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        java.util.stream.Collectors.summingInt(participant -> eloFor(
                                participant.uuid(),
                                strongest,
                                strong,
                                weak,
                                weakest
                        ))
                ));
        if (!assertEquals(context, 2800, teamElo.get(TeamId.RED), "Disabled ELO matchmaking should keep randomized roster order for team assignment.")) {
            return;
        }
        if (!assertEquals(context, 3000, teamElo.get(TeamId.BLUE), "Disabled ELO matchmaking should not rebalance team ELO sums.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameReadyRosterAllowsPlayersAboveActiveCap(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(index -> candidate("ready-roster-over-cap-" + index))
                .toList();

        for (StartCandidate candidate : candidates) {
            if (!assertTrue(context, game.markReady(candidate.uuid()), "Players above the active cap should still be able to ready.")) {
                return;
            }
        }
        if (!assertEquals(context, 30, game.readyPlayerCount(), "Ready roster should keep every ready player, even above active cap.")) {
            return;
        }

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                candidates,
                game.readyPlayerIds(),
                MatchMode.NORMAL
        );
        if (!assertPresent(context, plan, "Expected normal mode to select from the over-cap ready roster.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 25, value.activePlayerCount(), "Only 25 ready players should enter the match as active players.")) {
            return;
        }
        if (!assertEquals(context, 5, value.spectatorCount(), "Ready players above 25 should be assigned as spectators at start selection.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void lateSpectatorsDoNotPolluteResultSpectators(GameTestHelper context) {
        UUID redId = stableUuid("late-spectator-red");
        UUID blueId = stableUuid("late-spectator-blue");
        UUID lateSpectatorId = stableUuid("late-spectator-waiting");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "late-spectator-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "late-spectator-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for late spectator test.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(redId), "Active participants should not become late spectators.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(lateSpectatorId), "Late joiners should be able to register as match spectators.")) {
            return;
        }
        if (!assertEquals(context, 1, game.spectatorCount(), "Late spectator should count in the active match spectator set.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the late spectator test.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        if (!assertTrue(context, !result.get().spectatorIds().contains(lateSpectatorId), "Late spectators should not be stored as initial match spectators.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void eliminatedParticipantsRemainRatedParticipantsNotResultSpectators(GameTestHelper context) {
        UUID redId = stableUuid("eliminated-leave-red");
        UUID blueId = stableUuid("eliminated-leave-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate BLUE and end the match.")) {
            return;
        }
        if (!assertTrue(context, game.matchSpectatorIds().contains(blueId), "Eliminated players should be runtime spectators so they can leave/rejoin as observers.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertTrue(context, !matchResult.spectatorIds().contains(blueId), "Eliminated players must not be stored as initial/result spectators.")) {
            return;
        }
        if (!assertTrue(context, matchResult.participants().stream().anyMatch(participant -> participant.playerId().equals(blueId)), "Eliminated players should remain match participants for loss/rating/progression handling.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultKeepsStableMatchId(GameTestHelper context) {
        UUID redId = stableUuid("match-id-red");
        UUID blueId = stableUuid("match-id-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the match.")) {
            return;
        }

        Optional<MatchResult> first = game.matchResult();
        Optional<MatchResult> second = game.matchResult();
        if (!assertPresent(context, first, "Ended game should expose the first match result.")) {
            return;
        }
        if (!assertPresent(context, second, "Ended game should expose the second match result.")) {
            return;
        }
        if (!assertEquals(context, first.get().matchId(), second.get().matchId(), "Repeated matchResult calls should keep the same matchId.")) {
            return;
        }
        if (!assertTrue(context, first.get().startedAtEpochMillis() > 0, "Match result should expose a start timestamp.")) {
            return;
        }
        if (!assertTrue(context, first.get().endedAtEpochMillis() >= first.get().startedAtEpochMillis(), "End timestamp should not precede start timestamp.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultCapturesParticipantJobAndMode(GameTestHelper context) {
        UUID redId = stableUuid("job-statistics-result-red");
        UUID blueId = stableUuid("job-statistics-result-blue");
        ResourceLocation netherJobId = ResourceLocation.fromNamespaceAndPath("semion-td", "nether");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        if (!assertTrue(context, game.selectJob(redId, netherJobId), "Nether job should be selectable before match start.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "job-statistics-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "job-statistics-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Job-statistics game should start.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the job-statistics game.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a job-aware match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertEquals(context, MatchMode.NORMAL, matchResult.matchMode(), "Match result should preserve the start mode.")) {
            return;
        }
        Map<UUID, String> jobsByPlayer = matchResult.participants().stream()
                .collect(Collectors.toMap(MatchParticipantResult::playerId, MatchParticipantResult::jobId));
        if (!assertEquals(context, netherJobId.toString(), jobsByPlayer.get(redId), "Selected job id should be captured.")) {
            return;
        }
        if (!assertEquals(
                context,
                JobRegistry.defaultJob().id().toString(),
                jobsByPlayer.get(blueId),
                "Default job id should be captured."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void winningTeamsRemainBackwardCompatibleAndTeamResultsExposeGroups(GameTestHelper context) {
        UUID redId = stableUuid("team-result-red");
        UUID blueId = stableUuid("team-result-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the match.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertEquals(context, Set.of(TeamId.RED), matchResult.winningTeams(), "Winning teams should remain backward compatible.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.winnerCount(), "Participant winner count should remain compatible.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.loserCount(), "Participant loser count should remain compatible.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(matchResult);
        if (!assertEquals(context, MatchResultGroup.WIN_GROUP, byTeam.get(TeamId.RED).resultGroup(), "Winner team should be in the win group.")) {
            return;
        }
        if (!assertEquals(context, MatchResultGroup.LOSS_GROUP, byTeam.get(TeamId.BLUE).resultGroup(), "Eliminated team should be in the loss group.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "Winner should keep placement 1.")) {
            return;
        }
        if (!assertEquals(context, 2, byTeam.get(TeamId.BLUE).placement(), "Loser should get placement 2 in a two-team match.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void fiveTeamMatchResultOrdersEliminatedTeams(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("placement-red"), "placement-red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("placement-blue"), "placement-blue", TeamId.BLUE, 1),
                        new AssignedParticipant(stableUuid("placement-green"), "placement-green", TeamId.GREEN, 1),
                        new AssignedParticipant(stableUuid("placement-yellow"), "placement-yellow", TeamId.YELLOW, 1),
                        new AssignedParticipant(stableUuid("placement-purple"), "placement-purple", TeamId.PURPLE, 1)
                ),
                Set.of(),
                5
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Five-team game should start.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.YELLOW), "YELLOW should be eliminated first.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE should be eliminated second.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.GREEN), "GREEN should be eliminated third.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.PURPLE), "PURPLE should be eliminated fourth and finish the match.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Finished five-team game should expose a match result.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(result.get());
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "Living RED team should be first.")) {
            return;
        }
        if (!assertEquals(context, 2, byTeam.get(TeamId.PURPLE).placement(), "Last eliminated PURPLE team should be second.")) {
            return;
        }
        if (!assertEquals(context, 3, byTeam.get(TeamId.GREEN).placement(), "Third eliminated GREEN team should be third.")) {
            return;
        }
        if (!assertEquals(context, 4, byTeam.get(TeamId.BLUE).placement(), "Second eliminated BLUE team should be fourth.")) {
            return;
        }
        if (!assertEquals(context, 5, byTeam.get(TeamId.YELLOW).placement(), "First eliminated YELLOW team should be fifth.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void allEliminatedMatchResultIsUnratedDraw(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("draw-red"), "draw-red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("draw-blue"), "draw-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Two-team game should start.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE should be eliminated first.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.RED), "RED should also be eliminable after match end.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "All-eliminated game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(), result.get().winningTeams(), "All-eliminated games should have no winner.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(result.get());
        if (!assertEquals(context, MatchResultGroup.DRAW_OR_UNRATED, byTeam.get(TeamId.RED).resultGroup(), "No-winner RED result should be unrated.")) {
            return;
        }
        if (!assertEquals(context, MatchResultGroup.DRAW_OR_UNRATED, byTeam.get(TeamId.BLUE).resultGroup(), "No-winner BLUE result should be unrated.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "No-winner RED placement should be tied first.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.BLUE).placement(), "No-winner BLUE placement should be tied first.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultRepositoryPersistsDetailedResults(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-match-result-test").resolve("match-results.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary match result store path."));
            return;
        }

        MatchResult matchResult = new MatchResult(
                MatchId.newId(),
                1000L,
                2000L,
                List.of(new MatchParticipantResult(stableUuid("persisted-player"), "persisted-player", TeamId.RED, true)),
                Set.of(stableUuid("persisted-spectator")),
                Set.of(TeamId.RED),
                List.of(new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, -1, -1, 12.5)),
                7
        );
        FileMatchResultRepository repository = new FileMatchResultRepository(storePath);
        repository.saveMatchResult(matchResult);
        FileMatchResultRepository reloaded = new FileMatchResultRepository(storePath);
        Optional<MatchResult> loaded = reloaded.findMatchResult(matchResult.matchId());
        if (!assertPresent(context, loaded, "Saved match result should reload by stable matchId.")) {
            return;
        }
        if (!assertEquals(context, 7, loaded.get().finalRound(), "Persisted match result should keep final round.")) {
            return;
        }
        if (!assertEquals(context, 1, loaded.get().participantCount(), "Persisted match result should keep participant details.")) {
            return;
        }
        if (!assertEquals(context, 12.5, loaded.get().teamResults().getFirst().bossDamageTaken(), "Persisted match result should keep team debug statistics.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void teamSizeBalancePolicyDefaultsToNormalization(GameTestHelper context) {
        if (!assertEquals(
                context,
                TeamSizeBalancePolicy.ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION,
                TeamSizeBalancePolicy.defaultPolicy(),
                "Rating prework should keep uneven team rosters allowed with future normalized scoring."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void teamSelectedLateSpectatorValidatesTargetTeam(GameTestHelper context) {
        UUID redId = stableUuid("team-spectate-red");
        UUID blueId = stableUuid("team-spectate-blue");
        UUID greenId = stableUuid("team-spectate-green");
        UUID blueSpectatorId = stableUuid("team-spectate-blue-viewer");
        UUID greenSpectatorId = stableUuid("team-spectate-green-viewer");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        if (!assertTrue(context, game.canSpectateTeam(TeamId.BLUE), "BLUE should be a valid selected spectate target while active.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(blueSpectatorId, TeamId.BLUE), "Late joiner should be able to select BLUE for spectating.")) {
            return;
        }
        if (!assertEquals(context, 1, game.spectatorCount(), "Selected late spectator should be tracked.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(redId, TeamId.BLUE), "Active participants should not switch to selected spectating.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(greenSpectatorId, TeamId.YELLOW), "Inactive teams should not be selected for spectating.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate selected target team.")) {
            return;
        }
        if (!assertTrue(context, !game.canSpectateTeam(TeamId.BLUE), "Eliminated teams should not remain selected spectate targets.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(greenSpectatorId, TeamId.GREEN), "Late joiner should be able to select another active team.")) {
            return;
        }
        if (!assertEquals(context, 3, game.spectatorCount(), "Late spectators plus the eliminated BLUE participant should be tracked.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchHudTextSplitsActiveSpectatorAndEliminatedRoles(GameTestHelper context) {
        UUID redId = stableUuid("hud-role-red");
        UUID blueId = stableUuid("hud-role-blue");
        UUID greenId = stableUuid("hud-role-green");
        UUID spectatorId = stableUuid("hud-role-spectator");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        String activeText = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, activeText.contains("팀/라인"), "Active HUD should show team and lane.")) {
            return;
        }
        if (!assertTrue(context, !activeText.contains("다이아"), "Active HUD should move economy lines to actionbar.")) {
            return;
        }
        String actionbarText = SemionHudTextService.actionbarMarkupFor(game.players().get(redId), game);
        if (!assertTrue(context, actionbarText.contains("다이아"), "Active actionbar should show diamond economy.")) {
            return;
        }
        if (!assertTrue(context, actionbarText.contains("타워"), "Active actionbar should show tower limit.")) {
            return;
        }
        if (!assertTrue(context, game.purchaseTowerLimit(redId), "Tower limit purchase should succeed before actionbar rendering.")) {
            return;
        }
        String upgradedActionbarText = SemionHudTextService.actionbarMarkupFor(game.players().get(redId), game);
        String upgradedTowerLimitText = game.towerCount(redId) + "/" + game.towerLimitForPlayer(redId);
        if (!assertTrue(context, upgradedActionbarText.contains(upgradedTowerLimitText), "Active actionbar should reflect purchased tower slots.")) {
            return;
        }
        String scoreboardText = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, !scoreboardText.contains("다이아"), "Scoreboard HUD should keep diamond economy in actionbar.")) {
            return;
        }
        if (!assertTrue(context, !scoreboardText.contains("타워"), "Scoreboard HUD should keep tower limit in actionbar.")) {
            return;
        }
        if (!assertTrue(context, !scoreboardText.contains(upgradedTowerLimitText), "Scoreboard HUD should not duplicate purchased tower slots from actionbar.")) {
            return;
        }
        if (!assertTrue(context, scoreboardText.contains("전체 팀 보스"), "Scoreboard HUD should keep the full team boss summary.")) {
            return;
        }
        if (!assertTrue(context, activeText.contains("전체 팀 보스"), "Active HUD should keep the full team boss summary.")) {
            return;
        }

        if (!assertTrue(context, game.addLateSpectator(spectatorId, TeamId.GREEN), "Late spectator should register before HUD rendering.")) {
            return;
        }
        String spectatorText = SemionDisplayHudService.matchMarkupFor(
                spectatorId,
                Optional.of(game.teams().get(TeamId.GREEN)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, spectatorText.contains("관전 중"), "Spectator HUD should show spectator status.")) {
            return;
        }
        if (!assertTrue(context, spectatorText.contains("관전 팀 보스"), "Spectator HUD should show the viewed team's boss.")) {
            return;
        }
        if (!assertTrue(context, !spectatorText.contains("전체 팀 보스"), "Spectator HUD should not show the full team boss summary.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate BLUE for HUD role test.")) {
            return;
        }
        String eliminatedText = SemionDisplayHudService.matchMarkupFor(
                blueId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, eliminatedText.contains("탈락 후 관전 중"), "Eliminated HUD should distinguish eliminated spectators.")) {
            return;
        }
        if (!assertTrue(context, eliminatedText.contains("소속 팀"), "Eliminated HUD should show the original team.")) {
            return;
        }
        if (!assertTrue(context, eliminatedText.contains("관전 팀"), "Eliminated HUD should show the currently viewed team.")) {
            return;
        }
        if (!assertTrue(context, !eliminatedText.contains("다이아"), "Eliminated HUD should omit active economy lines.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerLimitConfigScalesFromRoundFiveAndCaps(GameTestHelper context) {
        EconomyConfig.TowerLimitConfig config = EconomyConfig.TowerLimitConfig.defaultConfig();
        if (!assertEquals(context, 5, config.limitForRound(1), "Tower limit should start at five.")) {
            return;
        }
        if (!assertEquals(context, 5, config.limitForRound(4), "Tower limit should stay at five before round five.")) {
            return;
        }
        if (!assertEquals(context, 8, config.limitForRound(5), "Tower limit should gain three slots at round five.")) {
            return;
        }
        if (!assertEquals(context, 11, config.limitForRound(10), "Tower limit should gain another three slots at round ten.")) {
            return;
        }
        if (!assertEquals(context, 11, config.limitForRound(50), "Tower limit should cap at eleven by default.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void statusReportSummarizesOperationalState(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();

        List<String> noGameLines = SemionCommands.statusLines(manager);
        if (!assertTrue(context, noGameLines.stream().anyMatch(line -> line.contains("activeGame=false")), "Status should report no active game.")) {
            return;
        }
        if (!assertTrue(context, noGameLines.stream().anyMatch(line -> line.contains("lobbyLoaded=false")), "Status should report unloaded lobby before create.")) {
            return;
        }

        try {
            SemionGame game = manager.createGame(server);
            UUID redId = stableUuid("status-red");
            UUID blueId = stableUuid("status-blue");
            UUID spectatorId = stableUuid("status-spectator");
            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "status-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "status-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(spectatorId),
                    2
            );
            if (!assertTrue(context, game.start(server, plan), "Status test game should start.")) {
                return;
            }

            List<String> statusLines = SemionCommands.statusLines(manager);
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("activeGame=true")), "Status should report active game.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("phase=PREPARE_AND_SUMMON")), "Status should report current phase.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("activeParticipants=2")), "Status should report active participants.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("spectators=1")), "Status should report spectators.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("lobbyLoaded=true")), "Status should report loaded lobby.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("arenaLoaded=5/5")), "Status should report loaded arenas.")) {
                return;
            }

            List<String> teamLines = SemionCommands.teamStatusLines(game);
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("팀 RED active=true")), "Team status should include active RED.")) {
                return;
            }
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("팀 GREEN active=false")), "Team status should include inactive GREEN.")) {
                return;
            }
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("boss=")), "Team status should include boss health.")) {
                return;
            }

            List<String> laneLines = SemionCommands.laneStatusLines(game);
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("라인 RED#1")), "Lane status should include active RED lane.")) {
                return;
            }
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("towerSample=")), "Lane status should include a tower placement sample.")) {
                return;
            }
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("laneArea=")), "Lane status should include the lane area bounds.")) {
                return;
            }

            List<String> playerLines = SemionCommands.playerStatusLines(game);
            if (!assertTrue(context, playerLines.stream().anyMatch(line -> line.contains("참가자 status-red")), "Player status should list active participants.")) {
                return;
            }
            if (!assertTrue(context, playerLines.stream().anyMatch(line -> line.contains("관전자 uuid=" + spectatorId)), "Player status should list spectators.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Status report should summarize operational state: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void managerStartSpectateAndResetFlowWorks(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-reset-flow").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }

        manager.configure(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            SemionGame game = manager.createGame(server);
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Create should load lobby.")) {
                return;
            }

            UUID redId = stableUuid("manager-reset-red");
            UUID blueId = stableUuid("manager-reset-blue");
            UUID lateSpectatorId = stableUuid("manager-reset-late-spectator");
            if (!assertTrue(context, game.markReady(redId), "Red player should ready before admin start.")) {
                return;
            }
            if (!assertTrue(context, game.markReady(blueId), "Blue player should ready before admin start.")) {
                return;
            }

            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "manager-reset-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "manager-reset-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, game.start(server, plan), "Game should start from the admin flow.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isPresent(), "Manager should retain the active game after start.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Started game should enter prepare.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    TeamId.RED,
                    game.teamForWorld(game.arena().teamArena(TeamId.RED).orElseThrow().world()).map(SemionTeam::id).orElse(null),
                    "RED runtime world should map back to RED for spectator HUD."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TeamId.BLUE,
                    game.teamForWorld(game.arena().teamArena(TeamId.BLUE).orElseThrow().world()).map(SemionTeam::id).orElse(null),
                    "BLUE runtime world should map back to BLUE for spectator HUD."
            )) {
                return;
            }
            if (!assertTrue(context, game.addLateSpectator(lateSpectatorId), "Late joiner should be able to spectate an active match.")) {
                return;
            }
            if (!assertEquals(context, 1, game.spectatorCount(), "Late spectator should be tracked in the active match.")) {
                return;
            }

            if (!assertTrue(context, manager.resetToLobby(server), "Reset should report an active game was closed.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isEmpty(), "Reset should clear the active game.")) {
                return;
            }
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Reset should keep or load the lobby world.")) {
                return;
            }
            if (!assertTrue(context, manager.lastMatchResult().isEmpty(), "Reset should clear stale match results.")) {
                return;
            }
            if (!assertTrue(context, !manager.resetToLobby(server), "Second reset should report no active game.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Manager start/spectate/reset flow should work: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void managerResetThenCreateStartsFreshMatch(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-recreate-flow").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }

        manager.configure(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            UUID redId = stableUuid("manager-recreate-red");
            UUID blueId = stableUuid("manager-recreate-blue");

            SemionGame firstGame = manager.createGame(server);
            ParticipantSelectionPlan firstPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "recreate-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "recreate-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, firstGame.start(server, firstPlan), "First game should start before reset.")) {
                return;
            }
            if (!assertTrue(context, manager.resetToLobby(server), "Reset should close the first active game.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isEmpty(), "Reset should clear active game before recreate.")) {
                return;
            }

            SemionGame secondGame = manager.createGame(server);
            if (!assertTrue(context, manager.activeGame().orElse(null) == secondGame, "Create should install a fresh active waiting game.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.WAITING, secondGame.phase(), "Fresh game should start from WAITING phase.")) {
                return;
            }
            if (!assertTrue(context, secondGame.markReady(redId), "RED should be able to ready in the fresh game.")) {
                return;
            }
            if (!assertTrue(context, secondGame.markReady(blueId), "BLUE should be able to ready in the fresh game.")) {
                return;
            }

            ParticipantSelectionPlan secondPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "recreate-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "recreate-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, secondGame.start(server, secondPlan), "Fresh game should start after reset and recreate.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, secondGame.phase(), "Fresh game should progress into prepare phase.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Reset/create/start sequence should remain usable: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void scoreboardTeamsAreCreated(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        VanillaTeamBridge.ensureTeams(server);

        if (!assertScoreboardTeam(context, server, "semion_red")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_blue")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_green")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_yellow")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_spectator")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startPlacementOffsetsPlayersByLane(GameTestHelper context) {
        var layout = testArena(context).teamArena(TeamId.RED)
                .orElseThrow()
                .layout();

        Vec3 laneOne = StartPlacement.activePlayerSpawn(
                layout,
                1
        );
        Vec3 laneFive = StartPlacement.activePlayerSpawn(
                layout,
                5
        );

        if (!assertEquals(context, layout.lane(1).orElseThrow().spawn(), laneOne, "Lane 1 should spawn at its assigned lane spawn.")) {
            return;
        }
        if (!assertEquals(context, layout.lane(5).orElseThrow().spawn(), laneFive, "Lane 5 should spawn at its assigned lane spawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void defaultWaveConfigCoversPreInfiniteRounds(GameTestHelper context) {
        WaveConfig config = WaveConfig.defaultConfig();

        for (int round = 1; round <= 20; round++) {
            if (!assertPresent(context, config.configForRound(round), "Default wave config should define round " + round + ".")) {
                return;
            }
        }
        if (!assertTrue(
                context,
                !config.configForRound(2).orElseThrow().entriesForLane("lane_1").isEmpty(),
                "Round 2 should enqueue default lane monsters."
        )) {
            return;
        }
        var firstWave = config.configForRound(1).orElseThrow().entriesForLane("lane_1").getFirst();
        if (!assertEquals(context, 10.0, firstWave.health(), "Round 1 monster health should be tuned for starter towers.")) {
            return;
        }
        if (!assertEquals(context, 1.0, firstWave.attackDamage(), "Round 1 monster attack should be tuned for starter towers.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void spectatorPlacementFloatsAboveTeamSpawn(GameTestHelper context) {
        var layout = testArena(context).teamArena(TeamId.RED).orElseThrow().layout();
        Vec3 spectatorZero = StartPlacement.spectatorSpawn(layout, 0);
        Vec3 spectatorThree = StartPlacement.spectatorSpawn(layout, 3);

        if (!assertEquals(
                context,
                layout.teamSpawn().add(-5.0, 8.0, 0.0),
                spectatorZero,
                "Spectator base spawn is incorrect."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                layout.teamSpawn().add(2.5, 8.0, 0.0),
                spectatorThree,
                "Spectator spread offset is incorrect."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void syntheticArenaProvidesSevenBySevenFinalDefenseSlots(GameTestHelper context) {
        PlayerLane lane = redLane(startedSinglePlayerGame(context, stableUuid("slot-owner"), TeamId.RED), 1);

        if (!assertEquals(context, 49, lane.laneLayout().finalDefenseTowerSlots().size(), "Lane should expose 49 final defense slots from its 7x7 region.")) {
            return;
        }
        if (!assertEquals(
                context,
                lane.laneLayout().finalDefenseTowerSlots().getFirst(),
                lane.laneLayout().finalDefenseTowerSlots().stream()
                        .min(java.util.Comparator.comparingDouble(slot -> lane.laneLayout().bossPosition().distanceTo(
                                new Vec3(slot.x() + 0.5, slot.y(), slot.z() + 0.5)
                        )))
                        .orElseThrow(),
                "Final defense slots should be ordered by distance to boss."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void mapConfigUsesPlainRegionMarkerNames(GameTestHelper context) {
        MapConfig.RegionMarkers markers = MapConfig.defaultConfig().regions();

        if (!assertEquals(context, "team_spawn", markers.teamSpawn(), "Team spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "lane_spawn", markers.laneSpawn(), "Lane spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "lane_path", markers.lanePath(), "Lane path marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "final_waypoint", markers.finalWaypoint(), "Final waypoint marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "boss_spawn", markers.bossSpawn(), "Boss spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "final_defense_lane", markers.finalDefenseTower(), "Final defense marker should not require a namespace.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void arenaLayoutUsesSharedFinalDefenseRegionForEveryLane(GameTestHelper context) {
        MapTemplate template = MapTemplate.createEmpty();
        template.getMetadata().addRegion("team_spawn", BlockBounds.ofBlock(new BlockPos(0, 64, 0)));
        template.getMetadata().addRegion("boss_spawn", BlockBounds.ofBlock(new BlockPos(20, 64, 0)));
        template.getMetadata().addRegion("final_defense_lane", BlockBounds.of(10, 64, -1, 12, 64, 1));
        template.getMetadata().addRegion("final_waypoint", BlockBounds.ofBlock(new BlockPos(8, 64, 0)), orderData(1));
        template.getMetadata().addRegion("final_waypoint", BlockBounds.ofBlock(new BlockPos(6, 64, 0)), orderData(0));
        for (int laneId = 1; laneId <= 5; laneId++) {
            template.getMetadata().addRegion(
                    "lane_spawn",
                    BlockBounds.ofBlock(new BlockPos(-10, 64, laneId)),
                    laneData(laneId)
            );
            template.getMetadata().addRegion(
                    "lane_path",
                    BlockBounds.of(-10, 64, laneId, 20, 64, laneId),
                    laneData(laneId)
            );
        }
        template.getMetadata().addRegion(
                "lane_waypoint",
                BlockBounds.ofBlock(new BlockPos(-5, 64, 1)),
                laneData(1, 0)
        );

        try {
            ArenaLayout layout = ArenaLayout.fromTemplate(
                    template,
                    MapConfig.RegionMarkers.defaultMarkers()
            );
            List<?> laneOneSlots = layout.lane(1).orElseThrow().finalDefenseTowerSlots();
            List<?> laneFiveSlots = layout.lane(5).orElseThrow().finalDefenseTowerSlots();
            if (!assertEquals(context, 9, laneOneSlots.size(), "Shared final defense region should expose all slots.")) {
                return;
            }
            if (!assertEquals(context, laneOneSlots, laneFiveSlots, "Every lane should share unlaned final defense slots.")) {
                return;
            }
            List<Vec3> laneOneWaypoints = layout.lane(1).orElseThrow().waypoints();
            List<Vec3> laneFiveWaypoints = layout.lane(5).orElseThrow().waypoints();
            if (!assertEquals(context, List.of(
                    new Vec3(-4.5, 65.0, 1.5),
                    new Vec3(6.5, 65.0, 0.5),
                    new Vec3(8.5, 65.0, 0.5)
            ), laneOneWaypoints, "Lane waypoints should be followed by shared final waypoints.")) {
                return;
            }
            if (!assertEquals(context, List.of(
                    new Vec3(6.5, 65.0, 0.5),
                    new Vec3(8.5, 65.0, 0.5)
            ), laneFiveWaypoints, "Lanes without lane waypoints should still use shared final waypoints.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Shared final defense region should be accepted: " + exception.getMessage()));
        }
    }

    @GameTest
    public void startSpawnsBossEntitiesForActiveTeams(GameTestHelper context) {
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(stableUuid("red-boss-owner"), "red-boss-owner", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan),
                "Game should start with a valid participant plan."
        )) {
            return;
        }

        context.runAfterDelay(1, () -> {
            if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should be tracked.")) {
                return;
            }
            if (!assertEquals(context, 1, countTrackedBossEntities(game), "One active team should track one boss entity.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    game.teams().get(TeamId.RED).laneGroup().bossEntity().filter(entity -> !entity.isRemoved()).isPresent(),
                    "RED boss entity reference should be alive."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void killingBossRemovesBossEntity(GameTestHelper context) {
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(stableUuid("red-boss-owner"), "red-boss-owner", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan),
                "Game should start with a valid participant plan."
        )) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.RED), "RED boss kill should succeed.")) {
            return;
        }

        context.runAfterDelay(1, () -> {
            if (!assertTrue(context, !game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should be cleared.")) {
                return;
            }
            if (!assertEquals(context, 0, countTrackedBossEntities(game), "No boss entity should remain tracked after killing RED boss.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void eliminatedTeamDisablesActiveAndQueuedLaneMonsters(GameTestHelper context) {
        UUID redId = stableUuid("disable-red-owner");
        UUID blueId = stableUuid("disable-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane blueLane = lane(game, TeamId.BLUE, 1);

        blueLane.enqueueWaveMonster(new WaveMonsterEntry(
                "disable-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                5,
                2
        ));
        blueLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, blueLane.activeMonsters().size(), "Blue lane should have one active monster before elimination.")) {
            return;
        }
        int activeMonsterEntityId = blueLane.activeMonsters().getFirst().minecraftEntityId();

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should eliminate the target team.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).eliminated(), "Blue team should be eliminated.")) {
            return;
        }
        if (!assertEquals(context, 0, blueLane.activeMonsters().size(), "Eliminated team lane should have no active monsters.")) {
            return;
        }
        if (!assertTrue(context, blueLane.clearedThisRound(), "Eliminated team lane should be marked resolved.")) {
            return;
        }

        blueLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 0, blueLane.activeMonsters().size(), "Eliminated team lane should not spawn queued wave monsters.")) {
            return;
        }
        if (!assertTrue(context, context.getLevel().getEntity(activeMonsterEntityId) == null
                || context.getLevel().getEntity(activeMonsterEntityId).isRemoved(), "Active monster entity should be discarded.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void survivingWaveMonsterScalesHealthAndAttackThroughLaneTick(GameTestHelper context) {
        UUID redId = stableUuid("monster-scaling-red");
        UUID blueId = stableUuid("monster-scaling-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);
        MonsterScalingConfig config = new MonsterScalingConfig(true, 0, 600, 1, 3.0, 3.0, true, true);

        redLane.enqueueWaveMonster(new WaveMonsterEntry(
                "scaling-wave",
                100.0,
                0.0,
                10.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1,
                1
        ));
        redLane.tick(context.getLevel().getServer(), null, Map.of(), config, 0);

        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "Scaling test should spawn one wave monster.")) {
            return;
        }
        Monster monster = redLane.activeMonsters().getFirst();
        if (!assertTrue(context, Math.abs(monster.maxHealth() - 103.0) < 0.0001, "Configured scaling should increase runtime max health by 3%. Actual=" + monster.maxHealth())) {
            return;
        }
        if (!assertTrue(context, Math.abs(monster.attackDamage() - 10.3) < 0.0001, "Configured scaling should increase runtime attack damage by 3%. Actual=" + monster.attackDamage())) {
            return;
        }
        if (!(context.getLevel().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
            context.fail(Component.literal("Scaled monster entity should exist in the world."));
            return;
        }
        if (!assertTrue(context, Math.abs(entity.getAttributeValue(Attributes.MAX_HEALTH) - 103.0) < 0.0001, "Entity max-health attribute should be synced after scaling. Actual=" + entity.getAttributeValue(Attributes.MAX_HEALTH))) {
            return;
        }
        if (!assertTrue(context, Math.abs(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) - 10.3) < 0.0001, "Entity attack-damage attribute should be synced after scaling. Actual=" + entity.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void waveMonstersSpawnAcrossLaneSpawnArea(GameTestHelper context) {
        UUID redId = stableUuid("wave-spawn-area-red");
        UUID blueId = stableUuid("wave-spawn-area-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);

        redLane.enqueueWaveMonster(new WaveMonsterEntry(
                "distributed-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1,
                3
        ));
        redLane.tick(context.getLevel().getServer());
        redLane.tick(context.getLevel().getServer());
        redLane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 3, redLane.activeMonsters().size(), "Three wave monsters should spawn after three lane ticks.")) {
            return;
        }

        Set<String> spawnCells = redLane.activeMonsters().stream()
                .map(monster -> BlockPos.containing(monster.spawnX(), monster.spawnY(), monster.spawnZ()))
                .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertTrue(context, spawnCells.size() >= 2, "Wave monsters should not all spawn on the same block.")) {
            return;
        }

        BlockBounds spawnArea = redLane.laneLayout().spawnArea();
        for (Monster monster : redLane.activeMonsters()) {
            BlockPos spawnPos = BlockPos.containing(monster.spawnX(), monster.spawnY(), monster.spawnZ());
            if (!assertTrue(context, spawnArea.contains(spawnPos), "Wave monster should spawn inside lane spawn area: " + spawnPos)) {
                return;
            }
            if (!(context.getLevel().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
                context.fail(Component.literal("Wave monster entity should exist in the world."));
                return;
            }
            if (!assertEquals(context, spawnPos, BlockPos.containing(entity.position()), "Runtime entity should be at the recorded spawn cell.")) {
                return;
            }
        }

        context.succeed();
    }

    @GameTest
    public void incomeSummonsKeepSingleSpawnPointWhenWaveSpawnAreaIsDistributed(GameTestHelper context) {
        UUID redId = stableUuid("income-single-spawn-red");
        UUID blueId = stableUuid("income-single-spawn-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);
        Monster incomeMonster = new Monster(
                "income-single-spawn",
                TeamId.RED,
                2,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                1
        );

        redLane.enqueueSummonedMonster(incomeMonster);
        redLane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "Income monster should spawn from the summon queue.")) {
            return;
        }
        Monster spawned = redLane.activeMonsters().getFirst();
        BlockPos recordedSpawn = BlockPos.containing(spawned.spawnX(), spawned.spawnY(), spawned.spawnZ());
        BlockPos legacySpawn = BlockPos.containing(redLane.laneLayout().spawn());
        if (!assertEquals(context, legacySpawn, recordedSpawn, "Income summons should keep the existing single lane spawn point in the MVP.")) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void incomeSummonRoutingUsesThreatPressureInRuntimeGame(GameTestHelper context) {
        UUID redId = stableUuid("income-threat-routing-red");
        UUID blueLaneOneId = stableUuid("income-threat-routing-blue-1");
        UUID blueLaneTwoId = stableUuid("income-threat-routing-blue-2");
        reloadDefaultIncomeSummons();
        EconomyConfig economy = new EconomyConfig(
                200,
                1000,
                0,
                EconomyConfig.GasCapConfig.defaultConfig(),
                EconomyConfig.GasProductionConfig.defaultConfig(),
                EconomyConfig.TowerLimitConfig.defaultConfig(),
                EconomyConfig.KillRewardConfig.defaultConfig()
        );
        SemionGame game = new SemionGame(
                economy,
                new WaveConfig(List.of(), 20, null),
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueLaneOneId, "blue-one", TeamId.BLUE, 1),
                        new AssignedParticipant(blueLaneTwoId, "blue-two", TeamId.BLUE, 2)
                ),
                java.util.Set.of(),
                3
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Threat routing game should start.")) {
            return;
        }

        var heavySummon = game.summonMonster(redId, "ravager");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, heavySummon.type(), "Heavy summon should succeed.")) {
            return;
        }
        PlayerLane blueLaneOne = lane(game, TeamId.BLUE, 1);
        PlayerLane blueLaneTwo = lane(game, TeamId.BLUE, 2);
        if (!assertEquals(context, 1, blueLaneOne.queuedSummonCount(), "First equal-pressure summon should use round-robin lane 1.")) {
            return;
        }
        if (!assertEquals(context, 0, blueLaneTwo.queuedSummonCount(), "Lane 2 should still be empty after first summon.")) {
            return;
        }

        var lightSummon = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, lightSummon.type(), "Light summon should succeed.")) {
            return;
        }
        if (!assertEquals(context, 1, blueLaneTwo.queuedSummonCount(), "Next summon should route to the lane with lower accumulated threat.")) {
            return;
        }
        if (!assertTrue(context, blueLaneOne.queuedSummonThreat() > blueLaneTwo.queuedSummonThreat(), "Ravager lane should remain much higher threat than chicken lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startLocksRosterAndActivatesOnlySelectedPlayers(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for game start.")) {
            return;
        }

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan.get()),
                "Game should start with a valid participant plan."
        )) {
            return;
        }
        if (!assertTrue(context, game.rosterLocked(), "Game roster should be locked after start.")) {
            return;
        }
        if (!assertTrue(context, !game.canConfigureRoster(), "Roster configuration should be blocked after start.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Game should enter prepare phase.")) {
            return;
        }
        if (!assertEquals(context, 9, game.players().size(), "Only active players should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 0, game.spectatorCount(), "Spectator count should match the selection plan.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).active(), "RED should be active.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).active(), "BLUE should be active.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.GREEN).active(), "GREEN should be active.")) {
            return;
        }
        if (!assertTrue(context, !game.teams().get(TeamId.YELLOW).active(), "YELLOW should be inactive.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startAssignsHighestEloParticipantAsTeamLeader(GameTestHelper context) {
        UUID redLaneOne = stableUuid("leader-elo-red-lane-one");
        UUID redLaneTwo = stableUuid("leader-elo-red-lane-two");
        UUID blue = stableUuid("leader-elo-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redLaneOne, "red-low", TeamId.RED, 1, 1200),
                        new AssignedParticipant(redLaneTwo, "red-high", TeamId.RED, 2, 1800),
                        new AssignedParticipant(blue, "blue", TeamId.BLUE, 1, 1500)
                ),
                Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with a two-player RED team.")) {
            return;
        }
        if (!assertEquals(context, redLaneTwo, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow(), "Highest ELO RED player should become team leader even outside lane 1.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 700)
    public void preparePhaseTeleportsPlayerAndGrantsHotbarTools(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        UUID blueId = stableUuid("prepare-hotbar-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(playerId, player.getGameProfile().getName(), TeamId.RED, 1),
                        new AssignedParticipant(blueId, "prepare-hotbar-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with the mock player.")) {
            return;
        }

        Vec3 laneSpawn = StartPlacement.activePlayerSpawn(game.arena().teamArena(TeamId.RED).orElseThrow().layout(), 1);
        if (!assertEquals(context, game.arena().teamArena(TeamId.RED).orElseThrow().world(), player.level(), "Active player should be moved to their team runtime world.")) {
            return;
        }
        if (!assertTrue(context, player.position().distanceTo(laneSpawn) < 0.01, "Active player should start at their assigned lane spawn.")) {
            return;
        }
        if (!assertTrue(context, player.getInventory().getItem(0).is(Items.COMPASS), "Tower control item should be granted in hotbar slot 0.")) {
            return;
        }
        if (!assertTrue(context, player.getInventory().getItem(1).is(Items.ECHO_SHARD), "Summon control item should be granted in hotbar slot 1.")) {
            return;
        }

        player.teleportTo(player.getX() + 12.0, player.getY(), player.getZ() + 12.0);
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 3);
        if (!assertEquals(context, 2, game.currentRound(), "Empty first wave should advance into round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Game should return to prepare after round payout.")) {
            return;
        }
        if (!assertTrue(context, player.position().distanceTo(laneSpawn) < 0.01, "Round prepare should return the player to their assigned lane spawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void playerFacingDialogsOpenForJobsTowersAndSummons(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        var player = context.makeMockServerPlayerInLevel();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        new SemionDialogService().showJobSelection(player, game);
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(player.getUUID(), player.getGameProfile().getName(), TeamId.RED, 1)),
                Set.of(),
                1
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Dialog test game should start.")) {
            return;
        }
        SemionDialogService dialogService = new SemionDialogService();
        dialogService.showTowerControl(player, game);
        dialogService.showSummonShop(player, game);
        dialogService.showSummonShop(player, game, 2);
        dialogService.showDebugSummonShop(player, 2);
        dialogService.showMatchResult(
                player,
                new MatchResult(
                        List.of(new MatchParticipantResult(player.getUUID(), player.getGameProfile().getName(), TeamId.RED, true)),
                        Set.of(),
                        Set.of(TeamId.RED),
                        3
                ),
                Map.of()
        );
        context.succeed();
    }

    @GameTest
    public void jobStatisticsDialogKeepsRegistryOrderAndAppendsRemovedJobs(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        JobStatisticsEntry villager = new JobStatisticsEntry(
                VillagerTowerJob.ID.toString(),
                3L,
                2L,
                3L,
                4L,
                30L,
                JobStatisticsTotals.empty(),
                1_000L,
                2_000L,
                3_000L,
                java.util.stream.IntStream.rangeClosed(1, JobStatisticsEntry.MAX_TRACKED_ROUND)
                        .mapToObj(round -> round <= 20 ? 3L : round <= 30 ? 1L : 0L)
                        .toList()
        );
        JobStatisticsEntry removed = new JobStatisticsEntry(
                "external:removed",
                2L,
                1L,
                2L,
                3L,
                20L,
                JobStatisticsTotals.empty(),
                1_000L,
                2_000L,
                3_000L
        );
        JobStatisticsSnapshot snapshot = new JobStatisticsSnapshot(
                3_000L,
                2L,
                5L,
                1_000L,
                2_000L,
                List.of(removed, villager)
        );

        List<SemionDialogService.JobStatisticsRow> rows = SemionDialogService.jobStatisticsRows(snapshot);
        List<String> registryOrder = JobRegistry.all().stream().map(job -> job.id().toString()).toList();
        if (!assertEquals(context, registryOrder, rows.subList(0, registryOrder.size()).stream()
                .map(SemionDialogService.JobStatisticsRow::jobId)
                .toList(), "Statistics rows should keep JobRegistry order.")) {
            return;
        }
        SemionDialogService.JobStatisticsRow last = rows.getLast();
        if (!assertEquals(context, "external:removed", last.jobId(), "Removed job ids should be appended.")) {
            return;
        }
        if (!assertTrue(context, !last.registered(), "Removed job ids should be marked unregistered.")) {
            return;
        }
        if (!assertEquals(context, 3L, villager.roundPassCount(20), "Round 20 pass count should be retained.")) {
            return;
        }
        if (!assertEquals(context, 1L, villager.roundPassCount(30), "Round 30 pass count should be retained.")) {
            return;
        }
        if (!assertEquals(context, 0L, villager.roundPassCount(40), "Round 40 pass count should be retained.")) {
            return;
        }
        SemionDialogService dialogService = new SemionDialogService();
        dialogService.showJobStatistics(player, snapshot, JobStatisticsState.READY);
        dialogService.showJobStatisticsDetail(
                player,
                snapshot,
                JobStatisticsState.READY,
                VillagerTowerJob.ID.toString()
        );
        context.succeed();
    }

    @GameTest
    public void playerStatusDialogRowsUseCurrentEconomyTowerCountAndJob(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        var player = context.makeMockServerPlayerInLevel();
        UUID redId = player.getUUID();
        UUID blueId = stableUuid("status-table-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        if (!assertTrue(context, game.selectJob(redId, NetherTowerJob.ID), "Status table test should select the nether job.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(blueId, "status-blue", TeamId.BLUE, 1),
                        new AssignedParticipant(redId, player.getGameProfile().getName(), TeamId.RED, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Status table test game should start.")) {
            return;
        }

        game.players().get(redId).economy().overrideStartingValues(321, 45, 17, 1);
        game.players().get(blueId).economy().overrideStartingValues(111, 22, 9, 1);
        PlayerLane lane = game.playerLane(redId).orElseThrow();
        BlockPos towerPos = towerPlacementPos(lane);
        lane.addTower(new TestTower(redId, TeamId.RED, 1, GridPosition.from(towerPos)));
        lane.addTower(new TestTower(redId, TeamId.RED, 1, GridPosition.from(towerPos.offset(1, 0, 0))));

        List<SemionDialogService.PlayerStatusRow> rows = SemionDialogService.playerStatusRows(game);
        if (!assertEquals(context, 2, rows.size(), "Status table should include every active participant.")) {
            return;
        }
        SemionDialogService.PlayerStatusRow red = rows.getFirst();
        if (!assertEquals(context, redId, red.playerId(), "Status rows should use deterministic team and lane ordering.")) {
            return;
        }
        if (!assertEquals(context, TeamId.RED, red.teamId(), "Status rows should expose the player's team for grouping and name color.")) {
            return;
        }
        if (!assertEquals(context, 321L, red.diamond(), "Status table should show current diamonds.")) {
            return;
        }
        if (!assertEquals(context, 45L, red.emerald(), "Status table should show current emeralds.")) {
            return;
        }
        if (!assertEquals(context, 17L, red.income(), "Status table should show current income.")) {
            return;
        }
        if (!assertEquals(context, 2, red.towerCount(), "Status table should count the player's current towers.")) {
            return;
        }
        if (!assertEquals(context, game.players().get(redId).job().orElseThrow().displayName().getString(), red.jobName(), "Status table should show the active job.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().displayName().getString(), rows.get(1).jobName(), "Status table should use the default job name when no job was selected.")) {
            return;
        }
        if (!assertEquals(context, TeamId.BLUE, rows.get(1).teamId(), "Status rows should keep players grouped in team order.")) {
            return;
        }

        new SemionDialogService().showGameStatus(player, game);
        context.succeed();
    }

    @GameTest
    public void productionTowerCatalogStartsEmptyForManualAuthoring(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        if (!assertTrue(context, ProductionTowerCatalog.all().isEmpty(), "Production catalog should start empty so towers can be authored manually.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void emptyProductionTowerCatalogRejectsBuildRequests(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-production-villager-owner");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.UNKNOWN_TOWER,
                ProductionTowerService.placeTower(game, playerId, towerPos, "missing_manual_tower"),
                "Empty production catalog should reject build requests until a tower is registered."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Rejected production build should not add a runtime tower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void emptyProductionTowerCatalogKeepsBuildListsEmpty(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID unjobbedId = stableUuid("unjobbed-production-owner");
        SemionGame unjobbedGame = startedSinglePlayerGame(context, unjobbedId, TeamId.RED);
        if (!assertEquals(
                context,
                0,
                ProductionTowerService.availableTowers(unjobbedGame, unjobbedId).size(),
                "Unjobbed players should see no production towers while the catalog is empty."
        )) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void animalTowerBuildListIncludesFoxStarterForAnimalJob(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        AnimalTowerCatalogs.register();
        UUID playerId = stableUuid("animal-fox-build-ui-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AnimalTowerJob.ID);

        boolean includesFoxStarter = ProductionTowerService.availableTowers(game, playerId).stream()
                .anyMatch(entry -> AnimalTowers.T1_FOX_TOWER.id().equals(entry.type().id()));
        if (!assertTrue(context, includesFoxStarter, "Animal tower build UI should include the T1 fox tower starter.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanWaterTowerPlacesSuppliesAndRestoresWaterloggedLight(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-runtime-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        List<ProductionTowerCatalog.CatalogEntry> starters = ProductionTowerService.availableTowers(game, playerId);
        long oceanStarterCount = starters.stream().filter(entry -> OceanTowers.isOceanTower(entry.type())).count();
        if (!assertEquals(context, 6L, oceanStarterCount, "Ocean job should expose all six tier-one paths.")) {
            return;
        }

        BlockPos towerPos = towerPlacementPos(lane);
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, OceanTowers.T1_WATER.id()),
                "Ocean water tower should place on an open lane floor."
        )) {
            return;
        }
        if (!(lane.towers().getFirst() instanceof OceanWaterTower waterTower)) {
            context.fail(Component.literal("Placed ocean supply tower should use the water runtime."));
            return;
        }
        if (!(lane.arenaWorld().getEntity(waterTower.entityId().orElseThrow()) instanceof SemionTowerEntity waterTowerEntity)) {
            context.fail(Component.literal("Ocean water tower should spawn a clickable tower entity."));
            return;
        }
        if (!assertTrue(context, waterTowerEntity.isNoAi(), "Water tower entity should disable floating AI.")) {
            return;
        }
        Vec3 entityPosition = waterTowerEntity.position();

        BlockPos waterPos = OceanWaterTower.waterBlockPos(waterTower.position());
        if (!assertEquals(
                context,
                OceanWaterTower.waterMarker(),
                lane.arenaWorld().getBlockState(waterPos),
                "Water tower should occupy its tower cell with a waterlogged light block."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getFluidState(waterPos).isSource(),
                "Water tower marker should expose a real source-water fluid state."
        )) {
            return;
        }

        GridPosition combatPosition = new GridPosition(
                waterTower.position().x() + 1,
                waterTower.position().y(),
                waterTower.position().z()
        );
        OceanTower codTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                combatPosition
        );
        lane.addTower(codTower);
        waterTower.syncPosition(new GridPosition(
                waterTower.originalPosition().x(),
                waterTower.originalPosition().y() + 3,
                waterTower.originalPosition().z()
        ));
        lane.markWaveStarted(1);
        if (!assertEquals(
                context,
                70.0,
                codTower.water(),
                "Water supply should use the fixed water block even if its proxy entity position drifted."
        )) {
            return;
        }
        waterTower.syncPosition(waterTower.originalPosition());

        codTower.syncPosition(new GridPosition(
                waterTower.position().x() + 20,
                waterTower.position().y(),
                waterTower.position().z()
        ));
        OceanTower lateNearbyTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_SALMON),
                playerId,
                TeamId.RED,
                1,
                combatPosition
        );
        lane.addTower(lateNearbyTower);
        lane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 71.0, codTower.water(), "Captured towers should keep receiving water after moving out of range.")) {
            return;
        }
        if (!assertEquals(context, 50.0, lateNearbyTower.water(), "Towers entering range after the first wave starts must not receive water.")) {
            return;
        }

        lane.resetForRound();
        lane.markWaveStarted(2);
        if (!assertEquals(context, 70.0, lateNearbyTower.water(), "The next wave should capture newly placed nearby towers.")) {
            return;
        }

        BlockPos freeWaterPos = waterPos.offset(4, 0, 0);
        lane.arenaWorld().setBlock(freeWaterPos.below(), Blocks.STONE.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos.east().below(), Blocks.STONE.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos, Blocks.WATER.defaultBlockState(), 3);
        lane.arenaWorld().setBlock(freeWaterPos.east(), Blocks.AIR.defaultBlockState(), 3);
        lane.arenaWorld().scheduleTick(freeWaterPos, Fluids.WATER, 1);

        context.runAfterDelay(10, () -> {
            if (!assertEquals(context, entityPosition, waterTowerEntity.position(), "Water tower hitbox should stay fixed inside water.")) {
                return;
            }
            boolean contained = List.of(waterPos.north(), waterPos.south(), waterPos.east(), waterPos.west()).stream()
                    .allMatch(neighbor -> lane.arenaWorld().getBlockState(neighbor).isAir());
            if (!assertTrue(context, contained, "Water tower should remain contained to one block after fluid ticks.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getBlockState(freeWaterPos.east()).isAir(),
                    "Water fluid ticks should not spread inside Fantasy runtime worlds."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TowerSellResult.SUCCESS,
                    ProductionTowerService.sellTower(game, playerId, waterTower.position()).result(),
                    "Water tower should remain a normal sellable tower."
            )) {
                return;
            }
            if (!assertTrue(context, lane.arenaWorld().getBlockState(waterPos).isAir(), "Selling should restore the original air block.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void oceanWaterTowerKeepsSupplyingAcrossGameRounds(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-round-transition-owner");
        SemionGame game = startedTwoPlayerGame(
                context,
                playerId,
                stableUuid("ocean-water-round-transition-opponent")
        );
        game.disableWaveSpawnsForTeam(TeamId.RED);
        game.disableWaveSpawnsForTeam(TeamId.BLUE);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        OceanWaterTower waterTower = new OceanWaterTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_WATER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPos)
        );
        OceanTower codTower = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                new GridPosition(
                        waterTower.originalPosition().x() + 1,
                        waterTower.originalPosition().y(),
                        waterTower.originalPosition().z()
                )
        );
        lane.addTower(waterTower);
        lane.addTower(codTower);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, 70.0, codTower.water(), "Round one should capture and supply the nearby cod tower.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), 3);
        if (!assertEquals(context, 2, game.currentRound(), "The empty first wave should advance to round two.")) {
            return;
        }
        double beforeRoundTwoWave = codTower.water();
        tickGame(
                game,
                context.getLevel().getServer(),
                SemionGame.DEFAULT_PREPARE_TICKS - game.phaseTicks()
        );
        if (!assertEquals(
                context,
                beforeRoundTwoWave + 20.0,
                codTower.water(),
                "The same water tower should recapture and supply the same cod tower in round two."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanTankWaterTransferRespectsCooldown(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-tank-transfer-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        GridPosition tankPosition = GridPosition.from(base);
        GridPosition targetPosition = GridPosition.from(base.east());
        OceanTower tank = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_PUFFERFISH),
                playerId,
                TeamId.RED,
                1,
                tankPosition
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                targetPosition
        );
        OceanTower guardian = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T2_GUARDIAN),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.west())
        );
        OceanTower elderGuardian = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T3_ELDER_GUARDIAN),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.north())
        );
        lane.addTower(tank);
        lane.addTower(target);
        lane.addTower(guardian);
        lane.addTower(elderGuardian);
        if (!(lane.arenaWorld().getEntity(tank.entityId().orElseThrow()) instanceof SemionTowerEntity tankEntity)) {
            context.fail(Component.literal("Ocean tank should spawn a tower entity."));
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 100.0, 70.0);
        if (!assertEquals(context, 74.0, target.water(), "The first hit should transfer the doubled tier-one cap.")) {
            return;
        }
        if (!assertEquals(context, 49.0, tank.water(), "A successful transfer should spend water once.")) {
            return;
        }
        if (!assertEquals(context, 50.0, guardian.water(), "Pufferfish water transfer should exclude guardian tanks.")) {
            return;
        }
        if (!assertEquals(context, 50.0, elderGuardian.water(), "Pufferfish water transfer should exclude elder guardian tanks.")) {
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 70.0, 40.0);
        if (!assertEquals(context, 74.0, target.water(), "Hits during the transfer cooldown should not create water.")) {
            return;
        }
        if (!assertEquals(context, 49.0, tank.water(), "Hits during the transfer cooldown should not spend water.")) {
            return;
        }

        for (int tick = 0; tick < 49; tick++) {
            tank.tick(lane);
        }
        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 74.0, target.water(), "The ability should remain blocked before fifty ticks pass.")) {
            return;
        }

        tank.tick(lane);
        tank.onDamaged(tankEntity, null, 10.0, 10.0, 0.0);
        if (!assertEquals(context, 74.0, target.water(), "A fatal hit should not create water.")) {
            return;
        }
        if (!assertEquals(context, 49.0, tank.water(), "A fatal hit should not spend transfer water.")) {
            return;
        }

        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 98.0, target.water(), "The ability should become ready after fifty ticks.")) {
            return;
        }
        if (!assertEquals(context, 48.0, tank.water(), "The next ready transfer should spend water exactly once.")) {
            return;
        }

        target.syncHealth(0.0);
        for (int tick = 0; tick < 50; tick++) {
            tank.tick(lane);
        }
        tank.onDamaged(tankEntity, null, 30.0, 40.0, 10.0);
        if (!assertEquals(context, 98.0, target.water(), "A dead tower should not receive transferred water.")) {
            return;
        }
        if (!assertEquals(context, 48.0, tank.water(), "No water should be spent when every nearby target is dead.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanSupportSpendsStoredWaterForEmpoweredBuff(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        OceanTower support = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_TROPICAL_FISH),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base)
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.east())
        );
        lane.addTower(support);
        lane.addTower(target);
        if (!(lane.arenaWorld().getEntity(target.entityId().orElseThrow()) instanceof SemionTowerEntity targetEntity)) {
            context.fail(Component.literal("Ocean support target should spawn a tower entity."));
            return;
        }

        support.addWater(50.0);
        lane.markWaveStarted(1);
        support.tick(lane);
        if (!assertEquals(context, 84.0, support.water(), "Empowered support should spend twice its normal water cost.")) {
            return;
        }
        if (!assertClose(context, 0.12, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS),
                "Empowered support should multiply its damage buff by one and a half.")) {
            return;
        }
        if (!assertClose(context, 0.15, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Empowered support should multiply its attack-speed buff by one and a half.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanHealerConsumesWaterAndHealsNearbyLivingTowers(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-healer-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        OceanTower healer = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_SQUID),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base)
        );
        OceanTower target = new OceanTower(
                TowerBalanceRuntime.resolve(OceanTowers.T1_COD),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(base.east())
        );
        lane.addTower(healer);
        lane.addTower(target);
        if (!(lane.arenaWorld().getEntity(target.entityId().orElseThrow()) instanceof SemionTowerEntity targetEntity)) {
            context.fail(Component.literal("Ocean heal target should spawn a tower entity."));
            return;
        }

        target.syncHealth(10.0);
        targetEntity.setHealth(10.0F);
        lane.markWaveStarted(1);
        healer.tick(lane);
        if (!assertEquals(context, 25.0, target.health(), "Squid should heal a nearby damaged tower by fifteen.")) {
            return;
        }
        if (!assertEquals(context, 44.0, healer.water(), "A successful squid heal should spend six water.")) {
            return;
        }

        healer.resetForRound(lane);
        healer.addWater(56.0);
        healer.onWaveStarted(lane, 2);
        target.syncHealth(target.currentMaxHealth());
        targetEntity.setHealth((float) target.currentMaxHealth());
        healer.tick(lane);
        if (!assertEquals(context, 100.0, healer.water(), "No stored water should be spent when no nearby tower needs healing.")) {
            return;
        }

        target.syncHealth(10.0);
        targetEntity.setHealth(10.0F);
        healer.tick(lane);
        if (!assertEquals(context, 32.5, target.health(), "Empowered squid should heal by one and a half times its normal amount.")) {
            return;
        }
        if (!assertEquals(context, 88.0, healer.water(), "Empowered squid should spend twice its normal water cost.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oceanTierThreeTowerHitboxesDoNotOverlapAdjacentCells(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-tier-three-hitbox-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        List<TowerType> tierThreeTypes = List.of(
                OceanTowers.T3_CURRENT,
                OceanTowers.T3_ELDER_GUARDIAN,
                OceanTowers.T3_GIANT_TROPICAL_FISH,
                OceanTowers.T3_DOLPHIN,
                OceanTowers.T3_GIANT_SALMON,
                OceanTowers.T3_GIANT_COD
        );
        SemionTowerEntity previousEntity = null;

        for (int index = 0; index < tierThreeTypes.size(); index++) {
            TowerType type = tierThreeTypes.get(index);
            Tower tower = ProductionTowerCatalog.find(type.id()).orElseThrow().create(
                    playerId,
                    TeamId.RED,
                    1,
                    GridPosition.from(base.offset(index, 0, 0))
            );
            lane.addTower(tower);
            if (!(tower instanceof EntityBackedTower entityBackedTower)
                    || !(lane.arenaWorld().getEntity(entityBackedTower.entityId().orElseThrow())
                    instanceof SemionTowerEntity towerEntity)) {
                context.fail(Component.literal(type.id() + " should spawn a clickable tower entity."));
                return;
            }
            if (!assertTrue(context, towerEntity.getBbWidth() <= 1.0F, type.id() + " hitbox should fit one cell.")) {
                return;
            }
            if (previousEntity != null && !assertTrue(
                    context,
                    !previousEntity.getBoundingBox().intersects(towerEntity.getBoundingBox()),
                    type.id() + " hitbox should not cover the adjacent tower."
            )) {
                return;
            }
            previousEntity = towerEntity;
        }
        context.succeed();
    }

    @GameTest
    public void oceanWaterTowerRejectsBlockedCellBeforeChargingDiamond(GameTestHelper context) {
        UUID playerId = stableUuid("ocean-water-blocked-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, OceanTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        BlockPos occupiedWaterCell = towerPos.above();
        lane.arenaWorld().setBlock(occupiedWaterCell, Blocks.STONE.defaultBlockState(), 3);
        long diamondBefore = game.players().get(playerId).economy().diamond();

        if (!assertEquals(
                context,
                TowerPlacementResult.OCCUPIED,
                ProductionTowerService.placeTower(game, playerId, towerPos, OceanTowers.T1_WATER.id()),
                "Water tower should reject a non-air tower cell."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                diamondBefore,
                game.players().get(playerId).economy().diamond(),
                "Rejected water placement must not spend diamonds."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Rejected water placement should not add a runtime tower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerBuildButtonLabelsColorUnaffordableTowersRed(GameTestHelper context) {
        ProductionTowerCatalog.CatalogEntry entry = productionFixtureEntry();
        Component affordable = SemionDialogService.towerButtonLabel(entry, true);
        Component unaffordable = SemionDialogService.towerButtonLabel(entry, false);
        Component recommended = SemionDialogService.towerButtonLabel(entry, false, true);

        if (!assertEquals(
                context,
                ChatFormatting.GREEN.getColor(),
                affordable.getStyle().getColor().getValue(),
                "Affordable tower button labels should be green when the UI opens."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                ChatFormatting.RED.getColor(),
                unaffordable.getStyle().getColor().getValue(),
                "Unaffordable tower button labels should be red when the UI opens."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                ChatFormatting.BLUE.getColor(),
                recommended.getStyle().getColor().getValue(),
                "Build-recommended tower button labels should be blue regardless of affordability."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildRecommendedUpgradeButtonLabelsUseBlue(GameTestHelper context) {
        TowerUpgradeOption option = new TowerUpgradeOption(
                "manual_upgrade",
                "Manual Upgrade",
                productionFixtureType("manual_fixture_blue_upgrade_target", List.of()),
                100
        );
        Component label = SemionDialogService.upgradeButtonLabel(option, false, true);
        if (!assertEquals(
                context,
                ChatFormatting.BLUE.getColor(),
                label.getStyle().getColor().getValue(),
                "Build-recommended upgrade button labels should be blue."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideRecordsSuccessfulActionsAndPersistsPublishedGuide(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-build-guide-test").resolve("build_guides.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary build guide store path."));
            return;
        }

        BuildGuideService service = new BuildGuideService(storePath);
        UUID redId = stableUuid("build-guide-red-owner");
        UUID blueId = stableUuid("build-guide-blue-owner");
        reloadDefaultIncomeSummons();
        ProductionTowerCatalog.clear();
        TowerType starterType = productionFixtureType("manual_fixture_build_record_starter", List.of());
        TowerType targetType = productionFixtureType("manual_fixture_build_record_target", List.of());
        ProductionTowerCatalog.registerStarter(starterType);
        ProductionTowerCatalog.register(targetType, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", targetType, 0);
        SemionJob testJob = registerTowerAllowingJob(
                "build_record",
                Set.of(starterType.id(), targetType.id())
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context),
                service
        );
        game.selectJob(redId, testJob.id());
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );
        TraitLoadout selectedTraits = new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.SUPPLY_DEPOT_ID
        );
        if (!assertTrue(
                context,
                game.start(
                        context.getLevel().getServer(),
                        plan,
                        new TraitSelectionSnapshot(Map.of(redId, selectedTraits))
                ),
                "Build recording game should start."
        )) {
            return;
        }

        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        ProductionTowerService.placeTower(game, redId, towerPos, "missing_tower");
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, redId, towerPos, starterType.id()),
                "Successful tower placement should be accepted for build recording."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, redId, towerPos, "manual_upgrade"),
                "Successful tower upgrade should be accepted for build recording."
        )) {
            return;
        }
        var summon = game.summonMonster(redId, "chicken");
        if (!assertEquals(
                context,
                kim.biryeong.semiontd.summon.SummonResultType.SUCCESS,
                summon.type(),
                "Successful summon should be accepted for build recording."
        )) {
            return;
        }
        if (!assertTrue(context, game.upgradeGasProduction(redId), "Successful emerald production upgrade should be accepted for build recording.")) {
            return;
        }

        service.finishMatch(game, 3);
        Optional<BuildGuide> published = service.publishLastRecording(redId, "테스트 빌드");
        if (!assertPresent(context, published, "Finished recording should publish a build guide.")) {
            return;
        }
        BuildGuide guide = published.get();
        if (!assertEquals(context, 4, guide.actions().size(), "Only successful placement, upgrade, summon, and emerald upgrade actions should be recorded.")) {
            return;
        }
        if (!assertTrue(context, guide.isPrivate(), "Newly recorded build guides should be private by default.")) {
            return;
        }
        if (!assertEquals(
                context,
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID.toString(),
                guide.traitLoadout().primaryTraitId(),
                "Published build guide should keep the selected primary trait."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                BuiltInTraits.SUPPLY_DEPOT_ID.toString(),
                guide.traitLoadout().secondaryTraitId(),
                "Published build guide should keep the selected secondary trait."
        )) {
            return;
        }
        String guideCode = guide.code();
        if (!assertTrue(context, service.publicGuides().stream().noneMatch(found -> found.code().equals(guideCode)), "Private guides should not appear in the public build list.")) {
            return;
        }
        if (!assertTrue(context, service.myGuides(redId).stream().anyMatch(found -> found.code().equals(guideCode)), "Owner should see private guides in my build list.")) {
            return;
        }
        if (!assertTrue(context, service.findViewable(guide.code(), blueId).isEmpty(), "Other players should not view private guides.")) {
            return;
        }
        if (!assertTrue(context, !service.track(blueId, guide.code()), "Other players should not track private guides.")) {
            return;
        }
        if (!assertTrue(context, service.setVisibility(blueId, guide.code(), BuildGuide.VISIBILITY_PUBLIC).isEmpty(), "Non-owners should not publish another player's guide.")) {
            return;
        }
        guide = service.setVisibility(redId, guide.code(), BuildGuide.VISIBILITY_PUBLIC).orElseThrow();
        if (!assertTrue(context, guide.isPublic(), "Owner should be able to publish a private guide.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().anyMatch(found -> found.code().equals(guideCode)), "Published guides should appear in the public build list.")) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.TOWER_PLACE), "Published guide should include tower placement.")) {
            return;
        }
        BuildAction placementAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.TOWER_PLACE)
                .findFirst()
                .orElseThrow();
        if (!assertTrue(context, placementAction.hasLaneRelativePosition(), "Recorded tower placement should store a lane-relative position.")) {
            return;
        }
        if (!assertEquals(
                context,
                starterType.displayName(),
                BuildGuideService.subjectDisplayName(placementAction),
                "Build guide tower placement display should use the tower display name instead of the internal id."
        )) {
            return;
        }
        GridPosition redAbsolutePosition = GridPosition.from(towerPos);
        if (!assertEquals(
                context,
                redAbsolutePosition,
                service.resolveActionPosition(game, redId, placementAction).orElse(null),
                "Lane-relative recorded placement should resolve back to the original player lane position."
        )) {
            return;
        }
        service.track(blueId, guide.code());
        GridPosition blueResolvedPosition = service.resolveActionPosition(game, blueId, placementAction).orElse(null);
        if (!assertTrue(context, blueResolvedPosition != null, "Lane-relative recorded placement should resolve for another player lane.")) {
            return;
        }
        if (!assertTrue(
                context,
                service.isRecommendedTower(game, blueId, placementAction.round(), blueResolvedPosition, starterType.id()),
                "Tracked placement recommendations should compare against the current player's lane-relative absolute position."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.TOWER_UPGRADE), "Published guide should include tower upgrade.")) {
            return;
        }
        BuildAction upgradeAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.TOWER_UPGRADE)
                .findFirst()
                .orElseThrow();
        if (!assertEquals(
                context,
                "Manual Upgrade",
                BuildGuideService.subjectDisplayName(upgradeAction),
                "Build guide tower upgrade display should use the upgrade display name instead of the internal id."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.SUMMON), "Published guide should include summon purchase.")) {
            return;
        }
        BuildAction summonAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.SUMMON)
                .findFirst()
                .orElseThrow();
        if (!assertTrue(
                context,
                !BuildGuideService.subjectDisplayName(summonAction).equals(summonAction.subjectId()),
                "Build guide summon display should use the summon display name instead of the internal id."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.EMERALD_PRODUCTION_UPGRADE), "Published guide should include emerald production upgrade.")) {
            return;
        }

        BuildGuideService reloaded = new BuildGuideService(storePath);
        Optional<BuildGuide> reloadedGuide = reloaded.find(guide.code());
        if (!assertPresent(context, reloadedGuide, "Published guide should survive build store reload.")) {
            return;
        }
        if (!assertEquals(
                context,
                guide.traitLoadout(),
                reloadedGuide.get().traitLoadout(),
                "Persisted build guide should keep the selected traits after reload."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideRemainsPublishableUntilNextCountdownCompletes(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        var player = context.makeMockServerPlayerInLevel();
        SemionGameManager manager = new SemionGameManager();
        manager.configureTraits(new TraitSelectionConfig(false, 45));
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-build-guide-manager-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }

        manager.configure(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            UUID redId = player.getUUID();
            UUID blueId = stableUuid("build-guide-waiting-blue-owner");
            SemionGame finishedGame = manager.createGame(server);
            ParticipantSelectionPlan firstPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, player.getGameProfile().getName(), TeamId.RED, 1),
                            new AssignedParticipant(blueId, "build-guide-waiting-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, finishedGame.start(server, firstPlan), "First game should start build recording.")) {
                return;
            }
            PlayerLane lane = redLane(finishedGame, 1);
            finishedGame.recordTowerPlacement(redId, "waiting_publish_tower", GridPosition.from(towerPlacementPos(lane)), 0L);
            if (!assertTrue(context, finishedGame.killBoss(TeamId.BLUE), "First game should end before the next game is created.")) {
                return;
            }

            manager.tick(server);
            for (int tick = 0; tick <= SemionGameManager.MATCH_RESULT_DELAY_TICKS; tick++) {
                manager.tick(server);
            }
            for (int tick = 0; tick <= SemionGameManager.MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS; tick++) {
                manager.tick(server);
            }
            if (!assertPresent(
                    context,
                    manager.publishLastBuild(player, "통계 화면 후 저장"),
                    "The surviving winner should publish after the result dialog is shown."
            )) {
                return;
            }

            SemionGame waitingGame = manager.createGame(server);
            if (!assertTrue(context, manager.lastMatchResult().isEmpty(), "Creating the next waiting game should clear stale match results.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.WAITING, waitingGame.phase(), "Next game should still be waiting before build publish.")) {
                return;
            }

            ParticipantSelectionPlan secondPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, player.getGameProfile().getName(), TeamId.RED, 1),
                            new AssignedParticipant(blueId, "build-guide-waiting-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertEquals(
                    context,
                    SemionGameManager.StartCountdownResult.SCHEDULED,
                    manager.scheduleStart(server, secondPlan),
                    "Next game countdown should start."
            )) {
                return;
            }
            for (int tick = 0; tick < SemionGameManager.START_COUNTDOWN_TICKS - 1; tick++) {
                manager.tick(server);
            }
            if (!assertEquals(context, RoundPhase.WAITING, waitingGame.phase(), "Next game should remain waiting until the final countdown tick.")) {
                return;
            }
            Optional<BuildGuide> published = manager.publishLastBuild(player, "카운트다운 중 저장");
            if (!assertPresent(context, published, "Last finished build recording should publish before the countdown completes.")) {
                return;
            }
            if (!assertEquals(context, 1, published.get().actions().size(), "Published waiting-period guide should preserve the previous match action.")) {
                return;
            }

            manager.tick(server);
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, waitingGame.phase(), "Final countdown tick should start the next game.")) {
                return;
            }
            if (!assertPresent(
                    context,
                    manager.publishLastBuild(player, "다음 경기 시작 후 저장"),
                    "Previous match recording should remain publishable until the next match finishes."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Build guide should remain publishable until the next countdown completes: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void buildGuideRoundActionsFilterCurrentRound(GameTestHelper context) {
        BuildGuide guide = new BuildGuide(
                "ABC123",
                "라운드 필터",
                stableUuid("build-round-author"),
                "author",
                "semion-td:default",
                kim.biryeong.semiontd.trait.TraitLoadoutSnapshot.none(),
                4,
                1L,
                BuildGuide.VISIBILITY_PUBLIC,
                List.of(
                        BuildAction.towerPlace(1, "tower_a", new GridPosition(1, 64, 1), 0),
                        BuildAction.towerPlace(2, "tower_b", new GridPosition(2, 64, 2), 0)
                )
        );
        List<BuildAction> roundTwo = BuildGuideService.actionsForRound(guide, 2);
        if (!assertEquals(context, 1, roundTwo.size(), "Round action filtering should include only the requested round.")) {
            return;
        }
        if (!assertEquals(context, "tower_b", roundTwo.getFirst().subjectId(), "Round action filtering should return the current-round action.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuidePublicListsHideDebugGuides(GameTestHelper context) {
        BuildGuideService service = new BuildGuideService(null);
        UUID authorId = stableUuid("build-debug-filter-author");
        BuildGuide debugGuide = service.saveDebugGuide(
                "DEBUG1",
                "디버그 빌드",
                authorId,
                "debug",
                "semion-td:debug",
                1,
                List.of(BuildAction.towerPlace(1, "debug_tower", new GridPosition(0, 0, 0), 0))
        );
        BuildGuide publicGuide = service.saveDebugGuide(
                "LIVE01",
                "실제 빌드",
                authorId,
                "debug",
                "semion-td:default",
                1,
                List.of(BuildAction.towerPlace(1, "live_tower", new GridPosition(0, 0, 0), 0))
        );

        if (!assertTrue(context, service.find(debugGuide.code()).isPresent(), "Debug guide should remain addressable by code for debug commands.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().noneMatch(BuildGuideService::isDebugGuide), "Normal public build list should hide debug guides.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().anyMatch(guide -> guide.code().equals(publicGuide.code())), "Normal public build list should still include real guides.")) {
            return;
        }
        if (!assertTrue(
                context,
                service.recentGuides(authorId, List.of(debugGuide.code(), publicGuide.code())).stream().noneMatch(BuildGuideService::isDebugGuide),
                "Normal recent build list should hide debug guides."
        )) {
            return;
        }
        if (!assertTrue(context, service.debugPublicGuides().stream().anyMatch(BuildGuideService::isDebugGuide), "Debug build list should still include debug guides.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideOwnerCanToggleVisibilityAndDelete(GameTestHelper context) {
        BuildGuideService service = new BuildGuideService(null);
        UUID ownerId = stableUuid("build-owner-management-owner");
        UUID otherId = stableUuid("build-owner-management-other");
        BuildGuide guide = service.saveDebugGuide(
                "LIVE02",
                "관리 테스트",
                ownerId,
                "owner",
                "semion-td:default",
                2,
                List.of(BuildAction.towerPlace(1, "live_tower", new GridPosition(0, 0, 0), 0))
        );

        if (!assertTrue(context, service.setVisibility(otherId, guide.code(), BuildGuide.VISIBILITY_PRIVATE).isEmpty(), "Non-owner should not change build visibility.")) {
            return;
        }
        guide = service.setVisibility(ownerId, guide.code(), BuildGuide.VISIBILITY_PRIVATE).orElseThrow();
        if (!assertTrue(context, guide.isPrivate(), "Owner should be able to make a build private.")) {
            return;
        }
        if (!assertTrue(context, service.findViewable(guide.code(), otherId).isEmpty(), "Private owner builds should not be viewable by other players.")) {
            return;
        }
        if (!assertTrue(context, !service.delete(otherId, guide.code()), "Non-owner should not delete another player's build.")) {
            return;
        }
        if (!assertTrue(context, service.delete(ownerId, guide.code()), "Owner should be able to delete their build.")) {
            return;
        }
        if (!assertTrue(context, service.find(guide.code()).isEmpty(), "Deleted build should be removed from the store.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideIndicatorUsesVanillaForPlayersWithoutGcb(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        if (!assertEquals(
                context,
                BuildGuideIndicatorService.DeliveryPath.VANILLA,
                BuildGuideIndicatorService.deliveryPath(player),
                "Players without the GCB client mod should use the vanilla particle fallback."
        )) {
            return;
        }
        context.succeed();
    }

    private static ProductionTowerCatalog.CatalogEntry productionFixtureEntry() {
        return new ProductionTowerCatalog.CatalogEntry(
                productionFixtureType("manual_fixture_entry", List.of()),
                null,
                1
        );
    }

    @GameTest
    public void productionCatalogFactoryAcceptsNonProductionEntityBackedTower(GameTestHelper context) {
        UUID playerId = stableUuid("red-custom-production-runtime-owner");
        TowerType type = productionFixtureType("manual_fixture_custom_runtime", List.of());
        ProductionTowerCatalog.CatalogEntry entry = new ProductionTowerCatalog.CatalogEntry(
                type,
                FixtureSupportTower::new,
                1
        );

        Tower tower = entry.create(
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 64, 0)
        );
        if (!assertTrue(
                context,
                tower instanceof FixtureSupportTower,
                "Production catalog factories should allow entity-backed tower implementations that do not extend ProductionTower."
        )) {
            return;
        }
        if (!assertEquals(context, type, tower.type(), "Factory-created custom runtime tower should preserve its catalog type.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerCopyFromTransfersSaleAndRuntimeState(GameTestHelper context) {
        UUID playerId = stableUuid("red-copy-runtime-state-owner");
        TowerType sourceType = productionFixtureType("manual_fixture_copy_source", List.of());
        TowerType targetType = productionFixtureType("manual_fixture_copy_target", List.of());
        kim.biryeong.semiontd.game.GridPosition position = new kim.biryeong.semiontd.game.GridPosition(0, 64, 0);
        TowerDataKey<Integer> stacksKey = TowerDataKey.of(
                ResourceLocation.fromNamespaceAndPath("semion-td", "test/support_stacks"),
                Integer.class
        );
        FixtureSupportTower sourceTower = new FixtureSupportTower(
                sourceType,
                playerId,
                TeamId.RED,
                1,
                position,
                position
        );
        sourceTower.recordPlacementEconomy(75, 2);
        sourceTower.markWaveStarted(2);
        sourceTower.setPersistentBonus(4);
        sourceTower.setData(stacksKey, 3);

        FixtureSupportTower targetTower = new FixtureSupportTower(
                targetType,
                playerId,
                TeamId.RED,
                1,
                position,
                position
        );
        targetTower.copyFrom(sourceTower, 25);

        if (!assertEquals(context, 100L, targetTower.paidMineralCost(), "copyFrom should carry sale cost plus upgrade cost.")) {
            return;
        }
        if (!assertEquals(context, 2, targetTower.placedRound(), "copyFrom should carry original placement round.")) {
            return;
        }
        if (!assertTrue(context, targetTower.waveStartedAfterPlacement(), "copyFrom should carry wave-start sale state.")) {
            return;
        }
        if (!assertEquals(context, 4, targetTower.persistentBonus(), "copyFrom should call the runtime-state copy hook.")) {
            return;
        }
        if (!assertTrue(context, targetTower.hasData(stacksKey), "copyFrom should carry generic tower data keys.")) {
            return;
        }
        if (!assertEquals(context, 3, targetTower.getDataOrDefault(stacksKey, 0), "copyFrom should carry generic tower data values.")) {
            return;
        }
        targetTower.removeData(stacksKey);
        if (!assertTrue(context, !targetTower.hasData(stacksKey), "removeData should clear generic tower data values.")) {
            return;
        }
        context.succeed();
    }

    private static TowerType productionFixtureType(String id, List<TowerUpgradeOption> upgradeOptions) {
        return new TowerType(
                id,
                "Manual Fixture",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0,
                "minecraft:villager",
                upgradeOptions
        );
    }

    @GameTest
    public void selectedJobPlaceholderShowsActivePlayerJob(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        ResourceLocation jobId = JobRegistry.defaultJob().id();
        SemionGame game = startedSinglePlayerGame(context, player.getUUID(), TeamId.RED, jobId);
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        SemionPlaceholders.register(manager);

        PlaceholderResult display = Placeholders.parsePlaceholder(
                ResourceLocation.fromNamespaceAndPath("semion-td", "selected_job"),
                null,
                PlaceholderContext.of(player)
        );
        if (!assertTrue(context, display.isValid(), "Selected job display placeholder should resolve for a player.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().displayName().getString(), display.text().getString(), "Selected job placeholder should show the chosen job display name.")) {
            return;
        }

        PlaceholderResult id = Placeholders.parsePlaceholder(
                ResourceLocation.fromNamespaceAndPath("semion-td", "selected_job_id"),
                null,
                PlaceholderContext.of(player)
        );
        if (!assertTrue(context, id.isValid(), "Selected job id placeholder should resolve for a player.")) {
            return;
        }
        if (!assertEquals(context, jobId.toString(), id.text().getString(), "Selected job id placeholder should show the chosen job id.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void jobSelectionDialogUsesPathCommandButtons(GameTestHelper context) {
        SemionJob job = JobRegistry.defaultJob();
        if (!assertEquals(
                context,
                "/semiontd job select " + job.id().getPath(),
                SemionDialogService.jobSelectionCommand(job),
                "Job selection buttons should send path-only job ids instead of namespaced identifiers."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                !SemionDialogService.jobSelectionCommand(job).contains("semion-td:"),
                "Job selection UI commands should not expose the namespace."
        )) {
            return;
        }
        Component selected = SemionDialogService.jobButtonLabel(job, true);
        if (!assertEquals(
                context,
                ChatFormatting.GREEN.getColor(),
                selected.getStyle().getColor().getValue(),
                "Selected job button labels should be highlighted."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerUpgradeRejectsUnregisteredTargetType(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        TowerType upgradeTarget = new TowerType("manual_fixture_t2", "Manual Fixture T2", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_starter",
                "Manual Fixture Starter",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType);
        try {
            ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", upgradeTarget, 0);
        } catch (IllegalArgumentException expected) {
            context.succeed();
            return;
        }
        context.fail(Component.literal("Production catalog should reject upgrade targets that are not registered before linking."));
    }

    @GameTest
    public void towerUpgradeServiceAcceptsNonProductionEntityBackedTower(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-custom-runtime-upgrade-owner");
        TowerType upgradeTarget = new TowerType("manual_fixture_custom_t2", "Manual Fixture Custom T2", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_custom_starter",
                "Manual Fixture Custom Starter",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType, FixtureSupportTower::new);
        ProductionTowerCatalog.register(upgradeTarget, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", upgradeTarget, 0);
        SemionJob testJob = registerTowerAllowingJob(
                "custom_runtime_upgrade",
                Set.of(starterType.id(), upgradeTarget.id())
        );
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED,
                testJob.id()
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        kim.biryeong.semiontd.game.GridPosition gridPosition = new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        );
        lane.addTower(new FixtureSupportTower(
                starterType,
                playerId,
                TeamId.RED,
                1,
                gridPosition,
                gridPosition
        ));

        if (!assertEquals(
                context,
                1,
                ProductionTowerService.availableUpgrades(game, playerId, towerPos).size(),
                "Upgrade service should read upgrade options from generic Tower state, not ProductionTower runtime type."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "manual_upgrade"),
                "Generic entity-backed towers should upgrade through catalog links."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerRejectsUnknownUpgradeId(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-production-upgrade-reject");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType targetType = new TowerType("manual_fixture_known_target", "Manual Fixture Known Target", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_unknown_upgrade",
                "Manual Fixture Unknown Upgrade",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType);
        ProductionTowerCatalog.register(targetType, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "known_upgrade", "Known Upgrade", targetType, 0);

        lane.addTower(new ProductionTower(
                starterType,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        ));
        if (!assertEquals(
                context,
                TowerUpgradeResult.UNKNOWN_UPGRADE,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "missing_branch"),
                "Unknown production upgrade ids should be rejected."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerRejectsNonOwnerUpgrade(GameTestHelper context) {
        UUID playerId = stableUuid("red-production-upgrade-viewer");
        UUID ownerId = stableUuid("red-production-upgrade-real-owner");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        lane.addTower(new ProductionTower(
                productionFixtureType("manual_fixture_non_owner", List.of(new TowerUpgradeOption(
                        "manual_upgrade",
                        "Manual Upgrade",
                        new TowerType("manual_fixture_non_owner_target", "Manual Fixture Non Owner Target", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0),
                        0
                ))),
                ownerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        ));
        game.players().get(playerId).economy().addMineral(500);

        if (!assertEquals(
                context,
                TowerUpgradeResult.TOWER_NOT_OWNED,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "militia_net"),
                "Players should get an explicit not-owned result when upgrading another player's production tower."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                ProductionTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(),
                "Other players should not see upgrade options for a production tower they do not own."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerCatalogUsesVanillaMobVisuals(GameTestHelper context) {
        if (!assertTrue(
                context,
                ProductionTowerCatalog.all().stream()
                        .noneMatch(entry -> "minecraft:armor_stand".equals(entry.type().entityTypeId())),
                "Production tower catalog should not render towers as armor stands."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void hudScaleUsesQaMultiplier(GameTestHelper context) {
        if (!assertEquals(
                context,
                3.0F,
                SemionDisplayHudService.HUD_SCALE_MULTIPLIER,
                "Display HUD scale multiplier should match QA decision."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void placingTestTowerConsumesMineralAndSpawnsEntity(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, towerPos);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, result, "Test tower placement should succeed.")) {
            return;
        }
        if (!assertEquals(
                context,
                EconomyConfig.defaultConfig().startingMineral() - TestTowerTypes.TEST_DIRECT.mineralCost(),
                game.players().get(playerId).economy().mineral(),
                "Test tower should consume its mineral cost."
        )) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Lane should contain one placed tower.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Placed tower should spawn a tracked entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity,
                "Placed tower should spawn a SemionTowerEntity."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buyingTowerLimitAddsPlayerSpecificTowerSlots(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-limit-buyer");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        game.players().get(playerId).assignTraitLoadout(new TraitLoadout(
                BuiltInTraits.SUPPLY_DEPOT_ID,
                BuiltInTraits.NONE_ID
        ));
        PlayerEconomy economy = game.players().get(playerId).economy();
        int roundLimit = game.towerLimitForCurrentRound();
        int traitLimit = roundLimit + 4;
        long diamondCost = game.economyConfig().towerLimit().initialPurchaseDiamondCost();
        long emeraldCost = game.economyConfig().towerLimit().initialPurchaseEmeraldCost();

        if (!assertEquals(context, 9, traitLimit, "Primary Supply Depot should raise the initial tower limit from 5 to 9.")) {
            return;
        }
        if (!assertEquals(context, traitLimit, game.towerLimitForPlayer(playerId), "Supply Depot should add four tower slots.")) {
            return;
        }
        if (!assertTrue(context, game.purchaseTowerLimit(playerId), "Player should be able to buy an extra tower slot.")) {
            return;
        }
        if (!assertEquals(context, traitLimit + game.economyConfig().towerLimit().purchaseIncreaseAmount(), game.towerLimitForPlayer(playerId), "Purchased slots should stack with Supply Depot.")) {
            return;
        }
        if (!assertEquals(context, EconomyConfig.defaultConfig().startingMineral() - diamondCost, economy.mineral(), "Tower slot purchase should spend the configured diamond cost.")) {
            return;
        }
        if (!assertEquals(context, EconomyConfig.defaultConfig().startingGas() - emeraldCost, economy.gas(), "Tower slot purchase should spend the configured emerald cost.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void placingTestTowerOutsideLanePathIsRejected(GameTestHelper context) {
        UUID playerId = stableUuid("red-outside-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, towerPlacementPos(lane).offset(20, 0, 20));

        if (!assertEquals(
                context,
                TowerPlacementResult.OUTSIDE_LANE_AREA,
                result,
                "Tower placement outside lane_path should be rejected."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void placingTestTowerFromAirUsesGroundedLaneBlock(GameTestHelper context) {
        UUID playerId = stableUuid("red-air-placement-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos laneColumn = towerPlacementPos(lane);
        BlockPos floorPos = new BlockPos(
                laneColumn.getX(),
                lane.laneLayout().laneArea().min().getY() - 1,
                laneColumn.getZ()
        );
        BlockPos sourcePos = floorPos.above(8);
        context.getLevel().setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, sourcePos);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                result,
                "Tower placement from air above lane_path should resolve to the ground block."
        )) {
            return;
        }
        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(
                context,
                GridPosition.from(floorPos),
                tower.position(),
                "Placed tower should store the grounded block position instead of the air source."
        )) {
            return;
        }
        SemionTowerEntity entity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        if (!assertEquals(
                context,
                floorPos.getY() + 1.0,
                entity.getY(),
                "Placed tower entity should stand on top of the grounded block."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sellingTowerBeforeWaveRefundsFullCost(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-sell-full");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        long startingMineral = game.players().get(playerId).economy().mineral();
        long towerCost = TestTowerTypes.TEST_DIRECT.mineralCost();

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before full refund sale."
        )) {
            return;
        }

        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, playerId, towerPos);
        if (!assertEquals(context, TowerSellResult.SUCCESS, sale.result(), "Tower sale should succeed before wave starts.")) {
            return;
        }
        if (!assertEquals(context, towerCost, sale.refundAmount(), "Tower sold before its first wave should refund the full paid cost.")) {
            return;
        }
        if (!assertEquals(context, startingMineral, game.players().get(playerId).economy().mineral(), "Full refund should restore the starting mineral balance.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Sold tower should be removed from its lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sellingTowerAfterWaveStartsRefundsHalfCost(GameTestHelper context) {
        UUID redId = stableUuid("red-tower-sell-half");
        UUID blueId = stableUuid("blue-tower-sell-half");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        long towerCost = TestTowerTypes.TEST_DIRECT.mineralCost();

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, redId, towerPos),
                "Test tower placement should succeed before half refund sale."
        )) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Game should be in wave phase before half refund sale.")) {
            return;
        }

        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, redId, towerPos);
        if (!assertEquals(context, TowerSellResult.SUCCESS, sale.result(), "Tower sale should succeed after wave starts.")) {
            return;
        }
        if (!assertEquals(context, Math.round(towerCost * 0.5), sale.refundAmount(), "Tower sold after wave starts should refund half of its paid cost.")) {
            return;
        }
        if (!assertEquals(
                context,
                EconomyConfig.defaultConfig().startingMineral() - towerCost + Math.round(towerCost * 0.5),
                game.players().get(redId).economy().mineral(),
                "Half refund should return exactly half the paid mineral cost."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Sold tower should be removed after wave-start sale.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void testTowerCanEvolveIntoAnotherTowerType(GameTestHelper context) {
        UUID playerId = stableUuid("red-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before upgrade."
        )) {
            return;
        }

        game.players().get(playerId).economy().addMineral(100);
        TestTower placedTower = (TestTower) lane.towers().getFirst();
        int previousEntityId = placedTower.entityId().orElse(-1);

        if (!assertEquals(
                context,
                2,
                TestTowerService.availableUpgrades(game, playerId, towerPos).size(),
                "Base test tower should expose two evolution choices."
        )) {
            return;
        }

        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                TestTowerService.upgradeTestTower(game, playerId, towerPos, "guard"),
                "Test tower should evolve into the selected target type."
        )) {
            return;
        }

        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Upgraded tower should still be a TestTower runtime object.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, "test_guard", evolvedTower.type().id(), "Tower should evolve into the guard type.")) {
            return;
        }
        if (!assertTrue(context, evolvedTower.entityId().isPresent(), "Evolved tower should spawn a replacement entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                evolvedTower.entityId().getAsInt() != previousEntityId,
                "Tower evolution should replace the old live entity."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                60L,
                game.players().get(playerId).economy().mineral(),
                "Tower evolution should spend the configured mineral cost."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void testTowerRejectsUnknownEvolutionId(GameTestHelper context) {
        UUID playerId = stableUuid("red-upgrade-reject-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before invalid upgrade."
        )) {
            return;
        }

        if (!assertEquals(
                context,
                TowerUpgradeResult.UNKNOWN_UPGRADE,
                TestTowerService.upgradeTestTower(game, playerId, towerPos, "missing"),
                "Unknown evolution ids should be rejected."
        )) {
            return;
        }
        context.succeed();
    }
    @GameTest
    public void evolvedSniperTowerCanEvolveIntoDeadeye(GameTestHelper context) {
        UUID playerId = stableUuid("red-sniper-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, TestTowerService.placeTestTower(game, playerId, towerPos), "Base test tower placement should succeed before chained upgrade.")) {
            return;
        }

        game.players().get(playerId).economy().addMineral(300);
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "sniper"), "Base tower should evolve into sniper.")) {
            return;
        }
        if (!assertEquals(context, 1, TestTowerService.availableUpgrades(game, playerId, towerPos).size(), "Sniper should expose exactly one follow-up evolution.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "deadeye"), "Sniper should evolve into deadeye.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, TestTowerTypes.TEST_DEADEYE.id(), evolvedTower.type().id(), "Sniper evolution should end at deadeye.")) {
            return;
        }
        if (!assertTrue(context, TestTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(), "Deadeye should be a leaf evolution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void evolvedGuardTowerCanEvolveIntoBastion(GameTestHelper context) {
        UUID playerId = stableUuid("red-guard-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, TestTowerService.placeTestTower(game, playerId, towerPos), "Base test tower placement should succeed before guard chain.")) {
            return;
        }

        game.players().get(playerId).economy().addMineral(300);
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "guard"), "Base tower should evolve into guard.")) {
            return;
        }
        if (!assertEquals(context, 1, TestTowerService.availableUpgrades(game, playerId, towerPos).size(), "Guard should expose exactly one follow-up evolution.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "bastion"), "Guard should evolve into bastion.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, TestTowerTypes.TEST_BASTION.id(), evolvedTower.type().id(), "Guard evolution should end at bastion.")) {
            return;
        }
        if (!assertTrue(context, TestTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(), "Bastion should be a leaf evolution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedDamageTowerUsesArrowAttackSoundCue(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-sound-owner");
        var position = new kim.biryeong.semiontd.game.GridPosition(1, 2, 3);
        TowerType rangedDamageType = new TowerType("sound_ranged", "Sound Ranged", TowerCategory.DIRECT, 0, 50.0, 8.0, 10.0, 20, 0);
        TowerType closeDamageType = new TowerType("sound_close", "Sound Close", TowerCategory.DIRECT, 0, 50.0, 2.0, 10.0, 20, 0);
        TowerType rangedSupportType = new TowerType("sound_support", "Sound Support", TowerCategory.SUPPORT, 0, 50.0, 8.0, 0.0, 20, 0);

        SemionTowerEntity ranged = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        ranged.configure(new TestTower(rangedDamageType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, ranged.playsRangedAttackSound(), "Damage-dealing ranged towers should play the arrow attack sound cue.")) {
            return;
        }

        SemionTowerEntity close = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        close.configure(new TestTower(closeDamageType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, !close.playsRangedAttackSound(), "Close-range towers should not use the ranged arrow sound cue.")) {
            return;
        }

        SemionTowerEntity support = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        support.configure(new TestTower(rangedSupportType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, !support.playsRangedAttackSound(), "Non-damage support towers should not use the ranged arrow sound cue.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void musicLibraryReadsConfigOggDurations(GameTestHelper context) {
        try {
            Path musicDir = Files.createTempDirectory("semion-music-library");
            Files.write(musicDir.resolve("Opening Theme!.ogg"), syntheticOggVorbis(48_000, 120_000));

            SemionMusicLibrary library = SemionMusicLibrary.load(musicDir, LoggerFactory.getLogger("semion-music-test"));
            if (!assertEquals(context, 1, library.tracks().size(), "Music library should load OGG tracks from the config music directory.")) {
                return;
            }
            SemionMusicTrack track = library.tracks().getFirst();
            if (!assertEquals(context, "opening_theme", track.id(), "Music track ids should be resource-pack safe.")) {
                return;
            }
            if (!assertEquals(context, 50L, track.durationTicks(), "Music library should record OGG playback duration in ticks.")) {
                return;
            }
            if (!assertEquals(context, ResourceLocation.fromNamespaceAndPath("semion-td", "music.opening_theme"), track.eventId(), "Music event ids should use the Semion TD namespace.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Music library test setup failed: " + exception.getMessage()));
        }
    }

    @GameTest
    public void musicResourcePackInjectsOggAssetsAndSoundsJson(GameTestHelper context) {
        try {
            Path musicDir = Files.createTempDirectory("semion-music-pack");
            Files.write(musicDir.resolve("Round One.ogg"), syntheticOggVorbis(44_100, 88_200));
            SemionMusicLibrary library = SemionMusicLibrary.load(musicDir, LoggerFactory.getLogger("semion-music-test"));
            CapturingResourcePackBuilder builder = new CapturingResourcePackBuilder();

            SemionMusicResourcePack.addToResourcePack(library, builder, LoggerFactory.getLogger("semion-music-test"));

            if (!assertTrue(
                    context,
                    builder.data().containsKey("assets/semion-td/sounds/music/round_one.ogg"),
                    "Music resource pack hook should copy config OGG files into generated sound assets."
            )) {
                return;
            }
            String soundsJson = builder.getStringData("assets/semion-td/sounds.json");
            if (!assertTrue(context, soundsJson != null && soundsJson.contains("\"music.round_one\""), "Music resource pack hook should register the sound event.")) {
                return;
            }
            if (!assertTrue(context, soundsJson.contains("\"name\": \"semion-td:music/round_one\""), "sounds.json should point at the copied sound file.")) {
                return;
            }
            if (!assertTrue(context, soundsJson.contains("\"stream\": true"), "Music sounds should be streamed by the client.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Music resource pack test setup failed: " + exception.getMessage()));
        }
    }

    @GameTest
    public void musicPlaybackTimerAvoidsMidTrackRestarts(GameTestHelper context) {
        Path fakeSource = Path.of("music.ogg");
        SemionMusicTrack first = new SemionMusicTrack(
                "first",
                fakeSource,
                ResourceLocation.fromNamespaceAndPath("semion-td", "music.first"),
                ResourceLocation.fromNamespaceAndPath("semion-td", "music/first"),
                40L
        );
        SemionMusicTrack second = new SemionMusicTrack(
                "second",
                fakeSource,
                ResourceLocation.fromNamespaceAndPath("semion-td", "music.second"),
                ResourceLocation.fromNamespaceAndPath("semion-td", "music/second"),
                60L
        );
        SemionMusicTrack third = new SemionMusicTrack(
                "third",
                fakeSource,
                ResourceLocation.fromNamespaceAndPath("semion-td", "music.third"),
                ResourceLocation.fromNamespaceAndPath("semion-td", "music/third"),
                50L
        );
        SemionMusicService service = new SemionMusicService(new SemionMusicLibrary(List.of(first, second, third)), () -> 100L, bound -> 1);
        UUID playerId = stableUuid("music-player");

        SemionMusicService.PlaybackDecision initial = service.decisionFor(playerId, 0L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, initial.action(), "Music should start when a player's client is at the beginning of a track.")) {
            return;
        }
        SemionMusicService.PlaybackDecision midTrackReconnect = service.decisionFor(playerId, 25L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.WAIT_FOR_NEXT_TRACK, midTrackReconnect.action(), "Reconnects or world changes mid-track should wait for the next track instead of restarting from an impossible offset.")) {
            return;
        }
        SemionMusicService.PlaybackDecision interTrackGap = service.decisionFor(playerId, 40L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.WAIT_FOR_NEXT_TRACK, interTrackGap.action(), "Music should keep a silent gap after a track ends.")) {
            return;
        }
        if (!assertTrue(context, interTrackGap.track() == null, "Inter-track music gaps should not select a sound event.")) {
            return;
        }
        SemionMusicService.PlaybackDecision nextTrack = service.decisionFor(playerId, 140L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, nextTrack.action(), "Stopped clients should resume when the next track boundary arrives.")) {
            return;
        }
        if (!assertEquals(context, third.eventId(), nextTrack.track().eventId(), "The next music boundary should choose an unplayed randomized track instead of always advancing sequentially.")) {
            return;
        }
        SemionMusicService.PlaybackDecision lastUnplayedTrack = service.decisionFor(playerId, 290L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, lastUnplayedTrack.action(), "Music should keep picking unplayed tracks before repeating the playlist.")) {
            return;
        }
        if (!assertEquals(context, second.eventId(), lastUnplayedTrack.track().eventId(), "Music should play every configured track once before any track repeats.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void musicInterTrackGapIsRandomizedWithinFiveToTenSeconds(GameTestHelper context) {
        for (int attempt = 0; attempt < 100; attempt++) {
            long gapTicks = SemionMusicService.randomInterTrackGapTicks();
            if (!assertTrue(
                    context,
                    gapTicks >= SemionMusicService.MIN_INTER_TRACK_GAP_TICKS
                            && gapTicks <= SemionMusicService.MAX_INTER_TRACK_GAP_TICKS,
                    "Music inter-track gaps should stay between five and ten seconds."
            )) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 80)
    public void testTowerEntityDamagesLaneMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-combat-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType highRangeType = new TowerType("damage_test", "Damage Test", TowerCategory.DIRECT, 0, 50.0, 30.0, 20.0, 5, 0);
        lane.addTower(new TestTower(highRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-target",
                40.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Lane should spawn one monster for the tower test.")) {
            return;
        }

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.fail(Component.literal("Damage test monster entity should exist."));
                return;
            }
            monsterEntity.setNoAi(true);
        });

        context.runAfterDelay(80, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.succeed();
                return;
            }

            if (!assertTrue(
                    context,
                    monsterEntity.getHealth() < 40.0F,
                    "Test tower entity should damage the monster through its own attack goal."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void towerEntityPrioritizesInRangeMonsterBeforeFarProgressTarget(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-range-priority-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType towerType = new TowerType("range_priority_test", "Range Priority Test", TowerCategory.DIRECT, 0, 50.0, 3.5, 10.0, 100, 0);
        lane.addTower(new TestTower(towerType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Range priority tower entity should exist.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();
        SemionMonsterEntity nearTarget = spawnRoleMonsterEntity(
                context,
                "near-range-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(2.0, 0.0, 0.0),
                40.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity farProgressTarget = spawnRoleMonsterEntity(
                context,
                "far-progress-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(8.0, 0.0, 0.0),
                40.0,
                List.of(SummonRole.SIEGE)
        );
        nearTarget.runtimeMonster().syncLaneProgress(0.1);
        farProgressTarget.runtimeMonster().syncLaneProgress(0.95);
        nearTarget.setNoAi(true);
        farProgressTarget.setNoAi(true);

        context.runAfterDelay(20, () -> {
            if (!assertTrue(
                    context,
                    nearTarget.getHealth() < 40.0F,
                    "Tower should attack an in-range monster before chasing a farther high-progress target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    nearTarget.getHealth() < farProgressTarget.getHealth(),
                    "In-range target should take priority over a farther high-progress target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void defaultTowerTargetingSplitsEqualPriorityTargets(GameTestHelper context) {
        UUID playerId = stableUuid("red-target-jitter-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        TowerType towerType = new TowerType("target_jitter_test", "Target Jitter Test", TowerCategory.DIRECT, 0, 50.0, 8.0, 0.0, 100, 0);
        List<SemionMonsterEntity> monsters = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            SemionMonsterEntity monster = spawnRoleMonsterEntity(
                    context,
                    "jitter-monster-" + index,
                    Optional.empty(),
                    TeamId.RED,
                    1,
                    origin.add(index * 1.5, 0.0, 4.0),
                    100.0,
                    List.of(SummonRole.RUSH)
            );
            monster.setUUID(stableUuid("jitter-monster-" + index));
            monster.runtimeMonster().syncLaneProgress(0.5);
            monster.setNoAi(true);
            monsters.add(monster);
        }

        List<SemionTowerEntity> towers = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Vec3 position = origin.add(index * 1.5, 0.0, 0.0);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.configure(new TestTower(towerType, playerId, TeamId.RED, 1, GridPosition.from(BlockPos.containing(position))), null);
            tower.setUUID(stableUuid("jitter-tower-" + index));
            tower.setPos(position);
            context.getLevel().addFreshEntity(tower);
            towers.add(tower);
        }

        context.runAfterDelay(20, () -> {
            Set<SemionMonsterEntity> selectedTargets = towers.stream()
                    .map(SemionTowerEntity::currentAttackTarget)
                    .filter(target -> target != null)
                    .collect(Collectors.toSet());
            if (!assertTrue(context, selectedTargets.size() >= 2, "Equal-priority targets should be split across default towers.")) {
                return;
            }
            if (!assertTrue(context, monsters.containsAll(selectedTargets), "Default towers should only select valid equal-priority candidates.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void defaultTowerKeepsTargetUntilItLeavesSearchRange(GameTestHelper context) {
        UUID playerId = stableUuid("red-target-lock-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        TowerType towerType = new TowerType("target_lock_test", "Target Lock Test", TowerCategory.DIRECT, 0, 50.0, 8.0, 0.0, 20, 0);
        SemionMonsterEntity firstTarget = spawnRoleMonsterEntity(
                context,
                "target-lock-first",
                Optional.empty(),
                TeamId.RED,
                1,
                origin.add(0.0, 0.0, 4.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        firstTarget.setNoAi(true);
        firstTarget.runtimeMonster().syncLaneProgress(0.1);

        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(new TestTower(towerType, playerId, TeamId.RED, 1, GridPosition.from(BlockPos.containing(origin))), null);
        tower.setUUID(stableUuid("target-lock-tower"));
        tower.setPos(origin);
        context.getLevel().addFreshEntity(tower);

        context.runAfterDelay(10, () -> {
            if (!assertTrue(context, tower.currentAttackTarget() == firstTarget, "Tower should acquire the first target.")) {
                return;
            }

            SemionMonsterEntity higherPriorityTarget = spawnRoleMonsterEntity(
                    context,
                    "target-lock-higher-priority",
                    Optional.empty(),
                    TeamId.RED,
                    1,
                    origin.add(1.0, 0.0, 4.0),
                    100.0,
                    List.of(SummonRole.SIEGE)
            );
            higherPriorityTarget.setNoAi(true);
            higherPriorityTarget.runtimeMonster().syncLaneProgress(0.95);

            context.runAfterDelay(20, () -> {
                if (!assertTrue(context, tower.currentAttackTarget() == firstTarget, "Tower should keep a valid cached target instead of reselecting by priority.")) {
                    return;
                }

                firstTarget.setPos(origin.add(128.0, 0.0, 128.0));
                context.runAfterDelay(5, () -> {
                    if (!assertTrue(context, tower.currentAttackTarget() == higherPriorityTarget, "Tower should reselect after the cached target leaves search range.")) {
                        return;
                    }
                    context.succeed();
                });
            });
        });
    }

    @GameTest
    public void illagerMarkOverridesCachedTargetForNearbyIllagerTower(GameTestHelper context) {
        UUID playerId = stableUuid("illager-forced-target-owner");
        int testLaneId = 99;
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        GridPosition towerPosition = GridPosition.from(BlockPos.containing(origin));
        TowerType towerType = new TowerType("illager_forced_target", "Illager Forced Target", TowerCategory.DIRECT, 0, 50.0, 8.0, 0.0, 100, 0);
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(new IllagerTower(towerType, playerId, TeamId.RED, testLaneId, towerPosition, towerPosition), null);
        tower.setPos(origin);
        context.getLevel().addFreshEntity(tower);

        SemionMonsterEntity cachedTarget = spawnRoleMonsterEntity(
                context,
                "illager-forced-cached",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                origin.add(0.0, 0.0, 4.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        cachedTarget.setNoAi(true);
        cachedTarget.runtimeMonster().syncLaneProgress(0.9);
        context.runAfterDelay(1, () -> {
            TowerAttackMonsterGoal targetingGoal = new TowerAttackMonsterGoal(tower);
            targetingGoal.tick();
            if (!assertTrue(context, tower.currentAttackTarget() == cachedTarget, "Illager tower should cache its original target before a mark appears.")) {
                return;
            }

            SemionMonsterEntity markedTarget = spawnRoleMonsterEntity(
                    context,
                    "illager-forced-marked",
                    Optional.empty(),
                    TeamId.RED,
                    testLaneId,
                    origin.add(1.0, 0.0, 4.0),
                    100.0,
                    List.of(SummonRole.RUSH)
            );
            markedTarget.setNoAi(true);
            markedTarget.runtimeMonster().syncLaneProgress(0.1);
            IllagerMarks.apply(markedTarget.runtimeMonster(), playerId, 0.2, 100, towerPosition, 2.0);

            targetingGoal.tick();
            if (!assertTrue(context, tower.currentAttackTarget() == markedTarget, "Nearby illager towers should replace cached targets with an active forced mark.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void foxTowerPrioritizesLowHealthTargetInRuntimeCombat(GameTestHelper context) {
        UUID playerId = stableUuid("red-fox-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType foxType = TowerBalanceRuntime.resolve(AnimalTowers.T1_FOX_TOWER);
        lane.addTower(new FoxTower(foxType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));
        FoxTower foxTower = (FoxTower) lane.towers().getFirst();
        if (!assertTrue(context, foxTower.entityId().isPresent(), "Fox tower entity should exist.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(foxTower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();

        SemionMonsterEntity healthyClose = spawnRoleMonsterEntity(
                context,
                "fox-healthy-close",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        SemionMonsterEntity lowHealthFar = spawnRoleMonsterEntity(
                context,
                "fox-low-health-far",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(2.0, 0.0, 0.0),
                200.0,
                List.of(SummonRole.RUSH)
        );
        healthyClose.runtimeMonster().syncLaneProgress(0.95);
        lowHealthFar.runtimeMonster().syncLaneProgress(0.10);
        healthyClose.setNoAi(true);
        lowHealthFar.setNoAi(true);
        lowHealthFar.setHealth(50.0F);
        if (!assertTrue(
                context,
                towerEntity.selectAttackTarget(List.of(healthyClose, lowHealthFar)) == lowHealthFar,
                "Fox tower policy should prefer the low-health target before combat ticks."
        )) {
            return;
        }

        context.runAfterDelay(18, () -> {
            if (!assertTrue(
                    context,
                    towerEntity.currentAttackTarget() == lowHealthFar,
                    "Fox tower should select the low-health in-range target before the healthier progress target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lowHealthFar.getHealth() < 50.0F,
                    "Fox tower should damage the low-health execute target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lowHealthFar.getHealth() < healthyClose.getHealth(),
                    "Low-health target should take more damage than the healthier progress target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void foxTowerGainsKillBonusDamageAfterNearbyMonsterDeath(GameTestHelper context) {
        UUID playerId = stableUuid("red-fox-kill-bonus-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 deathPosition = lane.laneLayout().positionAt(0.0);
        BlockPos towerPos = BlockPos.containing(deathPosition.x, deathPosition.y - 1.0, deathPosition.z);
        TowerType foxType = TowerBalanceRuntime.resolve(AnimalTowers.T1_FOX_TOWER);
        lane.addTower(new FoxTower(foxType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));
        FoxTower foxTower = (FoxTower) lane.towers().getFirst();
        if (!assertTrue(context, foxTower.entityId().isPresent(), "Fox tower entity should exist.")) {
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(foxTower.entityId().getAsInt());
        SemionMonsterEntity damageProbe = spawnRoleMonsterEntity(
                context,
                "fox-nearby-death-damage-probe",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        damageProbe.setNoAi(true);
        damageProbe.setHealth(20.0F);

        double beforeKillDamage = towerEntity.attackDamageAmount(damageProbe);
        Monster nearbyMonster = deathStackTestMonster("fox-nearby-death-target", Optional.empty(), TeamId.RED, 1);
        nearbyMonster.syncLaneProgress(0.0);
        nearbyMonster.syncHealth(0.0);
        lane.activeMonsters().add(nearbyMonster);
        lane.tick(context.getLevel().getServer());
        double afterKillDamage = towerEntity.attackDamageAmount(damageProbe);

        if (!assertTrue(
                context,
                afterKillDamage > beforeKillDamage,
                "Fox tower should gain attack damage after a monster dies nearby."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                foxTower.runtimeDetailLines().stream().anyMatch(line -> line.contains("사망 보너스 1/100")),
                "Fox tower runtime details should show the nearby death bonus stack."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerDamageAppliesTraitsTargetModifiersArmorAndSingleReward(GameTestHelper context) {
        UUID playerId = stableUuid("runtime-damage-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO)).add(2.0, 2.0, 2.0);
        TestTower tower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(BlockPos.containing(origin))
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(tower, null);
        towerEntity.setPos(origin);
        towerEntity.applyTimedEffect(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, 0.20, 40);
        context.getLevel().addFreshEntity(towerEntity);

        Monster runtimeMonster = new Monster(
                "runtime-damage-target",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                100.0,
                20.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                10
        );
        SemionMonsterEntity target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        target.configureFrom(runtimeMonster, null);
        target.setPos(origin.add(2.0, 0.0, 0.0));
        target.setNoAi(true);
        target.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 0.25, 40);
        context.getLevel().addFreshEntity(target);

        boolean firstHitKilled = tower.damageTarget(towerEntity, target, 100.0);
        if (!assertTrue(context, !firstHitKilled, "First hit should leave the armored target alive.")) {
            return;
        }
        if (!assertClose(context, 25.0, runtimeMonster.health(), "Outgoing bonus, target reduction, then armor should produce 75 damage.")) {
            return;
        }
        if (!assertClose(context, runtimeMonster.health(), target.getHealth(), "Runtime and entity health should stay synchronized.")) {
            return;
        }
        if (!assertTrue(context, runtimeMonster.lastHitPlayerId().filter(playerId::equals).isPresent(), "Tower owner should be recorded as the last hitter.")) {
            return;
        }
        if (!assertTrue(context, runtimeMonster.lastHitSourceKind() == KillSourceKind.TOWER, "Tower damage should record the tower kill source.")) {
            return;
        }

        if (!assertTrue(context, tower.damageTarget(towerEntity, target, 40.0), "Second hit should kill the target.")) {
            return;
        }
        SemionPlayer player = new SemionPlayer(
                playerId,
                "runtime-damage-owner",
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        EconomyService economyService = new EconomyService(EconomyConfig.defaultConfig());
        economyService.awardMonsterKillReward(runtimeMonster, Map.of(playerId, player));
        economyService.awardMonsterKillReward(runtimeMonster, Map.of(playerId, player));
        if (!assertEquals(context, 210L, player.economy().diamond(), "Monster reward should be granted exactly once.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 100)
    public void beeTowerPoisonDealsConfigDrivenRuntimeDamage(GameTestHelper context) {
        UUID playerId = stableUuid("red-bee-tower-owner");
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>(defaults.towers());
        TowerType baseBee = LegionTowers.T1_BEE_TOWER;
        towers.put(baseBee.id(), new TowerBalanceConfig.TowerStats(
                baseBee.mineralCost(),
                baseBee.maxHealth(),
                baseBee.range(),
                0.0,
                10,
                baseBee.aggroPriority()
        ));
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        abilities.put(baseBee.id(), Map.of(
                "maxSwarmStacks", 1.0,
                "poisonDamagePerStack", 5.0,
                "poisonDamagePerSwarmStack", 0.0,
                "maxPoisonStacks", 2.0,
                "poisonStacksPerSwarmStack", 0.0,
                "poisonDurationTicks", 40.0,
                "poisonTickIntervalTicks", 5.0
        ));
        TowerBalanceRuntime.apply(new TowerBalanceConfig(towers, defaults.upgradeCosts(), abilities));

        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType beeType = TowerBalanceRuntime.resolve(baseBee);
        lane.addTower(new BeeTower(beeType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));
        BeeTower beeTower = (BeeTower) lane.towers().getFirst();
        if (!assertTrue(context, beeTower.entityId().isPresent(), "Bee tower entity should exist.")) {
            TowerBalanceRuntime.apply(defaults);
            return;
        }
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(beeTower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "bee-poison-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(3.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        target.setNoAi(true);

        for (int sting = 0; sting < 3; sting++) {
            beeTower.onAttack(towerEntity, target, 0.0, false);
            for (int tick = 0; tick < 5; tick++) {
                beeTower.tick(lane);
            }
        }
        TowerBalanceRuntime.apply(defaults);
        if (!assertTrue(
                context,
                target.getHealth() <= 80.0F,
                "Bee tower has zero direct damage in this test, so health loss should come from config-driven poison. Actual health=" + target.getHealth()
        )) {
            return;
        }
        if (!assertClose(context, target.runtimeMonster().health(), target.getHealth(), "Bee poison should synchronize runtime and entity health.")) {
            return;
        }
        if (!assertTrue(context, target.runtimeMonster().lastHitSourceKind() == KillSourceKind.TOWER, "Bee poison should preserve tower kill attribution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 80)
    public void illusionCloneAttacksSharedSourceTargetInsteadOfScanningOwnTarget(GameTestHelper context) {
        UUID playerId = stableUuid("red-clone-shared-target-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        TowerType sourceType = new TowerType("shared_target_source", "Shared Target Source", TowerCategory.DIRECT, 0, 50.0, 6.0, 0.0, 100, 0);
        TowerType cloneType = new TowerType("shared_target_clone", "Shared Target Clone", TowerCategory.DIRECT, 0, 50.0, 6.0, 10.0, 10, 0);
        SemionTowerEntity sourceEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        sourceEntity.configure(new TestTower(sourceType, playerId, TeamId.RED, 1, GridPosition.from(context.absolutePos(BlockPos.ZERO))), null);
        sourceEntity.setNoAi(true);
        sourceEntity.setPos(origin);
        context.getLevel().addFreshEntity(sourceEntity);

        SemionTowerEntity cloneEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        cloneEntity.configure(new TestTower(cloneType, playerId, TeamId.RED, 1, GridPosition.from(context.absolutePos(BlockPos.ZERO.east(4)))), null);
        cloneEntity.useAttackTargetFrom(sourceEntity);
        cloneEntity.setPos(origin.add(4.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(cloneEntity);

        SemionMonsterEntity sharedTarget = spawnRoleMonsterEntity(
                context,
                "shared-source-target",
                Optional.empty(),
                TeamId.RED,
                1,
                origin.add(6.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity closerOwnTarget = spawnRoleMonsterEntity(
                context,
                "closer-clone-target",
                Optional.empty(),
                TeamId.RED,
                1,
                origin.add(5.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        sharedTarget.setNoAi(true);
        closerOwnTarget.setNoAi(true);
        sourceEntity.recordCurrentAttackTarget(sharedTarget);

        context.runAfterDelay(30, () -> {
            if (!assertTrue(context, sharedTarget.getHealth() < 100.0F, "Clone should damage the source tower's shared target.")) {
                return;
            }
            if (!assertEquals(context, 100.0F, closerOwnTarget.getHealth(), "Clone should not run its own target scan while a source target is shared.")) {
                return;
            }
            if (!assertTrue(context, cloneEntity.currentAttackTarget() == sharedTarget, "Clone should expose the source-selected monster as its current target.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void testTowerMovesTowardOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-anchor-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType lowRangeType = new TowerType("anchor_test", "Anchor Test", TowerCategory.DIRECT, 0, 50.0, 1.0, 12.0, 20, 0);
        lane.addTower(new TestTower(lowRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-move-target",
                200.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Lane should spawn one monster for the movement test.")) {
            return;
        }

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.fail(Component.literal("Anchor test monster entity should exist."));
                return;
            }
            monsterEntity.setNoAi(true);

            TestTower tower = (TestTower) lane.towers().getFirst();
            if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should still exist.")) {
                return;
            }
            if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
                context.fail(Component.literal("Tower entity should still be present in the arena world."));
                return;
            }
            Vec3 initialTowerPosition = towerEntity.position();
            double initialDistance = initialTowerPosition.distanceTo(monsterEntity.position());

            context.runAfterDelay(40, () -> {
                if (!assertTrue(
                        context,
                        lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity,
                        "Anchor test monster entity should still exist."
                )) {
                    return;
                }
                SemionMonsterEntity currentMonsterEntity = (SemionMonsterEntity) lane.arenaWorld().getEntity(monsterEntityId);

                if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity currentTowerEntity)) {
                    context.fail(Component.literal("Tower entity should still be present in the arena world."));
                    return;
                }
                Vec3 currentTowerPos = currentTowerEntity.position();
                if (!assertTrue(
                        context,
                        currentTowerPos.distanceTo(initialTowerPosition) > 0.1,
                        "Tower entity should move away from its initial position toward a live target that starts out of range."
                )) {
                    return;
                }
                if (!assertTrue(
                        context,
                        currentTowerPos.distanceTo(currentMonsterEntity.position()) < initialDistance,
                        "Tower entity should get closer to the out-of-range target."
                )) {
                    return;
                }
                context.succeed();
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void finalDefenseTowerDoesNotChaseOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-final-defense-anchor-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.PURPLE);
        PlayerLane lane = lane(game, TeamId.PURPLE, 1);
        TowerType shortRangeType = new TowerType(
                "final_defense_short_range",
                "Final Defense Short Range",
                TowerCategory.DIRECT,
                0,
                50.0,
                3.0,
                0.0,
                20,
                0
        );
        lane.addTower(new TestTower(
                shortRangeType,
                playerId,
                TeamId.PURPLE,
                1,
                GridPosition.from(towerPlacementPos(lane))
        ));

        GridPosition finalDefenseSlot = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        BlockPos finalDefenseAirPos = new BlockPos(finalDefenseSlot.x(), finalDefenseSlot.y(), finalDefenseSlot.z());
        context.getLevel().setBlock(finalDefenseAirPos.below(), Blocks.STONE.defaultBlockState(), 3);
        context.getLevel().setBlock(finalDefenseAirPos, Blocks.AIR.defaultBlockState(), 3);

        game.teams().get(TeamId.PURPLE).resetForRound();
        game.teams().get(TeamId.PURPLE).tick(context.getLevel().getServer());

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should be deployed at final defense before chase validation.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Final defense tower entity should exist before chase validation.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Final defense tower entity should be available."));
            return;
        }

        Vec3 initialTowerPosition = towerEntity.position();
        SemionMonsterEntity outOfRangeTarget = spawnRoleMonsterEntity(
                context,
                "final-defense-anchor-target",
                Optional.empty(),
                TeamId.PURPLE,
                1,
                initialTowerPosition.add(7.5, 0.0, 0.0),
                100000.0,
                List.of(SummonRole.RUSH)
        );
        outOfRangeTarget.setNoAi(true);
        outOfRangeTarget.runtimeMonster().syncLaneProgress(1.0);

        context.runAfterDelay(40, () -> {
            if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity currentTowerEntity)) {
                context.fail(Component.literal("Final defense tower entity should still be available."));
                return;
            }

            Vec3 currentTowerPosition = currentTowerEntity.position();
            double horizontalMovement = Math.hypot(
                    currentTowerPosition.x - initialTowerPosition.x,
                    currentTowerPosition.z - initialTowerPosition.z
            );
            if (!assertTrue(
                    context,
                    horizontalMovement < 0.05,
                    "Final defense tower should not chase targets outside its attack range."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.laneLayout().isInsideFinalDefenseTowerArea(currentTowerPosition),
                    "Final defense tower should stay inside the final defense tower area."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == null,
                    "Final defense tower should ignore monsters farther than seven blocks away."
            )) {
                return;
            }

            currentTowerEntity.setNoAi(true);
            TowerAttackMonsterGoal targetingGoal = new TowerAttackMonsterGoal(currentTowerEntity);
            outOfRangeTarget.setPos(initialTowerPosition.add(6.0, 0.0, 0.0));
            targetingGoal.tick();
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == outOfRangeTarget,
                    "Final defense tower should acquire a monster inside seven blocks."
            )) {
                return;
            }

            SemionMonsterEntity attackableTarget = spawnRoleMonsterEntity(
                    context,
                    "final-defense-attackable-target",
                    Optional.empty(),
                    TeamId.PURPLE,
                    1,
                    initialTowerPosition.add(2.0, 0.0, 0.0),
                    100000.0,
                    List.of(SummonRole.RUSH)
            );
            attackableTarget.setNoAi(true);
            attackableTarget.runtimeMonster().syncLaneProgress(1.0);
            targetingGoal.tick();
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == attackableTarget,
                    "An attackable target should replace a cached target outside attack range."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 160)
    public void laneMonsterDamagesTestTowerEntity(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-defense-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        BlockPos towerPos = towerPlacementPos(lane);
        TowerType dummyType = new TowerType("defense_dummy", "Defense Dummy", TowerCategory.DIRECT, 0, 50.0, 1.0, 1.0, 40, 100);
        lane.addTower(new TestTower(dummyType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should exist before combat.")) {
            return;
        }

        int towerEntityId = tower.entityId().getAsInt();
        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-breaker",
                120.0,
                0.0,
                2.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(120, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(towerEntityId) instanceof SemionTowerEntity,
                    "Tower entity should still exist while checking retaliation."
            )) {
                return;
            }

            SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(towerEntityId);
            if (!assertTrue(
                    context,
                    towerEntity.getHealth() < 50.0F,
                    "Lane monster should target and damage the tower entity."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void laneMonsterPrefersHigherAggroPriorityTower(GameTestHelper context) {
        UUID playerId = stableUuid("red-priority-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        BlockPos lowPriorityPos = towerPlacementPos(lane);
        BlockPos highPriorityPos = lowPriorityPos.offset(2, 0, 0);
        TowerType lowPriorityType = new TowerType("low_priority", "Low Priority", TowerCategory.DIRECT, 0, 50.0, 8.0, 12.0, 20, 0);
        TowerType highPriorityType = new TowerType("high_priority", "High Priority", TowerCategory.DIRECT, 0, 50.0, 8.0, 12.0, 20, 50);

        lane.addTower(new TestTower(lowPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                lowPriorityPos.getX(),
                lowPriorityPos.getY(),
                lowPriorityPos.getZ()
        )));
        lane.addTower(new TestTower(highPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                highPriorityPos.getX(),
                highPriorityPos.getY(),
                highPriorityPos.getZ()
        )));

        TestTower lowPriorityTower = (TestTower) lane.towers().get(0);
        TestTower highPriorityTower = (TestTower) lane.towers().get(1);
        if (!assertTrue(context, lowPriorityTower.entityId().isPresent(), "Low priority tower entity should exist.")) {
            return;
        }
        if (!assertTrue(context, highPriorityTower.entityId().isPresent(), "High priority tower entity should exist.")) {
            return;
        }

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "priority-breaker",
                120.0,
                0.0,
                12.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(20, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(highPriorityTower.entityId().getAsInt()) instanceof SemionTowerEntity,
                    "High priority tower entity should still exist."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity,
                    "Priority test monster entity should still exist."
            )) {
                return;
            }

            SemionMonsterEntity monsterEntity = (SemionMonsterEntity) lane.arenaWorld().getEntity(monsterEntityId);
            if (!assertTrue(
                    context,
                    monsterEntity.getTarget() != null
                            && monsterEntity.getTarget().getId() == highPriorityTower.entityId().getAsInt(),
                    "Monster should focus the higher aggro priority tower first."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void illusionSummonerSpawnsConfiguredTowerEntityClonesOnWaveStarted(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_fixture", "Illusion Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(2, 0, 0.25, 0.5, 1.5, 2.0, 1.0, 99)
        );
        lane.addTower(tower);

        if (!assertEquals(context, 1, lane.towers().size(), "Illusion summoner body should be the only lane catalog tower.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Illusion summoner body entity should spawn on placement.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity,
                "Illusion summoner body should be backed by a tower entity."
        )) {
            return;
        }

        lane.markWaveStarted(1);

        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 2, "Wave start should queue clones instead of spawning all clones immediately.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertTrue(context, tower.spawnedCloneEntities().size() > 0, "The first queued clone should spawn on the next global queue tick.")) {
            return;
        }
        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 2, "Clone spawning should stay distributed after one tick.")) {
            return;
        }
        for (int tick = 1; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 2, tower.spawnedCloneEntities().size(), "Wave start should spawn the configured clone count within 40 ticks.")) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Illusion clones should not be inserted into the lane tower list.")) {
            return;
        }

        for (SemionTowerEntity cloneEntity : tower.spawnedCloneEntities()) {
            if (!assertTrue(context, cloneEntity.isAlive(), "Spawned clone tower entity should be alive.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    cloneEntity.runtimeTower() instanceof IllusionRuntimeTower,
                    "Spawned clone should be backed by an illusion runtime tower."
            )) {
                return;
            }
            IllusionRuntimeTower cloneTower = (IllusionRuntimeTower) cloneEntity.runtimeTower();
            if (!assertEquals(context, "illusion_fixture#illusion", cloneTower.type().id(), "Clone type should use an internal illusion id.")) {
                return;
            }
            if (!assertEquals(context, playerId, cloneTower.ownerPlayer(), "Clone should keep the source owner.")) {
                return;
            }
            if (!assertEquals(context, TeamId.RED, cloneTower.teamId(), "Clone should keep the source team.")) {
                return;
            }
            if (!assertEquals(context, 1, cloneTower.laneId(), "Clone should keep the source lane.")) {
                return;
            }
            if (!assertClose(context, 25.0, cloneTower.currentMaxHealth(), "Clone health should use the configured ratio.")) {
                return;
            }
            if (!assertClose(context, 10.0, cloneTower.type().damage(), "Clone damage should use the configured ratio.")) {
                return;
            }
            if (!assertClose(context, 15.0, cloneTower.type().range(), "Clone range should use the configured ratio.")) {
                return;
            }
            if (!assertEquals(context, 24, cloneTower.type().attackIntervalTicks(), "Clone attack interval should use the configured multiplier.")) {
                return;
            }
            if (!assertEquals(context, 106, cloneTower.aggroPriority(), "Clone aggro should use the configured priority bonus.")) {
                return;
            }
        }

        List<SemionTowerEntity> firstRoundClones = List.copyOf(tower.spawnedCloneEntities());
        lane.moveTowersToFinalDefense();
        Set<GridPosition> finalDefensePositions = new java.util.HashSet<>();
        finalDefensePositions.add(tower.position());
        for (SemionTowerEntity cloneEntity : firstRoundClones) {
            if (!assertTrue(
                    context,
                    cloneEntity.runtimeTower() instanceof IllusionRuntimeTower,
                    "Final-defense clone should still be backed by an illusion runtime tower."
            )) {
                return;
            }
            IllusionRuntimeTower cloneTower = (IllusionRuntimeTower) cloneEntity.runtimeTower();
            finalDefensePositions.add(cloneTower.position());
            if (!assertTrue(context, cloneTower.deployedAtFinalDefense(), "Wave-cleared clone should move to final defense.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.laneLayout().isInsideFinalDefenseTowerArea(cloneEntity.position()),
                    "Wave-cleared clone entity should be positioned inside the final defense tower area."
            )) {
                return;
            }
        }
        if (!assertEquals(
                context,
                firstRoundClones.size() + 1,
                finalDefensePositions.size(),
                "Source tower and final-defense clones should use distinct shared slots while capacity remains."
        )) {
            return;
        }

        lane.resetForRound();
        for (SemionTowerEntity cloneEntity : firstRoundClones) {
            if (!assertTrue(context, cloneEntity.isRemoved(), "Round reset should discard existing illusion clones.")) {
                return;
            }
        }

        int previousCloneCount = tower.spawnedCloneEntities().size();
        lane.markWaveStarted(2);
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        List<SemionTowerEntity> secondRoundClones = tower.spawnedCloneEntities().subList(previousCloneCount, tower.spawnedCloneEntities().size());
        if (!assertEquals(context, 2, secondRoundClones.size(), "A later wave should spawn a fresh clone set within 40 ticks.")) {
            return;
        }
        if (!assertTrue(context, lane.removeTower(tower), "Removing the source tower should succeed.")) {
            return;
        }
        for (SemionTowerEntity cloneEntity : secondRoundClones) {
            if (!assertTrue(context, cloneEntity.isRemoved(), "Source removal should discard active illusion clones.")) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void illusionPendingCloneSpawnsCancelOnResetAndRemoval(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-cancel-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_cancel_fixture", "Illusion Cancel Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(10, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One clone should spawn before reset cancellation.")) {
            return;
        }
        SemionTowerEntity resetRoundClone = tower.spawnedCloneEntities().getFirst();
        lane.resetForRound();
        if (!assertTrue(context, resetRoundClone.isRemoved(), "Round reset should discard the already spawned clone.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "Round reset should cancel pending illusion clones.")) {
            return;
        }

        int cloneCountAfterReset = tower.spawnedCloneEntities().size();
        lane.markWaveStarted(2);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, cloneCountAfterReset + 1, tower.spawnedCloneEntities().size(), "One clone should spawn before removal cancellation.")) {
            return;
        }
        SemionTowerEntity removalRoundClone = tower.spawnedCloneEntities().get(cloneCountAfterReset);
        if (!assertTrue(context, lane.removeTower(tower), "Removing the illusion source should succeed.")) {
            return;
        }
        if (!assertTrue(context, removalRoundClone.isRemoved(), "Source removal should discard already spawned illusion clones.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, cloneCountAfterReset + 1, tower.spawnedCloneEntities().size(), "Source removal should cancel pending illusion clones.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameCloseDiscardsRuntimeEntitiesAndCancelsPendingIllusionClones(GameTestHelper context) {
        IllusionCloneSpawnQueue.clear();
        UUID playerId = stableUuid("red-illusion-close-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_close_fixture", "Illusion Close Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(10, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);
        int bodyEntityId = tower.entityId().orElse(-1);
        if (!assertTrue(context, bodyEntityId >= 0, "Illusion source tower entity should spawn before close.")) {
            return;
        }

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One clone should spawn before close.")) {
            return;
        }
        SemionTowerEntity spawnedClone = tower.spawnedCloneEntities().getFirst();

        game.close();
        if (!assertTrue(context, lane.towers().isEmpty(), "Closing the game should clear lane tower runtime state.")) {
            return;
        }
        if (!assertTrue(context, spawnedClone.isRemoved(), "Closing the game should discard already spawned illusion clones.")) {
            return;
        }
        if (!assertTrue(context, lane.arenaWorld().getEntity(bodyEntityId) == null || lane.arenaWorld().getEntity(bodyEntityId).isRemoved(), "Closing the game should discard the source tower entity.")) {
            return;
        }
        for (int tick = 0; tick < 40; tick++) {
            IllusionCloneSpawnQueue.tick();
        }
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "Closing the game should cancel pending illusion clone spawns.")) {
            return;
        }
        IllusionCloneSpawnQueue.clear();
        context.succeed();
    }

    @GameTest
    public void illusionCloneSpawnsAboveTenCompleteWithinConfiguredSpreadTicks(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-spread-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_spread_fixture", "Illusion Spread Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(12, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        if (!assertEquals(context, 0, tower.spawnedCloneEntities().size(), "Clone spawning should be queued after wave start.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertTrue(context, tower.spawnedCloneEntities().size() > 0, "At least one queued clone should spawn on the first tick.")) {
            return;
        }
        if (!assertTrue(context, tower.spawnedCloneEntities().size() < 12, "Clone counts above 10 should not all spawn in one tick.")) {
            return;
        }
        for (int tick = 1; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 12, tower.spawnedCloneEntities().size(), "All queued clones should spawn within 40 ticks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illusionCloneInheritsSourceTraitEffects(GameTestHelper context) {
        UUID playerId = stableUuid("red-illusion-trait-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.FORTITUDE_ID
        ));
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_trait_fixture", "Illusion Trait Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(1, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);
        lane.markWaveStarted(1);

        SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        sourceEntity.refreshTimedEffect(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                BuiltInTraits.OPENING_SALVO_ID,
                0.15,
                100
        );
        sourceEntity.setPersistentEffect(
                TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS,
                BuiltInTraits.TRANSCENDENCE_ID,
                0.30
        );
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, tower.spawnedCloneEntities().size(), "One illusion clone should spawn.")) {
            return;
        }
        SemionTowerEntity cloneEntity = tower.spawnedCloneEntities().getFirst();
        for (TimedEffectType type : List.of(
                TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS,
                TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS,
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS
        )) {
            if (!assertEquals(
                    context,
                    sourceEntity.activeEffectMagnitude(type),
                    cloneEntity.activeEffectMagnitude(type),
                    "Illusion clone should inherit source trait effect: " + type
            )) {
                return;
            }
        }
        if (!assertEquals(
                context,
                sourceEntity.applyTraitOutgoingDamage(null, 100.0),
                cloneEntity.applyTraitOutgoingDamage(null, 100.0),
                "Illusion clone should apply inherited trait effects to actual damage."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illusionCloneQueueHonorsConfiguredMaxSpawnsPerTick(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                defaults.abilities(),
                new TowerBalanceConfig.IllusionCloneQueueConfig(1, 2)
        ));
        UUID playerId = stableUuid("red-illusion-queue-limit-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        GridPosition position = GridPosition.from(towerPlacementPos(lane));
        TowerType towerType = new TowerType("illusion_queue_limit_fixture", "Illusion Queue Limit Fixture", TowerCategory.DIRECT, 0, 100.0, 10.0, 20.0, 12, 7);
        FixtureIllusionTower tower = new FixtureIllusionTower(
                towerType,
                playerId,
                TeamId.RED,
                1,
                position,
                new IllusionProfile(5, 0, 0.25, 0.5, 1.0, 1.0, 1.0, 0)
        );
        lane.addTower(tower);

        lane.markWaveStarted(1);
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 2, tower.spawnedCloneEntities().size(), "Global illusion queue should apply the configured per-tick spawn cap.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 4, tower.spawnedCloneEntities().size(), "Global illusion queue should continue draining capped ready spawns on later ticks.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 5, tower.spawnedCloneEntities().size(), "Global illusion queue should finish remaining capped ready spawns.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void clearedLaneMovesTowerToFinalDefense(GameTestHelper context) {
        UUID playerId = stableUuid("red-final-defense-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPlacementPos(lane)),
                "Test tower placement should succeed before final defense move."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        GridPosition finalDefenseSlot = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        BlockPos finalDefenseAirPos = new BlockPos(
                finalDefenseSlot.x(),
                finalDefenseSlot.y(),
                finalDefenseSlot.z()
        );
        BlockPos finalDefenseFloorPos = finalDefenseAirPos.below();
        context.getLevel().setBlock(finalDefenseFloorPos, Blocks.STONE.defaultBlockState(), 3);
        context.getLevel().setBlock(finalDefenseAirPos, Blocks.AIR.defaultBlockState(), 3);

        game.teams().get(TeamId.RED).resetForRound();
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should be marked as deployed at final defense.")) {
            return;
        }
        if (!assertEquals(
                context,
                finalDefenseFloorPos,
                BlockPos.containing(tower.position().x(), tower.position().y(), tower.position().z()),
                "Tower runtime position should use the final defense floor block when the slot itself is air."
        )) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Final defense tower entity should exist.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Final defense tower entity should be available."));
            return;
        }
        if (!assertEquals(
                context,
                finalDefenseAirPos.getY(),
                BlockPos.containing(towerEntity.position()).getY(),
                "Tower entity should stand at the final defense slot height instead of one block above it."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void roundResetReturnsTowerToLanePosition(GameTestHelper context) {
        UUID playerId = stableUuid("red-reset-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos originalPosition = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, originalPosition),
                "Test tower placement should succeed before round reset."
        )) {
            return;
        }

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should exist before reset validation.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity,
                "Tower entity should be available before reset validation."
        )) {
            return;
        }
        int originalEntityId = tower.entityId().getAsInt();
        lane.moveTowersToFinalDefense();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should enter final defense before reset validation.")) {
            return;
        }

        game.teams().get(TeamId.RED).resetForRound();

        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, !tower.deployedAtFinalDefense(), "Tower should leave final defense on round reset.")) {
            return;
        }
        if (!assertEquals(context, tower.maxHealth(), tower.health(), "Tower health should reset to max on round reset.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Live tower should retain a tower entity on round reset.")) {
            return;
        }
        if (!assertEquals(context, originalEntityId, tower.entityId().getAsInt(), "Live tower reset should keep the existing tower entity id.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity resetEntity
                        && resetEntity.isAlive(),
                "Reset tower entity should exist and be alive after round reset."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                originalPosition,
                BlockPos.containing(tower.position().x(), tower.position().y(), tower.position().z()),
                "Tower should return to its original lane block on round reset."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void criticalPiglinBruteBonusesTankAndHighHealthTargets(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_PIGLIN_BRUTE),
                stableUuid("piglin-brute-bonus-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        tower.syncHealth(tower.currentMaxHealth() * 0.30);

        SemionMonsterEntity normal = spawnRoleMonsterEntity(
                context, "piglin-brute-normal", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity tank = spawnRoleMonsterEntity(
                context, "piglin-brute-tank", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.TANK)
        );
        SemionMonsterEntity highHealth = spawnRoleMonsterEntity(
                context, "piglin-brute-high-health", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(2.0, 0.0, 0.0), 200.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity income = spawnRoleMonsterEntity(
                context, "piglin-brute-income", Optional.of(TeamId.BLUE), TeamId.RED, 1,
                Vec3.ZERO.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );

        if (!assertClose(context, 100.0, tower.modifyAttackDamage(null, normal, 100.0), "Piglin brute should not bonus ordinary targets.")) {
            return;
        }
        if (!assertClose(context, 175.0, tower.modifyAttackDamage(null, tank, 100.0), "Critical piglin brute should deal 75% bonus damage to tank targets.")) {
            return;
        }
        if (!assertClose(context, 175.0, tower.modifyAttackDamage(null, highHealth, 100.0), "Critical piglin brute should deal 75% bonus damage to high-health targets.")) {
            return;
        }
        if (!assertClose(context, 150.0, tower.modifyAttackDamage(null, income, 100.0), "Piglin brute should retain the piglin income damage bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void criticalGhastAppliesTwentyPercentDamageTakenMark(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_GHAST),
                stableUuid("ghast-mark-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        tower.syncHealth(tower.currentMaxHealth() * 0.30);
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context, "ghast-mark-target", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 100.0, List.of(SummonRole.RUSH)
        );

        tower.onAttack(null, target, 10.0, false);

        if (!assertClose(
                context,
                0.20,
                target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                "Critical ghast should apply a 20% tower-damage-taken mark."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void criticalMagmaCubePulseScalesWithBaseAttackDamage(GameTestHelper context) {
        UUID playerId = stableUuid("magma-pulse-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T1_MAGMA_CUBE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);
        tower.syncHealth(tower.currentMaxHealth() * 0.30);
        SemionTowerEntity towerEntity = lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity entity
                ? entity
                : null;
        if (!assertPresent(context, Optional.ofNullable(towerEntity), "Placed magma cube tower entity should exist.")) {
            return;
        }
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "magma-pulse-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(4.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(
                context,
                "magma-pulse-nearby",
                Optional.empty(),
                TeamId.RED,
                1,
                target.position().add(1.5, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );

        tower.onAttack(towerEntity, target, tower.type().damage(), false);

        if (!assertClose(context, 89.5, target.getHealth(), "Magma cube pulse should deal 150% of its configured 7 base damage.")) {
            return;
        }
        if (!assertClose(context, 89.5, nearby.getHealth(), "Magma cube pulse should be centered on the attack target.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ghastAttackSpeedScalesWithMissingHealth(GameTestHelper context) {
        UUID playerId = stableUuid("ghast-missing-health-speed-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_GHAST),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);
        tower.syncHealth(tower.currentMaxHealth() * 0.50);
        SemionTowerEntity towerEntity = lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity entity
                ? entity
                : null;
        if (!assertPresent(context, Optional.ofNullable(towerEntity), "Placed ghast tower entity should exist.")) {
            return;
        }

        tower.tick(lane);

        if (!assertClose(
                context,
                0.375,
                towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Ghast at half health should receive half of its 75% attack-speed cap."
        )) {
            return;
        }
        if (!assertEquals(context, 18, towerEntity.attackIntervalTicks(), "Ghast attack interval should reflect the missing-health speed bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void witherPrioritizesBossTargetsThenLowestHealthTargets(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_WITHER),
                stableUuid("wither-priority-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        SemionMonsterEntity boss = spawnRoleMonsterEntity(
                context, "wither-priority-boss", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 600.0, List.of(SummonRole.TANK)
        );
        SemionMonsterEntity lowHealth = spawnRoleMonsterEntity(
                context, "wither-priority-low", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        lowHealth.setHealth(10.0F);
        SemionMonsterEntity ordinary = spawnRoleMonsterEntity(
                context, "wither-priority-ordinary", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(2.0, 0.0, 0.0), 200.0, List.of(SummonRole.RUSH)
        );

        SemionMonsterEntity selectedBoss = tower.selectAttackTarget(null, List.of(lowHealth, boss)).orElse(null);
        if (!assertEquals(context, boss, selectedBoss, "Wither should prioritize monsters above its high-health threshold.")) {
            return;
        }
        SemionMonsterEntity selectedLowHealth = tower.selectAttackTarget(null, List.of(ordinary, lowHealth)).orElse(null);
        if (!assertEquals(context, lowHealth, selectedLowHealth, "Wither should fall back to the lowest-health target when no boss target exists.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void destroyedNetherTowerRespawnsWithConfiguredProxy(GameTestHelper context) {
        UUID playerId = stableUuid("nether-proxy-respawn-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T1_STRIDER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);

        int originalEntityId = tower.entityId().orElseThrow();
        if (!(lane.arenaWorld().getEntity(originalEntityId) instanceof SemionTowerEntity originalEntity)) {
            context.fail(Component.literal("Placed nether tower entity should exist."));
            return;
        }
        if (!assertEquals(context, EntityType.STRIDER, originalEntity.getPolymerEntityType(null), "Initial nether tower proxy should be a strider.")) {
            return;
        }

        originalEntity.discard();
        game.teams().get(TeamId.RED).resetForRound();

        int respawnedEntityId = tower.entityId().orElseThrow();
        if (!assertTrue(context, respawnedEntityId != originalEntityId, "Destroyed nether tower should use a fresh entity id after reset.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(respawnedEntityId) instanceof SemionTowerEntity respawnedEntity)) {
            context.fail(Component.literal("Respawned nether tower entity should exist."));
            return;
        }
        if (!assertEquals(context, EntityType.STRIDER, respawnedEntity.getPolymerEntityType(null), "Respawned nether tower proxy should remain a strider.")) {
            return;
        }
        long visibleTowerEntities = lane.arenaWorld().getEntitiesOfClass(
                SemionTowerEntity.class,
                respawnedEntity.getBoundingBox().inflate(64.0),
                entity -> !entity.isRemoved() && entity.runtimeTower() == tower
        ).size();
        if (!assertEquals(context, 1L, visibleTowerEntities, "Round reset should leave one live tower entity.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerProducedDefendersResetOnNextRound(GameTestHelper context) {
        UUID playerId = stableUuid("defender-reset-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        DefenderEntity defender = new DefenderEntity(playerId, "producer_test", TeamId.RED, 1, 40.0, 7.0, false);
        lane.addDefenderEntity(defender);

        game.teams().get(TeamId.RED).resetForRound();

        if (!assertTrue(context, lane.defenderEntities().isEmpty(), "Round reset should clear tower-produced lane defenders.")) {
            return;
        }
        if (!assertEquals(context, DefenderEntityState.REMOVED, defender.state(), "Round reset should mark tower-produced defender as removed.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 520)
    public void monsterReachingBossFightsBossUntilKilled(GameTestHelper context) {
        UUID playerId = stableUuid("boss-reach-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        double initialBossHealth = game.teams().get(TeamId.RED).laneGroup().boss().health();

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "boss-reacher",
                60.0,
                0.0,
                37.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        ));
        lane.tick(context.getLevel().getServer());
        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
            context.fail(Component.literal("Boss combat test monster entity should exist."));
            return;
        }
        if (game.teams().get(TeamId.RED).laneGroup().bossEntity().isEmpty()) {
            context.fail(Component.literal("Boss combat test boss entity should exist."));
            return;
        }

        SemionBossEntity bossEntity = game.teams().get(TeamId.RED).laneGroup().bossEntity().get();
        Vec3 bossPosition = lane.laneLayout().bossPosition();
        monsterEntity.teleportTo(bossPosition.x, bossPosition.y, bossPosition.z);
        monsterEntity.setTarget(bossEntity);
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Monster reaching boss should stay active for boss combat.")) {
            return;
        }
        if (!assertEquals(context, initialBossHealth, game.teams().get(TeamId.RED).laneGroup().boss().health(), "Monster should not damage the boss through instant lane removal.")) {
            return;
        }

        awaitBossCombatResolution(context, game, TeamId.RED, lane, initialBossHealth, monsterEntityId, 0);
    }

    @GameTest
    public void semionEntitiesIgnoreDirectPlayerDamage(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        Vec3 origin = context.absolutePos(BlockPos.ZERO).getCenter();

        SemionTowerEntity tower = spawnTowerEntity(context, TeamId.RED, 1, origin, TestTowerTypes.TEST_DIRECT);
        float towerHealth = tower.getHealth();
        context.hurt(tower, tower.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, towerHealth, tower.getHealth(), "Players should not damage tower entities directly.")) {
            return;
        }

        SemionMonsterEntity monster = spawnSummonEntity(context, "player-immune-monster", TeamId.BLUE, TeamId.RED, 1, origin.add(2.0, 0.0, 0.0), 100.0, 0.0);
        float monsterHealth = monster.getHealth();
        context.hurt(monster, monster.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, monsterHealth, monster.getHealth(), "Players should not damage wave or summon entities directly.")) {
            return;
        }

        BossMonster runtimeBoss = BossMonster.defaultBoss(TeamId.RED);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, runtimeBoss);
        boss.setPos(origin.add(4.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(boss);
        float bossHealth = boss.getHealth();
        double runtimeBossHealth = runtimeBoss.health();
        context.hurt(boss, boss.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, bossHealth, boss.getHealth(), "Players should not damage boss entities directly.")) {
            return;
        }
        if (!assertEquals(context, runtimeBossHealth, runtimeBoss.health(), "Player boss hits should not affect runtime boss health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void activePlayersAndSpectatorsAreCombatProtected(GameTestHelper context) {
        UUID activeId = stableUuid("protected-active");
        UUID spectatorId = stableUuid("protected-spectator");
        UUID outsiderId = stableUuid("protected-outsider");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(activeId, "protected-active", TeamId.RED, 1)),
                Set.of(spectatorId),
                1
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Protection test game should start.")) {
            return;
        }
        if (!assertTrue(context, SemionPlayerProtectionService.shouldProtectPlayer(game, activeId), "Active players should be protected from combat.")) {
            return;
        }
        if (!assertTrue(context, SemionPlayerProtectionService.shouldProtectPlayer(game, spectatorId), "Match spectators should be protected from combat.")) {
            return;
        }
        if (!assertTrue(context, !SemionPlayerProtectionService.shouldProtectPlayer(game, outsiderId), "Players outside the active match should not be protected by match rules.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterTowerTargetSearchUsesFiveBlockPadding(GameTestHelper context) {
        if (!assertEquals(context, 5.0, SemionMonsterEntity.DEFENSE_SEARCH_HORIZONTAL_PADDING, "Monster tower target search should use five-block horizontal padding.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void laneMonsterDropsDistantDefenseTargetAndResumesPath(GameTestHelper context) {
        UUID playerId = stableUuid("monster-leash-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 monsterPosition = lane.laneLayout().positionAt(0.45);
        SemionMonsterEntity monster = spawnLaneMonsterEntity(
                context,
                lane,
                "leash-check",
                TeamId.RED,
                1,
                monsterPosition
        );
        SemionTowerEntity distantTower = spawnTowerEntity(
                context,
                TeamId.RED,
                1,
                monsterPosition.add(SemionMonsterEntity.DEFENSE_TARGET_LEASH_RANGE + 2.0, 0.0, 0.0),
                TestTowerTypes.TEST_DIRECT
        );

        monster.setTarget(distantTower);
        new MonsterAttackTargetGoal(monster, 1.1).tick();
        if (!assertTrue(context, monster.getTarget() == null, "Monsters should drop defense targets outside the leash range.")) {
            return;
        }
        if (!assertTrue(context, monster.nextPathPointIndex() > 0, "A monster that drops a target mid-lane should resume from the current path progress.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void bossEntityStaysAnchoredAndPullsRangedMonsters(GameTestHelper context) {
        Vec3 anchor = context.absolutePos(BlockPos.ZERO).getCenter().add(4.0, 24.0, 4.0);
        BossMonster runtimeBoss = BossMonster.defaultBoss(TeamId.RED);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, runtimeBoss);
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        context.getLevel().addFreshEntity(boss);

        boss.teleportTo(anchor.x + 2.0, anchor.y, anchor.z);
        boss.aiStep();
        if (!assertTrue(context, boss.position().distanceTo(anchor) < 0.01, "Boss entity should stay fixed at its anchor position.")) {
            return;
        }

        Monster rangedMonster = new Monster(
                "boss-pull-ranged",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                4.0,
                AttackKind.RANGED,
                "minecraft:skeleton",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity rangedEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        rangedEntity.configureFrom(rangedMonster, null);
        rangedEntity.setPos(anchor.add(8.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(rangedEntity);

        double before = rangedEntity.distanceToSqr(boss);
        new BossAttackLaneMonsterGoal(boss).tick();
        double after = rangedEntity.distanceToSqr(boss);
        if (!assertTrue(context, after < before, "Boss should pull ranged monsters toward the fixed boss position.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void longRangeMonsterClosesIntoFinalDefenseEngagementRange(GameTestHelper context) {
        Vec3 anchor = context.absolutePos(BlockPos.ZERO).getCenter().add(4.0, 2.0, 4.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        boss.setNoAi(true);
        context.getLevel().addFreshEntity(boss);

        WaveMonsterEntry artillery = new WaveMonsterEntry(
                "final-defense-artillery",
                100.0,
                0.0,
                4.0,
                AttackKind.RANGED,
                "minecraft:pillager",
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
        runtimeMonster.enterFinalDefenseCombat();
        SemionMonsterEntity rangedEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        rangedEntity.configureFrom(runtimeMonster, null);
        rangedEntity.setPos(anchor.add(8.0, 0.0, 0.0));
        rangedEntity.setTarget(boss);
        context.getLevel().addFreshEntity(rangedEntity);

        new MonsterAttackTargetGoal(rangedEntity, 1.1).tick();
        if (!assertTrue(context, rangedEntity.getMoveControl().hasWanted(), "Long-range monsters should enter the movement branch outside final-defense range.")) {
            return;
        }
        if (!assertEquals(context, boss.getX(), rangedEntity.getMoveControl().getWantedX(), "Long-range monsters should move toward the boss X position.")) {
            return;
        }
        if (!assertEquals(context, boss.getZ(), rangedEntity.getMoveControl().getWantedZ(), "Long-range monsters should move toward the boss Z position.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void bossAttackDamagesNearbyMonstersWithSplash(GameTestHelper context) {
        Vec3 anchor = context.absolutePos(BlockPos.ZERO).getCenter().add(4.0, 2.0, 4.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.PURPLE, BossMonster.defaultBoss(TeamId.PURPLE));
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        boss.setNoAi(true);
        context.getLevel().addFreshEntity(boss);

        SemionMonsterEntity primary = spawnBossTargetMonster(context, "boss-splash-primary", anchor.add(2.0, 0.0, 0.0));
        SemionMonsterEntity nearby = spawnBossTargetMonster(context, "boss-splash-nearby", anchor.add(3.0, 0.0, 0.0));
        SemionMonsterEntity far = spawnBossTargetMonster(context, "boss-splash-far", anchor.add(7.0, 0.0, 0.0));

        context.runAfterDelay(1, () -> {
            new BossAttackLaneMonsterGoal(boss).tick();

            if (!assertTrue(context, primary.getHealth() < 100.0F, "Boss should damage its primary target.")) {
                return;
            }
            if (!assertTrue(context, nearby.getHealth() < 100.0F, "Boss attack should splash onto nearby monsters.")) {
                return;
            }
            if (!assertEquals(context, 100.0F, far.getHealth(), "Boss splash should not hit monsters outside the splash radius.")) {
                return;
            }
            if (!assertClose(context, 73.0, primary.runtimeMonster().health(), "Boss physical damage should use armor, not resistance.")) {
                return;
            }
            if (!assertClose(context, primary.runtimeMonster().health(), primary.getHealth(), "Boss damage should synchronize primary runtime and entity health.")) {
                return;
            }
            if (!assertClose(context, nearby.runtimeMonster().health(), nearby.getHealth(), "Boss splash should synchronize nearby runtime and entity health.")) {
                return;
            }
            if (!assertTrue(context, primary.runtimeMonster().lastHitSourceKind() == KillSourceKind.BOSS, "Boss damage should preserve boss kill attribution.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void bossDamageScalesByRoundAndTriplesAgainstSummons(GameTestHelper context) {
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        boss.setCurrentRound(3);

        Monster waveMonster = new Monster(
                "boss-wave-damage-target",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0
        );
        Monster summonMonster = new Monster(
                "boss-summon-damage-target",
                TeamId.RED,
                1,
                Optional.of(stableUuid("boss-summon-owner")),
                Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );

        double expectedWaveDamage = 18.0 * 1.2;
        double expectedSummonDamage = expectedWaveDamage * 3.0;
        if (!assertTrue(context, Math.abs(boss.attackDamageAgainst(waveMonster) - expectedWaveDamage) < 0.001, "Boss damage should gain 10% per round after round 1.")) {
            return;
        }
        if (!assertTrue(context, Math.abs(boss.attackDamageAgainst(summonMonster) - expectedSummonDamage) < 0.001, "Boss damage should be tripled against summon monsters.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void waveTimeoutMovesEnemiesAndTowersToFinalDefense(GameTestHelper context) {
        UUID redId = stableUuid("timeout-final-defense-red-owner");
        UUID blueId = stableUuid("timeout-final-defense-blue-owner");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for wave timeout test.")) {
            return;
        }

        PlayerLane lane = redLane(game, 1);
        TowerType timeoutTowerType = new TowerType(
                "timeout_final_defense_test_tower",
                "Timeout Final Defense Test Tower",
                TowerCategory.DIRECT,
                0,
                10000.0,
                6.0,
                0.0,
                20,
                0
        );
        lane.addTower(new TestTower(timeoutTowerType, redId, TeamId.RED, 1, GridPosition.from(towerPlacementPos(lane))));
        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "timeout-runner",
                100000.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        ));

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        if (lane.arenaWorld().getEntity(lane.activeMonsters().getFirst().minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity) {
            monsterEntity.setNoAi(true);
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS + 2);

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Wave timeout should move lane tower to final defense.")) {
            return;
        }
        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Wave timeout should keep the enemy active at final defense.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(lane.activeMonsters().getFirst().minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity)) {
            context.fail(Component.literal("Wave timeout monster entity should still exist."));
            return;
        }
        if (!assertTrue(
                context,
                lane.laneLayout().progressAt(monsterEntity.position()) >= 0.75,
                "Wave timeout should move enemy toward the final defense side."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void twoPlayerGameLifecycleProgressesThroughRoundSummonAndVictory(GameTestHelper context) {
        UUID redId = stableUuid("lifecycle-red-owner");
        UUID blueId = stableUuid("lifecycle-blue-owner");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Lifecycle game should start with two players.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Lifecycle game should enter prepare phase after start.")) {
            return;
        }
        if (!assertTrue(context, game.rosterLocked(), "Lifecycle game should lock the roster after start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).active(), "RED should be active after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).active(), "BLUE should be active after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should exist after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).laneGroup().hasBossEntity(), "BLUE boss entity should exist after lifecycle start.")) {
            return;
        }
        if (!assertEquals(context, kim.biryeong.semiontd.job.JobRegistry.defaultJob().id(), game.selectedJobOrDefault(redId).id(), "RED should use the default job when none is selected.")) {
            return;
        }

        long redGasBeforePrepareTick = game.players().get(redId).economy().gas();
        tickGame(game, context.getLevel().getServer(), 40);
        if (!assertEquals(context, redGasBeforePrepareTick + 2, game.players().get(redId).economy().gas(), "Prepare phase should tick gas production.")) {
            return;
        }

        game.players().get(redId).economy().addIncome(7);
        game.players().get(blueId).economy().addIncome(9);
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS - 40 + 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty first wave should resolve into the next prepare phase.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Lifecycle game should advance to round 2 after first payout.")) {
            return;
        }
        if (!assertEquals(context, 207L, game.players().get(redId).economy().mineral(), "Round payout should pay RED's accumulated income.")) {
            return;
        }
        if (!assertEquals(context, 209L, game.players().get(blueId).economy().mineral(), "Round payout should pay BLUE's accumulated income.")) {
            return;
        }

        long redGasBeforeSummon = game.players().get(redId).economy().gas();
        long redIncomeBeforeSummon = game.players().get(redId).economy().income();
        var summonResult = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.UNKNOWN_SUMMON, summonResult.type(), "Removed income summons should not be available in round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, redGasBeforeSummon, game.players().get(redId).economy().gas(), "Unknown lifecycle summon should not spend gas.")) {
            return;
        }
        if (!assertEquals(context, redIncomeBeforeSummon, game.players().get(redId).economy().income(), "Unknown lifecycle summon should not add income.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty round 2 should resolve into the next prepare phase.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Killing BLUE boss should finish the lifecycle game.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.ENDED, game.phase(), "Lifecycle game should end when only RED remains.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).eliminated(), "BLUE should be eliminated after boss death.")) {
            return;
        }
        var matchResult = game.matchResult();
        if (!assertPresent(context, matchResult, "Ended lifecycle game should expose a match result.")) {
            return;
        }
        if (!assertTrue(context, matchResult.get().winningTeams().contains(TeamId.RED), "Lifecycle match result should mark RED as winner.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.get().winnerCount(), "Lifecycle match should have one winner.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.get().loserCount(), "Lifecycle match should have one loser.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void playerEconomyStartsWithConfiguredValues(GameTestHelper context) {
        UUID playerId = stableUuid("economy-start-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);

        if (!assertEquals(context, 200L, game.players().get(playerId).economy().mineral(), "Starting mineral should match config default.")) {
            return;
        }
        if (!assertEquals(context, 50L, game.players().get(playerId).economy().gas(), "Starting gas should match config default.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(playerId).economy().income(), "Starting income should match config default.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(playerId).economy().gasPerSec(), "Starting gas per second should match config default.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gasTickIncreasesGasAndRespectsCap(GameTestHelper context) {
        UUID playerId = stableUuid("gas-cap-owner");
        EconomyConfig economyConfig = new EconomyConfig(
                200,
                50,
                0,
                new EconomyConfig.GasCapConfig(55, 0, 0, 0),
                new EconomyConfig.GasProductionConfig(3, 20, 50, 25, 1, CurrencyType.MINERAL)
        );
        SemionGame game = new SemionGame(economyConfig, WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(playerId, "tester", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for gas tick test.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), 40);
        if (!assertEquals(context, 55L, game.players().get(playerId).economy().gas(), "Gas should tick up but stop at the round cap.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gasUpgradeConsumesMineralAndIncreasesGasPerSecond(GameTestHelper context) {
        UUID playerId = stableUuid("gas-up-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);

        if (!assertTrue(context, game.upgradeGasProduction(playerId), "Gas upgrade should succeed with default starting mineral.")) {
            return;
        }
        if (!assertEquals(context, 150L, game.players().get(playerId).economy().mineral(), "Gas upgrade should consume mineral cost.")) {
            return;
        }
        if (!assertEquals(context, 2L, game.players().get(playerId).economy().gasPerSec(), "Gas upgrade should increase gas per second.")) {
            return;
        }
        if (!assertEquals(context, 1, game.players().get(playerId).economy().gasProductionUpgradeCount(), "Gas upgrade count should increase.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void roundWaitsForEveryActiveTeamToClearWave(GameTestHelper context) {
        UUID redId = stableUuid("round-barrier-red-owner");
        UUID blueId = stableUuid("round-barrier-blue-owner");
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "round-barrier",
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        );
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(
                        new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("lane_1", List.of(entry)))
                ), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "round-barrier-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "round-barrier-blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Round barrier game should start.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round barrier game should enter wave phase.")) {
            return;
        }

        PlayerLane redLane = redLane(game, 1);
        PlayerLane blueLane = lane(game, TeamId.BLUE, 1);
        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "RED lane should have one wave monster.")) {
            return;
        }
        if (!assertEquals(context, 1, blueLane.activeMonsters().size(), "BLUE lane should have one wave monster.")) {
            return;
        }

        redLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 1);
        if (!assertTrue(context, redLane.clearedThisRound(), "RED lane should be cleared after killing its wave monster.")) {
            return;
        }
        if (!assertTrue(context, !blueLane.clearedThisRound(), "BLUE lane should still be unresolved.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round should stay in wave phase until every active team is resolved.")) {
            return;
        }
        if (!assertEquals(context, 1, game.currentRound(), "Round should not advance while another active team is still fighting.")) {
            return;
        }

        blueLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Round should advance after every active team clears.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Next prepare phase should be round 2 after all teams clear.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultRecordsPerBuilderWaveOutcomes(GameTestHelper context) {
        UUID redId = stableUuid("builder-outcome-red");
        UUID blueId = stableUuid("builder-outcome-blue");
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "builder-outcome",
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        );
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(
                        new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("lane_1", List.of(entry)))
                ), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "builder-outcome-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "builder-outcome-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Builder outcome game should start.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);

        PlayerLane redLane = redLane(game, 1);
        redLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 1);
        if (!assertTrue(context, redLane.clearedThisRound(), "RED builder should clear its own lane.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the game.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose individual wave results.")) {
            return;
        }
        Map<UUID, MatchParticipantResult> participants = result.get().participants().stream()
                .collect(Collectors.toMap(MatchParticipantResult::playerId, participant -> participant));
        MatchParticipantResult red = participants.get(redId);
        MatchParticipantResult blue = participants.get(blueId);
        if (!assertEquals(context, List.of(1), red.attemptedRounds(), "RED should record the first wave attempt.")) {
            return;
        }
        if (!assertEquals(context, List.of(1), red.clearedRounds(), "RED should record only its own cleared wave.")) {
            return;
        }
        if (!assertEquals(context, List.of(1), blue.attemptedRounds(), "BLUE should record the first wave attempt.")) {
            return;
        }
        if (!assertEquals(context, List.of(), blue.clearedRounds(), "An eliminated BLUE lane must not inherit the team result.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void infiniteWaveTemplateSelectionIsSharedAcrossTeams(GameTestHelper context) {
        WaveMonsterEntry firstEntry = new WaveMonsterEntry("template-first", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:piglin", null, 1);
        WaveMonsterEntry secondEntry = new WaveMonsterEntry("template-second", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:blaze", null, 1);
        WaveMonsterEntry thirdEntry = new WaveMonsterEntry("template-third", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:wither_skeleton", null, 1);
        var first = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(firstEntry)));
        var second = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(secondEntry)));
        var third = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(thirdEntry)));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 1, first, List.of(first, second, third)),
                testArena(context)
        );
        UUID redId = stableUuid("shared-template-red");
        UUID blueId = stableUuid("shared-template-blue");
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Shared-template game should start.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        String redMonsterId = redLane(game, 1).activeMonsters().getFirst().id();
        String blueMonsterId = lane(game, TeamId.BLUE, 1).activeMonsters().getFirst().id();
        if (!assertEquals(context, redMonsterId, blueMonsterId, "All teams should receive the same infinite-wave template.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void roundPayoutPaysIncomeToLivingPlayersOnly(GameTestHelper context) {
        UUID redId = stableUuid("payout-red-owner");
        UUID blueId = stableUuid("payout-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        game.players().get(redId).economy().addIncome(7);
        game.players().get(blueId).economy().addIncome(9);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 2);

        if (!assertEquals(context, 207L, game.players().get(redId).economy().mineral(), "Living RED player should receive round payout.")) {
            return;
        }
        if (!assertEquals(context, 209L, game.players().get(blueId).economy().mineral(), "Living BLUE player should receive round payout.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void eliminatedPlayersDoNotReceiveGasTicks(GameTestHelper context) {
        UUID redId = stableUuid("elim-red-owner");
        UUID blueId = stableUuid("elim-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should succeed.")) {
            return;
        }
        long redGas = game.players().get(redId).economy().gas();
        long blueGas = game.players().get(blueId).economy().gas();

        tickGame(game, context.getLevel().getServer(), 40);

        if (!assertEquals(context, redGas, game.players().get(redId).economy().gas(), "Ended games should not keep generating gas for RED.")) {
            return;
        }
        if (!assertEquals(context, blueGas, game.players().get(blueId).economy().gas(), "Eliminated BLUE player should not receive gas ticks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonConsumesEmeraldAndAddsIncome(GameTestHelper context) {
        UUID redId = stableUuid("summon-red-owner");
        UUID blueId = stableUuid("summon-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Default income summon should be summonable.")) {
            return;
        }
        if (!assertEquals(context, 30L, game.players().get(redId).economy().gas(), "Successful summon should spend chicken emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(redId).economy().income(), "Successful summon should add chicken income.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Successful summon should queue one monster in the target lane.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), result.scheduledRound(), "Prepare phase summon should be scheduled for the current round.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonDisplaysColoredSenderName(GameTestHelper context) {
        UUID redId = stableUuid("summon-display-name-red-owner");
        UUID blueId = stableUuid("summon-display-name-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Income summon should succeed before checking its display name.")) {
            return;
        }

        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        targetLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Income summon should spawn in its target lane.")) {
            return;
        }

        Monster monster = targetLane.activeMonsters().getFirst();
        if (!(targetLane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
            context.fail(Component.literal("Spawned income monster entity should exist."));
            return;
        }
        Component displayName = entity.getCustomName();
        if (!assertTrue(context, displayName != null, "Income monster should have a custom display name.")) {
            return;
        }
        if (!assertEquals(context, "red", displayName.getString(), "Income monster should display the sending player's name.")) {
            return;
        }
        if (!assertEquals(context, ChatFormatting.RED.getColor(), displayName.getStyle().getColor().getValue(), "Income monster sender name should use the sender team's color.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonFeedbackTargetsColoredLaneOwnerNickname(GameTestHelper context) {
        UUID redId = stableUuid("summon-feedback-red-owner");
        UUID blueId = stableUuid("summon-feedback-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Income summon should succeed for feedback formatting.")) {
            return;
        }
        String markup = SemionCommands.summonSuccessMarkup(
                game,
                result,
                "chicken",
                game.currentRound(),
                result.scheduledRound().orElse(game.currentRound())
        );
        if (!assertEquals(context, "Chicken 이(가) <blue>blue</blue> 의 라인으로 공격합니다!", markup, "Income summon feedback should use the income name and target lane owner's colored nickname.")) {
            return;
        }
        if (!assertTrue(context, !markup.contains("팀=") && !markup.contains("라인="), "Income summon feedback should not expose team/lane labels.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wavePhaseIncomeSummonQueuesForNextRound(GameTestHelper context) {
        UUID redId = stableUuid("wave-summon-red-owner");
        UUID blueId = stableUuid("wave-summon-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Game should enter wave phase before reserved summon purchase.")) {
            return;
        }

        long gasBeforeSummon = game.players().get(redId).economy().gas();
        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Wave phase income summon should be purchasable.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(2), result.scheduledRound(), "Wave phase summon should be scheduled for the next round.")) {
            return;
        }
        String markup = SemionCommands.summonSuccessMarkup(
                game,
                result,
                "chicken",
                game.currentRound(),
                result.scheduledRound().orElse(game.currentRound())
        );
        if (!assertEquals(context, "Chicken 이(가) <blue>blue</blue> 의 라인으로 공격합니다!", markup, "Reserved summon feedback should use the fallback attack message.")) {
            return;
        }
        if (!assertTrue(context, !markup.contains("예약") && !markup.contains("팀=") && !markup.contains("라인="), "Reserved summon feedback should not expose reservation or team/lane labels.")) {
            return;
        }
        if (!assertEquals(context, gasBeforeSummon - 20, game.players().get(redId).economy().gas(), "Wave phase summon should spend emerald immediately.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(redId).economy().income(), "Wave phase summon should add income immediately.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(redId).matchStats().summonedMonsters(), "Wave phase summon should update match stats immediately.")) {
            return;
        }

        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        if (!assertEquals(context, 0, targetLane.queuedSummonCount(), "Wave phase summon should not enter the current round summon queue.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.pendingNextRoundSummonCount(), "Wave phase summon should wait in the next-round queue.")) {
            return;
        }
        if (!assertEquals(context, 0, targetLane.activeMonsters().size(), "Wave phase summon should not spawn in the current wave.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty wave should resolve into next prepare after the reserved purchase.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Reserved summon should carry into round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Reserved summon should move into the target lane summon queue during next prepare.")) {
            return;
        }
        if (!assertEquals(context, 0, targetLane.pendingNextRoundSummonCount(), "Next-round queue should be empty after transfer.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round 2 should enter wave phase.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Reserved summon should be queued for the next wave.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wavePhaseIncomeSummonUsesNextRoundScaling(GameTestHelper context) {
        String summonId = "wave_scale_probe";
        UUID redId = stableUuid("wave-scale-red-owner");
        UUID blueId = stableUuid("wave-scale-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        SummonRegistry.register(new SummonMonsterType(
                summonId,
                "Wave Scale Probe",
                0,
                0,
                100,
                0,
                20,
                AttackKind.MELEE,
                "minecraft:zombie",
                0
        ) {
        });
        game.refreshSummonShop();

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        var result = game.summonMonster(redId, summonId);
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Wave scaling probe should be purchasable during wave phase.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 3);

        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Wave scaling probe should spawn in round 2 wave.")) {
            return;
        }
        Monster spawned = targetLane.activeMonsters().getFirst();
        double expectedHealth = 100 * SummonBalancePolicy.summonHealthMultiplier(2);
        double expectedAttackDamage = 20 * SummonBalancePolicy.summonAttackDamageMultiplier(2);
        if (!assertClose(context, expectedHealth, spawned.maxHealth(), "Wave phase summon should use next round health scaling.")) {
            return;
        }
        if (!assertClose(context, expectedAttackDamage, spawned.attackDamage(), "Wave phase summon should use next round attack scaling.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonInvalidOutsidePrepareAndWave(GameTestHelper context) {
        UUID redId = stableUuid("summon-phase-red-owner");
        UUID blueId = stableUuid("summon-phase-blue-owner");

        SemionGame waitingGame = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        var waitingResult = waitingGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, waitingResult.type(), "Waiting game should reject summon purchases by phase.")) {
            return;
        }

        SemionGame payoutGame = startedTwoPlayerGame(context, redId, blueId);
        setField(payoutGame, "phase", RoundPhase.ROUND_PAYOUT);
        var payoutResult = payoutGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, payoutResult.type(), "Round payout should reject summon purchases by phase.")) {
            return;
        }

        SemionGame endedGame = startedTwoPlayerGame(context, redId, blueId);
        if (!assertTrue(context, endedGame.killBoss(TeamId.BLUE), "Ended phase setup should eliminate BLUE.")) {
            return;
        }
        var endedResult = endedGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, endedResult.type(), "Ended game should reject summon purchases by phase.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonRefundsWhenNoTargetTeamExists(GameTestHelper context) {
        UUID redId = stableUuid("refund-red-owner");
        SemionGame game = startedSinglePlayerGame(context, redId, TeamId.RED);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.NO_TARGET_TEAM, result.type(), "Summon should fail when there is no target team.")) {
            return;
        }
        if (!assertEquals(context, 50L, game.players().get(redId).economy().gas(), "Failed summon should refund emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(redId).economy().income(), "Failed summon should not add income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonDoesNotTargetEliminatedTeams(GameTestHelper context) {
        UUID redId = stableUuid("summon-living-red-owner");
        UUID blueId = stableUuid("summon-eliminated-blue-owner");
        UUID greenId = stableUuid("summon-living-green-owner");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should eliminate BLUE before summon targeting.")) {
            return;
        }

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Summon should still succeed with another living enemy team.")) {
            return;
        }
        if (!assertEquals(context, TeamId.GREEN, result.targetTeam().orElse(null), "Summon should skip eliminated BLUE and target living GREEN.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void waveMonsterKillRewardGoesToTowerOwner(GameTestHelper context) {
        UUID playerId = stableUuid("wave-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType rewardTowerType = new TowerType("reward_test", "Reward Test", TowerCategory.DIRECT, 0, 50.0, 30.0, 30.0, 5, 0);
        lane.addTower(new TestTower(rewardTowerType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "reward-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                9,
                1
        ));
        lane.tick(context.getLevel().getServer());

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity) {
                monsterEntity.setNoAi(true);
            }
        });

        context.runAfterDelay(100, () -> {
            lane.tick(context.getLevel().getServer(), new EconomyService(game.economyConfig()), game.players());
            if (!assertEquals(context, 209L, game.players().get(playerId).economy().mineral(), "Tower owner should receive wave monster mineral reward.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void defenderLastHitPaysMineralRewardOnce(GameTestHelper context) {
        UUID playerId = stableUuid("defender-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        EconomyService economyService = new EconomyService(game.economyConfig());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "defender-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                11,
                1
        ));
        lane.tick(context.getLevel().getServer());

        var monster = lane.activeMonsters().getFirst();
        monster.recordLastHit(playerId, KillSourceKind.DEFENDER);
        monster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        if (!assertEquals(context, 211L, game.players().get(playerId).economy().mineral(), "Defender last hit should pay the reward only once.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void bossAndUnknownDeathsDoNotGrantMineralReward(GameTestHelper context) {
        UUID playerId = stableUuid("no-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        EconomyService economyService = new EconomyService(game.economyConfig());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "boss-no-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                13,
                1
        ));
        lane.tick(context.getLevel().getServer());
        var bossKilledMonster = lane.activeMonsters().getFirst();
        bossKilledMonster.recordBossHit();
        bossKilledMonster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "unknown-no-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                17,
                1
        ));
        lane.tick(context.getLevel().getServer());
        var unknownKilledMonster = lane.activeMonsters().getFirst();
        unknownKilledMonster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        if (!assertEquals(context, 200L, game.players().get(playerId).economy().mineral(), "Boss or unknown kills should not pay mineral reward.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonRegistryProvidesDefaultFortyFour(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        List<String> expectedSummonIds = List.of(
                "chicken", "rabbit", "silverfish", "zombie", "husk", "skeleton", "wolf", "spider",
                "cave_spider", "bee", "turtle", "sheep", "zombie_villager", "stray", "allay", "vex",
                "fox", "slime", "goat", "bogged", "pillager", "piglin_brute", "ravager", "hoglin",
                "horse", "llama", "phantom", "enderman", "breeze", "guardian", "polar_bear",
                "magma_cube", "ocelot", "vindicator", "witch", "iron_golem", "blaze", "shulker",
                "ghast", "zoglin", "wither_skeleton", "evoker", "elder_guardian", "warden"
        );
        if (!assertEquals(context, 44, SummonRegistry.all().size(), "Default income registry should contain all 44 planned summons.")) {
            return;
        }
        for (String summonId : expectedSummonIds) {
            if (!assertPresent(context, SummonRegistry.find(summonId), "Default income summon should be registered: " + summonId)) {
                return;
            }
        }
        SummonMonsterType enderman = SummonRegistry.find("enderman").orElseThrow();
        if (!assertEquals(context, 300L, enderman.gasCost(), "Enderman should keep the planned high-income emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 30L, enderman.incomeGain(), "Enderman should keep the planned high-income gain.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonConfigAppendsMissingDefaultSummons(GameTestHelper context) {
        SummonConfig.SummonDefinition chicken = SummonConfig.defaultConfig().summons().get("chicken");
        SummonConfig partial = new SummonConfig(Map.of("chicken", chicken));
        SummonConfig merged = partial.withMissingDefaults(SummonConfig.defaultConfig());
        if (!assertEquals(context, 44, merged.summons().size(), "Summon config should append missing default summon ids.")) {
            return;
        }
        if (!assertPresent(context, Optional.ofNullable(merged.summons().get("warden")), "Missing T5 summon should be appended.")) {
            return;
        }
        if (!assertEquals(context, chicken, merged.summons().get("chicken"), "Existing summon config entry should be preserved.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonDescriptionsIncludeAbilityNumbers(GameTestHelper context) {
        SummonConfig config = SummonConfig.defaultConfig();
        SummonConfig.SummonDefinition bogged = config.summons().get("bogged");
        SummonConfig.SummonDefinition allay = config.summons().get("allay");
        SummonConfig.SummonDefinition chicken = config.summons().get("chicken");
        SummonConfig.SummonDefinition warden = config.summons().get("warden");
        if (!assertTrue(context, bogged.description().stream().anyMatch(line -> line.equals("반경 6블록 내 가까운 타워 최대 1기의 공격속도를 5초간 12% 감소시킵니다. (4.5초 쿨타임)")), "Bogged description should be a concise tower debuff ability line.")) {
            return;
        }
        if (!assertTrue(context, allay.description().stream().anyMatch(line -> line.equals("반경 6블록 내 아군 유닛 최대 6기를 8 회복시킵니다. (6초 쿨타임)")), "Allay description should describe only its healing ability.")) {
            return;
        }
        if (!assertTrue(context, chicken.description().isEmpty(), "Summons without abilities should not get filler description lines.")) {
            return;
        }
        if (!assertTrue(context, SummonRegistry.find("chicken").orElseThrow().description().isEmpty(), "Summon types without abilities should not generate fallback description lines.")) {
            return;
        }
        if (!assertTrue(context, warden.description().stream().anyMatch(line -> line.equals("방어 대상에게 50 고정 피해를 줍니다. (4.5초 쿨타임)")), "Warden description should be a concise siege ability line.")) {
            return;
        }
        if (!assertTrue(context, SummonRegistry.find("warden").orElseThrow().abilityActivations().equals(List.of(SummonAbilityActivation.COOLDOWN)), "Siege summon fixed-damage abilities should display as cooldown abilities.")) {
            return;
        }
        if (!assertTrue(context, config.summons().values().stream()
                .flatMap(definition -> definition.description().stream())
                .noneMatch(line -> line.contains("T1") || line.contains("T2") || line.contains("T3") || line.contains("T4") || line.contains("T5")
                        || line.contains("역할") || line.contains("경제") || line.contains("전투") || line.contains("인컴 유닛")), "Summon descriptions should not expose tier, category, or income-only wording.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterDamageTypesUseArmorResistanceAndTrueDamage(GameTestHelper context) {
        Monster monster = new Monster(
                "damage-policy",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.empty(),
                130,
                8,
                5,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                1,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                0
        );

        monster.damage(10, DamageType.PHYSICAL);
        double expectedHealth = 130.0 - 10.0 * 100.0 / 108.0;
        if (!assertClose(context, expectedHealth, monster.health(), "Physical damage should use percentage armor mitigation.")) {
            return;
        }
        monster.damage(10, DamageType.MAGIC);
        expectedHealth -= 10.0 * 100.0 / 101.0;
        if (!assertClose(context, expectedHealth, monster.health(), "Magic damage should use percentage resistance mitigation.")) {
            return;
        }
        monster.damage(10, DamageType.TRUE);
        expectedHealth -= 10.0;
        if (!assertClose(context, expectedHealth, monster.health(), "True damage should ignore armor and resistance.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonTargetPriorityUsesRoleProgressAndSiegeBonus(GameTestHelper context) {
        Monster support = new Monster(
                "manual_support_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                60.0,
                0.0,
                2.0,
                AttackKind.RANGED,
                "minecraft:allay",
                null,
                DamageType.MAGIC,
                0.0,
                SummonTier.T2,
                List.of(SummonRole.SUPPORT),
                4
        );
        Monster tank = new Monster(
                "manual_tank_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                130.0,
                8.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                1
        );
        support.syncLaneProgress(0.5);
        tank.syncLaneProgress(0.5);
        if (!assertTrue(context, tank.targetPriorityScore() > support.targetPriorityScore(), "Tank should be prioritized over support at the same progress.")) {
            return;
        }

        Monster siege = new Monster(
                "manual_siege_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                360.0,
                14.0,
                80.0,
                AttackKind.RANGED,
                "minecraft:ravager",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T5,
                List.of(SummonRole.SIEGE),
                4
        );
        siege.syncLaneProgress(SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS);
        double expected = (SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS * 100.0)
                + SummonRole.SIEGE.targetPriority()
                + SummonBalancePolicy.SIEGE_NEAR_BOSS_TARGET_BONUS;
        if (!assertEquals(context, expected, siege.targetPriorityScore(), "Siege should gain target priority near the boss line.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void singleAllyHealGoalHealsMostInjuredFriendlySummon(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "manual_support", TeamId.RED, TeamId.BLUE, 1, origin, 80.0, 0.0);
        SemionMonsterEntity lightInjury = spawnSummonEntity(context, "light_injury", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity heavyInjury = spawnSummonEntity(context, "heavy_injury", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 25.0);
        SemionMonsterEntity waveInjury = spawnRoleMonsterEntity(context, "wave_injury", Optional.empty(), TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        waveInjury.runtimeMonster().damage(35.0, DamageType.TRUE);
        waveInjury.setHealth((float) waveInjury.runtimeMonster().health());
        SemionMonsterEntity wrongTarget = spawnSummonEntity(context, "wrong_target_injury", TeamId.GREEN, TeamId.RED, 1, origin.add(4.0, 0.0, 0.0), 100.0, 35.0);

        new SingleAllyHealGoal<>(caster, SemionMonsterEntity.class, 8.0, 12.0, 80, 10).tick();

        if (!assertEquals(context, SemionAnimationState.HEAL, caster.animationState(), "Successful single heal should play the caster heal animation.")) {
            return;
        }
        if (!assertEquals(context, 90.0, lightInjury.runtimeMonster().health(), "Single heal should not heal the less injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 75.0, heavyInjury.runtimeMonster().health(), "Single heal should not heal the less injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 77.0, waveInjury.runtimeMonster().health(), "Single heal should heal the most injured same target-lane wave unit.")) {
            return;
        }
        if (!assertEquals(context, 65.0, wrongTarget.runtimeMonster().health(), "Single heal should ignore units attacking another target team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaAllyHealGoalHealsNearbyTargetLaneUnits(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "manual_area_support", TeamId.RED, TeamId.BLUE, 1, origin, 80.0, 0.0);
        SemionMonsterEntity first = spawnSummonEntity(context, "area_first", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity second = spawnSummonEntity(context, "area_second", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity wave = spawnRoleMonsterEntity(context, "area_wave", Optional.empty(), TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        wave.runtimeMonster().damage(10.0, DamageType.TRUE);
        wave.setHealth((float) wave.runtimeMonster().health());
        SemionMonsterEntity far = spawnSummonEntity(context, "area_far", TeamId.RED, TeamId.BLUE, 1, origin.add(8.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity wrongLane = spawnSummonEntity(context, "area_wrong_lane", TeamId.GREEN, TeamId.BLUE, 2, origin.add(1.0, 0.0, 1.0), 100.0, 10.0);

        new AreaAllyHealGoal<>(caster, SemionMonsterEntity.class, 5.0, 5.0, 4, 100, 10).tick();

        if (!assertEquals(context, 95.0, first.runtimeMonster().health(), "Area heal should heal nearby same sender units.")) {
            return;
        }
        if (!assertEquals(context, 95.0, second.runtimeMonster().health(), "Area heal should heal multiple same target-lane units.")) {
            return;
        }
        if (!assertEquals(context, 95.0, wave.runtimeMonster().health(), "Area heal should heal nearby same target-lane wave units.")) {
            return;
        }
        if (!assertEquals(context, 90.0, far.runtimeMonster().health(), "Area heal should ignore friendlies outside radius.")) {
            return;
        }
        if (!assertEquals(context, 90.0, wrongLane.runtimeMonster().health(), "Area heal should ignore units attacking another lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterTimedEffectsTargetSameLaneWaveUnits(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnRoleMonsterEntity(context, "effect_caster", Optional.of(TeamId.RED), TeamId.BLUE, 1, origin, 100.0, List.of(SummonRole.SUPPORT));
        SemionMonsterEntity summon = spawnRoleMonsterEntity(context, "effect_summon", Optional.of(TeamId.RED), TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wave = spawnRoleMonsterEntity(context, "effect_wave", Optional.empty(), TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wrongLane = spawnRoleMonsterEntity(context, "effect_wrong_lane", Optional.empty(), TeamId.BLUE, 2, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wrongTarget = spawnRoleMonsterEntity(context, "effect_wrong_target", Optional.of(TeamId.GREEN), TeamId.RED, 1, origin.add(4.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        new ApplyMonsterTimedEffectGoal(
                caster,
                TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS,
                0.25,
                6.0,
                80,
                60,
                10,
                4
        ).tick();

        if (!assertEquals(context, 0.25, caster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Caster should count as a same target-lane buff target.")) {
            return;
        }
        if (!assertEquals(context, 0.25, summon.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Summoned units on the same target lane should receive monster buffs.")) {
            return;
        }
        if (!assertEquals(context, 0.25, wave.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Wave units on the same target lane should receive monster buffs.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongLane.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Monster buffs should ignore another lane.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongTarget.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Monster buffs should ignore another target team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void timedEffectsKeepStrongestMagnitudeAndRefreshDuration(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.50, 10);
        if (!assertEquals(context, 0.50, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Timed effects should keep the configured magnitude without clamping.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.20, 50);
        if (!assertEquals(context, 0.50, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not replace a stronger active effect.")) {
            return;
        }
        if (!assertEquals(context, 10, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not refresh the stronger effect duration.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.50, 40);
        if (!assertEquals(context, 40, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Equal magnitude should refresh the active effect duration.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void timedEffectsRejectDuplicateSourcesAndStackDifferentSources(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        ResourceLocation firstSource = ResourceLocation.fromNamespaceAndPath("semion-td", "test/first_damage_bonus");
        ResourceLocation secondSource = ResourceLocation.fromNamespaceAndPath("semion-td", "test/second_damage_bonus");

        if (!assertTrue(context, effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, firstSource, 0.10, 20), "First sourced effect should apply.")) {
            return;
        }
        if (!assertEquals(context, 0.10, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Sourced effect should contribute its magnitude.")) {
            return;
        }
        if (!assertTrue(context, !effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, firstSource, 0.20, 40), "Duplicate source should not refresh or stack while active.")) {
            return;
        }
        if (!assertEquals(context, 0.10, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Duplicate source should leave magnitude unchanged.")) {
            return;
        }
        if (!assertEquals(context, 20, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Duplicate source should leave duration unchanged.")) {
            return;
        }
        if (!assertTrue(context, effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, secondSource, 0.15, 30), "Different sources should stack.")) {
            return;
        }
        if (!assertEquals(context, 0.25, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Different sourced effects should stack by type.")) {
            return;
        }
        if (!assertEquals(context, 30, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Type duration should expose the longest active source duration.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sourcedTimedEffectsCanBeRefreshedForPersistentAuras(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("semion-td", "test/refresh_damage_bonus");

        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.10, 20), "Refresh should apply a missing sourced effect.")) {
            return;
        }
        effects.tick();
        if (!assertEquals(context, 19, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh test should tick the sourced effect.")) {
            return;
        }
        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.10, 40), "Refresh should extend an existing sourced effect.")) {
            return;
        }
        if (!assertEquals(context, 40, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh should expose the extended duration.")) {
            return;
        }
        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.15, 30), "Refresh should replace a changed source magnitude.")) {
            return;
        }
        if (!assertEquals(context, 0.15, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh should expose the changed source magnitude.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void nullImpDebuffsOnlyNearestTargetLaneTower(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "null_imp", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTowerEntity wrongTeam = spawnTowerEntity(context, TeamId.RED, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity wrongLane = spawnTowerEntity(context, TeamId.BLUE, 2, origin.add(2.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity target = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity fartherTarget = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(4.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);

        new ApplyTowerTimedEffectGoal(
                caster,
                TimedEffectType.TOWER_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_RADIUS,
                SummonBalancePolicy.NULL_IMP_RANGE_DURATION_TICKS,
                SummonBalancePolicy.NULL_IMP_RANGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                1
        ).tick();

        if (!assertEquals(context, 0.0, wrongTeam.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should ignore towers from the sender team.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongLane.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should ignore towers on another lane.")) {
            return;
        }
        if (!assertEquals(context, SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION, target.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should debuff the nearest target-lane enemy tower.")) {
            return;
        }
        if (!assertEquals(context, 0.0, fartherTarget.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should affect only one tower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void siegeSummonsDealTrueBonusDamage(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity bombard = spawnSummonEntity(context, "bombard_toad", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTowerEntity tower = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        bombard.setTarget(tower);

        new SiegeTrueDamageGoal(
                bombard,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                0.0
        ).tick();
        if (!assertEquals(context, 30.0F, tower.getHealth(), "Bombard toad should bonus-damage towers without a progress condition.")) {
            return;
        }

        SemionMonsterEntity siege = spawnSummonEntity(context, "siege_breaker", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 0.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.BLUE, BossMonster.defaultBoss(TeamId.BLUE));
        boss.setPos(origin.add(3.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(boss);
        siege.setTarget(boss);

        new SiegeTrueDamageGoal(
                siege,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                0.0
        ).tick();
        if (!assertEquals(context, 955.0F, boss.getHealth(), "Siege breaker should bonus-damage boss targets.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void healGoalsCanTargetTowerEntities(GameTestHelper context) {
        UUID playerId = stableUuid("tower-heal-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        TowerType healerType = new TowerType("healer_test", "Healer Test", TowerCategory.SUPPORT, 0, 80.0, 1.0, 0.0, 20, 0);
        TowerType targetType = new TowerType("heal_target_test", "Heal Target Test", TowerCategory.DIRECT, 0, 80.0, 1.0, 0.0, 20, 0);
        TestTower healerTower = new TestTower(healerType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ()));
        TestTower targetTower = new TestTower(targetType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(base.getX() + 2, base.getY(), base.getZ()));
        lane.addTower(healerTower);
        lane.addTower(targetTower);
        targetTower.syncHealth(50.0);

        SemionTowerEntity healerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(healerTower.entityId().orElseThrow());
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(targetTower.entityId().orElseThrow());
        targetEntity.syncTowerState(targetTower);

        new SingleAllyHealGoal<>(healerEntity, SemionTowerEntity.class, 6.0, 15.0, 80, 10).tick();

        if (!assertEquals(context, 65.0, targetTower.health(), "Generic heal goal should update the tower runtime health.")) {
            return;
        }
        if (!assertEquals(context, 65.0F, targetEntity.getHealth(), "Generic heal goal should update the tower entity health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void allayTowerHealsNearbyTowersAndBlocksDuplicateHealing(GameTestHelper context) {
        UUID playerId = stableUuid("allay-heal-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        AllayTower allayTower = new AllayTower(
                VillagerTowers.T1_ALLAY_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        TestTower nearbyTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        TestTower farTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 5, base.getY(), base.getZ())
        );
        lane.addTower(allayTower);
        if (!assertTrue(context, allayTower.entityId().isPresent(), "Allay support tower should spawn a visible tower entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(allayTower.entityId().getAsInt()) instanceof SemionTowerEntity allayEntity
                        && allayEntity.getPolymerEntityType(null) == EntityType.ALLAY,
                "Allay support tower should render through an Allay polymer entity."
        )) {
            return;
        }
        lane.addTower(nearbyTower);
        lane.addTower(farTower);
        nearbyTower.syncHealth(30.0);
        farTower.syncHealth(30.0);
        ((SemionTowerEntity) lane.arenaWorld().getEntity(nearbyTower.entityId().orElseThrow())).syncTowerState(nearbyTower);
        ((SemionTowerEntity) lane.arenaWorld().getEntity(farTower.entityId().orElseThrow())).syncTowerState(farTower);

        allayTower.tick(lane);
        if (!assertEquals(context, 40.0, nearbyTower.health(), "Allay tower should heal nearby damaged towers.")) {
            return;
        }
        if (!assertEquals(context, 30.0, farTower.health(), "Allay tower should ignore towers outside its support radius.")) {
            return;
        }
        for (int i = 0; i < VillagerTowers.T1_ALLAY_TOWER.attackIntervalTicks() + 1; i++) {
            allayTower.tick(lane);
        }
        if (!assertEquals(context, 40.0, nearbyTower.health(), "Allay tower should not re-heal the same target inside the block window.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void golemTowerSurvivalBonusIncreasesCurrentMaxHealth(GameTestHelper context) {
        UUID playerId = stableUuid("golem-health-bonus-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower tower = new VillagerThornTower(
                VillagerTowers.T2_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);

        lane.resetForRound();

        double expectedMaxHealth = VillagerTowers.T2_GOLEM_TOWER.maxHealth() * 1.10;
        if (!assertEquals(context, expectedMaxHealth, tower.currentMaxHealth(), "Golem survival bonus should increase current max health.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, tower.health(), "Golem round reset should refill to current max health.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Golem tower entity should exist after reset."));
            return;
        }
        if (!assertEquals(
                context,
                expectedMaxHealth,
                towerEntity.getAttributeValue(Attributes.MAX_HEALTH),
                "Golem tower entity max health attribute should match current max health."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void golemTowerFinalDefenseSurvivalBonusHealsCurrentHealthDelta(GameTestHelper context) {
        UUID playerId = stableUuid("golem-final-defense-health-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower tower = new VillagerThornTower(
                VillagerTowers.T3_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);
        double initialHealth = tower.health();

        tower.moveToFinalDefense(lane, new GridPosition(base.getX() + 1, base.getY(), base.getZ()));

        double expectedMaxHealth = VillagerTowers.T3_GOLEM_TOWER.maxHealth() * 1.20;
        if (!assertEquals(context, expectedMaxHealth, tower.currentMaxHealth(), "Iron golem final-defense bonus should increase max health immediately.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, tower.health(), "Iron golem final-defense bonus should heal the newly gained max health.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth - initialHealth, tower.health() - initialHealth, "Iron golem should gain current health equal to the max-health delta.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ironGolemUpgradeCopiesSurvivalBonusAndHealsAboveBaseHealth(GameTestHelper context) {
        UUID playerId = stableUuid("iron-golem-upgrade-health-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        VillagerThornTower previousTower = new VillagerThornTower(
                VillagerTowers.T2_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        for (int round = 0; round < 5; round++) {
            previousTower.moveToFinalDefense(lane, previousTower.position());
        }
        VillagerThornTower upgradedTower = new VillagerThornTower(
                VillagerTowers.T3_GOLEM_TOWER,
                playerId,
                TeamId.RED,
                1,
                previousTower.originalPosition(),
                previousTower.position()
        );

        upgradedTower.copyFrom(previousTower, VillagerTowers.T3_GOLEM_TOWER.mineralCost());

        double expectedMaxHealth = VillagerTowers.T3_GOLEM_TOWER.maxHealth() * 2.0;
        if (!assertEquals(context, expectedMaxHealth, upgradedTower.currentMaxHealth(), "Iron golem upgrade should inherit capped survival stacks.")) {
            return;
        }
        if (!assertEquals(context, expectedMaxHealth, upgradedTower.health(), "Iron golem upgrade should heal to its inherited current max health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void weaponSmithTowerAppliesSourcedDamageAndSpeedBuffs(GameTestHelper context) {
        UUID playerId = stableUuid("weapon-smith-support-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        AllayTower weaponSmithTower = new AllayTower(
                VillagerTowers.T2_WEAPON_SMITH_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        TestTower targetTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        lane.addTower(weaponSmithTower);
        lane.addTower(targetTower);
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(targetTower.entityId().orElseThrow());

        weaponSmithTower.tick(lane);
        if (!assertEquals(context, 0.10, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Weapon smith should apply a sourced damage buff.")) {
            return;
        }
        if (!assertEquals(context, 0.10, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), "Weapon smith should apply a sourced attack-speed buff.")) {
            return;
        }
        for (int i = 0; i < VillagerTowers.T2_WEAPON_SMITH_TOWER.attackIntervalTicks() + 1; i++) {
            weaponSmithTower.tick(lane);
        }
        if (!assertEquals(context, 0.10, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Weapon smith should not stack the same sourced buff inside the block window.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void pigTowerStacksHealthDamageReductionAndSplash(GameTestHelper context) {
        UUID playerId = stableUuid("pig-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        PigTower tower = new PigTower(
                AnimalTowers.T3_PIG_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        lane.addTower(tower);
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 1, base.getY(), base.getZ())));
        lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 2, base.getY(), base.getZ())));

        if (!assertClose(context, 530.0, tower.currentMaxHealth(), "T3 pig should gain max health from two same-owner pig stacks.")) {
            return;
        }
        if (!assertClose(context, 530.0, tower.health(), "T3 pig should gain current health when stacks increase.")) {
            return;
        }
        if (!assertClose(context, 45.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T3 pig should gain damage from two pig stacks.")) {
            return;
        }
        if (!assertClose(context, 70.0, tower.modifyIncomingDamage(null, null, 100.0), "T3 pig should reduce incoming damage at max stacks.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 origin = towerEntity.position().add(1.0, 0.0, 0.0);
        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "pig-primary", Optional.empty(), TeamId.RED, 1, origin, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "pig-nearby", Optional.empty(), TeamId.RED, 1, origin.add(0.75, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity far = spawnRoleMonsterEntity(context, "pig-far", Optional.empty(), TeamId.RED, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        tower.onAttack(towerEntity, primary, 20.0, false);
        if (!assertClose(context, 90.0, nearby.runtimeMonster().health(), "T3 pig should splash half damage at max stacks.")) {
            return;
        }
        if (!assertClose(context, 100.0, far.runtimeMonster().health(), "T3 pig splash should ignore enemies outside radius.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wolfTowerStacksDamageIntervalAndSplash(GameTestHelper context) {
        UUID playerId = stableUuid("wolf-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        WolfTower tower = new WolfTower(
                AnimalTowers.T2_WOLF_DPS_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        WolfTower t1Tower = new WolfTower(
                AnimalTowers.T1_WOLF_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 1)
        );
        WolfTower t3Tower = new WolfTower(
                AnimalTowers.T3_WOLF_DPS_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ() + 1)
        );
        lane.addTower(tower);
        lane.addTower(t1Tower);
        lane.addTower(t3Tower);
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 3, base.getY(), base.getZ() + 1)));
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 4, base.getY(), base.getZ() + 2)));
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, stableUuid("other-wolf-owner"), TeamId.RED, 1, new GridPosition(base.getX() + 5, base.getY(), base.getZ() + 2)));

        if (!assertClose(context, 25.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "Four total same-owner wolves should stay below max stacks.")) {
            return;
        }
        if (!assertEquals(context, 16, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "Different-family and different-owner towers should not grant wolf stacks.")) {
            return;
        }
        if (!assertTrue(context, tower.runtimeDetailLines().stream().anyMatch(line -> line.contains("무리 스택 3/4")), "Four total same-owner wolves should show three supporting stacks.")) {
            return;
        }

        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 6, base.getY(), base.getZ() + 1)));

        if (!assertClose(context, 13.0, t1Tower.modifyAttackDamage(null, null, t1Tower.type().damage()), "T1 wolf should reach 13 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 15, t1Tower.adjustAttackInterval(t1Tower.type().attackIntervalTicks()), "T1 wolf should reach a 15-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 30.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T2 wolf should reach 30 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 12, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "T2 wolf should reach a 12-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 65.0, t3Tower.modifyAttackDamage(null, null, t3Tower.type().damage()), "T3 wolf should reach 65 damage with five total wolves.")) {
            return;
        }
        if (!assertEquals(context, 10, t3Tower.adjustAttackInterval(t3Tower.type().attackIntervalTicks()), "T3 wolf should reach a 10-tick interval at max stacks.")) {
            return;
        }
        if (!assertTrue(context, tower.runtimeDetailLines().stream().anyMatch(line -> line.contains("무리 스택 4/4")), "Five total same-owner wolves should activate max stacks.")) {
            return;
        }
        if (!assertTrue(context, tower.runtimeDetailLines().stream().anyMatch(line -> line.contains("공격 간격 -5틱")), "Wolf runtime details should show the rounded interval reduction used in combat.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 origin = towerEntity.position().add(1.0, 0.0, 0.0);
        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "wolf-primary", Optional.empty(), TeamId.RED, 1, origin, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "wolf-nearby", Optional.empty(), TeamId.RED, 1, origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity far = spawnRoleMonsterEntity(context, "wolf-far", Optional.empty(), TeamId.RED, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        tower.onAttack(towerEntity, primary, 20.0, false);
        if (!assertClose(context, 90.0, nearby.runtimeMonster().health(), "T2 wolf should splash half damage.")) {
            return;
        }
        if (!assertClose(context, 100.0, far.runtimeMonster().health(), "T2 wolf splash should ignore enemies outside radius.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rabbitTowerStacksDamageIntervalAndExtraAttack(GameTestHelper context) {
        UUID playerId = stableUuid("rabbit-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        RabbitTower tower = new RabbitTower(
                AnimalTowers.T3_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        RabbitTower t1Tower = new RabbitTower(
                AnimalTowers.T1_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ() + 2)
        );
        RabbitTower t2Tower = new RabbitTower(
                AnimalTowers.T2_RABBIT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 2, base.getY(), base.getZ() + 2)
        );
        lane.addTower(tower);
        lane.addTower(t1Tower);
        lane.addTower(t2Tower);
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 3, base.getY(), base.getZ() + 2)));
        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 4, base.getY(), base.getZ() + 1)));
        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, stableUuid("other-rabbit-owner"), TeamId.RED, 1, new GridPosition(base.getX() + 5, base.getY(), base.getZ() + 1)));

        if (!assertClose(context, 47.5, tower.modifyAttackDamage(null, null, tower.type().damage()), "Four total same-owner rabbits should stay below max stacks.")) {
            return;
        }
        if (!assertEquals(context, 13, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "Different-family and different-owner towers should not grant rabbit stacks.")) {
            return;
        }
        if (!assertTrue(context, tower.runtimeDetailLines().stream().anyMatch(line -> line.contains("무리 스택 3/4")), "Four total same-owner rabbits should show three supporting stacks.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "rabbit-extra-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        tower.onAttack(towerEntity, target, 20.0, false);
        if (!assertClose(context, 100.0, target.runtimeMonster().health(), "Four total rabbits should not activate the max-stack extra attack.")) {
            return;
        }

        lane.addTower(new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, playerId, TeamId.RED, 1, new GridPosition(base.getX() + 6, base.getY(), base.getZ() + 2)));

        if (!assertClose(context, 15.0, t1Tower.modifyAttackDamage(null, null, t1Tower.type().damage()), "T1 rabbit should reach 15 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 15, t1Tower.adjustAttackInterval(t1Tower.type().attackIntervalTicks()), "T1 rabbit should keep its 15-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 33.0, t2Tower.modifyAttackDamage(null, null, t2Tower.type().damage()), "T2 rabbit should reach 33 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 10, t2Tower.adjustAttackInterval(t2Tower.type().attackIntervalTicks()), "T2 rabbit should reach a 10-tick interval at max stacks.")) {
            return;
        }
        if (!assertClose(context, 60.0, tower.modifyAttackDamage(null, null, tower.type().damage()), "T3 rabbit should reach 60 damage with five total rabbits.")) {
            return;
        }
        if (!assertEquals(context, 8, tower.adjustAttackInterval(tower.type().attackIntervalTicks()), "T3 rabbit should reach an 8-tick interval at max stacks.")) {
            return;
        }
        if (!assertTrue(context, tower.runtimeDetailLines().stream().anyMatch(line -> line.contains("무리 스택 4/4")), "Five total same-owner rabbits should activate max stacks.")) {
            return;
        }
        tower.onAttack(towerEntity, target, 20.0, false);
        if (!assertClose(context, 60.0, target.runtimeMonster().health(), "T3 rabbit should deal 200% extra damage at max stacks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void undeadAnimalTowerDebuffsMonsterAttackAndTowerDamageTaken(GameTestHelper context) {
        UUID playerId = stableUuid("undead-animal-debuff-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        UndeadAnimalTower firstTower = new UndeadAnimalTower(
                UndeadTowers.T2_UNDEAD_ANIMAL_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        UndeadAnimalTower secondTower = new UndeadAnimalTower(
                UndeadTowers.T2_UNDEAD_ANIMAL_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        lane.addTower(firstTower);
        lane.addTower(secondTower);

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(firstTower.entityId().orElseThrow());
        SemionMonsterEntity monster = spawnAttackMonsterEntity(
                context,
                "undead-animal-target",
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                20.0
        );

        firstTower.tick(lane);
        secondTower.tick(lane);
        if (!assertClose(context, 0.10, monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION), "Undead animal tower should apply non-stacking monster attack damage reduction.")) {
            return;
        }
        if (!assertClose(context, 0.10, monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS), "T2 undead animal tower should apply non-stacking tower damage taken bonus.")) {
            return;
        }
        if (!assertClose(context, 18.0, monster.attackDamageAmount(), "Monster attack damage reduction should lower runtime attack damage.")) {
            return;
        }
        if (!assertClose(context, 110.0, monster.towerDamageTaken(100.0), "Tower damage taken bonus should increase runtime tower damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerAntiTankerCatBonusesNonWaveAndTankTargets(GameTestHelper context) {
        AntiTankerCatTower catTower = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                stableUuid("anti-tanker-cat-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionMonsterEntity wave = spawnRoleMonsterEntity(
                context,
                "cat-wave",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity rushSummon = spawnRoleMonsterEntity(
                context,
                "cat-rush",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity tankSummon = spawnRoleMonsterEntity(
                context,
                "cat-tank",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO.add(2.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.TANK)
        );

        if (!assertClose(context, 20.0, catTower.modifyAttackDamage(null, wave, 20.0), "Anti-tanker cat should not bonus wave monsters.")) {
            return;
        }
        if (!assertClose(context, 30.0, catTower.modifyAttackDamage(null, rushSummon, 20.0), "T2 anti-tanker cat should deal 50% bonus damage to non-wave summons.")) {
            return;
        }
        if (!assertClose(context, 40.0, catTower.modifyAttackDamage(null, tankSummon, 20.0), "T2 anti-tanker cat should deal 100% bonus damage to tank summons.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerLaneClearCatExplodesWithoutChainKills(GameTestHelper context) {
        UUID playerId = stableUuid("lane-clear-cat-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        LaneClearCatTower catTower = new LaneClearCatTower(
                VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ())
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(catTower, lane.laneLayout());
        Vec3 targetPosition = Vec3.atBottomCenterOf(base);
        towerEntity.setPos(targetPosition.add(0.0, 0.0, -1.0));
        context.getLevel().addFreshEntity(towerEntity);

        SemionMonsterEntity primary = spawnRoleMonsterEntity(context, "cat-primary", Optional.empty(), TeamId.RED, 1, targetPosition, 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity nearby = spawnRoleMonsterEntity(context, "cat-nearby", Optional.empty(), TeamId.RED, 1, targetPosition.add(0.8, 0.0, 0.0), 10.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity chainCandidate = spawnRoleMonsterEntity(context, "cat-chain-candidate", Optional.empty(), TeamId.RED, 1, targetPosition.add(1.6, 0.0, 0.0), 10.0, List.of(SummonRole.RUSH));

        catTower.onKill(towerEntity, primary, 15.0);

        if (!assertTrue(context, nearby.isRemoved() || !nearby.isAlive() || nearby.getHealth() <= 0.0F, "Lane-clear cat explosion should damage and kill monsters near the primary target.")) {
            return;
        }
        if (!assertTrue(context, chainCandidate.isAlive() && chainCandidate.getHealth() > 0.0F, "Lane-clear cat explosion should not chain from explosion-killed monsters.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaEffectApiFiltersOtherLanesAndReportsAppliedTargets(GameTestHelper context) {
        UUID playerId = stableUuid("area-effect-api-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPosition = towerPlacementPos(lane);
        TestTower tower = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(towerPosition));
        lane.addTower(tower);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 center = towerEntity.position().add(1.5, 0.0, 0.0);
        SemionMonsterEntity sameLane = spawnRoleMonsterEntity(
                context,
                "area-api-same-lane",
                Optional.empty(),
                TeamId.RED,
                1,
                center,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity otherLane = spawnRoleMonsterEntity(
                context,
                "area-api-other-lane",
                Optional.empty(),
                TeamId.RED,
                2,
                center.add(0.5, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                ResourceLocation.fromNamespaceAndPath("semion-td", "gametest/area_api_lane_filter"),
                towerEntity,
                center,
                3.0,
                Set.of(),
                null,
                AreaVfxSpec.none()
        );

        AreaEffectResult<SemionMonsterEntity> result = SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            target.setHealth(target.getHealth() - 10.0F);
            return AreaEffectOutcome.APPLIED;
        });

        if (!assertEquals(context, 1, result.candidateCount(), "Area API should only query the defended lane.")) {
            return;
        }
        if (!assertEquals(context, 1, result.appliedCount(), "Area API should report the changed target.")) {
            return;
        }
        if (!assertEquals(context, 0, result.killedCount(), "Non-lethal area effects should not report kills.")) {
            return;
        }
        if (!assertClose(context, 90.0, sameLane.getHealth(), "Area API should apply the action to the defended lane.")) {
            return;
        }
        if (!assertClose(context, 100.0, otherLane.getHealth(), "Area API should ignore monsters assigned to another lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaEffectApiRegisteredAndClonesModeOnlyAddsIllusions(GameTestHelper context) {
        UUID playerId = stableUuid("area-effect-api-clone-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos sourcePosition = towerPlacementPos(lane);
        BlockPos targetPosition = nearbyTowerPlacementPos(lane, sourcePosition);
        TestTower source = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(sourcePosition));
        TestTower registeredTarget = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(targetPosition));
        lane.addTower(source);
        lane.addTower(registeredTarget);
        SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(source.entityId().orElseThrow());

        Vec3 illusionPosition = sourceEntity.position().add(1.0, 0.0, 0.0);
        IllusionRuntimeTower illusion = new IllusionRuntimeTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(BlockPos.containing(illusionPosition))
        );
        SemionTowerEntity illusionEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        illusionEntity.configure(illusion, lane.laneLayout());
        illusionEntity.setPos(illusionPosition);
        context.getLevel().addFreshEntity(illusionEntity);

        Vec3 strayPosition = sourceEntity.position().add(1.5, 0.0, 0.0);
        TestTower unregisteredTower = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(BlockPos.containing(strayPosition)));
        SemionTowerEntity unregisteredEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        unregisteredEntity.configure(unregisteredTower, lane.laneLayout());
        unregisteredEntity.setPos(strayPosition);
        context.getLevel().addFreshEntity(unregisteredEntity);

        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                ResourceLocation.fromNamespaceAndPath("semion-td", "gametest/area_api_clone_filter"),
                sourceEntity,
                sourceEntity.position(),
                6.0,
                TowerAreaTargetMode.REGISTERED_AND_CLONES,
                false,
                null,
                AreaVfxSpec.none()
        );
        AreaEffectResult<AreaTowerTarget> result = SemionTdApi.areaEffects()
                .applyToTowers(request, ignored -> AreaEffectOutcome.APPLIED);

        if (!assertEquals(context, 2, result.candidateCount(), "Registered-and-clones mode should include one registered target and one illusion.")) {
            return;
        }
        if (!assertEquals(context, 1L, result.hits().stream().filter(hit -> hit.target().illusion()).count(), "Only IllusionRuntimeTower should be marked as an illusion.")) {
            return;
        }
        if (!assertTrue(context, result.hits().stream().noneMatch(hit -> hit.target().tower() == unregisteredTower), "An unrelated unregistered tower entity should not be treated as an illusion.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void deathStackTowersGainStacksFromNearbyWaveIncomeAndTowerDeaths(GameTestHelper context) {
        UUID playerId = stableUuid("death-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 deathPosition = lane.laneLayout().positionAt(0.0);
        BlockPos towerBlock = BlockPos.containing(deathPosition.x, deathPosition.y - 1.0, deathPosition.z);
        GridPosition stackTowerPosition = GridPosition.from(towerBlock);
        AntiTankerCatTower catTower = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                stackTowerPosition
        );
        lane.addTower(catTower);

        Monster waveMonster = deathStackTestMonster("death-stack-wave", Optional.empty(), TeamId.RED, 1);
        waveMonster.syncLaneProgress(0.0);
        waveMonster.syncHealth(0.0);
        lane.activeMonsters().add(waveMonster);
        lane.tick(context.getLevel().getServer());

        Monster incomeMonster = deathStackTestMonster("death-stack-income", Optional.of(TeamId.BLUE), TeamId.RED, 1);
        incomeMonster.syncLaneProgress(0.0);
        incomeMonster.syncHealth(0.0);
        lane.activeMonsters().add(incomeMonster);
        lane.tick(context.getLevel().getServer());

        ProductionTower nearbyTower = new ProductionTower(
                VillagerTowers.T1_SPLASH_TOWER,
                stableUuid("death-stack-nearby-tower"),
                TeamId.RED,
                1,
                new GridPosition(stackTowerPosition.x() + 1, stackTowerPosition.y(), stackTowerPosition.z())
        );
        lane.addTower(nearbyTower);
        lane.killTower(nearbyTower);

        List<String> detailLines = catTower.runtimeDetailLines();
        if (!assertTrue(context, detailLines.stream().anyMatch(line -> line.contains("사망 스택 3/")), "Nearby wave, income, and tower deaths should each add one death stack: " + detailLines)) {
            return;
        }
        if (!assertClose(context, 20.06, catTower.modifyAttackDamage(null, null, 20.0), "Three default T2 anti-tanker cat death stacks should add 0.06 attack damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void villagerTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        VillagerTowerCatalogs.register();

        long baseStarterCount = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> VillagerTowers.isBaseVillagerTower(entry.type()))
                .count();
        if (!assertEquals(context, 4L, baseStarterCount, "Villager catalog should expose four base starter tower families.")) {
            return;
        }
        long advStarterCount = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> VillagerTowers.isAdvVillagerTower(entry.type()))
                .count();
        if (!assertEquals(context, 4L, advStarterCount, "Villager ADV catalog should expose four separate starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(VillagerTowers.T1_SPLASH_TOWER).size(), "Splash starter should link to librarian tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(VillagerTowers.T1_GOLEM_TOWER).size(), "Golem starter should link to llama tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(VillagerTowers.T1_ALLAY_TOWER).size(), "Allay starter should branch to heal and weapon-smith support towers.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(VillagerTowers.T1_CAT_TOWER).size(), "Cat starter should branch to anti-tanker and lane-clear towers.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER).orElseThrow().create(stableUuid("cat-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof AntiTankerCatTower, "Anti-tanker cat catalog entry should create AntiTankerCatTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T2_LANE_CLEAR_CAT_TOWER).orElseThrow().create(stableUuid("lane-cat-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof LaneClearCatTower, "Lane-clear cat catalog entry should create LaneClearCatTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(VillagerTowers.T1_ALLAY_TOWER).orElseThrow().create(stableUuid("allay-catalog-owner"), TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)) instanceof AllayTower, "Allay catalog entry should create AllayTower through the widened production factory.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illagerTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        IllagerTowerCatalogs.register();

        if (!assertEquals(context, 3L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Illager catalog should expose three starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(IllagerTowers.T1_VINDICATOR).size(), "Vindicator starter should link to captain tank tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(IllagerTowers.T1_PILLAGER).size(), "Pillager starter should branch to single and splash captain towers.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(IllagerTowers.T1_VEX).size(), "Vex starter should branch to low-health and high-health witch towers.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE).orElseThrow().create(stableUuid("illager-single-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof IllagerTower, "Single captain catalog entry should create IllagerTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(IllagerTowers.T2_WITCH_LOW).orElseThrow().create(stableUuid("illager-witch-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof IllagerTower, "Witch catalog entry should create IllagerTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void undeadTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UndeadTowerCatalogs.register();

        if (!assertEquals(context, 3L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Undead catalog should expose three starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(UndeadTowers.T1_ZOMBIE_TOWER).size(), "Zombie starter should link to husk tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(UndeadTowers.T1_SKELETON_TOWER).size(), "Skeleton starter should branch to ranged and melee towers.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER).size(), "Undead animal starter should link to skeleton horse tower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T1_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-zombie-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadZombieTower, "Zombie catalog entry should create UndeadZombieTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-husk-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadHuskTower, "Husk catalog entry should create UndeadHuskTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T3_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-drowned-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadDrownedTower, "Drowned catalog entry should create UndeadDrownedTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_RANGED_SKELETON_TOWER).orElseThrow().create(stableUuid("undead-ranged-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadRangedSkeletonTower, "Ranged skeleton catalog entry should create UndeadRangedSkeletonTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_MELEE_TOWER).orElseThrow().create(stableUuid("undead-melee-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadMeleeSkeletonTower, "Melee skeleton catalog entry should create UndeadMeleeSkeletonTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER).orElseThrow().create(stableUuid("undead-animal-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadAnimalTower, "Undead animal catalog entry should create UndeadAnimalTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalTowerCatalogRegistersAndLinksAnimalFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        AnimalTowerCatalogs.register();

        if (!assertEquals(context, 4L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Animal catalog should expose pig, wolf, rabbit, and fox starter families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_PIG_TOWER).size(), "Pig starter should link to T2 pig tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_PIG_TOWER).size(), "T2 pig should link to T3 pig tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_WOLF_TOWER).size(), "Wolf starter should link to T2 wolf tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_WOLF_DPS_TOWER).size(), "T2 wolf should link to T3 wolf tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T1_RABBIT_TOWER).size(), "Rabbit starter should link to T2 rabbit tower.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(AnimalTowers.T2_RABBIT_TOWER).size(), "T2 rabbit should link to T3 rabbit tower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_PIG_TOWER).orElseThrow().create(stableUuid("pig-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof PigTower, "Pig catalog entry should create PigTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_WOLF_TOWER).orElseThrow().create(stableUuid("wolf-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof WolfTower, "Wolf catalog entry should create WolfTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_RABBIT_TOWER).orElseThrow().create(stableUuid("rabbit-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof RabbitTower, "Rabbit catalog entry should create RabbitTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(AnimalTowers.T1_FOX_TOWER).orElseThrow().create(stableUuid("fox-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof FoxTower, "Fox catalog entry should create FoxTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionTowerCatalogRegistersAndLinksLegionFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        LegionTowerCatalogs.register();

        if (!assertEquals(context, 6L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Legion catalog should expose chicken, slime, penguin, parrot, goat, and illusion starters.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(LegionTowers.T1_CHICKEN).size(), "Chicken starter should branch to social and DPS upgrades.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_SLIME_TOWER).size(), "Slime starter should link to T2 slime.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_PENGUIN).size(), "Penguin starter should link to T2 penguin.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(LegionTowers.T1_PARROT_TOWER).size(), "Parrot starter should link to T2 parrot.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.T1_SLIME_TOWER).orElseThrow().create(stableUuid("legion-slime-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionSlimeTower, "Slime catalog entry should create LegionSlimeTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.T1_PARROT_TOWER).orElseThrow().create(stableUuid("legion-parrot-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionParrotTower, "Parrot catalog entry should create LegionParrotTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(LegionTowers.ILLUSION_TOWER).orElseThrow().create(stableUuid("legion-illusion-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof LegionGlobalIllusionTower, "Illusion catalog entry should create LegionGlobalIllusionTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalTowerDescriptionsRenderConfiguredAbilityValues(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new java.util.LinkedHashMap<>(defaults.abilities());
        Map<String, Double> rabbitAbilities = new java.util.LinkedHashMap<>(abilities.get(AnimalTowers.T3_RABBIT_TOWER.id()));
        rabbitAbilities.put("damagePerStack", 12.0);
        rabbitAbilities.put("maxStackExtraIntervalReduction", 9.0);
        abilities.put(AnimalTowers.T3_RABBIT_TOWER.id(), rabbitAbilities);

        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        try {
            TowerType resolved = TowerBalanceRuntime.resolve(AnimalTowers.T3_RABBIT_TOWER);
            if (!assertTrue(context, resolved.description().stream().anyMatch(line -> line.contains("공격력이 12 증가")), "Rabbit description should render configured damage per stack.")) {
                return;
            }
            if (!assertTrue(context, resolved.description().stream().anyMatch(line -> line.contains("공격 주기가 9틱 감소")), "Rabbit description should render configured interval reduction.")) {
                return;
            }
        } finally {
            TowerBalanceRuntime.apply(defaults);
        }
        context.succeed();
    }

    @GameTest
    public void legionTowerDescriptionsRenderConfiguredAbilityValues(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new java.util.LinkedHashMap<>(defaults.abilities());
        Map<String, Double> penguinAbilities = new java.util.LinkedHashMap<>(abilities.get(LegionTowers.T2_PENGUIN.id()));
        penguinAbilities.put("cloneCount", 4.0);
        penguinAbilities.put("cloneHealthRatio", 0.80);
        penguinAbilities.put("splashRadius", 2.0);
        penguinAbilities.put("splashDamageRatio", 0.40);
        abilities.put(LegionTowers.T2_PENGUIN.id(), penguinAbilities);
        Map<String, Double> slimeAbilities = new java.util.LinkedHashMap<>(abilities.get(LegionTowers.T2_SLIME_TOWER.id()));
        slimeAbilities.put("regenAmount", 7.0);
        abilities.put(LegionTowers.T2_SLIME_TOWER.id(), slimeAbilities);
        Map<String, Double> parrotAbilities = new java.util.LinkedHashMap<>(abilities.get(LegionTowers.T1_PARROT_TOWER.id()));
        parrotAbilities.put("attackStackBonus", 0.25);
        parrotAbilities.put("maxAttackStacks", 3.0);
        abilities.put(LegionTowers.T1_PARROT_TOWER.id(), parrotAbilities);

        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        try {
            TowerType penguin = TowerBalanceRuntime.resolve(LegionTowers.T2_PENGUIN);
            if (!assertTrue(context, penguin.description().stream().anyMatch(line -> line.contains("80%") && line.contains("4체")), "Penguin description should render configured clone ratio and count.")) {
                return;
            }
            if (!assertTrue(context, penguin.description().stream().anyMatch(line -> line.contains("2블록") && line.contains("40%")), "Penguin description should render configured splash values.")) {
                return;
            }
            TowerType slime = TowerBalanceRuntime.resolve(LegionTowers.T2_SLIME_TOWER);
            if (!assertTrue(context, slime.description().stream().anyMatch(line -> line.contains("체력이 7씩 재생")), "Slime description should render configured regen amount.")) {
                return;
            }
            TowerType parrot = TowerBalanceRuntime.resolve(LegionTowers.T1_PARROT_TOWER);
            if (!assertTrue(context, parrot.description().stream().anyMatch(line -> line.contains("25% 증가") && line.contains("최대 75%")), "Parrot description should render configured stack bonus and cap.")) {
                return;
            }
        } finally {
            TowerBalanceRuntime.apply(defaults);
        }
        context.succeed();
    }

    @GameTest
    public void warlockTowerDescriptionsRenderConfiguredAbilityValues(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new java.util.LinkedHashMap<>(defaults.abilities());
        Map<String, Double> rangedAbilities = new java.util.LinkedHashMap<>(abilities.get(WarlockTowers.RANGED_WARLOCK_TOWER.id()));
        rangedAbilities.put("lowHealthSacrificeThreshold", 0.25);
        rangedAbilities.put("splashRadiusPerStep", 3.0);
        abilities.put(WarlockTowers.RANGED_WARLOCK_TOWER.id(), rangedAbilities);

        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        try {
            TowerType resolved = TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER);
            if (!assertTrue(context, resolved.description().stream().anyMatch(line -> line.contains("체력이 25% 미만")), "Warlock description should render configured absorb threshold.")) {
                return;
            }
            if (!assertTrue(context, resolved.description().stream().anyMatch(line -> line.contains("3블록의 스플래시")), "Warlock description should render configured splash radius.")) {
                return;
            }
        } finally {
            TowerBalanceRuntime.apply(defaults);
        }
        context.succeed();
    }

    @GameTest
    public void builtInCatalogReloadRegistersTowerJobs(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        if (!assertPresent(context, JobRegistry.find(VillagerTowerJob.ID), "Built-in reload should register the villager tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(UndeadTowerJob.ID), "Built-in reload should register the undead tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(AnimalTowerJob.ID), "Built-in reload should register the animal tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(WarlockTowerJob.ID), "Built-in reload should register the warlock tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(LegionTowerJob.ID), "Built-in reload should register the legion tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(ResonanceTowerJob.ID), "Built-in reload should register the resonance tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(IllagerTowerJob.ID), "Built-in reload should register the illager tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(NetherTowerJob.ID), "Built-in reload should register the nether tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(EndTowerJob.ID), "Built-in reload should register the end tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(OceanTowerJob.ID), "Built-in reload should register the ocean tower job.")) {
            return;
        }
        if (!assertEquals(context, 44L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Built-in reload should expose every production starter family including end and the six ocean paths.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void endTowerJobLimitsCoreToOne(GameTestHelper context) {
        UUID playerId = stableUuid("end-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, EndTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        BlockPos secondCorePos = nearbyTowerPlacementPos(lane, corePos);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        EndTowers.BASE_END_TOWER.id(),
                        EndTowers.T1_ENDERMITE_TOWER.id(),
                        EndTowers.T1_SHULKER_TOWER.id()
                ),
                starterIds,
                "End job should expose its core and two feeder starters."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, corePos, EndTowers.BASE_END_TOWER.id()),
                "End job should place its first core tower."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, secondCorePos, EndTowers.BASE_END_TOWER.id()),
                "End job should reject a second core tower."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void hatchedEndCoreKeepsItsGridHeightDuringEntitySync(GameTestHelper context) {
        UUID playerId = stableUuid("end-core-height-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, EndTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, corePos, EndTowers.BASE_END_TOWER.id()),
                "End height test should place its core tower."
        )) {
            return;
        }
        EndTower core = (EndTower) lane.towerAt(GridPosition.from(corePos));
        int originalGridY = core.position().y();
        core.onWaveStarted(lane, 1);
        core.tick(lane);
        SemionTowerEntity entity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(core.entityId().orElseThrow());

        for (int index = 0; index < 5; index++) {
            core.isDestroyed(lane);
            core.onStateChanged(lane);
        }

        if (!assertEquals(context, originalGridY, core.position().y(),
                "Hatched End core entity sync must not increase its grid Y coordinate.")) {
            return;
        }
        if (!assertClose(context, originalGridY + 2.0, entity.getY(),
                "Phantom should remain exactly one visual block above the normal tower anchor.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illagerTowerJobUsesIllagerStartersAndBranchUpgrades(GameTestHelper context) {
        UUID playerId = stableUuid("illager-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, IllagerTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos pillagerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        IllagerTowers.T1_VINDICATOR.id(),
                        IllagerTowers.T1_PILLAGER.id(),
                        IllagerTowers.T1_VEX.id()
                ),
                starterIds,
                "Illager job should expose exactly vindicator, pillager, and vex starters."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, pillagerPos, AnimalTowers.T1_PIG_TOWER.id()),
                "Illager job should reject non-illager starter placement."
        )) {
            return;
        }
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, pillagerPos, IllagerTowers.T1_PILLAGER.id()), "Illager job should place pillager tower.")) {
            return;
        }
        Set<String> pillagerUpgradeIds = ProductionTowerService.availableUpgrades(game, playerId, pillagerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id()),
                pillagerUpgradeIds,
                "Pillager tower should branch to single and splash captain upgrades."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void illagerRaidBonusesAreExposedAsTowerTimedEffects(GameTestHelper context) {
        UUID playerId = stableUuid("illager-raid-effect-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, IllagerTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        GridPosition towerPosition = new GridPosition(base.getX(), base.getY(), base.getZ());
        IllagerTower tower = new IllagerTower(
                IllagerTowers.T3_RAVAGER,
                playerId,
                TeamId.RED,
                1,
                towerPosition,
                towerPosition
        );
        lane.addTower(tower);
        if (!assertTrue(context, tower.entityId().isPresent(), "Illager tower should spawn a runtime tower entity.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Illager tower entity should exist."));
            return;
        }

        SemionPlayer player = game.players().get(playerId);
        if (player == null) {
            context.fail(Component.literal("Illager raid test player should exist."));
            return;
        }
        IllagerRaidStates.onRoundStarted(new JobContext(game, player));
        IllagerRaidState state = IllagerRaidStates.get(playerId).orElseThrow();
        state.resetForRound(4);
        state.addGauge(100, 100);

        int activatedTowers = IllagerRaidStates.playPendingActivationEffects(context.getLevel().getServer(), lane);
        if (!assertEquals(context, 1, activatedTowers, "Illager raid activation should emit VFX for each live illager tower.")) {
            return;
        }
        if (!assertTrue(context, !state.pendingActivationEffects(), "Illager raid activation effects should be consumed once.")) {
            return;
        }

        tower.tick(lane);

        if (!assertClose(context, 0.20, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Illager raid damage bonus should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertClose(context, 0.08, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS), "Illager raid attack speed bonus should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertClose(context, 0.25, towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Illager raid damage reduction should be exposed as a tower timed effect.")) {
            return;
        }
        if (!assertEquals(context, 40, towerEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Illager raid timed effect duration should come from towerbalance.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionTowerJobUsesLegionStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("legion-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, LegionTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        LegionTowers.T1_CHICKEN.id(),
                        LegionTowers.T1_SLIME_TOWER.id(),
                        LegionTowers.T1_PENGUIN.id(),
                        LegionTowers.T1_PARROT_TOWER.id(),
                        LegionTowers.T1_GOAT_TOWER.id(),
                        LegionTowers.ILLUSION_TOWER.id()
                ),
                starterIds,
                "Legion job should expose all legion starters including goat and illusion tower."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, LegionTowers.T1_CHICKEN.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Legion job should be allowed to place chicken tower.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(LegionTowers.T2_CHICKEN_TOWER.id(), LegionTowers.T2_DPS_CHICKEN_TOWER.id()),
                upgradeIds,
                "Legion chicken starter should branch to both chicken upgrades."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void resonanceTowerJobUsesResonanceStartersAndRuntimeLinks(GameTestHelper context) {
        UUID playerId = stableUuid("resonance-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, ResonanceTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos focusPos = towerPlacementPos(lane);
        game.players().get(playerId).economy().addMineral(1_000);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        ResonanceTowers.FOCUS_CRYSTAL.id(),
                        ResonanceTowers.WAVE_CRYSTAL.id(),
                        ResonanceTowers.FROST_CRYSTAL.id(),
                        ResonanceTowers.AMPLIFY_CRYSTAL.id()
                ),
                starterIds,
                "Resonance job should expose only resonance starter towers."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, focusPos, AnimalTowers.T1_PIG_TOWER.id()),
                "Resonance job should reject non-resonance starter placement."
        )) {
            return;
        }
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, focusPos, ResonanceTowers.FOCUS_CRYSTAL.id()), "Resonance job should place focus crystal.")) {
            return;
        }
        Set<String> focusUpgradeIds = ProductionTowerService.availableUpgrades(game, playerId, focusPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(ResonanceTowers.FOCUS_PRISM.id()),
                focusUpgradeIds,
                "Focus crystal should upgrade into the T2 focus prism."
        )) {
            return;
        }
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_CRYSTAL, focusPos.offset(1, 0, 0));
        addResonanceTower(lane, playerId, ResonanceTowers.FROST_CRYSTAL, focusPos.offset(-1, 0, 0));
        addResonanceTower(lane, playerId, ResonanceTowers.AMPLIFY_CRYSTAL, focusPos.offset(0, 0, 1));
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_PRISM, focusPos.offset(0, 0, -1));
        addResonanceTower(lane, playerId, ResonanceTowers.FROST_PRISM, focusPos.offset(1, 0, 1));
        lane.markWaveStarted(1);
        if (!assertTrue(context, lane.towers().get(0) instanceof ResonanceTower, "Placed focus crystal should use ResonanceTower runtime.")) {
            return;
        }
        ResonanceTower focus = (ResonanceTower) lane.towerAt(GridPosition.from(focusPos));
        if (!assertEquals(context, 1, focus.resonanceLevel(), "T1 focus should cap at resonance level 1.")) {
            return;
        }
        if (!assertEquals(context, 5, focus.resonanceLinks(), "Focus should count five nearby different species within one tile.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, ProductionTowerService.upgradeTower(game, playerId, focusPos, ResonanceTowers.FOCUS_PRISM.id()), "Focus crystal should upgrade into T2 focus prism.")) {
            return;
        }
        focus = (ResonanceTower) lane.towerAt(GridPosition.from(focusPos));
        if (!assertEquals(context, 1, focus.resonanceLevel(), "An in-wave upgrade should retain the captured resonance level.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, ProductionTowerService.upgradeTower(game, playerId, focusPos, ResonanceTowers.FOCUS_CORE.id()), "Focus prism should upgrade into T3 focus core.")) {
            return;
        }
        focus = (ResonanceTower) lane.towerAt(GridPosition.from(focusPos));
        if (!assertEquals(context, 1, focus.resonanceLevel(), "A second in-wave upgrade should retain the captured resonance level.")) {
            return;
        }
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof ResonanceTower && tower != focus) {
                lane.killTower(tower);
            }
        }
        if (!assertEquals(context, 5, focus.resonanceLinks(), "Links captured at wave start should survive nearby tower deaths.")) {
            return;
        }
        lane.markWaveStarted(2);
        if (!assertEquals(context, 0, focus.resonanceLinks(), "The next wave start should replace the previous link snapshot.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void resonanceFrostMoobloomAppliesAttackSpeedDebuffAndSharesDamageAura(GameTestHelper context) {
        UUID playerId = stableUuid("resonance-frost-aura-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, ResonanceTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos frostPos = towerPlacementPos(lane);
        GridPosition frostGrid = GridPosition.from(frostPos);
        ResonanceTower frost = new ResonanceTower(
                TowerBalanceRuntime.resolve(ResonanceTowers.FROST_CORE),
                playerId,
                TeamId.RED,
                1,
                frostGrid,
                frostGrid
        );
        lane.addTower(frost);
        BlockPos focusPos = frostPos.offset(1, 0, 0);
        GridPosition focusGrid = GridPosition.from(focusPos);
        ResonanceTower focus = new ResonanceTower(
                TowerBalanceRuntime.resolve(ResonanceTowers.FOCUS_CRYSTAL),
                playerId,
                TeamId.RED,
                1,
                focusGrid,
                focusGrid
        );
        lane.addTower(focus);
        addResonanceTower(lane, playerId, ResonanceTowers.FOCUS_PRISM, frostPos.offset(-1, 0, 0));
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_CRYSTAL, frostPos.offset(0, 0, 1));
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_PRISM, frostPos.offset(0, 0, -1));
        addResonanceTower(lane, playerId, ResonanceTowers.AMPLIFY_CRYSTAL, frostPos.offset(1, 0, 1));
        addResonanceTower(lane, playerId, ResonanceTowers.AMPLIFY_PRISM, frostPos.offset(-1, 0, -1));
        ResonanceService.refresh(lane.towers());

        if (!assertEquals(context, 3, frost.resonanceLevel(), "T3 frost should unlock level 3 with six nearby different mooblooms.")) {
            return;
        }
        if (!assertClose(context, 1.0, focus.auraDamageVsSlowedBonus(), "Nearby mooblooms should receive frost's damage-vs-debuffed aura.")) {
            return;
        }
        SemionTowerEntity frostEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(frost.entityId().orElseThrow());
        if (frostEntity == null) {
            context.fail(Component.literal("Frost moobloom entity should exist."));
            return;
        }
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "frost-aura-target",
                Optional.empty(),
                TeamId.RED,
                1,
                frostEntity.position().add(0.0, 0.0, 1.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        if (!assertClose(context, 100.0, focus.modifyAttackDamage(null, target, 100.0), "Frost aura should require an active frost debuff.")) {
            return;
        }

        frost.onAttack(frostEntity, target, 20.0, false);

        if (!assertClose(context, 0.40, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "T3 frost should reduce movement speed.")) {
            return;
        }
        if (!assertClose(context, 0.40, target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION), "T3 frost should reduce attack speed.")) {
            return;
        }
        if (!assertClose(context, 200.0, focus.modifyAttackDamage(null, target, 100.0), "Frost aura should make nearby mooblooms deal bonus damage to debuffed targets.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionSlimeCloneUsesCatalogRuntimeAndRegenerates(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-slime-clone-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        TowerType slimeType = ProductionTowerCatalog.entry(LegionTowers.T2_SLIME_TOWER).orElseThrow().type();
        CapturingLegionSlimeTower slime = new CapturingLegionSlimeTower(
                slimeType,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(slime);
        lane.markWaveStarted(1);
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }

        if (!assertEquals(context, 2, slime.spawnedCloneEntities().size(), "T2 slime should spawn configured clone count within 40 ticks.")) {
            return;
        }
        SemionTowerEntity cloneEntity = slime.spawnedCloneEntities().getFirst();
        if (!assertTrue(context, cloneEntity.runtimeTower() instanceof LegionSlimeTower, "Slime clone should reuse the catalog LegionSlimeTower runtime.")) {
            return;
        }
        LegionSlimeTower cloneTower = (LegionSlimeTower) cloneEntity.runtimeTower();
        double damagedHealth = cloneTower.health() - 10.0;
        cloneTower.syncHealth(damagedHealth);
        cloneEntity.setHealth((float) damagedHealth);
        for (int tick = 0; tick < 20; tick++) {
            slime.tick(lane);
        }
        if (!assertClose(context, damagedHealth + 3.0, cloneTower.health(), "Slime clone should regenerate using configured runtime logic.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionIllusionDeathClonesParrotWithCatalogRuntimeAndStackLogic(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-global-parrot-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos parrotPos = towerPlacementPos(lane);
        LegionParrotTower parrot = new LegionParrotTower(
                ProductionTowerCatalog.entry(LegionTowers.T2_PARROT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(parrotPos)
        );
        lane.addTower(parrot);
        CapturingGlobalIllusionTower illusion = new CapturingGlobalIllusionTower(
                ProductionTowerCatalog.entry(LegionTowers.ILLUSION_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(nearbyTowerPlacementPos(lane, parrotPos))
        );
        lane.addTower(illusion);

        illusion.syncHealth(0.0);
        illusion.notifyDeath(lane);

        if (!assertEquals(context, 0, illusion.spawnedCloneEntities().size(), "Illusion tower death should queue clone spawning.")) {
            return;
        }
        tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        if (!assertEquals(context, 1, illusion.spawnedCloneEntities().size(), "Illusion tower death should clone the living same-owner parrot tower on global queue tick.")) {
            return;
        }
        SemionTowerEntity cloneEntity = illusion.spawnedCloneEntities().getFirst();
        if (!assertTrue(context, cloneEntity.runtimeTower() instanceof LegionParrotTower, "Illusion-created parrot clone should reuse LegionParrotTower runtime.")) {
            return;
        }
        LegionParrotTower cloneTower = (LegionParrotTower) cloneEntity.runtimeTower();
        double baseDamage = cloneTower.type().damage();
        int baseInterval = cloneTower.type().attackIntervalTicks();
        if (!assertClose(context, baseDamage, cloneTower.modifyAttackDamage(cloneEntity, null, baseDamage), "Parrot clone should start without attack stacks.")) {
            return;
        }
        cloneTower.onAttack(cloneEntity, null, baseDamage, false);
        if (!assertEquals(context, 1, cloneTower.attackStacks(), "Parrot clone should gain a stack after attacking.")) {
            return;
        }
        if (!assertClose(context, baseDamage * 1.2, cloneTower.modifyAttackDamage(cloneEntity, null, baseDamage), "T2 parrot clone should apply configured attack stack damage bonus.")) {
            return;
        }
        if (!assertEquals(context, (int) Math.ceil(baseInterval / 1.2), cloneTower.adjustAttackInterval(baseInterval), "T2 parrot clone should apply configured attack stack speed bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatTowerCatalogRegistersStarterAndUpgradeTree(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        ProductionTowerCatalog.CatalogEntry t1Entry = ProductionTowerCatalog.entry(LegionTowers.T1_GOAT_TOWER).orElseThrow();
        ProductionTowerCatalog.CatalogEntry t2Entry = ProductionTowerCatalog.entry(LegionTowers.T2_STRONG_GOAT_TOWER).orElseThrow();
        ProductionTowerCatalog.CatalogEntry t3Entry = ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow();
        if (!assertTrue(context, t1Entry.starter(), "T1 goat should be a starter legion tower.")) {
            return;
        }
        if (!assertEquals(context, 2, t2Entry.tier(), "Strong goat should be registered as tier 2.")) {
            return;
        }
        if (!assertEquals(context, 3, t3Entry.tier(), "Extreme goat should be registered as tier 3.")) {
            return;
        }
        if (!assertEquals(context, 70L, t1Entry.type().mineralCost(), "T1 goat should cost 70 minerals.")) {
            return;
        }
        if (!assertEquals(context, 150L, t2Entry.type().mineralCost(), "T2 goat should cost 150 minerals.")) {
            return;
        }
        if (!assertEquals(context, 250L, t3Entry.type().mineralCost(), "T3 goat should cost 250 minerals.")) {
            return;
        }
        TowerUpgradeOption t2Upgrade = ProductionTowerCatalog.upgrade(LegionTowers.T1_GOAT_TOWER, LegionTowers.T2_STRONG_GOAT_TOWER.id()).orElseThrow();
        TowerUpgradeOption t3Upgrade = ProductionTowerCatalog.upgrade(LegionTowers.T2_STRONG_GOAT_TOWER, LegionTowers.T3_EXTREME_GOAT_TOWER.id()).orElseThrow();
        if (!assertEquals(context, 150L, t2Upgrade.mineralCost(), "T2 goat upgrade should cost 150 minerals.")) {
            return;
        }
        if (!assertEquals(context, 250L, t3Upgrade.mineralCost(), "T3 goat upgrade should cost 250 minerals.")) {
            return;
        }
        Tower created = t1Entry.create(stableUuid("legion-goat-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0));
        if (!assertTrue(context, created instanceof LegionGoatTower, "Goat catalog entry should create LegionGoatTower runtime.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatBuffPulsesDuringPrepareAndAppearsOnRightClick(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, LegionTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos targetPos = towerPlacementPos(lane);
        BlockPos goatPos = nearbyTowerPlacementPos(lane, targetPos);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, targetPos, LegionTowers.T1_CHICKEN.id()), "Player should place a Legion target during prepare.")) {
            return;
        }
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, ProductionTowerService.placeTower(game, playerId, goatPos, LegionTowers.T1_GOAT_TOWER.id()), "Player should place a goat during prepare.")) {
            return;
        }

        Tower target = lane.towers().stream()
                .filter(tower -> tower.type().id().equals(LegionTowers.T1_CHICKEN.id()))
                .findFirst()
                .orElseThrow();
        LegionGoatTower goat = lane.towers().stream()
                .filter(LegionGoatTower.class::isInstance)
                .map(LegionGoatTower.class::cast)
                .findFirst()
                .orElseThrow();
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(((EntityBackedTower) target).entityId().orElseThrow());

        game.tick(context.getLevel().getServer());
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Goat buff verification should run during the real prepare phase.")) {
            return;
        }
        if (!assertClose(context, 0.02, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat should buff a player's Legion tower during prepare.")) {
            return;
        }
        if (!assertClose(context, 0.02, targetEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat should temporarily reduce incoming damage for a player's Legion tower during prepare.")) {
            return;
        }
        if (!assertTrue(context, towerTimedEffectBody(targetEntity).contains("피해 증가 +2.0%"), "Right-click tower details should show the active goat damage buff.")) {
            return;
        }
        if (!assertTrue(context, towerTimedEffectBody(targetEntity).contains("받피 감소 +2.0%"), "Right-click tower details should show the active goat damage reduction buff.")) {
            return;
        }

        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        try {
            InteractionResult result = SemionTowerInteractionService.handleUse(
                    manager,
                    player,
                    context.getLevel(),
                    InteractionHand.MAIN_HAND,
                    targetEntity,
                    new EntityHitResult(targetEntity)
            );
            if (!assertEquals(context, InteractionResult.SUCCESS, result, "A real player right-click should open the buffed tower details.")) {
                return;
            }
        } finally {
            setField(manager, "activeGame", null);
            manager.shutdown();
        }

        for (int tick = 0; tick <= goat.type().attackIntervalTicks(); tick++) {
            targetEntity.aiStep();
            game.tick(context.getLevel().getServer());
        }
        int buffDurationTicks = TowerBalanceRuntime.abilityTicks(goat.type().id(), "buffDurationTicks");
        if (!assertEquals(context, buffDurationTicks, targetEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat should refresh its buff every configured pulse interval.")) {
            return;
        }
        if (!assertEquals(context, buffDurationTicks, targetEntity.activeTimedEffectTicks(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat should refresh its damage reduction every configured pulse interval.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void legionGoatTowerBuffsLegionBodiesAndClonesUpToThreeStacks(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID playerId = stableUuid("legion-goat-buff-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos slimePos = towerPlacementPos(lane);
        CapturingLegionSlimeTower slime = new CapturingLegionSlimeTower(
                ProductionTowerCatalog.entry(LegionTowers.T2_SLIME_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(slimePos)
        );
        lane.addTower(slime);

        BlockPos goatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower goat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(goatPos)
        );
        lane.addTower(goat);
        BlockPos secondGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower secondGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(secondGoatPos)
        );
        lane.addTower(secondGoat);
        BlockPos thirdGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower thirdGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(thirdGoatPos)
        );
        lane.addTower(thirdGoat);
        BlockPos fourthGoatPos = nearbyTowerPlacementPos(lane, slimePos);
        LegionGoatTower fourthGoat = new LegionGoatTower(
                ProductionTowerCatalog.entry(LegionTowers.T3_EXTREME_GOAT_TOWER).orElseThrow().type(),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(fourthGoatPos)
        );
        lane.addTower(fourthGoat);

        lane.markWaveStarted(1);
        for (int tick = 0; tick < 40; tick++) {
            tickLaneWithGlobalCloneQueue(lane, context.getLevel().getServer());
        }
        if (!assertEquals(context, 2, slime.spawnedCloneEntities().size(), "T2 slime should spawn clones for goat buff targeting.")) {
            return;
        }

        SemionTowerEntity bodyEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(slime.entityId().orElseThrow());
        SemionTowerEntity cloneEntity = slime.spawnedCloneEntities().getFirst();
        goat.tick(lane);
        secondGoat.tick(lane);
        thirdGoat.tick(lane);
        fourthGoat.tick(lane);
        if (!assertClose(context, 0.15, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat body damage buff should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.195, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat body damage reduction should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.195, cloneEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Goat clone damage buff should stack up to three times.")) {
            return;
        }
        if (!assertClose(context, 0.195, cloneEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION), "Goat clone damage reduction should stack up to three times.")) {
            return;
        }

        if (!assertTrue(context, lane.killTower(goat), "Goat stack test should kill the first provider.")) {
            return;
        }
        int buffDurationTicks = TowerBalanceRuntime.abilityTicks(goat.type().id(), "buffDurationTicks");
        for (int tick = 0; tick < buffDurationTicks; tick++) {
            bodyEntity.aiStep();
        }
        for (LegionGoatTower provider : List.of(secondGoat, thirdGoat, fourthGoat)) {
            provider.resetForRound(lane);
            provider.tick(lane);
        }
        if (!assertClose(context, 0.15, bodyEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "A dead goat should not consume one of the three buff stacks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void undeadTowerJobUsesUndeadStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("undead-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, UndeadTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        UndeadTowers.T1_ZOMBIE_TOWER.id(),
                        UndeadTowers.T1_SKELETON_TOWER.id(),
                        UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id()
                ),
                starterIds,
                "Undead job should expose only undead starter towers."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, UndeadTowers.T1_ZOMBIE_TOWER.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Undead job should be allowed to place zombie tower.")) {
            return;
        }

        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(UndeadTowers.T2_ZOMBIE_TOWER.id()),
                upgradeIds,
                "Undead zombie starter should connect to the husk upgrade for undead players."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void animalTowerJobUsesAnimalStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("animal-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AnimalTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        AnimalTowers.T1_PIG_TOWER.id(),
                        AnimalTowers.T1_WOLF_TOWER.id(),
                        AnimalTowers.T1_RABBIT_TOWER.id(),
                        AnimalTowers.T1_FOX_TOWER.id()
                ),
                starterIds,
                "Animal job should expose only animal starter towers."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, towerPos, UndeadTowers.T1_ZOMBIE_TOWER.id()),
                "Animal job should reject undead starter placement."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, towerPos, VillagerTowers.T1_SPLASH_TOWER.id()),
                "Animal job should reject villager starter placement."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, AnimalTowers.T1_RABBIT_TOWER.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Animal job should be allowed to place rabbit tower.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(AnimalTowers.T2_RABBIT_TOWER.id()),
                upgradeIds,
                "Animal rabbit starter should connect only to the rabbit upgrade."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void warlockTowerJobLimitsCoreToOneAndAllowsSacrifices(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        BlockPos secondTowerPos = nearbyTowerPlacementPos(lane, towerPos);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        WarlockTowers.BASE_WARLOCK_TOWER.id(),
                        WarlockTowers.T1_SLAVE.id(),
                        WarlockTowers.T1_RANGED_SLAVE.id()
                ),
                starterIds,
                "Warlock job should expose only the core and sacrifice starter towers."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Warlock job should be allowed to place the first core tower."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof WarlockTower, "Placed warlock core should use WarlockTower runtime behavior.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(TowerUpgradeOption::targetType)
                .map(TowerType::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(WarlockTowers.RANGED_WARLOCK_TOWER.id(), WarlockTowers.MELEE_WARLOCK_TOWER.id()),
                upgradeIds,
                "Base warlock core should branch to ranged and melee cores."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Warlock job should reject a second core tower."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "ranged_warlock_tower"),
                "Warlock core should upgrade to the ranged branch."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Upgraded warlock core should still block an extra core tower."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.T1_SLAVE.id()),
                "Warlock sacrifice towers should still be placeable while one core exists."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedWarlockAbsorbsLowPriorityTowerAndGainsConfiguredStats(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-ranged-absorb-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        BlockPos t1Pos = nearbyTowerPlacementPos(lane, corePos);
        WarlockSacrificeTower t1Ranged = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t1Pos)
        );
        lane.addTower(t1Ranged);
        BlockPos t3Pos = nearbyTowerPlacementPos(lane, t1Pos);
        WarlockSacrificeTower t3Ranged = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T3_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t3Pos)
        );
        lane.addTower(t3Ranged);
        int sacrificedEntityId = t3Ranged.entityId().orElseThrow();

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(10.0);
        coreEntity.setHealth(10.0F);
        core.onDamaged(coreEntity, null, 50.0, 60.0, 10.0);

        if (!assertTrue(context, lane.towers().contains(t1Ranged), "Ranged warlock should leave the higher-numbered aggro tower alive after absorbing lowest priority.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().contains(t3Ranged), "Ranged warlock sacrifice target should stay in the lane for next-round respawn.")) {
            return;
        }
        if (!assertEquals(context, 0.0, t3Ranged.health(), "Ranged warlock sacrifice target should be dead for the current round.")) {
            return;
        }
        if (!assertClose(context, 105.5, core.currentMaxHealth(), "Ranged warlock should gain round, permanent, and surviving-pet health bonuses.")) {
            return;
        }
        if (!assertClose(context, 16.9625, core.modifyAttackDamage(null, null, 8.0), "Ranged warlock should gain round, permanent, and surviving-pet damage bonuses.")) {
            return;
        }
        if (!assertEquals(context, 14, core.adjustAttackInterval(20), "Ranged warlock should gain attack interval reduction from absorbed faster tower.")) {
            return;
        }
        core.syncHealth(10.0);
        coreEntity.setHealth(10.0F);
        core.onDamaged(coreEntity, null, 50.0, 60.0, 10.0);
        if (!assertEquals(context, 0.0, t1Ranged.health(), "Ranged warlock should absorb the next living tower instead of reabsorbing a dead target.")) {
            return;
        }
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertEquals(context, t3Ranged.currentMaxHealth(), t3Ranged.health(), "Ranged warlock sacrifice target should respawn with full health next round.")) {
            return;
        }
        if (!assertTrue(context, t3Ranged.entityId().isPresent() && t3Ranged.entityId().getAsInt() != sacrificedEntityId, "Ranged warlock sacrifice target should get a fresh entity on next-round respawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void meleeWarlockAbsorbsHighPriorityTowerAndGainsConfiguredStats(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-melee-absorb-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        BlockPos t1Pos = nearbyTowerPlacementPos(lane, corePos);
        WarlockSacrificeTower t1Melee = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t1Pos)
        );
        lane.addTower(t1Melee);
        BlockPos t3Pos = nearbyTowerPlacementPos(lane, t1Pos);
        WarlockSacrificeTower t3Melee = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T3_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t3Pos)
        );
        lane.addTower(t3Melee);
        int sacrificedEntityId = t3Melee.entityId().orElseThrow();

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(20.0);
        coreEntity.setHealth(20.0F);
        core.onDamaged(coreEntity, null, 80.0, 100.0, 20.0);

        if (!assertTrue(context, lane.towers().contains(t1Melee), "Melee warlock should leave the lower-priority sacrifice tower alive.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().contains(t3Melee), "Melee warlock sacrifice target should stay in the lane for next-round respawn.")) {
            return;
        }
        if (!assertEquals(context, 0.0, t3Melee.health(), "Melee warlock sacrifice target should be dead for the current round.")) {
            return;
        }
        if (!assertClose(context, 212.5, core.currentMaxHealth(), "Melee warlock should gain round, permanent, and surviving-sacrifice health bonuses.")) {
            return;
        }
        if (!assertClose(context, 11.8125, core.modifyAttackDamage(null, null, 5.0), "Melee warlock should gain round, permanent, and surviving-sacrifice damage bonuses.")) {
            return;
        }
        if (!assertClose(context, 100.0, core.modifyIncomingDamage(null, null, 100.0), "Melee warlock should not reduce incoming damage before five absorbed towers.")) {
            return;
        }
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertEquals(context, t3Melee.currentMaxHealth(), t3Melee.health(), "Melee warlock sacrifice target should respawn with full health next round.")) {
            return;
        }
        if (!assertTrue(context, t3Melee.entityId().isPresent() && t3Melee.entityId().getAsInt() != sacrificedEntityId, "Melee warlock sacrifice target should get a fresh entity on next-round respawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void warlockTowerMovesTowardOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-move-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        Vec3 initialTowerPosition = towerEntity.position();
        SemionMonsterEntity target = spawnSummonEntity(
                context,
                "warlock-move-target",
                TeamId.BLUE,
                TeamId.RED,
                1,
                initialTowerPosition.add(8.0, 0.0, 0.0),
                200.0,
                0.0
        );
        target.setNoAi(true);
        double initialDistance = initialTowerPosition.distanceTo(target.position());

        context.runAfterDelay(40, () -> {
            if (!(lane.arenaWorld().getEntity(core.entityId().orElseThrow()) instanceof SemionTowerEntity currentTowerEntity)) {
                context.fail(Component.literal("Warlock tower entity should still exist while checking movement."));
                return;
            }
            Vec3 currentTowerPosition = currentTowerEntity.position();
            if (!assertTrue(
                    context,
                    currentTowerPosition.distanceTo(initialTowerPosition) > 0.1,
                    "Warlock tower should move away from its initial position toward an out-of-range target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    currentTowerPosition.distanceTo(target.position()) < initialDistance,
                    "Warlock tower should get closer to the out-of-range target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void warlockSacrificeSlaveDeathAppliesMonsterEffect(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-slave-death-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        WarlockSacrificeTower tower = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T2_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPos)
        );
        lane.addTower(tower);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        SemionMonsterEntity monster = spawnSummonEntity(
                context,
                "warlock-slave-death-target",
                TeamId.BLUE,
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                0.0
        );

        towerEntity.setHealth(0.0F);
        lane.tick(context.getLevel().getServer());

        if (!assertTrue(context, lane.towers().contains(tower), "Destroyed sacrifice tower should stay in the lane until round reset.")) {
            return;
        }
        if (!assertEquals(context, 0.0, tower.health(), "Destroyed sacrifice tower runtime health should sync to zero before reset.")) {
            return;
        }
        if (!assertClose(
                context,
                0.05,
                monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                "Sacrifice tower death should apply configured monster damage-taken bonus."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                monster.activeTimedEffectTicks(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0,
                "Sacrifice tower death effect should have a positive duration."
        )) {
            return;
        }
        int originalEntityId = tower.entityId().orElseThrow();
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertTrue(context, tower.entityId().isPresent(), "Destroyed sacrifice tower should respawn a tower entity on round reset.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().getAsInt() != originalEntityId, "Respawned sacrifice tower should use a fresh entity id.")) {
            return;
        }
        if (!assertEquals(context, tower.currentMaxHealth(), tower.health(), "Respawned sacrifice tower should reset to full health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sniperCatPrioritizesHighestCurrentHealthIncludingAdv(GameTestHelper context) {
        SemionMonsterEntity lowerHealth = spawnRoleMonsterEntity(
                context,
                "sniper-cat-lower-health",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity higherHealth = spawnRoleMonsterEntity(
                context,
                "sniper-cat-higher-health",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                200.0,
                List.of(SummonRole.TANK)
        );
        lowerHealth.setHealth(80.0F);
        higherHealth.setHealth(120.0F);

        for (TowerType type : List.of(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER
        )) {
            AntiTankerCatTower tower = new AntiTankerCatTower(
                    type,
                    stableUuid("sniper-cat-" + type.id()),
                    TeamId.RED,
                    1,
                    GridPosition.from(context.absolutePos(BlockPos.ZERO))
            );
            if (!assertTrue(
                    context,
                    tower.selectAttackTarget(null, List.of(lowerHealth, higherHealth)).orElse(null) == higherHealth,
                    type.id() + " should prioritize the target with the highest current health."
            )) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void strayAdditionalTargetsUseTwoBlockRangeBonus(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        UndeadRangedSkeletonTower stray = new UndeadRangedSkeletonTower(
                TowerBalanceRuntime.resolve(UndeadTowers.T3_RANGED_SKELETON_TOWER),
                stableUuid("stray-extra-range-owner"),
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(BlockPos.ZERO))
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(stray, null);
        towerEntity.setNoGravity(true);
        towerEntity.setPos(origin);
        context.getLevel().addFreshEntity(towerEntity);

        SemionMonsterEntity primary = spawnRoleMonsterEntity(
                context, "stray-primary", Optional.empty(), TeamId.RED, 1,
                origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity insideBonusRange = spawnRoleMonsterEntity(
                context, "stray-inside-bonus-range", Optional.empty(), TeamId.RED, 1,
                origin.add(7.5, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity outsideBonusRange = spawnRoleMonsterEntity(
                context, "stray-outside-bonus-range", Optional.empty(), TeamId.RED, 1,
                origin.add(8.5, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        stray.onAttack(towerEntity, primary, 10.0, false);

        if (!assertTrue(context, insideBonusRange.getHealth() < 100.0F, "Stray should acquire an extra target within attack range +2 blocks.")) {
            return;
        }
        if (!assertClose(context, 100.0, outsideBonusRange.getHealth(), "Stray should not acquire an extra target beyond attack range +2 blocks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void witherSkeletonDeathStacksUseFiveBlockRange(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        GridPosition position = GridPosition.from(context.absolutePos(BlockPos.ZERO));
        Vec3 center = new Vec3(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);

        for (TowerType type : List.of(UndeadTowers.T2_MELEE_TOWER, UndeadTowers.T3_MELEE_TOWER)) {
            UndeadMeleeSkeletonTower tower = new UndeadMeleeSkeletonTower(
                    TowerBalanceRuntime.resolve(type),
                    stableUuid("wither-stack-range-" + type.id()),
                    TeamId.RED,
                    1,
                    position
            );
            double baseDamage = tower.modifyAttackDamage(null, null, type.damage());
            tower.onNearbyMonsterDeath(null, null, center.add(4.0, 0.0, 0.0));
            double damageAfterInsideDeath = tower.modifyAttackDamage(null, null, type.damage());
            if (!assertTrue(context, damageAfterInsideDeath > baseDamage, type.id() + " should gain a stack from a death four blocks away.")) {
                return;
            }
            tower.onNearbyMonsterDeath(null, null, center.add(5.1, 0.0, 0.0));
            if (!assertClose(
                    context,
                    damageAfterInsideDeath,
                    tower.modifyAttackDamage(null, null, type.damage()),
                    type.id() + " should not gain a stack from beyond five blocks."
            )) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void villagerCatUpgradesCopyKillStackDamage(GameTestHelper context) {
        UUID playerId = stableUuid("cat-stack-copy-owner");
        AntiTankerCatTower t2Anti = new AntiTankerCatTower(
                VillagerTowers.T2_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t2Anti.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        AntiTankerCatTower t3Anti = new AntiTankerCatTower(
                VillagerTowers.T3_ANTI_TANKER_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t3Anti.copyFrom(t2Anti, 0);

        SemionMonsterEntity rushSummon = spawnRoleMonsterEntity(
                context,
                "cat-copy-rush",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                Vec3.ZERO,
                100.0,
                List.of(SummonRole.RUSH)
        );
        if (!assertClose(context, 40.04, t3Anti.modifyAttackDamage(null, rushSummon, 20.0), "Anti-tanker cat upgrade should keep death stack count before applying T3 summon bonus.")) {
            return;
        }

        LaneClearCatTower t2LaneClear = new LaneClearCatTower(
                VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t2LaneClear.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        LaneClearCatTower t3LaneClear = new LaneClearCatTower(
                VillagerTowers.T3_LANE_CLEAR_CAT_TOWER,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        t3LaneClear.copyFrom(t2LaneClear, 0);

        SemionMonsterEntity wave = spawnRoleMonsterEntity(
                context,
                "cat-copy-wave",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.ZERO.add(1.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        if (!assertClose(context, 35.04375, t3LaneClear.modifyAttackDamage(null, wave, 20.0), "Lane-clear cat upgrade should keep death stack damage before applying T3 wave bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void configLoaderCreatesSummonsConfigFile(GameTestHelper context) {
        try {
            Path tempDir = Files.createTempDirectory("semion-td-config-test");
            SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("semion-td-config-test"));
            if (!assertTrue(context, Files.exists(tempDir.resolve("summons.json")), "Summon defaults should be written to summons.json.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Failed to load configs: " + exception.getMessage()));
        }
    }

    @GameTest
    public void defaultPersistenceBackendIsSqlite(GameTestHelper context) {
        try {
            Path tempDir = Files.createTempDirectory("semion-persistence-config-test");
            SemionConfigLoader.LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("semion-td-persistence-config-test"));
            if (!assertEquals(context, SemionPersistenceBackendType.SQLITE, configs.persistence().backend(), "Default persistence backend should be SQLITE.")) {
                return;
            }
            if (!assertTrue(context, Files.exists(tempDir.resolve("persistence.json")), "Persistence config should be created next to other config files.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Failed to load persistence config: " + exception.getMessage()));
        }
    }

    @GameTest
    public void appliedMatchRepositorySeparatesSubsystems(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-applied-match-test").resolve("progression-applied-matches.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary applied-match store path."));
            return;
        }

        FileAppliedMatchRepository repository = new FileAppliedMatchRepository(storePath);
        MatchId matchId = MatchId.newId();
        if (!assertTrue(context, repository.markApplied(matchId, "progression", 1000L), "First progression mark should be recorded.")) {
            return;
        }
        if (!assertTrue(context, repository.hasApplied(matchId, "progression"), "Progression mark should be readable.")) {
            return;
        }
        if (!assertTrue(context, !repository.markApplied(matchId, "progression", 2000L), "Duplicate progression mark should be rejected.")) {
            return;
        }
        if (!assertTrue(context, repository.markApplied(matchId, "rating", 3000L), "Same matchId should be markable for another subsystem.")) {
            return;
        }

        FileAppliedMatchRepository reloaded = new FileAppliedMatchRepository(storePath);
        if (!assertTrue(context, reloaded.hasApplied(matchId, "progression"), "Progression mark should survive reload.")) {
            return;
        }
        if (!assertTrue(context, reloaded.hasApplied(matchId, "rating"), "Rating mark should survive reload separately.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void waveMonsterBlockbenchVisualReachesRuntimeEntity(GameTestHelper context) {
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "model_wave",
                25,
                0,
                3,
                AttackKind.MELEE,
                null,
                "semion-td:monster/model_wave",
                1
        );
        Monster monster = Monster.fromWaveEntry(entry, TeamId.RED, 1);
        if (!assertEquals(context, Optional.of("semion-td:monster/model_wave"), monster.blockbenchModelId(), "Wave monster should keep its Blockbench model id.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:zombie", monster.entityTypeId(), "Blockbench-only monsters should keep gameplay fallback entity data separate from BIL rendering.")) {
            return;
        }
        if (!assertEquals(context, MonsterDimensions.DEFAULT, monster.dimensions(), "Wave monsters should default to the shared monster hitbox.")) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        if (!assertEquals(context, EntityType.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Blockbench monsters should render through BIL's animated entity display type.")) {
            return;
        }
        if (!assertTrue(context, !entity.hasBilModelHolder(), "Missing test model resources should not create a BIL holder.")) {
            return;
        }
        if (!assertEquals(context, SemionAnimationState.IDLE, entity.animationState(), "Configured monster should start in idle animation state.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.WALK);
        if (!assertEquals(context, SemionAnimationState.WALK, entity.animationState(), "Monster should expose walk animation state.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterDimensionsAreAuthoredAndAppliedAtRuntime(GameTestHelper context) {
        MonsterDimensions waveDimensions = MonsterDimensions.of(1.25, 0.9);
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "wide_wave",
                25,
                0,
                3,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                waveDimensions,
                1
        );
        Monster waveMonster = Monster.fromWaveEntry(entry, TeamId.RED, 1);
        if (!assertEquals(context, waveDimensions, waveMonster.dimensions(), "Wave monster should keep authored dimensions.")) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(waveMonster, null);
        if (!assertClose(context, 1.25, entity.getBbWidth(), "Runtime monster width should refresh from authored dimensions.")) {
            return;
        }
        if (!assertClose(context, 0.9, entity.getBbHeight(), "Runtime monster height should refresh from authored dimensions.")) {
            return;
        }
        if (!assertClose(context, 0.9, entity.getBoundingBox().getYsize(), "Runtime monster AABB should refresh from authored dimensions.")) {
            return;
        }

        SummonMonsterType summon = new SummonMonsterType(
                "wide_custom",
                "Wide Custom",
                10,
                1,
                40,
                0,
                4,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                MonsterDimensions.of(1.7, 1.1),
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                6
        ) {
        };
        Monster summonMonster = summon.createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(
                                stableUuid("wide-custom-owner"),
                                "owner",
                                TeamId.RED,
                                1,
                                new PlayerEconomy(EconomyConfig.defaultConfig())
                        )
                ),
                TeamId.BLUE,
                1
        );
        if (!assertEquals(context, MonsterDimensions.of(1.7, 1.1), summonMonster.dimensions(), "Summon monster should keep authored dimensions.")) {
            return;
        }
        if (!assertInvalidDimensionsRejected(context)) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void customSummonClassIsUsedByGame(GameTestHelper context) {
        UUID redId = stableUuid("custom-class-summon-red-owner");
        UUID blueId = stableUuid("custom-class-summon-blue-owner");
        String summonId = "custom_class";
        if (SummonRegistry.find(summonId).isEmpty()) {
            SummonRegistry.register(new SummonMonsterType(
                    summonId,
                    "Custom Class",
                    15,
                    4,
                    60,
                    0,
                    6,
                    AttackKind.MELEE,
                    "minecraft:husk",
                    8
            ) {
                @Override
                public void onSummoned(SummonContext context, Monster monster) {
                    monster.damage(10);
                }
            });
        }
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with custom summon class registered.")) {
            return;
        }

        var result = game.summonMonster(redId, summonId);
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Custom summon class should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 35L, game.players().get(redId).economy().gas(), "Custom summon class should spend its gas cost.")) {
            return;
        }
        if (!assertEquals(context, 4L, game.players().get(redId).economy().income(), "Custom summon class should grant its income.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        targetLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Custom summon class should queue one monster on the target lane.")) {
            return;
        }
        Monster summoned = targetLane.activeMonsters().getFirst();
        if (!assertEquals(context, 50.0, summoned.health(), "Custom summon onSummoned hook should be able to mutate the runtime monster.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:husk", summoned.entityTypeId(), "Custom summon class should control the spawned entity type.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void customSummonClassCanUseBlockbenchModel(GameTestHelper context) {
        String summonId = "custom_model";
        if (SummonRegistry.find(summonId).isEmpty()) {
            SummonRegistry.register(new SummonMonsterType(
                    summonId,
                    "Custom Model",
                    10,
                    1,
                    40,
                    0,
                    4,
                    AttackKind.MELEE,
                    null,
                    "semion-td:summon/custom_model",
                    6
            ) {
            });
        }

        SummonMonsterType summon = SummonRegistry.find(summonId).orElseThrow();
        Monster monster = summon.createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(
                                stableUuid("custom-model-owner"),
                                "owner",
                                TeamId.RED,
                                1,
                                new PlayerEconomy(EconomyConfig.defaultConfig())
                        )
                ),
                TeamId.BLUE,
                1
        );
        if (!assertEquals(context, Optional.of("semion-td:summon/custom_model"), monster.blockbenchModelId(), "Summon monster should keep its Blockbench model id.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:zombie", monster.entityTypeId(), "Blockbench-only summon should keep zombie as gameplay fallback entity data.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerTypeProvidesVisualAndAnimationState(GameTestHelper context) {
        TowerType towerType = new TowerType(
                "visual_tower",
                "Visual Tower",
                TowerCategory.DIRECT,
                100,
                50,
                8,
                12,
                20,
                0,
                "minecraft:iron_golem",
                "semion-td:tower/visual",
                List.of()
        );
        TestTower tower = new TestTower(
                towerType,
                stableUuid("visual-tower-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertEquals(context, EntityType.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Modeled towers should render through BIL's animated entity display type.")) {
            return;
        }
        if (!assertEquals(context, "semion-td:tower/visual", entity.blockbenchModelId(), "Tower should keep its Blockbench model id.")) {
            return;
        }
        if (!assertTrue(context, !entity.hasBilModelHolder(), "Missing test model resources should not create a BIL holder.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.ATTACK);
        if (!assertEquals(context, SemionAnimationState.ATTACK, entity.animationState(), "Tower should expose attack animation state.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void entityVisualScaleAppliesToTowerEntity(GameTestHelper context) {
        EntityVisual visual = EntityVisual.builder("minecraft:villager").scale(1.5).build();
        if (!assertClose(context, 1.5, visual.scale(), "EntityVisual builder should keep configured scale.")) {
            return;
        }

        EntityVisual stringScaleVisual = new EntityVisual("minecraft:villager", null, Map.of("scale", "2.25"));
        if (!assertClose(context, 2.25, stringScaleVisual.scale(), "EntityVisual should accept scale from property maps.")) {
            return;
        }
        if (!assertTrue(context, !stringScaleVisual.properties().containsKey("scale"), "Scale should be normalized out of generic visual properties.")) {
            return;
        }

        TowerType towerType = new TowerType(
                "scaled_visual_tower",
                "Scaled Visual Tower",
                TowerCategory.DIRECT,
                100,
                50,
                8,
                12,
                20,
                0,
                visual,
                List.of()
        );
        TestTower tower = new TestTower(
                towerType,
                stableUuid("scaled-visual-tower-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertClose(context, 1.5, entity.getScale(), "Tower entity should apply EntityVisual scale to the runtime entity.")) {
            return;
        }
        if (!assertClose(context, 1.5, entity.getAttributeValue(Attributes.SCALE), "Tower entity scale attribute should match EntityVisual scale.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void endCrystalVisualUsesSmallServerCollisionBox(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        EndTower tower = new EndTower(
                EndTowers.T3_END_CRYSTAL_TOWER,
                stableUuid("end-crystal-hitbox-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 0, 0)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertEquals(context, EntityType.END_CRYSTAL, entity.getPolymerEntityType(null), "End Crystal appearance should remain unchanged.")) {
            return;
        }
        if (!assertClose(context, 0.5, entity.getScale(), "End Crystal server collision scale should be reduced.")) {
            return;
        }
        if (!assertClose(context, 0.4, entity.getBbWidth(), "End Crystal server collision width should be 0.4 blocks.")) {
            return;
        }
        if (!assertClose(context, 0.9, entity.getBbHeight(), "End Crystal server collision height should be 0.9 blocks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void endEggPhantomAndDragonAreStatesOfOneRuntimeTower(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        EndTower tower = new EndTower(
                EndTowers.BASE_END_TOWER,
                stableUuid("end-dragon-scale-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 0, 0)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertEquals(context, EntityType.ARMOR_STAND, entity.getPolymerEntityType(null), "Ender Dragon EGG state should use an attribute-compatible living proxy.")) {
            return;
        }
        if (!assertTrue(context, entity.isInvisible(), "The living EGG proxy should stay hidden behind its Dragon Egg block display.")) {
            return;
        }
        tower.onWaveStarted(null, 1);
        for (int tick = 0; tick < 200; tick++) {
            tower.tick(null);
        }
        entity.syncTowerState(tower);
        if (!assertEquals(context, EntityType.PHANTOM, entity.getPolymerEntityType(null), "Hatching should refresh the same tower entity to a vanilla Phantom proxy.")) {
            return;
        }
        if (!assertTrue(context, !entity.isInvisible(), "The Phantom proxy should become visible after leaving the EGG state.")) {
            return;
        }
        if (entity.hasBilModelHolder()) {
            throw new AssertionError("The End core must not load a BIL model in PHANTOM state.");
        }
        if (entity.hasEndCoreInteractionHitbox()) {
            throw new AssertionError("PHANTOM state should not create a dedicated right-click interaction hitbox.");
        }
        if (!assertTrue(context, entity.isNoGravity(), "PHANTOM state should be gravity-free to remain stable above its tower block.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getBbWidth(), "PHANTOM state should have a one-block-wide server hitbox.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getBbHeight(), "PHANTOM state should have a one-block-high server hitbox.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getScale(), "Phantom growth must not enlarge the server collision box.")) {
            return;
        }
        List<ClientboundUpdateAttributesPacket.AttributeSnapshot> clientAttributes = new ArrayList<>();
        clientAttributes.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(
                Attributes.SCALE,
                entity.getAttributeValue(Attributes.SCALE),
                List.of()
        ));
        entity.modifyRawEntityAttributeData(clientAttributes, null, true);
        double clientScale = clientAttributes.stream()
                .filter(snapshot -> snapshot.attribute().equals(Attributes.SCALE))
                .findFirst()
                .orElseThrow()
                .base();
        if (!assertClose(context, EndTowers.phantomScaleForMaxHealth(tower.currentMaxHealth()), clientScale, "Phantom growth should remain visible to clients.")) {
            return;
        }
        if (entity.runtimeTower() != tower) {
            throw new AssertionError("Visual state changes must retain the real End tower used by right-click details.");
        }

        tower.syncMaxHealth(1999.99, true);
        tower.tick(null);
        if (!assertEquals(context, kim.biryeong.semiontd.tower.end.EndTowerState.PHANTOM, tower.state(), "Exactly 2000 max health must remain PHANTOM.")) {
            return;
        }
        tower.syncMaxHealth(2000.0, true);
        tower.tick(null);
        entity.syncTowerState(tower);
        if (!assertEquals(context, EntityType.ENDER_DRAGON, entity.getPolymerEntityType(null), "More than 2000 max health should evolve the Phantom into a vanilla Ender Dragon proxy.")) {
            return;
        }
        if (entity.hasBilModelHolder()) {
            throw new AssertionError("The evolved vanilla Ender Dragon must not load a BIL model holder.");
        }
        if (!entity.hasEndCoreInteractionHitbox()) {
            throw new AssertionError("DRAGON state should use the upstream 16x8 redirected interaction hitbox.");
        }
        if (!assertTrue(context, entity.isNoGravity(), "DRAGON state should be gravity-free to remain stable above its tower block.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getBbWidth(), "DRAGON state should have a one-block-wide server hitbox.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getBbHeight(), "DRAGON state should have a one-block-high server hitbox.")) {
            return;
        }
        if (!assertClose(context, 1.0, entity.getScale(), "Max-health-proportional scale must stop after evolving into the Ender Dragon.")) {
            return;
        }
        if (!assertClose(context, 11.0, entity.applyTraitOutgoingDamage(null, 10.0), "DRAGON state should grant 10% final damage.")) {
            return;
        }
        if (!assertClose(context, 0.10, tower.incomeDebuffResistance(), "DRAGON state should reduce income-monster debuff magnitudes by 10%.")) {
            return;
        }
        SemionMonsterEntity facingTarget = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        facingTarget.setPos(entity.getX() + 10.0, entity.getY(), entity.getZ());
        entity.faceAttackTarget(facingTarget);
        if (!assertClose(context, 90.0, entity.getYRot(), "DRAGON model should rotate toward its attack target instead of facing backward.")) {
            return;
        }
        if (!assertClose(context, entity.getYRot(), entity.yBodyRot, "DRAGON body rotation should match its attack direction.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.IDLE);
        if (!assertEquals(context, SemionAnimationState.IDLE, entity.animationState(), "Vanilla Ender Dragon should retain the tower idle state.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.WALK);
        if (!assertEquals(context, SemionAnimationState.WALK, entity.animationState(), "Vanilla Ender Dragon should retain the tower walk state.")) {
            return;
        }
        if (!assertClose(context, 7.0, tower.adjustAttackRange(tower.type().range()), "Ender Dragon attack range should gain 2 blocks after evolution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void slimeVisualBuilderAppliesClampedSize(GameTestHelper context) {
        EntityVisual visual = SlimeVisual.builder().size(4).build();
        if (!assertEquals(context, "minecraft:slime", visual.entityTypeId(), "Slime visual builder should use the slime entity type.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(4), appliedSlimeSize(context, visual), "Slime visual should apply size 4 to tracked data.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), appliedSlimeSize(context, SlimeVisual.builder().size(0).build()), "Slime size should clamp zero to 1.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), appliedSlimeSize(context, SlimeVisual.builder().size(-5).build()), "Slime size should clamp negative values to 1.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(127), appliedSlimeSize(context, SlimeVisual.builder().size(200).build()), "Slime size should clamp large values to 127.")) {
            return;
        }
        if (!assertEquals(
                context,
                Optional.of(6),
                appliedSlimeSize(context, new EntityVisual("minecraft:slime", null, Map.of("size", "6"))),
                "Slime visual should accept the size alias."
        )) {
            return;
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        reloadDefaultIncomeSummons();
        method.invoke(this, context);
    }

    private static StartCandidate candidate(String name) {
        return new StartCandidate(stableUuid(name), name);
    }

    private static StartCandidate candidate(String name, int displayElo) {
        return new StartCandidate(stableUuid(name), name, displayElo);
    }

    private static int eloFor(UUID playerId, StartCandidate... candidates) {
        for (StartCandidate candidate : candidates) {
            if (candidate.uuid().equals(playerId)) {
                return candidate.displayElo();
            }
        }
        throw new IllegalArgumentException("Unknown playerId " + playerId);
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId) {
        return startedSinglePlayerGame(context, playerId, teamId, null);
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId, net.minecraft.resources.ResourceLocation jobId) {
        reloadDefaultIncomeSummons();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        if (jobId != null && !game.selectJob(playerId, jobId)) {
            throw new IllegalStateException("Failed to select test job " + jobId);
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(playerId, "tester", teamId, 1)),
                java.util.Set.of(),
                1
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start single-player Semion test game.");
        }
        return game;
    }

    private static SemionGame startedTwoPlayerGame(GameTestHelper context, UUID redId, UUID blueId) {
        reloadDefaultIncomeSummons();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start two-player Semion test game.");
        }
        return game;
    }

    private static SemionGame startedThreePlayerGame(GameTestHelper context, UUID redId, UUID blueId, UUID greenId) {
        reloadDefaultIncomeSummons();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1),
                        new AssignedParticipant(greenId, "green", TeamId.GREEN, 1)
                ),
                java.util.Set.of(),
                3
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start three-player Semion test game.");
        }
        return game;
    }

    private static void reloadDefaultIncomeSummons() {
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
    }

    private static SemionJob registerTowerAllowingJob(String path, Set<String> towerIds) {
        Set<String> allowedTowerIds = Set.copyOf(towerIds);
        return JobRegistry.registerIfAbsent(new SemionJob(
                ResourceLocation.fromNamespaceAndPath("semion-td", "test/" + path),
                Component.literal("Test Job"),
                List.of()
        ) {
            @Override
            public boolean canUseTower(kim.biryeong.semiontd.job.JobContext context, TowerType towerType) {
                return towerType != null && allowedTowerIds.contains(towerType.id());
            }
        });
    }

    private static void tickGame(SemionGame game, MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.tick(server);
        }
    }

    private static void tickLaneWithGlobalCloneQueue(PlayerLane lane, MinecraftServer server) {
        IllusionCloneSpawnQueue.tick();
        lane.tick(server);
    }

    private static String towerTimedEffectBody(SemionTowerEntity entity) {
        try {
            Method method = SemionDialogService.class.getDeclaredMethod("appendTowerTimedEffects", StringBuilder.class, SemionTowerEntity.class);
            method.setAccessible(true);
            StringBuilder body = new StringBuilder();
            method.invoke(null, body, entity);
            return body.toString();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to render tower timed effects.", exception);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set field " + fieldName + " on " + target.getClass().getName(), exception);
        }
    }

    private static PlayerLane redLane(SemionGame game, int laneId) {
        return lane(game, TeamId.RED, laneId);
    }

    private static PlayerLane lane(SemionGame game, TeamId teamId, int laneId) {
        return game.teams().get(teamId).laneGroup().lane(laneId).orElseThrow();
    }

    private static BlockPos towerPlacementPos(PlayerLane lane) {
        return BlockPos.containing(lane.laneLayout().positionAt(0.35));
    }

    private static void addResonanceTower(PlayerLane lane, UUID playerId, TowerType type, BlockPos position) {
        GridPosition gridPosition = GridPosition.from(position);
        lane.addTower(new ResonanceTower(
                TowerBalanceRuntime.resolve(type),
                playerId,
                lane.teamId(),
                lane.laneId(),
                gridPosition,
                gridPosition
        ));
    }

    private static BlockPos nearbyTowerPlacementPos(PlayerLane lane, BlockPos origin) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (!candidate.equals(origin) && lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(GridPosition.from(candidate))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Could not find nearby tower placement position.");
    }

    private static SemionMonsterEntity spawnSummonEntity(
            GameTestHelper context,
            String id,
            TeamId senderTeam,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            double damageTaken
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.ofNullable(senderTeam),
                maxHealth,
                0,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        if (damageTaken > 0) {
            monster.damage(damageTaken, DamageType.TRUE);
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Monster deathStackTestMonster(String id, Optional<TeamId> senderTeam, TeamId targetTeam, int targetLaneId) {
        return new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                senderTeam,
                100.0,
                0,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
    }

    private static SemionMonsterEntity spawnRoleMonsterEntity(
            GameTestHelper context,
            String id,
            Optional<TeamId> senderTeam,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            List<SummonRole> roles
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                senderTeam,
                maxHealth,
                0,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                roles,
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionMonsterEntity spawnBossTargetMonster(GameTestHelper context, String id, Vec3 position) {
        Monster monster = new Monster(
                id,
                TeamId.PURPLE,
                1,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                100.0,
                100,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                300,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionMonsterEntity spawnLaneMonsterEntity(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                100.0,
                0,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionMonsterEntity spawnAttackMonsterEntity(
            GameTestHelper context,
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            double attackDamage
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                maxHealth,
                0,
                attackDamage,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionTowerEntity spawnTowerEntity(
            GameTestHelper context,
            TeamId teamId,
            int laneId,
            Vec3 position,
            TowerType towerType
    ) {
        TestTower tower = new TestTower(
                towerType,
                stableUuid("tower-" + teamId.name() + "-" + laneId + "-" + position),
                teamId,
                laneId,
                new kim.biryeong.semiontd.game.GridPosition((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z))
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void awaitBossCombatResolution(
            GameTestHelper context,
            SemionGame game,
            TeamId teamId,
            PlayerLane lane,
            double initialBossHealth,
            int monsterEntityId,
            int elapsedTicks
    ) {
        game.teams().get(teamId).tick(context.getLevel().getServer());
        boolean bossDamaged = game.teams().get(teamId).laneGroup().boss().health() < initialBossHealth;
        boolean monsterCleared = lane.activeMonsters().isEmpty();
        if (bossDamaged && monsterCleared) {
            if (!assertTrue(context, lane.arenaWorld().getEntity(monsterEntityId) == null
                    || lane.arenaWorld().getEntity(monsterEntityId).isRemoved(), "Boss-killed monster entity should be removed.")) {
                return;
            }
            context.succeed();
            return;
        }
        if (elapsedTicks >= 440) {
            if (!assertTrue(context, bossDamaged, "Monster should damage the boss through normal combat.")) {
                return;
            }
            assertEquals(context, 0, lane.activeMonsters().size(), "Boss should be able to kill and clear the reached monster.");
            return;
        }

        context.runAfterDelay(10, () -> awaitBossCombatResolution(
                context,
                game,
                teamId,
                lane,
                initialBossHealth,
                monsterEntityId,
                elapsedTicks + 10
        ));
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertScoreboardTeam(GameTestHelper context, MinecraftServer server, String teamName) {
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamName);
        return assertTrue(context, team != null, "Missing scoreboard team " + teamName + ".");
    }

    private static int countTrackedBossEntities(SemionGame game) {
        int count = 0;
        for (TeamId teamId : TeamId.values()) {
            if (game.teams().get(teamId).laneGroup().hasBossEntity()) {
                count++;
            }
        }
        return count;
    }


    private static boolean assertAssignedTeam(
            GameTestHelper context,
            ParticipantSelectionPlan plan,
            String candidateName,
            TeamId expectedTeam
    ) {
        UUID candidateId = stableUuid(candidateName);
        for (AssignedParticipant participant : plan.activeParticipants()) {
            if (participant.uuid().equals(candidateId)) {
                return assertEquals(
                        context,
                        expectedTeam,
                        participant.teamId(),
                        "Expected " + candidateName + " to stay on " + expectedTeam + "."
                );
            }
        }
        context.fail(Component.literal("Missing active participant " + candidateName + "."));
        return false;
    }

    private static boolean assertTeamSizes(
            GameTestHelper context,
            ParticipantSelectionPlan plan,
            Map<TeamId, Integer> expectedSizes
    ) {
        Map<TeamId, Integer> actualSizes = new EnumMap<>(TeamId.class);
        for (AssignedParticipant participant : plan.activeParticipants()) {
            actualSizes.merge(participant.teamId(), 1, Integer::sum);
        }
        return assertEquals(context, expectedSizes, actualSizes, "Unexpected team size distribution.");
    }

    private static Map<TeamId, TeamMatchResult> teamResultsByTeam(MatchResult matchResult) {
        Map<TeamId, TeamMatchResult> byTeam = new EnumMap<>(TeamId.class);
        for (TeamMatchResult result : matchResult.teamResults()) {
            byTeam.put(result.teamId(), result);
        }
        return byTeam;
    }

    private static boolean assertPresent(
            GameTestHelper context,
            Optional<?> optional,
            String message
    ) {
        return assertTrue(context, optional.isPresent(), message);
    }

    private static CompoundTag laneData(int laneId) {
        CompoundTag data = new CompoundTag();
        data.putInt("lane", laneId);
        return data;
    }

    private static CompoundTag laneData(int laneId, int order) {
        CompoundTag data = laneData(laneId);
        data.putInt("order", order);
        return data;
    }

    private static CompoundTag orderData(int order) {
        CompoundTag data = new CompoundTag();
        data.putInt("order", order);
        return data;
    }

    private static Optional<Integer> appliedSlimeSize(GameTestHelper context, EntityVisual visual) {
        List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        EntityVisualApplierRegistry.apply(visual, EntityType.SLIME, context.getLevel().registryAccess(), data);
        return dataValue(data, SlimeAccessor.semiontd$idSize());
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> dataValue(
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<T> accessor
    ) {
        for (SynchedEntityData.DataValue<?> dataValue : data) {
            if (dataValue.id() == accessor.id()) {
                return Optional.of((T)dataValue.value());
            }
        }
        return Optional.empty();
    }

    private static byte[] syntheticOggVorbis(int sampleRate, long samples) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeOggPage(output, 0L, 0, syntheticVorbisIdentificationPacket(sampleRate));
        writeOggPage(output, samples, 1, new byte[] {0});
        return output.toByteArray();
    }

    private static byte[] syntheticVorbisIdentificationPacket(int sampleRate) {
        byte[] packet = new byte[30];
        packet[0] = 1;
        byte[] vorbis = "vorbis".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(vorbis, 0, packet, 1, vorbis.length);
        packet[11] = 1;
        writeLittleEndianInt(packet, 12, sampleRate);
        packet[28] = 0x11;
        packet[29] = 1;
        return packet;
    }

    private static void writeOggPage(ByteArrayOutputStream output, long granulePosition, int sequence, byte[] body) throws java.io.IOException {
        output.write("OggS".getBytes(StandardCharsets.US_ASCII));
        output.write(0);
        output.write(0);
        writeLittleEndianLong(output, granulePosition);
        writeLittleEndianInt(output, 1);
        writeLittleEndianInt(output, sequence);
        writeLittleEndianInt(output, 0);
        output.write(1);
        output.write(body.length);
        output.write(body);
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static void writeLittleEndianLong(ByteArrayOutputStream output, long value) {
        for (int index = 0; index < 8; index++) {
            output.write((int) ((value >>> (8 * index)) & 0xff));
        }
    }

    private static final class FixtureSupportTower extends EntityBackedTower {
        private int persistentBonus;

        private FixtureSupportTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                kim.biryeong.semiontd.game.GridPosition originalPosition,
                kim.biryeong.semiontd.game.GridPosition currentPosition
        ) {
            super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        }

        private void setPersistentBonus(int persistentBonus) {
            this.persistentBonus = persistentBonus;
        }

        private int persistentBonus() {
            return persistentBonus;
        }

        @Override
        protected void copyRuntimeStateFrom(kim.biryeong.semiontd.tower.Tower previousTower) {
            if (previousTower instanceof FixtureSupportTower fixtureSupportTower) {
                persistentBonus = fixtureSupportTower.persistentBonus;
            }
        }
    }

    private static final class FixtureIllusionTower extends IllusionSummonerTower {
        private final IllusionProfile profile;
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private FixtureIllusionTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position,
                IllusionProfile profile
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
            this.profile = profile;
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected IllusionProfile illusionProfile(PlayerLane lane) {
            return profile;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }

    private static final class CapturingLegionSlimeTower extends LegionSlimeTower {
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private CapturingLegionSlimeTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }

    private static final class CapturingGlobalIllusionTower extends LegionGlobalIllusionTower {
        private final List<SemionTowerEntity> spawnedCloneEntities = new ArrayList<>();

        private CapturingGlobalIllusionTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition position
        ) {
            super(type, ownerPlayer, teamId, laneId, position);
        }

        private List<SemionTowerEntity> spawnedCloneEntities() {
            return spawnedCloneEntities;
        }

        @Override
        protected void onCloneSpawned(PlayerLane lane, SemionTowerEntity cloneEntity, Tower cloneTower) {
            spawnedCloneEntities.add(cloneEntity);
        }
    }

    private static final class CapturingResourcePackBuilder implements ResourcePackBuilder {
        private final Map<String, byte[]> data = new java.util.HashMap<>();

        private Map<String, byte[]> data() {
            return data;
        }

        @Override
        public boolean addData(String path, byte[] data) {
            this.data.put(path, data);
            return true;
        }

        @Override
        public boolean copyAssets(String modId) {
            return false;
        }

        @Override
        public boolean copyFromPath(Path path, String targetPrefix, boolean override) {
            return false;
        }

        @Override
        public byte @Nullable [] getData(String path) {
            return data.get(path);
        }

        @Override
        public byte @Nullable [] getDataOrSource(String path) {
            return data.get(path);
        }

        @Override
        public void forEachFile(BiConsumer<String, byte[]> consumer) {
            data.forEach(consumer);
        }

        @Override
        public boolean addAssetsSource(String modId) {
            return false;
        }

        @Override
        public void addWriteConverter(BiFunction<String, byte[], byte @Nullable []> converter) {
        }

        @Override
        public void addPreFinishTask(Consumer<ResourcePackBuilder> consumer) {
        }
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertClose(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }

    private static boolean assertInvalidDimensionsRejected(GameTestHelper context) {
        try {
            MonsterDimensions.of(0, 1);
            context.fail(Component.literal("Monster dimensions should reject non-positive width."));
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }
}
