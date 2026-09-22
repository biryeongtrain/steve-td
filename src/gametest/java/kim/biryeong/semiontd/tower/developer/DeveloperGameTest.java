package kim.biryeong.semiontd.tower.developer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.AreaEffectVfxEvent;
import kim.biryeong.semiontd.entity.tower.vfx.AreaEffectVfxTestHooks;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Entity-backed checks for the 개발자 builder.
 *
 * <p>The unit suite covers the arithmetic. What has to be verified against a live lane is the thing
 * the whole family leans on: three separate mechanics — ability towers, 긴급 점검 and instability
 * stalls — all express "does nothing" by returning a zero attack range, and
 * {@code TowerAttackMonsterGoal} has to actually treat that as switched off once the entity exists.
 */
public final class DeveloperGameTest {

    @GameTest
    public void intendedHardcodingKeepsWrongSpeciesPenaltyAndCopiedBonusHasNoPenalty(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-intended-owner");
        PlayerLane lane = testLane(context, owner);
        DeveloperTower tower = tower(DeveloperTowers.BETA, owner, context, new BlockPos(4, 2, 4));
        DeveloperTower copied = tower(DeveloperTowers.BETA, owner, context, new BlockPos(5, 2, 4));
        lane.addTower(tower);
        lane.addTower(copied);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(
                new PlayerAugmentState.Selection(5, AugmentRarity.SILVER, DeveloperAugments.COPIER,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()),
                new PlayerAugmentState.Selection(15, AugmentRarity.GOLD, DeveloperAugments.INTENDED,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
        DeveloperStates.clear(owner);
        SemionMonsterEntity first = monster(context, lane, "first_species",
                Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 6))), 1000);
        SemionMonsterEntity other = monster(context, lane, "other_species", first.position().add(1, 0, 0), 1000);
        try {
            DeveloperTowerData.addBug(tower, DeveloperBug.HARDCODED);
            tower.onAttackResolved(towerEntity(context, tower), first, 1, 1, 1, false);
            copied.onAttackResolved(towerEntity(context, copied), first, 1, 1, 1, false);
            requireClose(200, tower.modifyAttackDamage(towerEntity(context, tower), first, 100), "Native favored species");
            requireClose(120, tower.modifyAttackDamage(towerEntity(context, tower), other, 100),
                    "Unconditional positive damage must keep the native .6 wrong-species penalty");
            requireClose(150, copied.modifyAttackDamage(towerEntity(context, copied), other, 100),
                    "Copied enhancement is half of the bonus and has no wrong-species penalty");
            require(!copied.hasBug(DeveloperBug.HARDCODED), "Copying a benefit must not install a native defect");
            context.succeed();
        } finally {
            lane.clearTowers();
            DeveloperStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 120)
    public void abilityTowersSpawnWithoutFightingOrHoldingTheLane(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-ability-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);

