package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public abstract class GameTestParticipantFixture implements CustomTestMethodInvoker {
    protected static kim.biryeong.semiontd.map.GameArena testArena(GameTestHelper context) {
        return SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(net.minecraft.core.BlockPos.ZERO));
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        reloadDefaultIncomeSummons();
        method.invoke(this, context);
    }

    protected static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId) {
        return startedSinglePlayerGame(context, playerId, teamId, null);
    }

    protected static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId, net.minecraft.resources.Identifier jobId) {
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

    protected static SemionGame startedTwoPlayerGame(GameTestHelper context, UUID redId, UUID blueId) {
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

    protected static void reloadDefaultIncomeSummons() {
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
    }

    protected static void tickGame(SemionGame game, MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.tick(server);
        }
    }

    protected static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set field " + fieldName + " on " + target.getClass().getName(), exception);
        }
    }

    protected static PlayerLane redLane(SemionGame game, int laneId) {
        return lane(game, TeamId.RED, laneId);
    }

    protected static PlayerLane lane(SemionGame game, TeamId teamId, int laneId) {
        return game.teams().get(teamId).laneGroup().lane(laneId).orElseThrow();
    }

    protected static BlockPos towerPlacementPos(PlayerLane lane) {
        return BlockPos.containing(lane.laneLayout().positionAt(0.35));
    }

    protected static BlockPos nearbyTowerPlacementPos(PlayerLane lane, BlockPos origin) {
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

    protected static SemionMonsterEntity spawnSummonEntity(
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

    protected static Monster deathStackTestMonster(String id, Optional<TeamId> senderTeam, TeamId targetTeam, int targetLaneId) {
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

    protected static SemionMonsterEntity spawnRoleMonsterEntity(
            GameTestHelper context,
            String id,
            Optional<TeamId> senderTeam,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            List<SummonRole> roles
    ) {
        return spawnRoleMonsterEntity(
                context,
                id,
                senderTeam,
                targetTeam,
                targetLaneId,
                position,
                maxHealth,
                0.0,
                0.0,
                roles
        );
    }

    protected static SemionMonsterEntity spawnRoleMonsterEntity(
            GameTestHelper context,
            String id,
            Optional<TeamId> senderTeam,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            double armor,
            double resistance,
            List<SummonRole> roles
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                senderTeam,
                maxHealth,
                armor,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                resistance,
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

    protected static SemionTowerEntity spawnTowerEntity(
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

    protected static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    protected static boolean assertPresent(
            GameTestHelper context,
            Optional<?> optional,
            String message
    ) {
        return assertTrue(context, optional.isPresent(), message);
    }

    protected static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    protected static boolean assertClose(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }

    protected static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }
}
