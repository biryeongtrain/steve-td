package kim.biryeong.semiontd.tower.resonance;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.faboslav.friendsandfoes.common.entity.MoobloomEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.MoobloomVisual;
import kim.biryeong.semiontd.mixin.accessor.MoobloomAccessor;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.ResonanceTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.resonance.ResonanceTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class ResonanceTowerRuntimeTest extends GameTestParticipantFixture {
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
            if (visual.shouldBeSaved()) {
                context.fail(Component.literal("Runtime Moobloom overlays must not be written into chunks."));
                return;
            }
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
        addResonanceTower(lane, playerId, ResonanceTowers.FOCUS_CRYSTAL, focusPos.offset(-1, 0, -1));
        lane.markWaveStarted(1);
        if (!assertTrue(context, lane.towers().get(0) instanceof ResonanceTower, "Placed focus crystal should use ResonanceTower runtime.")) {
            return;
        }
        ResonanceTower focus = (ResonanceTower) lane.towerAt(GridPosition.from(focusPos));
        if (!assertEquals(context, 1, focus.resonanceLevel(), "T1 focus should cap at resonance level 1.")) {
            return;
        }
        if (!assertEquals(context, 5, focus.resonanceLinks(), "Focus should exclude its own type and count duplicate types only once.")) {
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
        if (!assertClose(context, 1.5, focus.auraDamageVsSlowedBonus(), "Nearby mooblooms should receive frost's damage-vs-debuffed aura.")) {
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
        if (!assertClose(context, 250.0, focus.modifyAttackDamage(null, target, 100.0), "Frost aura should make nearby mooblooms deal bonus damage to debuffed targets.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void resonanceDamageAbilitiesUseMagicDamageWithoutIgnite(GameTestHelper context) {
        UUID playerId = stableUuid("resonance-magic-abilities-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, ResonanceTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));
        BlockPos center = towerPlacementPos(lane);

        ResonanceTower focus = addResonanceTower(lane, playerId, ResonanceTowers.FOCUS_CORE, center);
        ResonanceTower wave = addResonanceTower(lane, playerId, ResonanceTowers.WAVE_CORE, center);
        ResonanceTower frost = addResonanceTower(lane, playerId, ResonanceTowers.FROST_CORE, center);
        addResonanceTower(lane, playerId, ResonanceTowers.FOCUS_CRYSTAL, center);
        addResonanceTower(lane, playerId, ResonanceTowers.FOCUS_PRISM, center);
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_CRYSTAL, center);
        addResonanceTower(lane, playerId, ResonanceTowers.WAVE_PRISM, center);
        addResonanceTower(lane, playerId, ResonanceTowers.FROST_CRYSTAL, center);
        addResonanceTower(lane, playerId, ResonanceTowers.FROST_PRISM, center);
        ResonanceService.refresh(lane.towers());
        if (!assertEquals(context, 3, focus.resonanceLevel(), "Focus should reach resonance level 3.")) {
            return;
        }
        if (!assertEquals(context, 3, wave.resonanceLevel(), "Wave should reach resonance level 3.")) {
            return;
        }
        if (!assertEquals(context, 3, frost.resonanceLevel(), "Frost should reach resonance level 3.")) {
            return;
        }

        SemionTowerEntity focusEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(focus.entityId().orElseThrow());
        SemionMonsterEntity focusTarget = spawnRoleMonsterEntity(
                context,
                "resonance-focus-magic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                focusEntity.position().add(4.0, 0.0, 0.0),
                5_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        focusTarget.setNoAi(true);
        int focusEvery = TowerBalanceRuntime.abilityInt(focus.type().id(), "focusStrikeEveryAttacks");
        for (int attack = 1; attack < focusEvery; attack++) {
            focus.onAttack(focusEntity, focusTarget, focus.type().damage(), false);
        }
        double beforeFocusStrike = focusTarget.getHealth();
        focus.onAttack(focusEntity, focusTarget, focus.type().damage(), false);
        double expectedFocusDamage = focus.resolveOutgoingDamage(
                focusEntity,
                focusTarget,
                focus.type().damage() * TowerBalanceRuntime.ability(focus.type().id(), "focusStrikeDamageRatio")
        );
        if (!assertClose(context, beforeFocusStrike - expectedFocusDamage, focusTarget.getHealth(),
                "Focus strike should use magic resistance instead of armor.")) {
            return;
        }
        if (!assertTrue(context, focusTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Focus magic strike should not apply ignite.")) {
            return;
        }
        focusTarget.discard();

        SemionTowerEntity waveEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(wave.entityId().orElseThrow());
        SemionMonsterEntity wavePrimary = spawnRoleMonsterEntity(
                context,
                "resonance-wave-primary",
                Optional.empty(),
                TeamId.RED,
                1,
                waveEntity.position().add(4.0, 0.0, 0.0),
                5_000.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity waveSecondary = spawnRoleMonsterEntity(
                context,
                "resonance-wave-magic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                wavePrimary.position().add(1.0, 0.0, 0.0),
                5_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        wavePrimary.setNoAi(true);
        waveSecondary.setNoAi(true);
        double beforeWaveSplash = waveSecondary.getHealth();
        double expectedWaveDamage = wave.resolveOutgoingDamage(
                waveEntity,
                waveSecondary,
                wave.type().damage() * TowerBalanceRuntime.ability(wave.type().id(), "waveLevel3SplashDamageRatio")
        );
        wave.onAttack(waveEntity, wavePrimary, wave.type().damage(), false);
        if (!assertClose(context, beforeWaveSplash - expectedWaveDamage, waveSecondary.getHealth(),
                "Wave splash should use magic resistance instead of armor.")) {
            return;
        }
        if (!assertTrue(context, waveSecondary.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Wave magic splash should not apply ignite.")) {
            return;
        }
        wavePrimary.discard();
        waveSecondary.discard();

        SemionTowerEntity frostEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(frost.entityId().orElseThrow());
        SemionMonsterEntity frostTarget = spawnRoleMonsterEntity(
                context,
                "resonance-frost-magic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                frostEntity.position().add(4.0, 0.0, 0.0),
                5_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        frostTarget.setNoAi(true);
        int frostEvery = TowerBalanceRuntime.abilityInt(frost.type().id(), "frostPulseEveryAttacks");
        for (int attack = 1; attack < frostEvery; attack++) {
            frost.onAttack(frostEntity, frostTarget, frost.type().damage(), false);
        }
        double beforeFrostPulse = frostTarget.getHealth();
        frost.onAttack(frostEntity, frostTarget, frost.type().damage(), false);
        double expectedFrostDamage = frost.resolveOutgoingDamage(
                frostEntity,
                frostTarget,
                frost.type().damage() * TowerBalanceRuntime.ability(frost.type().id(), "frostPulseDamageRatio")
        );
        if (!assertClose(context, beforeFrostPulse - expectedFrostDamage, frostTarget.getHealth(),
                "Frost pulse should use magic resistance instead of armor.")) {
            return;
        }
        if (!assertTrue(context, frostTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Frost magic pulse should not apply ignite.")) {
            return;
        }
        context.succeed();
    }

    private static ResonanceTower addResonanceTower(PlayerLane lane, UUID playerId, TowerType type, BlockPos position) {
        GridPosition gridPosition = GridPosition.from(position);
        ResonanceTower tower = new ResonanceTower(
                TowerBalanceRuntime.resolve(type),
                playerId,
                lane.teamId(),
                lane.laneId(),
                gridPosition,
                gridPosition
        );
        lane.addTower(tower);
        return tower;
    }
}
