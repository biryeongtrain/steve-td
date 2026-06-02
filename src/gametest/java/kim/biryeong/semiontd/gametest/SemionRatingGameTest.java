package kim.biryeong.semiontd.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.map.GameArenaLoader;
import kim.biryeong.semiontd.persistence.SQLiteRatingRepository;
import kim.biryeong.semiontd.rating.EloRatingCalculator;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchInput;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingParticipant;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionRatingGameTest {
    @GameTest
    public void eloCalculatorKeepsRatingChangesFinite(GameTestHelper context) {
        UUID winnerId = UUID.nameUUIDFromBytes("gametest-rating-winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("gametest-rating-loser".getBytes());
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(101L),
                1000L,
                List.of(
                        new RatingParticipant(
                                winnerId,
                                "winner",
                                TeamId.RED,
                                true,
                                PlayerRatingProfile.initial(winnerId, "winner")
                        ),
                        new RatingParticipant(
                                loserId,
                                "loser",
                                TeamId.BLUE,
                                false,
                                PlayerRatingProfile.initial(loserId, "loser")
                        )
                )
        ));

        if (result.adjustments().size() != 2) {
            throw new AssertionError("Expected two rating adjustments");
        }
        result.adjustments().forEach(adjustment -> {
            if (!Double.isFinite(adjustment.after().mu()) || !Double.isFinite(adjustment.after().sigma())) {
                throw new AssertionError("Rating values must stay finite");
            }
        });
        context.succeed();
    }

    @GameTest
    public void matchResultCanFeedRatingCalculatorWithoutSpectators(GameTestHelper context) {
        UUID winnerId = UUID.nameUUIDFromBytes("gametest-match-winner".getBytes());
        UUID spectatorId = UUID.nameUUIDFromBytes("gametest-match-spectator".getBytes());
        MatchResult matchResult = new MatchResult(
                new MatchId(102L),
                1L,
                2L,
                List.of(
                        new MatchParticipantResult(winnerId, "winner", TeamId.RED, true),
                        new MatchParticipantResult(spectatorId, "spectator", TeamId.BLUE, false)
                ),
                Set.of(spectatorId),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1L, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1L, 0.0)
                ),
                10
        );
        List<RatingParticipant> ratingParticipants = matchResult.participants().stream()
                .filter(participant -> !matchResult.spectatorIds().contains(participant.playerId()))
                .map(participant -> new RatingParticipant(
                        participant.playerId(),
                        participant.playerName(),
                        participant.teamId(),
                        participant.winner(),
                        PlayerRatingProfile.initial(participant.playerId(), participant.playerName())
                ))
                .toList();

        RatingMatchResult ratingResult = new EloRatingCalculator().calculate(new RatingMatchInput(
                matchResult.matchId(),
                matchResult.endedAtEpochMillis(),
                ratingParticipants
        ));

        if (ratingResult.adjustments().size() != 1) {
            throw new AssertionError("Spectator must not receive a rating adjustment");
        }
        context.succeed();
    }

    @GameTest
    public void contributionStatsAffectRatingDeltaInGameTestRuntime(GameTestHelper context) {
        UUID strongWinnerId = UUID.nameUUIDFromBytes("gametest-strong-winner".getBytes());
        UUID weakWinnerId = UUID.nameUUIDFromBytes("gametest-weak-winner".getBytes());
        UUID strongLoserId = UUID.nameUUIDFromBytes("gametest-strong-loser".getBytes());
        UUID weakLoserId = UUID.nameUUIDFromBytes("gametest-weak-loser".getBytes());
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(104L),
                1000L,
                List.of(
                        new RatingParticipant(strongWinnerId, "strongWinner", TeamId.RED, true,
                                PlayerRatingProfile.initial(strongWinnerId, "strongWinner"),
                                new PlayerMatchStatsSnapshot(20, 200, 4, 80)),
                        new RatingParticipant(weakWinnerId, "weakWinner", TeamId.RED, true,
                                PlayerRatingProfile.initial(weakWinnerId, "weakWinner"),
                                new PlayerMatchStatsSnapshot(5, 50, 1, 20)),
                        new RatingParticipant(strongLoserId, "strongLoser", TeamId.BLUE, false,
                                PlayerRatingProfile.initial(strongLoserId, "strongLoser"),
                                new PlayerMatchStatsSnapshot(20, 200, 4, 80)),
                        new RatingParticipant(weakLoserId, "weakLoser", TeamId.BLUE, false,
                                PlayerRatingProfile.initial(weakLoserId, "weakLoser"),
                                new PlayerMatchStatsSnapshot(5, 50, 1, 20))
                )
        ));

        if (result.adjustments().get(0).displayEloDelta() <= result.adjustments().get(1).displayEloDelta()) {
            throw new AssertionError("Stronger winner contribution should earn a larger delta");
        }
        if (Math.abs(result.adjustments().get(2).displayEloDelta()) >= Math.abs(result.adjustments().get(3).displayEloDelta())) {
            throw new AssertionError("Stronger loser contribution should reduce the penalty");
        }
        context.succeed();
    }

    @GameTest
    public void laneAttributionStatsAreRecordedThroughPlayerLaneRuntime(GameTestHelper context) {
        kim.biryeong.semiontd.map.GameArena arena = null;
        try {
            arena = GameArenaLoader.load(context.getLevel().getServer(), MapConfig.defaultConfig());
            UUID laneOwnerId = UUID.nameUUIDFromBytes("gametest-rating-lane-owner".getBytes());
            UUID senderId = UUID.nameUUIDFromBytes("gametest-rating-income-sender".getBytes());
            PlayerLane lane = new PlayerLane(
                    TeamId.RED,
                    1,
                    laneOwnerId,
                    arena.teamArena(TeamId.RED).orElseThrow().world(),
                    arena.teamArena(TeamId.RED).orElseThrow().layout().lane(1).orElseThrow()
            );
            SemionPlayer laneOwner = new SemionPlayer(
                    laneOwnerId,
                    "laneOwner",
                    TeamId.RED,
                    1,
                    new PlayerEconomy(EconomyConfig.defaultConfig())
            );
            SemionPlayer sender = new SemionPlayer(
                    senderId,
                    "incomeSender",
                    TeamId.BLUE,
                    1,
                    new PlayerEconomy(EconomyConfig.defaultConfig())
            );
            Map<UUID, SemionPlayer> players = Map.of(laneOwnerId, laneOwner, senderId, sender);
            Monster incomeMonster = new Monster(
                    "gametest-income-unit",
                    TeamId.RED,
                    1,
                    Optional.of(senderId),
                    Optional.of(TeamId.BLUE),
                    20.0,
                    0.0,
                    5.0,
                    AttackKind.MELEE,
                    "minecraft:zombie",
                    3L
            );

            lane.enqueueSummonedMonster(incomeMonster);
            lane.tick(context.getLevel().getServer(), null, players);

            PlayerMatchStatsSnapshot afterSpawn = laneOwner.matchStats().snapshot(laneOwner.economy().income());
            double threat = incomeMonster.attributionThreat();
            if (Math.abs(afterSpawn.ownLaneIncomingThreat() - threat) > 0.0001) {
                throw new AssertionError("Lane owner should record incoming income-unit threat through PlayerLane runtime");
            }
            if (Math.abs(afterSpawn.incomingIncomeThreat() - threat) > 0.0001) {
                throw new AssertionError("Lane owner should record incoming income-specific threat through PlayerLane runtime");
            }

            if (!(lane.arenaWorld().getEntity(incomeMonster.minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity)) {
                throw new AssertionError("Spawned income unit should have a runtime monster entity");
            }
            var leakPosition = lane.laneLayout().positionAt(0.95);
            monsterEntity.teleportTo(leakPosition.x, leakPosition.y, leakPosition.z);
            lane.tick(context.getLevel().getServer(), null, players);

            PlayerMatchStatsSnapshot afterLeak = laneOwner.matchStats().snapshot(laneOwner.economy().income());
            PlayerMatchStatsSnapshot senderStats = sender.matchStats().snapshot(sender.economy().income());
            if (Math.abs(afterLeak.ownLaneLeakedThreat() - threat) > 0.0001) {
                throw new AssertionError("Lane owner should record leaked threat through PlayerLane runtime");
            }
            if (Math.abs(senderStats.incomeAttackSuccessThreat() - threat) > 0.0001) {
                throw new AssertionError("Income unit sender should record successful pressure through PlayerLane runtime");
            }
            context.succeed();
        } catch (Exception exception) {
            throw new AssertionError("Lane attribution GameTest failed", exception);
        } finally {
            if (arena != null) {
                arena.unload();
            }
        }
    }

    @GameTest
    public void sqliteRatingRepositoryPersistsProfileInGameTestRuntime(GameTestHelper context) {
        try {
            Path database = Files.createTempFile("semion-rating-gametest", ".db");
            UUID playerId = UUID.nameUUIDFromBytes("gametest-sqlite-rating-player".getBytes());
            PlayerRatingProfile profile = new PlayerRatingProfile(
                    playerId,
                    "sqlitePlayer",
                    kim.biryeong.semiontd.rating.RatingSystemId.ELO,
                    1,
                    1,
                    1,
                    0,
                    1516.0,
                    350.0,
                    1516,
                    new MatchId(103L),
                    1000L
            );
            new SQLiteRatingRepository(database).saveProfile(playerId, profile);
            PlayerRatingProfile loaded = new SQLiteRatingRepository(database).findProfile(playerId).orElseThrow();
            Files.deleteIfExists(database);
            if (loaded.displayElo() != 1516 || loaded.gamesPlayed() != 1) {
                throw new AssertionError("SQLite rating repository did not preserve profile fields");
            }
            context.succeed();
        } catch (Exception exception) {
            throw new AssertionError("SQLite rating repository failed in GameTest runtime", exception);
        }
    }

    @GameTest
    public void ratingTopCommandRunsWithEmptyLeaderboard(GameTestHelper context) {
        try {
            int result = context.getLevel().getServer().getCommands().getDispatcher().execute(
                    "semiontd rating top",
                    context.getLevel().getServer().createCommandSourceStack()
            );
            if (result < 1) {
                throw new AssertionError("/semiontd rating top should succeed with an empty leaderboard");
            }
            context.succeed();
        } catch (Exception exception) {
            throw new AssertionError("/semiontd rating top failed", exception);
        }
    }
}
