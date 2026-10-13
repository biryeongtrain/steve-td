package kim.biryeong.semiontd.tower.ancientcity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AncientCityTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTower;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class AncientCityTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void ancientCityTerritorySeedsAndGrowsWithoutResetting(GameTestHelper context) {
        UUID playerId = stableUuid("ancient-city-territory-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AncientCityTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos origin = towerPlacementPos(lane);
        BlockBounds laneArea = lane.laneLayout().laneArea();
        BlockPos blockedColumn = List.of(origin.north(), origin.south(), origin.west(), origin.east()).stream()
                .filter(position -> position.getX() >= laneArea.min().getX() && position.getX() <= laneArea.max().getX())
                .filter(position -> position.getZ() >= laneArea.min().getZ() && position.getZ() <= laneArea.max().getZ())
                .map(position -> new BlockPos(position.getX(), laneArea.max().getY(), position.getZ()))
                .findFirst()
                .orElseThrow();
        lane.arenaWorld().setBlock(blockedColumn, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
        lane.arenaWorld().setBlock(blockedColumn.above(), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
        AncientCityTower catalyst = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.CATALYST_T1),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(origin)
        );
        lane.addTower(catalyst);
        if (!(lane.arenaWorld().getEntity(catalyst.entityId().orElseThrow()) instanceof SemionTowerEntity catalystEntity)) {
            context.fail(Component.literal("Ancient-city catalyst should spawn a clickable tower entity."));
            return;
        }
        if (!assertClose(context, 0.65, catalystEntity.getBbWidth(),
                "Block-display tower hitbox width should match its visible block.")) {
            return;
        }
        if (!assertClose(context, 0.65, catalystEntity.getBbHeight(),
                "Block-display tower hitbox height should match its visible block.")) {
            return;
        }
        if (!assertTrue(context, !catalystEntity.isInvisible(),
                "Block-display towers should hide only the client armor-stand proxy so server raycasts remain targetable.")) {
            return;
        }

        if (!assertEquals(context, 9, AncientCityStates.territoryCount(playerId),
                "The first ancient-city tower should seed nine sculk cells by routing around a blocked direction.")) {
            return;
        }
        if (!assertTrue(context, AncientCityStates.territoryPositions(playerId).stream()
                        .noneMatch(position -> position.getX() == blockedColumn.getX()
                                && position.getZ() == blockedColumn.getZ()),
                "Sculk spread should skip the blocked column and continue through another frontier.")) {
            return;
        }
        if (!assertTrue(context, AncientCityStates.territoryPositions(playerId).stream()
                        .allMatch(position -> lane.arenaWorld().getBlockState(position).is(Blocks.SCULK)),
                "Every recorded territory cell should be an actual sculk block.")) {
            return;
        }
        BlockPos otherSculk = AncientCityStates.territoryPositions(playerId).stream()
                .filter(position -> position.getX() != origin.getX() || position.getZ() != origin.getZ())
                .findFirst()
                .orElseThrow();
        catalyst.syncPosition(GridPosition.from(origin.offset(20, 0, 0)));
        if (!assertTrue(context, !AncientCityStates.resonanceActive(catalyst),
                "A tower that moved off sculk should lose resonance even when its original position remains on sculk.")) {
            return;
        }
        catalyst.syncPosition(GridPosition.from(otherSculk));
        if (!assertTrue(context, AncientCityStates.resonanceActive(catalyst),
                "A tower should gain resonance when its current position moves onto owned sculk.")) {
            return;
        }
        catalyst.syncPosition(GridPosition.from(origin));

        lane.markWaveStarted(1);
        if (!assertEquals(context, 13, AncientCityStates.territoryCount(playerId),
                "Wave start should spread four connected sculk cells.")) {
            return;
        }
        Vec3 deathPosition = lane.laneLayout().positionAt(0.75);
        AncientCityStates.recordAttributedDeath(playerId, lane, 1, deathPosition);
        BlockPos deathCell = BlockPos.containing(deathPosition);
        if (!assertTrue(context, AncientCityStates.territoryPositions(playerId).stream()
                        .anyMatch(position -> position.getX() == deathCell.getX() && position.getZ() == deathCell.getZ()),
                "A death outside the connected territory should create a new sculk seed.")) {
            return;
        }
        for (int death = 1; death < 7; death++) {
            AncientCityStates.recordAttributedDeath(playerId, lane, 1, deathPosition);
        }
        if (!assertEquals(context, 19, AncientCityStates.territoryCount(playerId),
                "Attributed deaths should spread at most six successful cells per round.")) {
            return;
        }

        BlockPos secondPosition = nearbyTowerPlacementPos(lane, origin);
        AncientCityTower sensor = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.SENSOR_T1),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(secondPosition)
        );
        lane.addTower(sensor);
        if (!assertEquals(context, 19, AncientCityStates.territoryCount(playerId),
                "Additional tier-one towers must not create another seed.")) {
            return;
        }

        AncientCityTower upgraded = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.CATALYST_T2),
                playerId,
                TeamId.RED,
                1,
                catalyst.originalPosition(),
                catalyst.position()
        );
        upgraded.copyFrom(catalyst, 110);
        lane.replaceTower(catalyst, upgraded);
        if (!assertEquals(context, 19, AncientCityStates.territoryCount(playerId),
                "Upgrading should preserve the existing territory.")) {
            return;
        }
        GridPosition finalDefensePosition = lane.nextFinalDefenseTowerPosition(upgraded);
        upgraded.moveToFinalDefense(lane, finalDefensePosition);
        if (!assertTrue(context, AncientCityStates.resonanceActive(upgraded),
                "A final-defense tower standing on its reseeded sculk should retain resonance.")) {
            return;
        }
        upgraded.syncPosition(new GridPosition(
                finalDefensePosition.x() + 20,
                finalDefensePosition.y(),
                finalDefensePosition.z()
        ));
        if (!assertTrue(context, !AncientCityStates.resonanceActive(upgraded),
                "Final-defense deployment alone should not keep resonance after moving off reseeded sculk.")) {
            return;
        }
        lane.removeTower(upgraded);
        lane.removeTower(sensor);
        AncientCityStates.recordAttributedDeath(playerId, lane, 2, deathPosition);
        if (!assertEquals(context, 19, AncientCityStates.territoryCount(playerId),
                "Selling every ancient-city tower should keep territory but stop growth.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ancientCitySensorUsesMagicDamageAndDoesNotApplyIgnite(GameTestHelper context) {
        UUID playerId = stableUuid("ancient-city-sensor-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AncientCityTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));
        AncientCityTower sensor = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.SENSOR_T1),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(sensor);
        lane.markWaveStarted(1);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(sensor.entityId().orElseThrow());
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "ancient-sensor-target",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                1,
                towerEntity.position().add(2.0, 0.0, 0.0),
                1_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        target.setNoAi(true);
        target.runtimeMonster().markMinecraftEntitySpawned(
                target.getId(), target.getX(), target.getY(), target.getZ()
        );
        lane.activeMonsters().add(target.runtimeMonster());

        sensor.tick(lane);
        double expectedMagicDamage = 5.0 * (1.0 + 13.0 / 224.0 * 2.25) * 1.75;
        if (!assertClose(context, 1_000.0 - expectedMagicDamage, target.getHealth(),
                "Sensor ability should use magic resistance and the income-target multiplier.")) {
            return;
        }
        if (!assertClose(context, expectedMagicDamage, sensor.roundMagicDamageDealt(),
                "Sensor ability damage should be recorded as magic damage.")) {
            return;
        }
        if (!assertClose(context, 0.0, sensor.roundPhysicalDamageDealt(),
                "Sensor ability must not be recorded as physical damage.")) {
            return;
        }
        if (!assertTrue(context, target.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Ancient-city magic abilities must not apply ignite.")) {
            return;
        }
        if (!assertTrue(context, target.activeTimedEffectTicks(TimedEffectType.MONSTER_MARKED) > 0,
                "Sensor damage should apply its owner-scoped mark after the first hit.")) {
            return;
        }

        SemionMonsterEntity basicTarget = spawnRoleMonsterEntity(
                context,
                "ancient-basic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(3.0, 0.0, 0.0),
                1_000.0,
                List.of(SummonRole.RUSH)
        );
        basicTarget.setNoAi(true);
        towerEntity.damageBasicAttackSecondaryTargetResult(basicTarget, sensor.type().damage());
        if (!assertTrue(context, basicTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) > 0,
                "Ancient-city physical basic attacks should still apply ignite.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ancientCityMagicTargetsOtherLanesAtFinalDefense(GameTestHelper context) {
        UUID playerId = stableUuid("ancient-city-final-defense-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AncientCityTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        AncientCityTower sensor = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.SENSOR_T1),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(sensor);
        lane.markWaveStarted(1);
        sensor.moveToFinalDefense(lane, lane.nextFinalDefenseTowerPosition(sensor));
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(sensor.entityId().orElseThrow());
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "ancient-final-defense-target",
                Optional.of(TeamId.BLUE),
                TeamId.RED,
                2,
                towerEntity.position().add(2.0, 0.0, 0.0),
                1_000.0,
                0.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        target.setNoAi(true);
        target.runtimeMonster().markMinecraftEntitySpawned(
                target.getId(), target.getX(), target.getY(), target.getZ()
        );

        sensor.tick(lane);
        if (!assertTrue(context, target.getHealth() < 1_000.0,
                "Ancient-city magic should target another lane's monster at final defense.")) {
            return;
        }
        if (!assertTrue(context, sensor.roundMagicDamageDealt() > 0.0,
                "Final-defense ancient-city ability damage should be recorded as magic damage.")) {
            return;
        }
        context.succeed();
    }
}
