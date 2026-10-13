package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowerState;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class EndTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void permanentGrowthRetainsSixPercentAfterRealWaveReset(GameTestHelper context) {
        UUID owner = stableUuid("end-six-percent-growth");
        SemionGame game = startedSinglePlayerGame(context, owner, TeamId.RED, EndTowerJob.ID);
        try {
            PlayerLane lane = redLane(game, 1);
            BlockPos corePos = towerPlacementPos(lane);
            EndTower core = new EndTower(TowerBalanceRuntime.resolve(EndTowers.BASE_END_TOWER),
                    owner, TeamId.RED, 1, GridPosition.from(corePos));
            lane.addTower(core);
            BlockPos shulkerPos = nearbyTowerPlacementPos(lane, corePos);
            EndTower shulker = new EndTower(TowerBalanceRuntime.resolve(EndTowers.T1_SHULKER_TOWER),
                    owner, TeamId.RED, 1, GridPosition.from(shulkerPos));
            lane.addTower(shulker);
            BlockPos crystalPos = nearbyTowerPlacementPos(lane, shulkerPos);
            EndTower crystal = new EndTower(TowerBalanceRuntime.resolve(EndTowers.T1_ENDERMITE_TOWER),
                    owner, TeamId.RED, 1, GridPosition.from(crystalPos));
            lane.addTower(crystal);
            double health = shulker.currentMaxHealth();
            double damage = crystal.sacrificeAttackDamage();
            lane.markWaveStarted(1);
            for (int tick = 0; tick < 200; tick++) {
                core.tick(lane);
            }
            if (!assertClose(context, 0, shulker.health(), "The shulker transfer must complete.")) return;
            if (!assertClose(context, 0, crystal.health(), "The crystal-line transfer must complete.")) return;
            if (!assertClose(context, health * .06, core.transferStats().permanentHealthBonus(), "Permanent health must be six percent.")) return;
            if (!assertClose(context, damage * .06, core.transferStats().permanentDamageBonus(), "Permanent damage must be six percent.")) return;
            if (!assertClose(context, health * .50, core.transferStats().roundHealthBonus(), "Round health remains fifty percent.")) return;
            if (!assertClose(context, damage * .66, core.transferStats().roundDamageBonus(), "Round damage remains sixty-six percent.")) return;
            game.teams().get(TeamId.RED).resetForRound();
            if (!assertClose(context, health * .06, core.transferStats().permanentHealthBonus(), "Round reset preserves permanent health.")) return;
            if (!assertClose(context, damage * .06, core.transferStats().permanentDamageBonus(), "Round reset preserves permanent damage.")) return;
            if (!assertClose(context, 0, core.transferStats().roundHealthBonus(), "Round reset clears temporary health.")) return;
            if (!assertClose(context, 0, core.transferStats().roundDamageBonus(), "Round reset clears temporary damage.")) return;
            context.succeed();
        } finally {
            game.close();
        }
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
        Set<String> availableAfterCore = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T1_SHULKER_TOWER.id()),
                availableAfterCore,
                "End feeders should remain available after the single core is placed."
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
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.END_CRYSTAL, entity.getPolymerEntityType(null), "End Crystal appearance should remain unchanged.")) {
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
    public void babyEndDragonStopsAtFriendlyTowerButDragonCanAdvance(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        Vec3 babyPosition = Vec3.atBottomCenterOf(
                context.absolutePos(new BlockPos(1, 2, 1))
        );
        EndTower endTower = new EndTower(
                EndTowers.BASE_END_TOWER,
                stableUuid("end-movement-blocker-owner"),
                TeamId.RED,
                1,
                GridPosition.from(BlockPos.containing(babyPosition))
        );
        endTower.onWaveStarted(null, 1);
        for (int tick = 0; tick < 200; tick++) {
            endTower.tick(null);
        }
        if (!assertEquals(
                context,
                EndTowerState.PHANTOM,
                endTower.state(),
                "End core should hatch into the baby dragon before movement checks."
        )) {
            return;
        }
        endTower.moveToFinalDefense(null, GridPosition.from(BlockPos.containing(babyPosition)));

        SemionTowerEntity endEntity = new SemionTowerEntity(
                SemionEntityTypes.TOWER,
                context.getLevel()
        );
        endEntity.configure(endTower, null);
        endEntity.setPos(babyPosition);
        context.getLevel().addFreshEntity(endEntity);
        spawnTowerEntity(
                context,
                TeamId.RED,
                2,
                babyPosition.add(1.0, -1.0, 0.0),
                TestTowerTypes.TEST_DIRECT
        );

        double blockedX = endEntity.getX();
        Vec3 targetPosition = babyPosition.add(8.0, 0.0, 0.0);
        endEntity.moveTowardTarget(targetPosition, endEntity.chaseSpeedModifier());
        if (!assertClose(
                context,
                blockedX,
                endEntity.getX(),
                "A final-defense baby dragon should stop before a friendly tower from another lane."
        )) {
            return;
        }

        endTower.syncMaxHealth(2000.0, true);
        endTower.tick(null);
        endEntity.syncTowerState(endTower);
        if (!assertEquals(
                context,
                EndTowerState.DRAGON,
                endTower.state(),
                "End core should evolve before testing adult movement."
        )) {
            return;
        }

        endEntity.moveTowardTarget(targetPosition, endEntity.chaseSpeedModifier());
        if (!assertTrue(
                context,
                endEntity.getX() > blockedX,
                "Adult Ender Dragon should continue toward enemies through friendly towers."
        )) {
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
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.ARMOR_STAND, entity.getPolymerEntityType(null), "Ender Dragon EGG state should use an attribute-compatible living proxy.")) {
            return;
        }
        if (!assertTrue(context, !entity.isInvisible(), "The EGG server hitbox should remain visible to server-side raycasts.")) {
            return;
        }
        List<SynchedEntityData.DataValue<?>> eggProxyData = new ArrayList<>();
        entity.modifyRawTrackedData(eggProxyData, null, true);
        if (!assertTrue(context, eggProxyData.stream().anyMatch(data -> data.value() instanceof Byte flags && (flags & 0x20) != 0),
                "The client EGG armor-stand proxy should stay hidden behind its Dragon Egg block display.")) {
            return;
        }
        tower.onWaveStarted(null, 1);
        for (int tick = 0; tick < 200; tick++) {
            tower.tick(null);
        }
        entity.syncTowerState(tower);
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.PHANTOM, entity.getPolymerEntityType(null), "Hatching should refresh the same tower entity to a vanilla Phantom proxy.")) {
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
        if (!assertClose(context, tower.phantomScaleForMaxHealth(tower.currentMaxHealth()), clientScale, "Phantom growth should remain visible to clients.")) {
            return;
        }
        if (entity.runtimeTower() != tower) {
            throw new AssertionError("Visual state changes must retain the real End tower used by right-click details.");
        }

        tower.syncMaxHealth(1999.99, true);
        tower.tick(null);
        if (!assertEquals(context, kim.biryeong.semiontd.tower.end.EndTowerState.PHANTOM, tower.state(), "Max health below 2000 must remain PHANTOM.")) {
            return;
        }
        tower.syncMaxHealth(2000.0, true);
        tower.tick(null);
        entity.syncTowerState(tower);
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.ENDER_DRAGON, entity.getPolymerEntityType(null), "At least 2000 max health should evolve the Phantom into a vanilla Ender Dragon proxy.")) {
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
        if (!assertClose(context, 11.0, entity.applyTraitOutgoingDamage(null, 10.0), "DRAGON state should grant the configured 10% final damage.")) {
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
}
