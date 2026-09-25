package kim.biryeong.semiontd.tower.frost;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.AreaEffectVfxEvent;
import kim.biryeong.semiontd.entity.tower.vfx.AreaEffectVfxTestHooks;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.entity.visual.EntityVisualApplierRegistry;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.mixin.accessor.AxolotlAccessor;
import kim.biryeong.semiontd.mixin.accessor.SnowGolemAccessor;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class FrostGameTest {
    @GameTest(maxTicks = 120)
    public void refrigerantRequiresSevenHitsAndReducesAttackAndSpeedByFifteenPercent(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-refrigerant-nerf");
        SemionMonsterEntity enemy = spawnTarget(context, Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 6))));
        try {
            for (int hit = 0; hit < 6; hit++) {
                FrostMonsterStates.applyChill(enemy, FrostBalance.chillPerHit());
            }
            if (FrostMonsterStates.isRefrigerated(enemy.runtimeMonster())) {
                throw new AssertionError("Six hits must not refrigerate the target.");
            }
            requireClose(.9, FrostMonsterStates.chill(enemy.runtimeMonster()), "Six hits store 90% chill");
            FrostMonsterStates.applyChill(enemy, FrostBalance.chillPerHit());
            if (!FrostMonsterStates.isRefrigerated(enemy.runtimeMonster())) {
                throw new AssertionError("The seventh hit must refrigerate the target.");
            }
            requireClose(.15, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION), "Refrigerant damage reduction");
            requireClose(.15, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION), "Refrigerant attack speed reduction");
            FrostMonsterStates.thaw(enemy);
            requireClose(0, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION), "Thaw clears the damage debuff");
            requireClose(0, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION), "Thaw clears the speed debuff");
            context.succeed();
        } finally {
            enemy.discard();
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void rapidFreezeDoublesActualEmissionForEnemiesAndAllies(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-emission");
        try {
            selectAugments(setup.lane(), FrostAugments.FREEZE);
            FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(6, 2, 3));
            FrostHealingTower icebox = healer(FrostTowers.ICEBOX_T1, setup.owner(), context, new BlockPos(4, 2, 3));
            setup.lane().addTower(cooling);
            setup.lane().addTower(icebox);
            SemionTowerEntity source = towerEntity(context, cooling);
            SemionMonsterEntity enemy = spawnTarget(context, source.position().add(-3, 0, 0));
            cooling.execute(setup.lane());
            requireClose(FrostBalance.chillPerHit() * 2, FrostMonsterStates.chill(enemy.runtimeMonster()), "Enemy emission chill");
            requireClose(FrostBalance.chillPerHit() * 2, icebox.chillForTest(), "Allied emission chill");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void rapidFreezeLeavesUnselectedEmissionAndDongtaeSpreadUnchanged(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-emission-negative");
        SemionMonsterEntity enemy = null;
        try {
            FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(6, 2, 3));
            FrostHealingTower icebox = healer(FrostTowers.ICEBOX_T1, setup.owner(), context, new BlockPos(4, 2, 3));
            setup.lane().addTower(cooling);
            setup.lane().addTower(icebox);
            enemy = spawnTarget(context, towerEntity(context, cooling).position().add(-3, 0, 0));
            cooling.execute(setup.lane());
            requireClose(FrostBalance.chillPerHit(), FrostMonsterStates.chill(enemy.runtimeMonster()),
                    "Without rapid freeze, actual enemy emission chill must keep its base amount");
            requireClose(FrostBalance.chillPerHit(), icebox.chillForTest(),
                    "Without rapid freeze, actual allied emission chill must keep its base amount");

            selectAugments(setup.lane(), FrostAugments.FREEZE);
            FrostVanguardTower dongtae = vanguard(FrostTowers.DONGTAE, setup.owner(), context, new BlockPos(4, 2, 4));
            setup.lane().addTower(dongtae);
            double beforeSpread = FrostMonsterStates.chill(enemy.runtimeMonster());
            for (int hit = 0; hit < 7; hit++) dongtae.onEmissionWaveHit(setup.lane());
            requireClose(FrostBalance.chillPerHit(), FrostMonsterStates.chill(enemy.runtimeMonster()) - beforeSpread,
                    "Selected rapid freeze must not double Dongtae's non-emission chill spread");
            requireClose(0, dongtae.chillForTest(), "Dongtae must consume its chill for the real spread");
            context.succeed();
        } finally {
            if (enemy != null) enemy.discard();
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void icePatchRespectsRadiusDurationAndDoesNotDuplicateDeath(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-ice");
        try {
            selectAugments(setup.lane(), FrostAugments.ICE);
            FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(2, 2, 3));
            setup.lane().addTower(cooling);
            SemionTowerEntity source = towerEntity(context, cooling);
            SemionMonsterEntity frozen = spawnTarget(context, source.position().add(1, 0, 0));
            SemionMonsterEntity inside = spawnTarget(context, frozen.position().add(1.9, 0, 0));
            SemionMonsterEntity outside = spawnTarget(context, frozen.position().add(2.1, 0, 0));
            FrostMonsterStates.applyChill(source, frozen, 1);
            long now = context.getLevel().getGameTime();
            FrostAugments.onMonsterDeath(setup.lane(), frozen.runtimeMonster(), frozen.position());
            FrostAugments.onMonsterDeath(setup.lane(), frozen.runtimeMonster(), frozen.position());
            requireClose(.3, inside.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "Ice slow");
            requireClose(0, outside.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "Ice radius");
            FrostAugments.tick(setup.lane(), now + 79);
            if (inside.activeTimedEffectTicks(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 2) {
                throw new AssertionError("Ice refresh must not leave a long slow after leaving the patch");
            }
            inside.aiStep();
            inside.aiStep();
            FrostAugments.tick(setup.lane(), now + 80);
            requireClose(0, inside.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION), "Ice expires at four seconds");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void icePatchRejectsIneligibleDeathsAndRoundResetCancelsIt(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-ice-failure");
        List<SemionMonsterEntity> enemies = new ArrayList<>();
        try {
            FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(2, 2, 3));
            setup.lane().addTower(cooling);
            SemionTowerEntity source = towerEntity(context, cooling);
            SemionMonsterEntity frozen = spawnTarget(context, source.position().add(1, 0, 0));
            SemionMonsterEntity normal = spawnTarget(context, frozen.position().add(.5, 0, 0));
            SemionMonsterEntity receiver = spawnTarget(context, frozen.position().add(1, 0, 0));
            enemies.addAll(List.of(frozen, normal, receiver));
            FrostMonsterStates.applyChill(source, frozen, 1);
            FrostAugments.onMonsterDeath(setup.lane(), frozen.runtimeMonster(), frozen.position());
            requireClose(0, receiver.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "A refrigerated death without the ice augment must not create a patch");

            selectAugments(setup.lane(), FrostAugments.ICE);
            FrostAugments.onMonsterDeath(setup.lane(), normal.runtimeMonster(), normal.position());
            requireClose(0, receiver.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "A non-refrigerated death must not create an ice patch");
            kim.biryeong.semiontd.augment.AugmentCombat.runWithoutTriggers(
                    () -> FrostAugments.onMonsterDeath(setup.lane(), frozen.runtimeMonster(), frozen.position()));
            requireClose(0, receiver.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Augment-generated deaths must not create an ice patch");

            FrostAugments.onMonsterDeath(setup.lane(), frozen.runtimeMonster(), frozen.position());
            requireClose(.3, receiver.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Rejected deaths must not consume the later valid ice trigger");
            setup.lane().resetForRound();
            receiver.aiStep();
            receiver.aiStep();
            FrostAugments.tick(setup.lane(), context.getLevel().getGameTime() + 2);
            requireClose(0, receiver.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION),
                    "Round reset must cancel the patch before its four-second duration ends");
            context.succeed();
        } finally {
            enemies.forEach(SemionMonsterEntity::discard);
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void thawAugmentCapsAreaAndRejectsRepeatedOrGeneratedTriggers(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-thaw");
        try {
            selectAugments(setup.lane(), FrostAugments.THAW);
            FrostSplashTower breaker = splash(FrostTowers.ICE_BREAKER_T1, setup.owner(), context, new BlockPos(2, 2, 3));
            setup.lane().addTower(breaker);
            SemionTowerEntity source = towerEntity(context, breaker);
            List<SemionMonsterEntity> enemies = new ArrayList<>();
            for (int index = 0; index < 14; index++) enemies.add(spawnTarget(context, source.position().add(2 + index * .02, 0, 0)));
            double damage = source.attackDamageAmount(null) * 3;
            FrostAugments.onThawed(breaker, source, enemies.getFirst());
            if (enemies.stream().filter(enemy -> enemy.runtimeMonster().health() < 100_000).count() != 12) {
                throw new AssertionError("Thaw area must hit at most twelve enemies including the center");
            }
            requireClose(100_000 - damage, enemies.getFirst().runtimeMonster().health(), "Thaw uses three times attack as magic damage");
            FrostAugments.onThawed(breaker, source, enemies.getFirst());
            kim.biryeong.semiontd.augment.AugmentCombat.runWithoutTriggers(
                    () -> FrostAugments.onThawed(breaker, source, enemies.get(1)));
            requireClose(100_000 - damage, enemies.getFirst().runtimeMonster().health(), "Cooldown and generated attacks cannot trigger another thaw");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void iceAgeCapsOwnSourcesAndTargetsWithoutRefreshingStun(GameTestHelper context) {
        TestSetup setup = augmentSetup(context, "frost-augment-ice-age");
        try {
            selectAugments(setup.lane(), FrostAugments.AGE);
            FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(3, 2, 3));
            setup.lane().addTower(cooling);
            SemionTowerEntity source = towerEntity(context, cooling);
            FrostAugments.beginWave(setup.lane());
            List<SemionMonsterEntity> refrigerated = new ArrayList<>();
            List<SemionMonsterEntity> receivers = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                SemionMonsterEntity frozen = spawnTarget(context, source.position().add(index % 2 * 4 - 2, 0, index / 2 * 4 - 2));
                refrigerated.add(frozen);
                FrostMonsterStates.applyChill(source, frozen, 1);
                receivers.add(spawnTarget(context, frozen.position().add(.5, 0, 0)));
            }
            List<SemionMonsterEntity> firstNeighbors = new ArrayList<>(List.of(receivers.getFirst()));
            for (int index = 1; index < 9; index++) firstNeighbors.add(spawnTarget(context, refrigerated.getFirst().position().add(.5 + index * .01, 0, 0)));
            long now = context.getLevel().getGameTime();
            FrostAugments.tick(setup.lane(), now + 39);
            requireClose(0, FrostMonsterStates.chill(receivers.getFirst().runtimeMonster()), "No propagation before two seconds");
            FrostAugments.tick(setup.lane(), now + 40);
            requireClose(2, firstNeighbors.stream().mapToDouble(enemy -> FrostMonsterStates.chill(enemy.runtimeMonster())).sum(), "Only eight neighbors receive 25 percent chill");
            requireClose(.25, FrostMonsterStates.chill(receivers.get(1).runtimeMonster()), "Second own source");
            requireClose(.25, FrostMonsterStates.chill(receivers.get(2).runtimeMonster()), "Third own source");
            requireClose(0, FrostMonsterStates.chill(receivers.get(3).runtimeMonster()), "Fourth source cannot propagate");
            SemionMonsterEntity first = refrigerated.getFirst();
            if (first.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) != 40) throw new AssertionError("New refrigerant must stun for two seconds");
            first.aiStep();
            FrostMonsterStates.applyChill(source, first, 1);
            FrostMonsterStates.thaw(first);
            FrostMonsterStates.applyChill(source, first, 1);
            if (first.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN) != 39) throw new AssertionError("Repeated refrigeration cannot extend stun inside eight seconds");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    private static void selectAugments(PlayerLane lane, String... cards) {
        lane.assignAugmentSnapshot(new kim.biryeong.semiontd.augment.AugmentSnapshot(
                kim.biryeong.semiontd.augment.AugmentConfig.defaults(), java.util.Arrays.stream(cards)
                .map(id -> new kim.biryeong.semiontd.augment.PlayerAugmentState.Selection(5,
                        kim.biryeong.semiontd.augment.AugmentRarity.GOLD, id,
                        kim.biryeong.semiontd.augment.PlayerAugmentState.Outcome.SELECTED, null,
                        kim.biryeong.semiontd.augment.AugmentChoice.none())).toList()));
    }

    private static TestSetup augmentSetup(GameTestHelper context, String ownerSeed) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes(ownerSeed.getBytes(StandardCharsets.UTF_8));
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 6, 7));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 2)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 6))), BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(6, 2, 5)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) context.getLevel().setBlock(context.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        }
        return new TestSetup(owner, lane);
    }

    @GameTest(maxTicks = 120)
    public void iceBreakerPrioritizesARefrigeratedTarget(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-ice-breaker-target-owner");
        FrostSplashTower breaker = splash(
                FrostTowers.ICE_BREAKER_T1,
                setup.owner(),
                context,
                new BlockPos(5, 2, 6)
        );
        try {
            setup.lane().addTower(breaker);
            SemionTowerEntity breakerEntity = towerEntity(context, breaker);
            SemionMonsterEntity normal = spawnTarget(context, breakerEntity.position().add(2.0, 0.0, 0.0));
            SemionMonsterEntity refrigerated = spawnTarget(context, breakerEntity.position().add(4.0, 0.0, 0.0));
            for (int hit = 0; hit < 7; hit++) {
                FrostMonsterStates.applyChill(refrigerated);
            }

            if (breaker.selectAttackTarget(breakerEntity, List.of(normal, refrigerated)).orElse(null)
                    != refrigerated) {
                throw new AssertionError("Ice breakers must prioritize a refrigerated target in range.");
            }
            if (breaker.selectAttackTarget(breakerEntity, List.of(normal)).isPresent()) {
                throw new AssertionError("Without refrigerant, ice breakers must defer to shared targeting.");
            }
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void defrostHeaterFiresOneImmediateExtraAttackWithoutChangingCooldown(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-defrost-heater-extra-attack-owner");
        FrostSplashTower heater = splash(
                FrostTowers.ICE_BREAKER_T3,
                setup.owner(),
                context,
                new BlockPos(5, 2, 6)
        );
        try {
            setup.lane().addTower(heater);
            SemionTowerEntity source = towerEntity(context, heater);
            SemionMonsterEntity target = spawnTarget(
                    context, source.position().add(2.0, 0.0, 0.0), 100_000.0, false);
            TowerAttackMonsterGoal normalAttack = new TowerAttackMonsterGoal(source);

            double beforeAttack = target.runtimeMonster().health();
            normalAttack.tick();
            requireClose(beforeAttack - 40.0, target.runtimeMonster().health(),
                    "The defrost heater must fire one and only one immediate 20-damage extra attack.");

            double afterExtraAttack = target.runtimeMonster().health();
            for (int tick = 0; tick < 16; tick++) {
                normalAttack.tick();
            }
            requireClose(afterExtraAttack, target.runtimeMonster().health(),
                    "The immediate extra attack must not pull the next normal attack forward.");
            normalAttack.tick();
            if (target.runtimeMonster().health() >= afterExtraAttack) {
                throw new AssertionError("The original 17-tick normal attack schedule must remain active.");
            }
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 140)
    public void fullOperationShowsAndEnforcesFixedDefenseAndAttackForExactlyFiveSeconds(
            GameTestHelper context
    ) {
        TestSetup setup = setup(context, "frost-full-operation-fixed-stats-owner");
        FrostSplashTower tower = splash(
                FrostTowers.ICE_BREAKER_T3,
                setup.owner(),
                context,
                new BlockPos(5, 2, 6)
        );
        setup.lane().addTower(tower);
        SemionTowerEntity entity = towerEntity(context, tower);
        long activationTick = context.getLevel().getGameTime();
        FrostFullOperationService.PlayerState state = FrostFullOperationService.stateForTest(setup.owner());
        state.beginWave();
        state.activate(activationTick);
        FrostFullOperationService.tick(setup.lane());

        requireClose(0.95, FrostFullOperationService.displayedDamageReduction(
                        setup.owner(), activationTick, entity.activeTimedEffectMagnitude(
                                TimedEffectType.TOWER_DAMAGE_REDUCTION)),
                "Full operation must display exactly 95% damage reduction.");
        requireClose(5.0, entity.attackDamageAmount(null),
                "Full operation must display and resolve ordinary attack damage as 5.");
        requireClose(5.0, tower.modifyFinalIncomingDamage(entity, null, 100.0, 1.0),
                "Full operation must enforce exactly five percent of original incoming damage.");

        context.runAfterDelay(101, () -> {
            try {
                FrostFullOperationService.tick(setup.lane());
                requireClose(20.0, entity.attackDamageAmount(null),
                        "Normal attack damage must return after five seconds.");
                requireClose(0.0, entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                        "The full-operation damage-reduction marker must be removed after five seconds.");
                context.succeed();
            } finally {
                FrostFullOperationService.clearPlayer(setup.owner());
                cleanup(setup);
            }
        });
    }

    @GameTest(maxTicks = 120)
    public void vanguardVisualChangesFromSnowGolemsToBlueAxolotl(GameTestHelper context) {
        TestSetup setup = null;
        try {
            setup = setup(context, "frost-vanguard-visual-owner");
            FrostVanguardTower tierOne = vanguard(
                    FrostTowers.ICE_VANGUARD,
                    setup.owner(),
                    context,
                    new BlockPos(5, 2, 6)
            );
            FrostVanguardTower tierThree = vanguard(
                    FrostTowers.DONGTAE,
                    setup.owner(),
                    context,
                    new BlockPos(8, 2, 6)
            );
            FrostVanguardTower tierTwo = vanguard(
                    FrostTowers.STURDY_ICE_VANGUARD,
                    setup.owner(),
                    context,
                    new BlockPos(6, 2, 6)
            );
            setup.lane().addTower(tierOne);
            setup.lane().addTower(tierTwo);
            setup.lane().addTower(tierThree);

            if (towerEntity(context, tierOne).getPolymerEntityType(null) != EntityType.SNOW_GOLEM
                    || snowGolemPumpkinData(context, FrostTowers.ICE_VANGUARD) != 0) {
                throw new AssertionError("The T1 vanguard must be a pumpkinless snow golem.");
            }
            if (towerEntity(context, tierTwo).getPolymerEntityType(null) != EntityType.SNOW_GOLEM
                    || snowGolemPumpkinData(context, FrostTowers.STURDY_ICE_VANGUARD) != 0) {
                throw new AssertionError("The T2 vanguard must be a pumpkinless 1.0x snow golem.");
            }
            SemionTowerEntity tierThreeEntity = towerEntity(context, tierThree);
            if (tierThreeEntity.getPolymerEntityType(null) != EntityType.AXOLOTL
                    || Math.abs(tierThreeEntity.getScale() - 1.2F) > 0.0001F
                    || axolotlVariantData(context, FrostTowers.DONGTAE) != Axolotl.Variant.BLUE.getId()) {
                throw new AssertionError("Dongtae must reveal a 1.2x blue axolotl visual at T3.");
            }
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Frost vanguard visual GameTest failed: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage()));
        } finally {
            if (setup != null) {
                cleanup(setup);
            }
        }
    }

    @GameTest(maxTicks = 120)
    public void coolingDevicesAloneBreakLaneDefenseAndStayOutOfFinalDefense(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-device-only-defense-owner");
        FrostCoolingTower emission = cooling(setup.owner(), context, new BlockPos(10, 2, 6));
        FrostEruptionCoolingTower eruption = eruption(
                setup.owner(), 1, context, new BlockPos(9, 2, 6));
        SemionMonsterEntity target = null;
        try {
            setup.lane().addTower(emission);
            setup.lane().addTower(eruption);
            GridPosition emissionPosition = emission.position();
            GridPosition eruptionPosition = eruption.position();
            target = spawnTarget(context, Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 6))));
            setup.lane().activeMonsters().add(target.runtimeMonster());

            setup.lane().tick(context.getLevel().getServer());
            if (!target.runtimeMonster().inFinalDefenseCombat()) {
                throw new AssertionError("A lane with only cooling devices must collapse normally.");
            }
            setup.lane().moveTowersToFinalDefense();
            if (!emission.position().equals(emissionPosition) || !eruption.position().equals(eruptionPosition)) {
                throw new AssertionError("Cooling devices must not occupy final-defense slots.");
            }
            context.succeed();
        } finally {
            setup.lane().activeMonsters().clear();
            if (target != null) {
                target.discard();
            }
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void activationItemCannotBeDroppedOrMovedThroughInventoryClicks(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        try {
            if (player.drop(FrostFullOperationService.activationItemForTest().copy(), false) != null) {
                throw new AssertionError("The full-operation item must not create a dropped item entity.");
            }
            player.inventoryMenu.setCarried(FrostFullOperationService.activationItemForTest().copy());
            player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
            if (!FrostFullOperationService.isActivationItem(player.inventoryMenu.getCarried())) {
                throw new AssertionError("Container clicks must not move the full-operation item.");
            }
            context.succeed();
        } finally {
            player.inventoryMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            player.discard();
        }
    }

    @GameTest(maxTicks = 120)
    public void fullOperationQueuesOneSharedBuffAreaEvent(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-full-operation-vfx-owner");
        FrostCoolingTower emission = cooling(setup.owner(), context, new BlockPos(10, 2, 6));
        FrostEruptionCoolingTower eruption = eruption(
                setup.owner(), 1, context, new BlockPos(9, 2, 6));
        FrostVanguardTower vanguard = vanguard(
                FrostTowers.ICE_VANGUARD, setup.owner(), context, new BlockPos(7, 2, 6));
        List<AreaEffectVfxEvent> observed = new ArrayList<>();
        try {
            setup.lane().addTower(emission);
            setup.lane().addTower(eruption);
            setup.lane().addTower(vanguard);
            AreaEffectVfxTestHooks.setObserver(observed::add);

            if (!FrostFullOperationService.showFullOperationVfx(setup.lane())) {
                throw new AssertionError("A live cooling device must provide the full-operation VFX source.");
            }
            if (observed.size() != 1
                    || !observed.getFirst().visual().styleId().equals(AreaVfxStyles.BUFF)
                    || observed.getFirst().visual().appliedCount() != 3
                    || !observed.getFirst().visual().effectId().getPath().endsWith("/frost_full_operation")) {
                throw new AssertionError("Full operation must queue one shared BUFF area event: " + observed);
            }
            context.succeed();
        } finally {
            AreaEffectVfxTestHooks.setObserver(null);
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void iceboxHealsAndAppliesNineFamilyDamageReduction(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-icebox-basic-owner");
        FrostHealingTower healer = healer(FrostTowers.ICEBOX_T3, setup.owner(), context, new BlockPos(6, 2, 6));
        FrostVanguardTower target = vanguard(FrostTowers.DONGTAE, setup.owner(), context, new BlockPos(8, 2, 6));
        try {
            setup.lane().addTower(healer);
            setup.lane().addTower(target);
            for (int index = 0; index < 8; index++) {
                setup.lane().addTower(healer(
                        switch (index % 3) {
                            case 0 -> FrostTowers.ICEBOX_T1;
                            case 1 -> FrostTowers.ICEBOX_T2;
                            default -> FrostTowers.ICEBOX_T3;
                        },
                        setup.owner(),
                        context,
                        new BlockPos(2 + index, 2, 10)
                ));
            }
            setup.lane().markWaveStarted(1);
            setHealth(context, target, 100.0);

            healer.tick(setup.lane());

            requireClose(220.0, target.health(), "The T3 icebox must heal 120 health.");
            requireClose(0.10, towerEntity(context, target).activeTimedEffectMagnitude(
                    TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Nine icebox-family towers must grant 10% damage reduction to a healed tower.");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void seventhEmissionHitReleasesTheRefrigerantHealingPulse(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-icebox-refrigerant-owner");
        FrostHealingTower healer = healer(FrostTowers.ICEBOX_T3, setup.owner(), context, new BlockPos(6, 2, 6));
        FrostVanguardTower target = vanguard(FrostTowers.DONGTAE, setup.owner(), context, new BlockPos(8, 2, 6));
        try {
            setup.lane().addTower(healer);
            setup.lane().addTower(target);
            setHealth(context, target, 100.0);

            for (int hit = 0; hit < 6; hit++) {
                healer.onEmissionWaveHit(setup.lane());
            }
            requireClose(100.0, target.health(), "Six emission hits must not release the special pulse.");
            requireClose(0.90, healer.chillForTest(), "Six emission hits must store 90% chill.");

            healer.onEmissionWaveHit(setup.lane());

            requireClose(269.2, target.health(), "The seventh hit must heal 120 x 1.41 health.");
            requireClose(0.0, healer.chillForTest(), "The special pulse must consume all stored chill.");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void emissionWaveHitsAnIceboxAcrossTheLane(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-emission-wave-owner");
        FrostCoolingTower cooling = cooling(setup.owner(), context, new BlockPos(10, 2, 6));
        FrostHealingTower healer = healer(FrostTowers.ICEBOX_T1, setup.owner(), context, new BlockPos(6, 2, 6));
        FrostSplashTower food = splash(
                FrostTowers.FROZEN_DUMPLING_T1,
                setup.owner(),
                context,
                new BlockPos(8, 2, 6)
        );
        FrostVanguardTower dongtae = vanguard(
                FrostTowers.DONGTAE,
                setup.owner(),
                context,
                new BlockPos(7, 2, 6)
        );
        FrostEruptionCoolingTower eruption = eruption(
                setup.owner(),
                1,
                context,
                new BlockPos(9, 2, 6)
        );
        try {
            setup.lane().addTower(cooling);
            setup.lane().addTower(healer);
            setup.lane().addTower(food);
            setup.lane().addTower(dongtae);
            setup.lane().addTower(eruption);

            cooling.tick(setup.lane());

            requireClose(0.15, healer.chillForTest(),
                    "The real emission wave must hit an icebox positioned inside its lane-wide path.");
            requireClose(0.15, food.chill(),
                    "The real emission wave must also hit frozen food inside its lane-wide path.");
            requireClose(0.15, dongtae.chillForTest(),
                    "The real emission wave must hit Dongtae for its fully-frozen cycle.");
            requireClose(0.15, eruption.operationChill(),
                    "The real emission wave must charge the eruption device beyond refrigerant rules.");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void seventhEmissionHitFullyFreezesDongtaeAndSpreadsChill(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-dongtae-fully-frozen-owner");
        FrostVanguardTower dongtae = vanguard(
                FrostTowers.DONGTAE,
                setup.owner(),
                context,
                new BlockPos(6, 2, 6)
        );
        try {
            setup.lane().addTower(dongtae);
            SemionTowerEntity source = towerEntity(context, dongtae);
            SemionMonsterEntity target = spawnTarget(
                    context, source.position().add(2.0, 0.0, 0.0), 10_000.0, true);

            for (int hit = 0; hit < 7; hit++) {
                dongtae.onEmissionWaveHit(setup.lane());
            }

            requireClose(0.0, dongtae.chillForTest(),
                    "The seventh emission hit must consume Dongtae's chill.");
            requireClose(0.10, source.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION),
                    "Fully frozen Dongtae must receive 10% damage reduction for one second.");
            requireClose(0.15, FrostMonsterStates.chill(target.runtimeMonster()),
                    "Fully frozen Dongtae must spread 15% chill within three blocks.");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void frozenFoodNineThresholdPrioritizesTheLargestIncomeMonster(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-frozen-food-threshold-owner");
        FrostSplashTower food = splash(
                FrostTowers.FROZEN_DUMPLING_T1,
                setup.owner(),
                context,
                new BlockPos(5, 2, 6)
        );
        try {
            setup.lane().addTower(food);
            for (int index = 0; index < 8; index++) {
                TowerType tier = switch (index % 3) {
                    case 0 -> FrostTowers.FROZEN_DUMPLING_T1;
                    case 1 -> FrostTowers.FROZEN_DUMPLING_T2;
                    default -> FrostTowers.FROZEN_DUMPLING_T3;
                };
                setup.lane().addTower(splash(
                        tier,
                        setup.owner(),
                        context,
                        new BlockPos(2 + index, 2, 10)
                ));
            }
            setup.lane().markWaveStarted(1);

            SemionTowerEntity source = towerEntity(context, food);
            SemionMonsterEntity wave = spawnTarget(
                    context, source.position().add(2.0, 0.0, 0.0), 10_000.0, false);
            SemionMonsterEntity smallerIncome = spawnTarget(
                    context, source.position().add(3.0, 0.0, 0.0), 1_000.0, true);
            SemionMonsterEntity largerIncome = spawnTarget(
                    context, source.position().add(4.0, 0.0, 0.0), 5_000.0, true);

            if (food.selectAttackTarget(source, List.of(wave, smallerIncome, largerIncome)).orElse(null)
                    != largerIncome) {
                throw new AssertionError("Nine frozen-food towers must prioritize the highest-max-health income monster.");
            }
            requireClose(15.0, source.attackDamageAmount(wave),
                    "Three frozen-food towers must add 3 attack damage.");
            requireClose(16.5, source.attackDamageAmount(largerIncome),
                    "T1 frozen food must deal 10% additional damage to income monsters at nine towers.");
            requireClose(1.8, food.effectiveSplashRadiusForTest(),
                    "Six frozen-food towers must add one block of splash radius.");
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void seventhEmissionHitFiresThreeAttacksWithoutChangingTheNormalCooldown(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-frozen-food-refrigerant-owner");
        FrostSplashTower food = splash(
                FrostTowers.FROZEN_DUMPLING_T1,
                setup.owner(),
                context,
                new BlockPos(5, 2, 6)
        );
        try {
            setup.lane().addTower(food);
            SemionTowerEntity source = towerEntity(context, food);
            SemionMonsterEntity target = spawnTarget(
                    context, source.position().add(2.0, 0.0, 0.0), 100_000.0, true);
            TowerAttackMonsterGoal normalAttack = new TowerAttackMonsterGoal(source);
            normalAttack.tick();

            double beforeCooling = target.runtimeMonster().health();
            for (int hit = 0; hit < 6; hit++) {
                food.onEmissionWaveHit(setup.lane());
            }
            requireClose(beforeCooling, target.runtimeMonster().health(), "Six hits must not trigger bonus attacks.");
            requireClose(0.9, food.chill(), "Six hits store 90% chill.");
            double beforeSeventhHit = target.runtimeMonster().health();
            food.onEmissionWaveHit(setup.lane());
            requireClose(beforeSeventhHit - 36.0, target.runtimeMonster().health(),
                    "The seventh wave hit must immediately fire exactly three 12-damage attacks.");
            requireClose(0.0, food.chill(), "The seventh wave hit must consume all stored chill.");

            double afterBonusAttacks = target.runtimeMonster().health();
            for (int tick = 0; tick < 19; tick++) {
                normalAttack.tick();
            }
            requireClose(afterBonusAttacks, target.runtimeMonster().health(),
                    "Bonus attacks must not pull the next normal attack forward.");
            normalAttack.tick();
            if (target.runtimeMonster().health() >= afterBonusAttacks) {
                throw new AssertionError("The original 20-tick normal attack schedule must remain active.");
            }
            context.succeed();
        } finally {
            cleanup(setup);
        }
    }

    @GameTest(maxTicks = 120)
    public void iceboxLeavesItsPlacementTileLikeTheOceanSquid(GameTestHelper context) {
        TestSetup setup = setup(context, "frost-icebox-movement-owner");
        FrostHealingTower healer = healer(FrostTowers.ICEBOX_T1, setup.owner(), context, new BlockPos(4, 2, 6));
        try {
            setup.lane().addTower(healer);
            SemionTowerEntity healerEntity = towerEntity(context, healer);
            Vec3 initialPosition = healerEntity.position();
            SemionMonsterEntity target = spawnTarget(
                    context,
                    initialPosition.add(8.0, 0.0, 0.0)
            );

            context.runAfterDelay(40, () -> {
                try {
                    SemionTowerEntity currentHealer = towerEntity(context, healer);
                    if (currentHealer.position().distanceTo(initialPosition) <= 0.1) {
                        throw new AssertionError("The icebox must leave its placement tile to follow the frontline.");
                    }
                    context.succeed();
                } finally {
                    cleanup(setup);
                }
            });
        } catch (RuntimeException | AssertionError exception) {
            cleanup(setup);
            throw exception;
        }
    }

    @GameTest(maxTicks = 120)
    public void expandedEruptionAuraKeepsOnlyTheStrongestAlliedFrostBuilderEffect(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        prepareFloor(context);
        UUID firstOwner = UUID.nameUUIDFromBytes("frost-eruption-first-owner".getBytes(StandardCharsets.UTF_8));
        UUID secondOwner = UUID.nameUUIDFromBytes("frost-eruption-second-owner".getBytes(StandardCharsets.UTF_8));
        UUID targetOwner = UUID.nameUUIDFromBytes("frost-eruption-target-owner".getBytes(StandardCharsets.UTF_8));
        PlayerLane firstLane = testLane(context, firstOwner, 1);
        PlayerLane secondLane = testLane(context, secondOwner, 2);
        PlayerLane targetLane = testLane(context, targetOwner, 3);
        TeamLaneGroup laneGroup = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        laneGroup.addLane(firstLane);
        laneGroup.addLane(secondLane);
        laneGroup.addLane(targetLane);
        FrostEruptionCoolingTower firstTower = eruption(firstOwner, 1, context, new BlockPos(4, 2, 6));
        FrostEruptionCoolingTower secondTower = eruption(secondOwner, 2, context, new BlockPos(6, 2, 6));
        SemionMonsterEntity target = spawnTarget(context, Vec3.atCenterOf(context.absolutePos(new BlockPos(8, 2, 6))));
        targetLane.activeMonsters().add(target.runtimeMonster());
        FrostTeamEffects.registerTeam(firstOwner, laneGroup);
        FrostTeamEffects.registerTeam(secondOwner, laneGroup);
        try {
            if (FrostTeamEffects.refreshEruptionAura(firstTower, firstLane, 4) != 1
                    || FrostTeamEffects.refreshEruptionAura(secondTower, secondLane, 10) != 1
                    || FrostTeamEffects.refreshEruptionAura(firstTower, firstLane, 4) != 1) {
                throw new AssertionError("Both allied compressors must reach the target lane.");
            }
            requireClose(0.15, target.activeTimedEffectMagnitude(
                            TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION),
                    "Allied eruption damage reduction must keep one strongest Frost-builder effect, not sum sources.");
            requireClose(0.10, target.activeTimedEffectMagnitude(
                            TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION),
                    "Allied eruption attack-speed reduction must keep one strongest Frost-builder effect, not sum sources.");
            context.succeed();
        } finally {
            FrostTeamEffects.unregisterPlayer(firstOwner);
            FrostTeamEffects.unregisterPlayer(secondOwner);
            targetLane.activeMonsters().clear();
            target.discard();
        }
    }

    private static TestSetup setup(GameTestHelper context, String ownerSeed) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        prepareFloor(context);
        UUID owner = UUID.nameUUIDFromBytes(ownerSeed.getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        AreaEffectLaneIndex.register(lane);
        return new TestSetup(owner, lane);
    }

    private static FrostHealingTower healer(
            TowerType type,
            UUID owner,
            GameTestHelper context,
            BlockPos position
    ) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new FrostHealingTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, grid, grid);
    }

    private static FrostVanguardTower vanguard(
            TowerType type,
            UUID owner,
            GameTestHelper context,
            BlockPos position
    ) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new FrostVanguardTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, grid, grid);
    }

    private static FrostSplashTower splash(
            TowerType type,
            UUID owner,
            GameTestHelper context,
            BlockPos position
    ) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new FrostSplashTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, grid, grid);
    }

    private static FrostCoolingTower cooling(UUID owner, GameTestHelper context, BlockPos position) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new FrostCoolingTower(
                TowerBalanceRuntime.resolve(FrostTowers.EMISSION_COOLING_DEVICE),
                owner,
                TeamId.RED,
                1,
                grid,
                grid
        );
    }

    private static FrostEruptionCoolingTower eruption(
            UUID owner,
            int laneId,
            GameTestHelper context,
            BlockPos position
    ) {
        GridPosition grid = GridPosition.from(context.absolutePos(position));
        return new FrostEruptionCoolingTower(
                TowerBalanceRuntime.resolve(FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED),
                owner,
                TeamId.RED,
                laneId,
                grid,
                grid
        );
    }

    private static void setHealth(GameTestHelper context, FrostVanguardTower tower, double health) {
        tower.syncHealth(health);
        towerEntity(context, tower).setHealth((float) health);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, EntityBackedTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    private static byte snowGolemPumpkinData(GameTestHelper context, TowerType type) {
        List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        EntityVisualApplierRegistry.apply(
                type.visual(),
                EntityType.SNOW_GOLEM,
                context.getLevel().registryAccess(),
                data
        );
        int accessorId = SnowGolemAccessor.semiontd$dataPumpkinId().id();
        for (SynchedEntityData.DataValue<?> value : data) {
            if (value.id() == accessorId && value.value() instanceof Byte pumpkinData) {
                return pumpkinData;
            }
        }
        throw new AssertionError("Snow golem pumpkin metadata was not applied.");
    }

    private static int axolotlVariantData(GameTestHelper context, TowerType type) {
        List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        EntityVisualApplierRegistry.apply(
                type.visual(),
                EntityType.AXOLOTL,
                context.getLevel().registryAccess(),
                data
        );
        return data.stream()
                .filter(value -> value.id() == AxolotlAccessor.semiontd$dataVariant().id())
                .map(SynchedEntityData.DataValue::value)
                .map(Integer.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static SemionMonsterEntity spawnTarget(GameTestHelper context, Vec3 position) {
        return spawnTarget(context, position, 100_000.0, true);
    }

    private static SemionMonsterEntity spawnTarget(
            GameTestHelper context,
            Vec3 position,
            double maxHealth,
            boolean income
    ) {
        Monster monster = new Monster(
                "frost-target-" + UUID.randomUUID(),
                TeamId.RED,
                1,
                Optional.empty(),
                income ? Optional.of(TeamId.BLUE) : Optional.empty(),
                maxHealth,
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
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setNoAi(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        return entity;
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        return testLane(context, owner, 1);
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner, int laneId) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                laneId,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                        Vec3.atCenterOf(context.absolutePos(new BlockPos(10, 2, 2)))
                ),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(10, 2, 12))),
                BlockBounds.of(min, max),
                List.of(
                        GridPosition.from(context.absolutePos(new BlockPos(8, 2, 10))),
                        GridPosition.from(context.absolutePos(new BlockPos(10, 2, 10)))
                )
        );
        return new PlayerLane(TeamId.RED, laneId, owner, context.getLevel(), layout);
    }

    private static void prepareFloor(GameTestHelper context) {
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void cleanup(TestSetup setup) {
        FrostAugments.clearPlayer(setup.owner());
        setup.lane().clearTowers();
        AreaEffectLaneIndex.unregister(setup.lane());
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-5) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual);
        }
    }

    private record TestSetup(UUID owner, PlayerLane lane) {
    }
}
