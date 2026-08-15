package kim.biryeong.semiontd.tower.engineer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EngineerGameTest {
    @GameTest
    public void engineerPlateIgnoresEntitiesOtherThanCopperGolem(GameTestHelper context) {
        UUID owner = stableUuid("engineer-golem-only-plate");
        PlayerLane lane = testLane(context, owner);
        GridPosition platePosition = floor(context, 6, 2, 6);
        prepareFloor(context, platePosition);
        EngineerCircuitTower plate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                owner,
                TeamId.RED,
                1,
                platePosition,
                platePosition
        );
        lane.addTower(plate);
        Mob intruder = context.spawn(EntityType.ZOMBIE, new BlockPos(6, 3, 6));
        intruder.setNoAi(true);

        context.runAfterDelay(5, () -> {
            try {
                require(!plate.platePressed(lane),
                        "Players and ordinary entities must not power an engineer pressure plate.");
                context.succeed();
            } catch (Throwable failure) {
                context.fail(Component.literal("Engineer golem-only plate GameTest failed: "
                        + failure.getClass().getName() + ": " + failure.getMessage()));
            } finally {
                intruder.discard();
                lane.clearTowers();
            }
        });
    }

    @GameTest
    public void circuitsUseRealBlocksAndPoweredTrapStopsAtFinalDefense(GameTestHelper context) {
        UUID owner = stableUuid("engineer-circuit");
        PlayerLane lane = testLane(context, owner);
        GridPosition dustPosition = floor(context, 2, 2, 3);
        GridPosition repeaterPosition = floor(context, 4, 2, 3);
        GridPosition trapPosition = floor(context, 6, 2, 3);
        GridPosition platePosition = floor(context, 5, 2, 3);
        GridPosition doorPosition = floor(context, 8, 2, 3);
        GridPosition dispenserPosition = floor(context, 10, 2, 3);
        GridPosition pistonPosition = floor(context, 12, 2, 3);
        prepareFloor(context, dustPosition, repeaterPosition, platePosition, trapPosition, doorPosition,
                dispenserPosition, pistonPosition);

        EngineerCircuitTower dust = new EngineerCircuitTower(
                EngineerTowers.REDSTONE_DUST, owner, TeamId.RED, 1, dustPosition, dustPosition
        );
        EngineerCircuitTower repeater = new EngineerCircuitTower(
                EngineerTowers.repeater(Direction.EAST), owner, TeamId.RED, 1, repeaterPosition, repeaterPosition
        );
        EngineerTrapTower slime = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.SLIME, 1),
                owner, TeamId.RED, 1, trapPosition, trapPosition
        );
        EngineerCircuitTower plate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                owner, TeamId.RED, 1, platePosition, platePosition
        );
        EngineerTrapTower door = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 1),
                owner, TeamId.RED, 1, doorPosition, doorPosition
        );
        EngineerTrapTower dispenser = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 1),
                owner, TeamId.RED, 1, dispenserPosition, dispenserPosition
        );
        EngineerTrapTower piston = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 1),
                owner, TeamId.RED, 1, pistonPosition, pistonPosition
        );
        try {
            lane.addTower(dust);
            lane.addTower(repeater);
            lane.addTower(plate);
            lane.addTower(slime);
            lane.addTower(door);
            lane.addTower(dispenser);
            lane.addTower(piston);
            require(context.getLevel().getBlockState(dust.circuitPosition()).is(Blocks.REDSTONE_WIRE),
                    "Dust must place a real redstone wire block.");
            require(context.getLevel().getBlockState(repeater.circuitPosition()).is(Blocks.REPEATER),
                    "Repeater must place a real vanilla repeater block.");
            require(context.getLevel().getBlockState(repeater.circuitPosition()).getValue(RepeaterBlock.FACING) == Direction.EAST,
                    "Repeater facing must match the selected direction.");
            require(TowerPlacementPositions.resolveGrid(lane, dust.circuitPosition()).orElseThrow().equals(dustPosition),
                    "Clicking the physical wire must resolve to the logical tower below it.");
            require(door.hasUpperDoorVisual(), "Iron door traps must render both lower and upper halves.");
            require(BlockDisplayVisual.blockState(dispenser.visual()).getValue(DispenserBlock.FACING) == Direction.WEST,
                    "Dispenser front must face the incoming lane spawn.");
            require(BlockDisplayVisual.blockState(piston.visual()).getValue(PistonBaseBlock.FACING) == Direction.WEST,
                    "Piston front must face the incoming lane spawn.");

            lane.markWaveStarted(1);
            context.getLevel().setBlock(slimePosition(trapPosition).east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            slime.tick(lane);
            require(slime.activeTicksRemaining() == 0,
                    "A powered loop without a recent copper-golem plate press must not activate a trap.");
            context.getLevel().setBlock(slimePosition(trapPosition).east(), Blocks.AIR.defaultBlockState(), 3);
            require(plate.pressPlate(lane), "The engineer plate must create the authorized circuit pulse.");
            slime.tick(lane);
            require(slime.activeTicksRemaining() > 0, "A powered trap must latch for three seconds.");
            require(slime.activationPlateDistance() == 1,
                    "A directly adjacent activated plate must be recorded at circuit distance one.");
            SemionTowerEntity slimeEntity = (SemionTowerEntity) context.getLevel()
                    .getEntity(slime.entityId().orElseThrow());
            require(slimeEntity.getCustomName() != null
                            && slimeEntity.getCustomName().getString().startsWith("활성화된 "),
                    "An active trap name must be prefixed with 활성화된.");
            context.getLevel().setBlock(
                    plate.circuitPosition(),
                    context.getLevel().getBlockState(plate.circuitPosition())
                            .setValue(BlockStateProperties.POWERED, false),
                    3
            );
            for (int tick = 0; tick < 10; tick++) {
                slime.tick(lane);
            }
            require(slime.activeTicksRemaining() > 0, "The trap must remain active after power is removed.");
            require(plate.pressPlate(lane), "The plate must be able to send a second authorized pulse.");
            slime.tick(lane);
            require(slime.activeTicksRemaining() >= EngineerBalance.activeTicks() - 1,
                    "A new power edge must refresh an active trap back to three seconds.");
            lane.moveTowersToFinalDefense();
            require(slime.activeTicksRemaining() == 0, "Forced final defense must stop traps immediately.");
            require(!slime.deployedAtFinalDefense(), "Engineer traps must not consume a final-defense slot.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Engineer circuit GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void copperGolemChoosesGoldBeforeWoodAndIsInvulnerable(GameTestHelper context) {
        UUID owner = stableUuid("engineer-golem-priority");
        PlayerLane lane = testLane(context, owner);
        GridPosition golemPosition = floor(context, 5, 2, 7);
        GridPosition woodPosition = floor(context, 6, 2, 7);
        GridPosition pathOne = floor(context, 7, 2, 7);
        GridPosition pathTwo = floor(context, 8, 2, 7);
        GridPosition goldPosition = floor(context, 9, 2, 7);
        prepareFloor(context, golemPosition, woodPosition, pathOne, pathTwo, goldPosition);

        EngineerGolemTower golem = new EngineerGolemTower(
                EngineerTowers.COPPER_GOLEM, owner, TeamId.RED, 1, golemPosition, golemPosition
        );
        EngineerCircuitTower wood = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD), owner, TeamId.RED, 1, woodPosition, woodPosition
        );
        EngineerCircuitTower gold = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.GOLD), owner, TeamId.RED, 1, goldPosition, goldPosition
        );
        try {
            lane.addTower(golem);
            lane.addTower(wood);
            lane.addTower(gold);
            Mob entity = golem.golemEntity(lane);
            require(entity != null, "Copper golem must spawn when its tower is placed.");
            require(entity.isInvulnerable(), "Copper golem must be invulnerable.");
            require(golem.ownsGolemEntity(entity), "The visible copper golem must resolve to its logical tower for clicking.");
            require(entity.getCustomName() != null && entity.getCustomName().getString().equals("구리 골렘")
                            && entity.isCustomNameVisible(),
                    "Copper golem must show its tower name above the model.");

            lane.markWaveStarted(1);
            require(goldPosition.equals(golem.targetPlate()),
                    "Gold plate must be selected immediately when the wave starts.");
            for (int tick = 0; tick < 80 && golem.pressesThisWave() < 1; tick++) {
                golem.tick(lane);
            }
            require(golem.pressesThisWave() == 1,
                    "The golem must reach and physically power the selected pressure plate.");
            require(Math.abs(entity.getYRot() - entity.getYHeadRot()) < 0.001,
                    "The copper golem head must keep facing its movement direction.");
            for (int tick = 0; tick < 80 && golem.pressesThisWave() < 2; tick++) {
                golem.tick(lane);
            }
            require(golem.pressesThisWave() == 2,
                    "The golem must continue to the remaining pressure plate.");
            Vec3 waitingPosition = entity.position();
            for (int tick = 0; tick < 20; tick++) {
                golem.tick(lane);
            }
            require(entity.position().distanceToSqr(waitingPosition) < 0.01,
                    "With no available plate, the golem must wait on its last plate instead of returning home.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Engineer golem GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void repeaterDirectionAndOwnerControlAuthorizedCircuitPaths(GameTestHelper context) {
        UUID owner = stableUuid("engineer-directed-circuit");
        UUID other = stableUuid("engineer-directed-circuit-other");
        PlayerLane lane = testLane(context, owner);
        GridPosition platePosition = floor(context, 2, 2, 5);
        GridPosition repeaterPosition = floor(context, 3, 2, 5);
        GridPosition trapPosition = floor(context, 4, 2, 5);
        prepareFloor(context, platePosition, repeaterPosition, trapPosition);

        EngineerCircuitTower plate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                owner, TeamId.RED, 1, platePosition, platePosition
        );
        EngineerCircuitTower backwards = new EngineerCircuitTower(
                EngineerTowers.repeater(Direction.WEST),
                owner, TeamId.RED, 1, repeaterPosition, repeaterPosition
        );
        EngineerTrapTower trap = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 1),
                owner, TeamId.RED, 1, trapPosition, trapPosition
        );
        try {
            lane.addTower(plate);
            lane.addTower(backwards);
            lane.addTower(trap);
            require(plate.pressPlate(lane), "The source plate must be pressable.");
            require(trap.recentPlateDistance(lane).isEmpty(),
                    "A backwards repeater must not authorize the trap.");

            require(lane.removeTower(backwards), "The backwards repeater must be removable.");
            EngineerCircuitTower forwards = new EngineerCircuitTower(
                    EngineerTowers.repeater(Direction.EAST),
                    owner, TeamId.RED, 1, repeaterPosition, repeaterPosition
            );
            lane.addTower(forwards);
            require(plate.pressPlate(lane), "The plate must refresh its authorized press time.");
            require(trap.recentPlateDistance(lane).orElse(-1) == 2,
                    "A forward repeater must report a two-block circuit path.");

            require(lane.removeTower(plate), "The owner plate must be removable.");
            EngineerCircuitTower foreignPlate = new EngineerCircuitTower(
                    EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                    other, TeamId.RED, 1, platePosition, platePosition
            );
            lane.addTower(foreignPlate);
            require(foreignPlate.pressPlate(lane), "The foreign plate must be physically pressable.");
            require(trap.recentPlateDistance(lane).isEmpty(),
                    "Another owner's plate must not authorize the trap.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Engineer directed circuit GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void doorTauntLastsSixSecondsAndRefreshesWithoutAPlateCycleGap(GameTestHelper context) {
        UUID owner = stableUuid("engineer-door-duration");
        PlayerLane lane = testLane(context, owner);
        GridPosition platePosition = floor(context, 6, 2, 7);
        GridPosition doorPosition = floor(context, 7, 2, 7);
        prepareFloor(context, platePosition, doorPosition);
        EngineerCircuitTower plate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                owner, TeamId.RED, 1, platePosition, platePosition
        );
        EngineerTrapTower door = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 1),
                owner, TeamId.RED, 1, doorPosition, doorPosition
        );
        SemionMonsterEntity monster = null;
        try {
            AreaEffectLaneIndex.register(lane);
            lane.addTower(plate);
            lane.addTower(door);
            monster = spawnMonster(context, lane, "engineer-door-target",
                    Vec3.atCenterOf(new BlockPos(doorPosition.x(), doorPosition.y() + 1, doorPosition.z())));
            SemionTowerEntity doorEntity = (SemionTowerEntity) context.getLevel()
                    .getEntity(door.entityId().orElseThrow());
            lane.markWaveStarted(1);
            require(plate.pressPlate(lane), "The plate must activate both adjacent traps.");
            door.tick(lane);
            require(door.activeTicksRemaining() == EngineerBalance.doorActiveTicks() - 1,
                    "The iron door must latch for six seconds.");
            require(monster.getTarget() == doorEntity, "The active door must taunt nearby monsters.");

            unpowerPlate(context, plate);
            door.tick(lane);
            for (int tick = 0; tick < 97; tick++) {
                door.tick(lane);
            }
            require(door.activeTicksRemaining() > 0 && monster.getTarget() == doorEntity,
                    "The six-second taunt must cover the five-second plate cycle without a gap.");

            require(plate.pressPlate(lane), "The next plate pulse must refresh the door.");
            door.tick(lane);
            require(door.activeTicksRemaining() == EngineerBalance.doorActiveTicks() - 1,
                    "A new rising edge must refresh the door to six seconds.");
            require(lane.removeTower(door), "The door must be removable.");
            require(monster.getTarget() == null, "Selling the door must immediately release its targets.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Engineer door duration GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            if (monster != null) {
                monster.discard();
            }
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void newestConnectedPlateDeterminesDispenserDistance(GameTestHelper context) {
        UUID owner = stableUuid("engineer-newest-plate");
        PlayerLane lane = testLane(context, owner);
        GridPosition trapPosition = floor(context, 7, 2, 7);
        GridPosition nearPlatePosition = floor(context, 6, 2, 7);
        GridPosition farPlatePosition = floor(context, 7, 2, 4);
        GridPosition dustOnePosition = floor(context, 7, 2, 5);
        GridPosition dustTwoPosition = floor(context, 7, 2, 6);
        prepareFloor(context, trapPosition, nearPlatePosition, farPlatePosition, dustOnePosition, dustTwoPosition);

        EngineerTrapTower trap = new EngineerTrapTower(
                EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 1),
                owner, TeamId.RED, 1, trapPosition, trapPosition
        );
        EngineerCircuitTower nearPlate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                owner, TeamId.RED, 1, nearPlatePosition, nearPlatePosition
        );
        EngineerCircuitTower farPlate = new EngineerCircuitTower(
                EngineerTowers.plate(EngineerTowers.PlateKind.STONE),
                owner, TeamId.RED, 1, farPlatePosition, farPlatePosition
        );
        EngineerCircuitTower dustOne = new EngineerCircuitTower(
                EngineerTowers.REDSTONE_DUST, owner, TeamId.RED, 1, dustOnePosition, dustOnePosition
        );
        EngineerCircuitTower dustTwo = new EngineerCircuitTower(
                EngineerTowers.REDSTONE_DUST, owner, TeamId.RED, 1, dustTwoPosition, dustTwoPosition
        );
        lane.addTower(trap);
        lane.addTower(nearPlate);
        lane.addTower(farPlate);
        lane.addTower(dustOne);
        lane.addTower(dustTwo);
        try {
            require(nearPlate.pressPlate(lane), "The near plate must be pressable.");
            require(trap.recentPlateDistance(lane).orElse(-1) == 1,
                    "The initial direct plate must report distance one.");
            context.runAfterDelay(2, () -> {
                try {
                    require(farPlate.pressPlate(lane), "The far plate must be pressable.");
                    require(trap.recentPlateDistance(lane).orElse(-1) == 3,
                            "The newest connected plate must win even when its path is longer.");
                    context.succeed();
                } catch (Throwable failure) {
                    context.fail(Component.literal("Engineer newest plate GameTest failed: "
                            + failure.getClass().getName() + ": " + failure.getMessage()));
                } finally {
                    lane.clearTowers();
                }
            });
        } catch (Throwable failure) {
            lane.clearTowers();
            context.fail(Component.literal("Engineer newest plate setup failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        }
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(14, 6, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Vec3 waypoint = Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(
                1, spawn, List.of(waypoint), boss, BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static GridPosition floor(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void prepareFloor(GameTestHelper context, GridPosition... positions) {
        for (GridPosition position : positions) {
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y(), position.z()),
                    Blocks.STONE.defaultBlockState(), 3
            );
            context.getLevel().setBlock(
                    new BlockPos(position.x(), position.y() + 1, position.z()),
                    Blocks.AIR.defaultBlockState(), 3
            );
        }
    }

    private static SemionMonsterEntity spawnMonster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            Vec3 position
    ) {
        Monster monster = new Monster(
                id, lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                1_000.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "The door target must spawn.");
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static void unpowerPlate(GameTestHelper context, EngineerCircuitTower plate) {
        context.getLevel().setBlock(
                plate.circuitPosition(),
                context.getLevel().getBlockState(plate.circuitPosition())
                        .setValue(BlockStateProperties.POWERED, false),
                3
        );
    }

    private static BlockPos slimePosition(GridPosition position) {
        return new BlockPos(position.x(), position.y() + 1, position.z());
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