        DeveloperTower workbench = tower(DeveloperTowers.WORKBENCH, owner, context, new BlockPos(4, 2, 4));
        try {
            lane.addTower(workbench);
            SemionTowerEntity entity = towerEntity(context, workbench);

            require(entity != null, "The ability tower must still spawn an entity.");
            requireClose(0.0, entity.attackRange(), "An ability tower must resolve to a zero attack range.");
            require(workbench.slotWeight() == 0, "An ability tower must not consume a lane slot.");
            require(!workbench.countsForLaneDefense(),
                    "A lane holding only ability towers must not count as defended.");

            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void maintenanceTakesTheTowerOfflineForExactlyOneRound(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-maintenance-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);

        DeveloperTower release = tower(DeveloperTowers.RELEASE, owner, context, new BlockPos(5, 2, 5));
        try {
            lane.addTower(release);
            double armedRange = towerEntity(context, release).attackRange();
            require(armedRange > 0.0, "A healthy tower must have a positive attack range.");

            DeveloperTowerData.scheduleMaintenance(release, 3);
            release.onWaveStarted(lane, 3);
            requireClose(0.0, towerEntity(context, release).attackRange(),
                    "A tower under maintenance must resolve to a zero attack range.");

            release.onWaveStarted(lane, 4);
            require(towerEntity(context, release).attackRange() > 0.0,
                    "Maintenance must end after exactly one round.");
            require(DeveloperTowerData.hasMaintenanceBonus(release, 4),
                    "The round after a maintenance must carry the damage bonus.");

            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void hotfixesLandImmediatelyAndReviewedPatchesWaitForTheNextWave(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-patch-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);

        DeveloperTower beta = tower(DeveloperTowers.BETA, owner, context, new BlockPos(5, 2, 5));
        DeveloperTower hotfixTarget = tower(DeveloperTowers.LTS, owner, context, new BlockPos(8, 2, 5));
        try {
            lane.addTower(beta);
            lane.addTower(hotfixTarget);
            double baseRange = towerEntity(context, beta).attackRange();

            DeveloperTowerData.addActivePatch(beta, DeveloperPatch.ATTACK, 0.01);
            DeveloperTowerData.addActivePatch(beta, DeveloperPatch.FIRE_RATE, 0.01);
            DeveloperTowerData.addPendingPatch(beta, DeveloperPatch.RANGE, 0.20);
            beta.onStateChanged(lane);
            requireClose(baseRange, towerEntity(context, beta).attackRange(),
                    "A reviewed patch must not change anything before the next wave.");
            require(DeveloperBalance.patchMilestone(DeveloperTowerData.activeAttackPatchCount(beta)) == 0,
                    "A pending patch must not unlock an attack milestone.");

            beta.onWaveStarted(lane, 2);
            require(towerEntity(context, beta).attackRange() > baseRange,
                    "A reviewed patch must take hold when the next wave starts.");
            require(DeveloperBalance.patchMilestone(DeveloperTowerData.activeAttackPatchCount(beta)) == 1,
                    "The promoted patch must unlock the milestone on the next wave.");

            DeveloperTowerData.addActivePatch(hotfixTarget, DeveloperPatch.ATTACK, 0.01);
            DeveloperTowerData.addActivePatch(hotfixTarget, DeveloperPatch.RANGE, 0.01);
            DeveloperStates.openRound(owner, 2, new DeveloperStates.Capacity(0, 1, false, false, 0, false, 0));
            require(DeveloperPatchService.applyPatch(lane, hotfixTarget, DeveloperPatch.FIRE_RATE, true).success(),
                    "A wave-time hotfix must succeed when capacity remains.");
            require(DeveloperBalance.patchMilestone(DeveloperTowerData.activeAttackPatchCount(hotfixTarget)) == 1,
                    "A hotfix must unlock its milestone immediately.");

            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void attackPatchMilestoneSplashesNearbySecondariesWithSharedVfx(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-milestone-splash-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DeveloperTower release = tower(DeveloperTowers.RELEASE, owner, context, new BlockPos(5, 2, 5));
        List<AreaEffectVfxEvent> observed = new ArrayList<>();
        AreaEffectLaneIndex.register(lane);
        try {
            lane.addTower(release);
            release.markWaveStarted(1);
            DeveloperTowerData.addActivePatch(release, DeveloperPatch.ATTACK, 0.01);
            DeveloperTowerData.addActivePatch(release, DeveloperPatch.RANGE, 0.01);
            DeveloperTowerData.addActivePatch(release, DeveloperPatch.FIRE_RATE, 0.01);

            SemionTowerEntity source = towerEntity(context, release);
            SemionMonsterEntity primary = monster(context, lane, "developer-splash-primary",
                    source.position().add(2.0, 0.0, 0.0), 100.0);
            SemionMonsterEntity secondary = monster(context, lane, "developer-splash-secondary",
                    primary.position().add(0.75, 0.0, 0.0), 30.0);
            SemionMonsterEntity distant = monster(context, lane, "developer-splash-distant",
                    primary.position().add(2.0, 0.0, 0.0), 100.0);
            AreaEffectVfxTestHooks.setObserver(observed::add);

            release.onAttackResolved(source, primary, 100.0, 100.0, 100.0, false);

            if (!assertClose(context, 100.0, primary.runtimeMonster().health(),
                    "The primary target must not take splash damage.")) return;
            if (!assertClose(context, 0.0, secondary.runtimeMonster().health(),
                    "The nearby secondary must take the 40% splash.")) return;
            if (!assertClose(context, 100.0, distant.runtimeMonster().health(),
                    "Targets outside the radius must remain untouched.")) return;
            if (!assertTrue(context, release.roundMetricsTracker().snapshot().killCount() == 1,
                    "A splash kill must remain attributed to the developer tower.")) return;
            if (!assertTrue(context,
                    observed.stream().anyMatch(event -> event.visual().styleId().equals(AreaVfxStyles.SPLASH)),
                    "The patch splash must use the shared splash VFX.")) return;
            context.succeed();
        } finally {
            AreaEffectVfxTestHooks.setObserver(null);
            AreaEffectLaneIndex.unregister(lane);
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void defensePatchMilestoneReducesDamageOnALiveTowerEntity(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-milestone-defense-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DeveloperTower release = tower(DeveloperTowers.RELEASE, owner, context, new BlockPos(5, 2, 5));
        try {
            lane.addTower(release);
            for (int index = 0; index < 7; index++) {
                DeveloperTowerData.addActivePatch(release, DeveloperPatch.HEALTH, 0.01);
            }
            release.resyncHealth(lane, false);
            SemionTowerEntity entity = towerEntity(context, release);
            double previousHealth = entity.getHealth();

            require(entity.hurtServer(context.getLevel(), entity.damageSources().generic(), 50.0F),
                    "The live tower entity must accept the test hit.");
            requireClose(previousHealth - 40.0, entity.getHealth(),
                    "Seven defense patches must reduce incoming damage by 20%.");
            requireClose(entity.getHealth(), release.health(),
                    "Runtime and entity health must stay synchronized after mitigation.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void garbageCollectionRecoversALiveEntityFromLethalDamage(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-gc-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DeveloperTower beta = tower(DeveloperTowers.BETA, owner, context, new BlockPos(5, 2, 5));
        try {
            lane.addTower(beta);
            SemionTowerEntity entity = towerEntity(context, beta);
            DeveloperTowerData.addBug(beta, DeveloperBug.GARBAGE_COLLECTION);
            DeveloperTowerData.addActivePatch(beta, DeveloperPatch.HEALTH, 0.20);
            beta.resyncHealth(lane, false);
            beta.onWaveStarted(lane, 1);

            double resolved = beta.modifyIncomingDamage(entity, null, beta.currentMaxHealth() * 10.0);

            requireClose(0.0, resolved, "Lethal damage must be cancelled by garbage collection.");
            requireClose(beta.currentMaxHealth(), beta.health(), "Runtime health must fully recover.");
            requireClose(beta.currentMaxHealth(), entity.getHealth(), "Entity health must fully recover.");
            require(DeveloperTowerData.activeCount(beta, DeveloperPatch.HEALTH) == 0,
                    "Recovery must consume one active patch.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void signFlipRejectsAllyHealingButAllowsSelfHealingWithoutFalseStats(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-sign-flip-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DeveloperTower healer = tower(DeveloperTowers.BETA, owner, context, new BlockPos(4, 2, 4));
        DeveloperTower target = tower(DeveloperTowers.RELEASE, owner, context, new BlockPos(6, 2, 6));
        try {
            lane.addTower(healer);
            lane.addTower(target);
            SemionTowerEntity targetEntity = towerEntity(context, target);
            DeveloperTowerData.addBug(target, DeveloperBug.SIGN_FLIP);
            target.syncHealth(target.currentMaxHealth() / 2.0);
            target.onStateChanged(lane);
            healer.markWaveStarted(1);

            require(!targetEntity.canReceiveHealing(), "Heal target search must skip SIGN_FLIP towers.");
            require(!healer.healTarget(targetEntity, 20.0), "Direct ally healing must also be rejected.");
            requireClose(0.0, healer.roundMetricsTracker().snapshot().healingDone(),
                    "Rejected healing must not be recorded.");

            target.markWaveStarted(1);
            require(targetEntity.healTarget(targetEntity, 20.0), "Self healing must remain allowed.");
            require(target.health() > target.currentMaxHealth() / 2.0, "Self healing must change health.");
            require(target.roundMetricsTracker().snapshot().healingDone() > 0.0,
                    "Successful self healing must be recorded.");
            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest(maxTicks = 120)
    public void patchesAndDefectsSurviveAnUpgradeOnALiveLane(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = stableUuid("developer-upgrade-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);

        DeveloperTower alpha = tower(DeveloperTowers.ALPHA, owner, context, new BlockPos(7, 2, 7));
        try {
            lane.addTower(alpha);
            DeveloperTowerData.addActivePatch(alpha, DeveloperPatch.ATTACK, 0.33);
            DeveloperTowerData.addBug(alpha, DeveloperBug.AGGRO_STORM);

            DeveloperTower release = tower(DeveloperTowers.RELEASE, owner, context, new BlockPos(7, 2, 7));
            release.copyFrom(alpha, 0L);

            requireClose(0.33, DeveloperTowerData.activeAmount(release, DeveloperPatch.ATTACK),
                    "Accumulated patches must survive an upgrade.");
            require(release.hasBug(DeveloperBug.AGGRO_STORM), "Defects must survive an upgrade.");
            require(release.aggroPriority() > DeveloperTowers.RELEASE.aggroPriority(),
                    "어그로 폭주 must still raise aggro after the upgrade.");

            context.succeed();
        } finally {
            lane.clearTowers();
        }
    }

    /**
     * An instability stall has to lift on its own.
     *
     * <p>{@code SemionTowerEntity} caches {@code attackRange} at sync time instead of asking the
     * runtime tower every tick, so a stall that merely counted down would leave the tower switched
     * off for the whole wave rather than the three seconds it is supposed to cost. This is the
     * regression that behaviour needs.
     */
    @GameTest(maxTicks = 200)
    public void anInstabilityStallLiftsInsteadOfKillingTheWholeWave(GameTestHelper context) {
        // Force the roll instead of hunting for a lucky seed: at max instability a stall chance of
        // 0.2 makes the resolved probability exactly 1.0, so the stall always happens and the test
        // measures the thing it is actually about — whether the stall ever lifts.
        TowerBalanceRuntime.apply(withStallChance(0.2));
        UUID owner = stableUuid("developer-stall-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);

        DeveloperTower beta = tower(DeveloperTowers.BETA, owner, context, new BlockPos(3, 2, 3));
        try {
            lane.addTower(beta);
            double armedRange = towerEntity(context, beta).attackRange();
            if (!assertTrue(context, armedRange > 0.0, "정상 타워는 사거리가 0보다 커야 합니다.")) {
                return;
            }

            DeveloperTowerData.addInstability(beta, DeveloperBalance.maxInstability());
            beta.onWaveStarted(lane, 2);

            if (!assertTrue(context, beta.stalledByInstability(), "최대 불안정에서는 반드시 정지해야 합니다.")) {
                return;
            }
            if (!assertClose(context, 0.0, towerEntity(context, beta).attackRange(),
                    "정지 중인 타워는 사거리가 0이어야 합니다.")) {
                return;
            }

            for (int tick = 0; tick <= DeveloperBalance.instabilityStallTicks(); tick++) {
                beta.tick(lane);
            }

            if (!assertTrue(context, towerEntity(context, beta).attackRange() > 0.0,
                    "정지는 지속시간이 끝나면 풀려야 합니다. 웨이브 내내 죽어 있으면 안 됩니다.")) {
                return;
            }
            context.succeed();
        } finally {
            lane.clearTowers();
            TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        }
    }

    /** Default balance with one developer knob overridden, for tests that need a certain outcome. */
    private static TowerBalanceConfig withStallChance(double chance) {
        TowerBalanceConfig base = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(base.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(DeveloperBalance.CONFIG_ID));
        global.put("instabilityStallChance", chance);
        abilities.put(DeveloperBalance.CONFIG_ID, global);
        return new TowerBalanceConfig(
                new LinkedHashMap<>(base.towers()),
                new LinkedHashMap<>(base.upgradeCosts()),
                abilities
        );
    }

    // ------------------------------------------------------------------ 헬퍼

    private static DeveloperTower tower(TowerType type, UUID owner, GameTestHelper context, BlockPos position) {
        return new DeveloperTower(
                TowerBalanceRuntime.resolve(type),
                owner,
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(position))
        );
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, DeveloperTower tower) {
        require(tower.entityId().isPresent(), tower.type().id() + " 의 엔티티가 스폰되지 않았습니다.");
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());
    }

    private static SemionMonsterEntity monster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            Vec3 position,
            double maxHealth
    ) {
        Monster monster = new Monster(
                id,
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                maxHealth,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                MonsterDimensions.DEFAULT,
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "The splash target must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
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

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertClose(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-6) {
            context.fail(Component.literal(message + " expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-6) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual);
        }
    }
}
