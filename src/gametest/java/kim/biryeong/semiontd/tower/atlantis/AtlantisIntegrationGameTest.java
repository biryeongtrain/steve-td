package kim.biryeong.semiontd.tower.atlantis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Runtime behaviour that the unit suite cannot reach: pressure zones are deployed onto the lane
 * path rather than around the turtle, and the deployed set tracks the turtle roster.
 */
public final class AtlantisIntegrationGameTest {
    private static final UUID OWNER = stableUuid("atlantis-zone-owner");
    private static final UUID OTHER = stableUuid("atlantis-other-owner");

    @GameTest
    public void turtlePlacementDeploysZonesAheadOnThePathNotAroundTheTower(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);
            GridPosition turtlePos = position(context, 3, 2, 8);
            AtlantisTower turtle = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, turtlePos);
            lane.addTower(turtle);

            List<PressureZone> zones = AtlantisStates.zones(OWNER);
            require(zones.size() == 3, "T3 turtle should deploy three zones, got " + zones.size());

            Vec3 towerCentre = new Vec3(turtlePos.x() + 0.5, turtlePos.y(), turtlePos.z() + 0.5);
            double turtleProgress = lane.laneLayout().progressAt(towerCentre);
            for (PressureZone zone : zones) {
                double zoneProgress = lane.laneLayout().progressAt(zone.center());
                // Monsters walk 0 -> 1, so the ground they have yet to cross is the lower side.
                // Zones laid towards 1 would sit behind the wave and never be used.
                require(zoneProgress <= turtleProgress + 1.0E-6,
                        "Zone must cover the approach, not the ground behind the turtle: zone "
                                + zoneProgress + " vs turtle " + turtleProgress);
                require(zone.ownerPosition().equals(turtlePos),
                        "Zone must record the deploying turtle position.");
            }

            double first = lane.laneLayout().progressAt(zones.get(0).center());
            double second = lane.laneLayout().progressAt(zones.get(1).center());
            require(second < first, "Zones must step back along the approach.");
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void zoneCapacityFollowsTurtleTiersAndStopsAtTheGlobalCap(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);

            AtlantisTower t1 = atlantisTower(AtlantisTowers.TURTLE_T1, OWNER, position(context, 2, 2, 8));
            lane.addTower(t1);
            require(AtlantisStates.zoneCount(OWNER) == 1,
                    "One T1 turtle should hold one zone, got " + AtlantisStates.zoneCount(OWNER));

            AtlantisTower t3 = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 3, 2, 8));
            lane.addTower(t3);
            require(AtlantisStates.zoneCount(OWNER) == 4,
                    "T1 + T3 should hold four zones, got " + AtlantisStates.zoneCount(OWNER));

            AtlantisTower extraA = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 4, 2, 8));
            AtlantisTower extraB = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 5, 2, 8));
            lane.addTower(extraA);
            lane.addTower(extraB);
            require(AtlantisStates.zoneCount(OWNER) == AtlantisBalance.maxZoneCount(),
                    "Zone count must stop at maxZoneCount, got " + AtlantisStates.zoneCount(OWNER));
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void removingATurtleReclaimsItsZones(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);
            AtlantisTower keep = atlantisTower(AtlantisTowers.TURTLE_T1, OWNER, position(context, 2, 2, 8));
            AtlantisTower drop = atlantisTower(AtlantisTowers.TURTLE_T2, OWNER, position(context, 3, 2, 8));
            lane.addTower(keep);
            lane.addTower(drop);
            require(AtlantisStates.zoneCount(OWNER) == 3,
                    "T1 + T2 should hold three zones, got " + AtlantisStates.zoneCount(OWNER));

            lane.removeTower(drop);
            require(AtlantisStates.zoneCount(OWNER) == 1,
                    "Removing the T2 turtle should leave one zone, got " + AtlantisStates.zoneCount(OWNER));

            lane.removeTower(keep);
            require(AtlantisStates.zoneCount(OWNER) == 0,
                    "Removing every turtle should clear the zones.");
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void zonesAreScopedToTheirOwningPlayer(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);
            AtlantisTower mine = atlantisTower(AtlantisTowers.TURTLE_T2, OWNER, position(context, 2, 2, 8));
            AtlantisTower theirs = atlantisTower(AtlantisTowers.TURTLE_T3, OTHER, position(context, 4, 2, 8));
            lane.addTower(mine);
            lane.addTower(theirs);

            require(AtlantisStates.zoneCount(OWNER) == 2,
                    "Owner should only count their own turtle, got " + AtlantisStates.zoneCount(OWNER));
            require(AtlantisStates.zoneCount(OTHER) == 3,
                    "Other player should own their own zones, got " + AtlantisStates.zoneCount(OTHER));

            AtlantisStates.clear(OWNER);
            require(AtlantisStates.zoneCount(OWNER) == 0, "Clearing the owner must drop their zones.");
            require(AtlantisStates.zoneCount(OTHER) == 3,
                    "Clearing one player must not touch another player's zones.");
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void turtleAtTheStartOfThePathSkipsZonesInsteadOfClampingThem(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);
            // Sit the turtle on the spawn: there is no approach left in front of it to cover.
            GridPosition startOfPath = GridPosition.from(BlockPos.containing(lane.laneLayout().spawn()));
            AtlantisTower turtle = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, startOfPath);
            lane.addTower(turtle);

            require(AtlantisStates.zoneCount(OWNER) == 0,
                    "A turtle with no approach in front of it must deploy nothing, got "
                            + AtlantisStates.zoneCount(OWNER));
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void finalDefenseRelaysZonesOntoTheApproachKeepingTheSameCount(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        PlayerLane lane = testLane(context, OWNER, 1, 0);
        PlayerLane finalDefenseLane = testFinalDefenseLane(context);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        group.addLane(finalDefenseLane);
        try {
            AtlantisTower turtle = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 3, 2, 8));
            lane.addTower(turtle);
            int before = AtlantisStates.zoneCount(OWNER);
            require(before == 3, "T3 turtle should hold three zones before final defense, got " + before);

            turtle.moveToFinalDefense(lane, position(context, 7, 2, 11));

            List<PressureZone> zones = AtlantisStates.zones(OWNER);
            require(zones.size() == before,
                    "Final defense must keep the same zone count, got " + zones.size() + " from " + before);

            double line = kim.biryeong.semiontd.entity.monster.Monster.FINAL_DEFENSE_PROGRESS;
            for (PressureZone zone : zones) {
                double progress = finalDefenseLane.laneLayout().progressAt(zone.center());
                require(progress <= line + 0.001,
                        "Final defense zones must sit on the approach to the line, got " + progress);
                require(progress > 0.0, "Final defense zones must stay on the path, got " + progress);
            }
            double leading = finalDefenseLane.laneLayout().progressAt(zones.get(0).center());
            require(Math.abs(leading - line) < 0.001,
                    "The leading zone should sit on the final defense line, got " + leading);
            Vec3 boss = finalDefenseLane.laneLayout().bossPosition();
            require(zones.getLast().center().distanceToSqr(boss) > zones.getFirst().center().distanceToSqr(boss),
                    "Final defense zones must extend away from the boss along lane 5.");

            // The round reset clears the final defense flag, so zones return to the forward layout.
            turtle.resetForRound(lane);
            List<PressureZone> afterReset = AtlantisStates.zones(OWNER);
            require(afterReset.size() == before,
                    "Round reset should keep the zone count, got " + afterReset.size());
            double resetLeading = lane.laneLayout().progressAt(afterReset.get(0).center());
            require(resetLeading < line,
                    "After the reset zones must return ahead of the turtle, got " + resetLeading);
            context.succeed();
        } finally {
            group.closeRuntime();
            AtlantisStates.clearAll();
        }
    }

    /**
     * The turtle tooltip promises damage reduction to allies standing in the zone. Nothing else
     * reads {@link PressureZone#allyDamageReduction()}, so without this the promise is silent.
     */
    @GameTest
    public void zonesShieldFriendlyTowersStandingInsideThem(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        try {
            PlayerLane lane = testLane(context, OWNER, 1, 0);
            AtlantisTower turtle = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 3, 2, 8));
            lane.addTower(turtle);

            List<PressureZone> zones = AtlantisStates.zones(OWNER);
            require(!zones.isEmpty(), "The turtle must deploy a zone before this test means anything.");
            PressureZone zone = zones.get(0);

            // Stand a dolphin on the zone centre, and another one well outside it.
            AtlantisTower inside = atlantisTower(
                    AtlantisTowers.DOLPHIN_T1, OWNER, GridPosition.from(BlockPos.containing(zone.center())));
            // Well clear of the wall: zones sit on the path at x=5 with radius 4.
            AtlantisTower outside = atlantisTower(AtlantisTowers.DOLPHIN_T1, OWNER, position(context, 10, 2, 13));
            lane.addTower(inside);
            lane.addTower(outside);

            turtle.tick(lane);

            double expected = TowerBalanceRuntime.ability(AtlantisTowers.TURTLE_T3.id(), "zoneAllyDamageReduction", 0.0);
            require(expected > 0.0, "The T3 turtle must define a non-zero zoneAllyDamageReduction.");
            require(Math.abs(reduction(lane, inside) - expected) < 1.0E-6,
                    "A tower inside the zone must receive the reduction, got " + reduction(lane, inside)
                            + " expected " + expected);
            require(reduction(lane, outside) == 0.0,
                    "A tower outside the zone must not be shielded, got " + reduction(lane, outside));
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void destroyedTurtleDropsZonesUntilTheNextRoundReset(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisStates.clearAll();
        PlayerLane lane = testLane(context, OWNER, 1, 0);
        AtlantisTower turtle = atlantisTower(AtlantisTowers.TURTLE_T3, OWNER, position(context, 3, 2, 8));
        try {
            lane.addTower(turtle);
            require(AtlantisStates.zoneCount(OWNER) == 3, "A living T3 turtle must deploy three zones.");

            require(lane.killTower(turtle), "The turtle must be killable through the lane lifecycle.");
            require(AtlantisStates.zoneCount(OWNER) == 0,
                    "A destroyed turtle must stop contributing pressure zones.");

            lane.resetForRound();
            require(AtlantisStates.zoneCount(OWNER) == 3,
                    "Round reset must revive the turtle and rebuild its zones.");
            context.succeed();
        } finally {
            AtlantisStates.clearAll();
        }
    }

    @GameTest
    public void pressureBurstDamagesItsCarrierAndNearbyTargetsAsMagic(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisPressure.clearAll();
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, OWNER, 1, 0);
        group.addLane(lane);
        try {
            AtlantisTower dolphin = atlantisTower(
                    AtlantisTowers.DOLPHIN_T3, OWNER, position(context, 5, 2, 8));
            lane.addTower(dolphin);
            Monster carrier = spawnMonster(context, lane, "atlantis-carrier", position(context, 5, 2, 7));
            Monster nearby = spawnMonster(context, lane, "atlantis-nearby", position(context, 7, 2, 7));
            SemionMonsterEntity carrierEntity = entity(context, carrier);
            SemionTowerEntity dolphinEntity = towerEntity(context, dolphin);

            for (int hit = 0; hit < 4; hit++) {
                dolphin.onAttackResolved(dolphinEntity, carrierEntity, 1.0, 1.0, 1.0, false);
            }

            requireClose(904.0, carrier.health(), "The pressure carrier must be included in the burst.");
            requireClose(904.0, nearby.health(), "A nearby target must take the same pressure burst.");
            requireClose(192.0, dolphin.roundMagicDamageDealt(),
                    "Both pressure hits must be attributed as magic damage.");
            require(OWNER.equals(carrier.lastHitPlayerId().orElse(null))
                            && carrier.lastHitSourceKind() == KillSourceKind.TOWER,
                    "The carrier must retain tower last-hit attribution.");
            require(AtlantisPressure.stacks(OWNER, carrierEntity.getUUID()) == 0,
                    "A released pressure entry must be consumed exactly once.");
            context.succeed();
        } finally {
            group.closeRuntime();
            AtlantisPressure.clearAll();
        }
    }

    @GameTest
    public void anotherTowerKillingTheCarrierStillTriggersPressure(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisPressure.clearAll();
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        PlayerLane lane = testLane(context, OWNER, 1, 0);
        group.addLane(lane);
        try {
            AtlantisTower dolphin = atlantisTower(
                    AtlantisTowers.DOLPHIN_T3, OWNER, position(context, 5, 2, 8));
            lane.addTower(dolphin);
            Monster carrier = spawnMonster(context, lane, "atlantis-death-carrier", position(context, 5, 2, 7));
            Monster nearby = spawnMonster(context, lane, "atlantis-death-nearby", position(context, 7, 2, 7));
            UUID carrierId = entity(context, carrier).getUUID();
            AtlantisPressure.addStacks(
                    carrierId, OWNER, dolphin.originalPosition(), 5, 40.0, 10, 100);

            carrier.damage(Double.MAX_VALUE);
            lane.tick(context.getLevel().getServer());

            requireClose(952.0, nearby.health(),
                    "A different tower's kill must release this dolphin's stored pressure.");
            requireClose(48.0, dolphin.roundMagicDamageDealt(),
                    "Death-triggered splash must remain attributed as magic damage.");
            require(AtlantisPressure.stacks(OWNER, carrierId) == 0,
                    "The later player-lane death notification must not burst the same entry twice.");
            context.succeed();
        } finally {
            group.closeRuntime();
            AtlantisPressure.clearAll();
        }
    }

    private static double reduction(PlayerLane lane, AtlantisTower tower) {
        if (tower.entityId().isEmpty()
                || !(lane.arenaWorld().getEntity(tower.entityId().getAsInt())
                instanceof kim.biryeong.semiontd.entity.tower.SemionTowerEntity entity)) {
            throw new AssertionError("Placed tower should have spawned an entity: " + tower.type().id());
        }
        return entity.activeTimedEffectMagnitude(
                kim.biryeong.semiontd.effect.TimedEffectType.TOWER_DAMAGE_REDUCTION);
    }

    private static Monster spawnMonster(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            GridPosition position
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
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return monster;
    }

    private static SemionMonsterEntity entity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, AtlantisTower tower) {
        if (tower.entityId().isEmpty()
                || !(context.getLevel().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity entity)) {
            throw new AssertionError("Placed Atlantis tower did not spawn its runtime entity.");
        }
        return entity;
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-6) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual);
        }
    }

    private static AtlantisTower atlantisTower(
            kim.biryeong.semiontd.tower.TowerType type,
            UUID owner,
            GridPosition position
    ) {
        return new AtlantisTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, position);
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner, int laneId, int xOffset) {
        BlockPos min = context.absolutePos(new BlockPos(xOffset, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(xOffset + 10, 5, 14));
        // The approach runs well behind the structure so the lane is long enough to lay a wall of
        // zones at the configured block spacing. Towers still sit inside the structure; only the
        // path geometry extends, and positionAt/progressAt are pure coordinate maths.
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(xOffset + 1, 2, -25)));
        Vec3 waypoint = Vec3.atCenterOf(context.absolutePos(new BlockPos(xOffset + 5, 2, 0)));
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

    private static PlayerLane testFinalDefenseLane(GameTestHelper context) {
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 40)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(
                5,
                spawn,
                BlockBounds.of(BlockPos.containing(spawn), BlockPos.containing(spawn)),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 20)))),
                boss,
                BlockBounds.of(
                        context.absolutePos(new BlockPos(0, 1, 0)),
                        context.absolutePos(new BlockPos(10, 5, 40))
                ),
                List.of(position(context, 7, 2, 11)),
                0
        );
        return new PlayerLane(TeamId.RED, 5, OTHER, context.getLevel(), layout);
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
}
