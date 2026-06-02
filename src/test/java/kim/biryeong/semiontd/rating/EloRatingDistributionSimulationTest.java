package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class EloRatingDistributionSimulationTest {
    private static final int PLAYER_COUNT = 100;
    private static final int GAME_COUNT = 1000;
    private static final int PLAYERS_PER_GAME = 5;
    private static final long PLAYER_POOL_SEED = 0x5EED_100L;
    private static final long MATCHMAKING_SEED = 0x5EED_1000L;
    private static final double TRUE_SKILL_MEAN = 1500.0;
    private static final double TRUE_SKILL_STDDEV = 220.0;
    private static final double TRUE_SKILL_MIN = 900.0;
    private static final double TRUE_SKILL_MAX = 2100.0;
    private static final double PERFORMANCE_NOISE_STDDEV = 170.0;

    @Test
    void simulatedHundredPlayerPoolConvergesTowardLatentSkillDistribution() {
        List<SimulatedPlayer> players = createPlayers();
        Map<UUID, SimulatedPlayer> playersById = players.stream()
                .collect(Collectors.toMap(
                        SimulatedPlayer::playerId,
                        Function.identity(),
                        (left, right) -> left,
                        HashMap::new
                ));
        EloRatingCalculator calculator = new EloRatingCalculator();
        Random matchRandom = new Random(MATCHMAKING_SEED);
        long secondPlaceFinishes = 0L;
        long negativeSecondPlaceDeltas = 0L;

        for (int gameIndex = 0; gameIndex < GAME_COUNT; gameIndex++) {
            List<SimulatedPlayer> selected = pickPlayers(playersById, matchRandom);
            List<MatchPerformance> ranked = rankPerformances(selected, matchRandom);
            Map<UUID, Integer> placementByPlayerId = ranked.stream()
                    .collect(Collectors.toMap(performance -> performance.player().playerId(), MatchPerformance::placement));
            List<RatingParticipant> participants = ranked.stream()
                    .map(performance -> {
                        SimulatedPlayer current = playersById.get(performance.player().playerId());
                        return new RatingParticipant(
                                current.playerId(),
                                current.playerName(),
                                performance.teamId(),
                                performance.placement() == 1,
                                performance.placement(),
                                1.0,
                                current.profile()
                        );
                    })
                    .toList();

            RatingMatchResult result = calculator.calculate(new RatingMatchInput(
                    new MatchId(gameIndex + 1L),
                    gameIndex + 1L,
                    participants
            ));

            for (RatingAdjustment adjustment : result.adjustments()) {
                SimulatedPlayer current = playersById.get(adjustment.playerId());
                if (placementByPlayerId.get(adjustment.playerId()) == 2) {
                    secondPlaceFinishes++;
                    if (adjustment.displayEloDelta() < 0) {
                        negativeSecondPlaceDeltas++;
                    }
                }
                playersById.put(adjustment.playerId(), current.withProfile(adjustment.after()));
            }
        }

        List<SimulatedPlayer> finalPlayers = playersById.values().stream()
                .sorted(Comparator.comparing(SimulatedPlayer::playerName))
                .toList();
        DistributionSummary trueSkillSummary = summarize(finalPlayers.stream()
                .map(SimulatedPlayer::trueSkill)
                .toList());
        DistributionSummary finalEloSummary = summarize(finalPlayers.stream()
                .map(player -> player.profile().mu())
                .toList());
        double correlation = pearsonCorrelation(finalPlayers);
        double quartileGap = trueSkillQuartileEloGap(finalPlayers);
        double secondPlaceNegativeRate = (double) negativeSecondPlaceDeltas / secondPlaceFinishes;

        System.out.printf(
                "Simulated ELO distribution after %d players / %d games:%n"
                        + "trueSkill=%s%n"
                        + "finalElo=%s%n"
                        + "correlation(trueSkill, finalMu)=%.3f%n"
                        + "trueSkillQuartileEloGap=%.1f%n"
                        + "secondPlaceNegativeRate=%.3f (%d/%d)%n",
                PLAYER_COUNT,
                GAME_COUNT,
                trueSkillSummary,
                finalEloSummary,
                correlation,
                quartileGap,
                secondPlaceNegativeRate,
                negativeSecondPlaceDeltas,
                secondPlaceFinishes
        );

        assertEquals(PLAYER_COUNT, finalPlayers.size());
        assertEquals(GAME_COUNT * PLAYERS_PER_GAME, finalPlayers.stream()
                .mapToInt(player -> player.profile().gamesPlayed())
                .sum());
        assertEquals(GAME_COUNT, secondPlaceFinishes);
        assertTrue(finalPlayers.stream().allMatch(player -> player.profile().gamesPlayed() > 0));
        assertTrue(finalPlayers.stream().allMatch(player -> Double.isFinite(player.profile().mu())));
        assertTrue(finalPlayers.stream().allMatch(player -> Double.isFinite(player.profile().displayElo())));
        assertTrue(finalEloSummary.min() < finalEloSummary.p10());
        assertTrue(finalEloSummary.p10() < finalEloSummary.median());
        assertTrue(finalEloSummary.median() < finalEloSummary.p90());
        assertTrue(finalEloSummary.p90() < finalEloSummary.max());
        assertTrue(finalEloSummary.stddev() > 0.0);
        assertTrue(Double.isFinite(correlation));
        assertTrue(correlation >= 0.65, "correlation=" + correlation);
        assertTrue(quartileGap >= 120.0, "quartileGap=" + quartileGap);
        assertTrue(secondPlaceNegativeRate <= 0.05, "secondPlaceNegativeRate=" + secondPlaceNegativeRate);
    }

    private static List<SimulatedPlayer> createPlayers() {
        Random random = new Random(PLAYER_POOL_SEED);
        List<SimulatedPlayer> players = new ArrayList<>();
        for (int index = 0; index < PLAYER_COUNT; index++) {
            String name = "player-%03d".formatted(index);
            UUID playerId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            double trueSkill = clamp(
                    TRUE_SKILL_MEAN + random.nextGaussian() * TRUE_SKILL_STDDEV,
                    TRUE_SKILL_MIN,
                    TRUE_SKILL_MAX
            );
            players.add(new SimulatedPlayer(playerId, name, trueSkill, PlayerRatingProfile.initial(playerId, name)));
        }
        return players;
    }

    private static List<SimulatedPlayer> pickPlayers(Map<UUID, SimulatedPlayer> playersById, Random random) {
        List<SimulatedPlayer> shuffled = playersById.values().stream()
                .sorted(Comparator.comparing(SimulatedPlayer::playerName))
                .collect(Collectors.toCollection(ArrayList::new));
        java.util.Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, PLAYERS_PER_GAME));
    }

    private static List<MatchPerformance> rankPerformances(List<SimulatedPlayer> selected, Random random) {
        TeamId[] teams = TeamId.values();
        List<MatchPerformance> performances = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            SimulatedPlayer player = selected.get(index);
            double performance = player.trueSkill() + random.nextGaussian() * PERFORMANCE_NOISE_STDDEV;
            performances.add(new MatchPerformance(player, teams[index], performance, 0));
        }
        performances.sort(Comparator.comparingDouble(MatchPerformance::performance).reversed());

        List<MatchPerformance> ranked = new ArrayList<>();
        for (int index = 0; index < performances.size(); index++) {
            ranked.add(performances.get(index).withPlacement(index + 1));
        }
        return ranked;
    }

    private static DistributionSummary summarize(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = sorted.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2.0))
                .average()
                .orElseThrow();
        return new DistributionSummary(
                sorted.get(0),
                percentile(sorted, 0.10),
                percentile(sorted, 0.25),
                percentile(sorted, 0.50),
                percentile(sorted, 0.75),
                percentile(sorted, 0.90),
                sorted.get(sorted.size() - 1),
                mean,
                Math.sqrt(variance)
        );
    }

    private static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            throw new IllegalArgumentException("values cannot be empty");
        }
        double position = percentile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double weight = position - lower;
        return sortedValues.get(lower) * (1.0 - weight) + sortedValues.get(upper) * weight;
    }

    private static double pearsonCorrelation(List<SimulatedPlayer> players) {
        double trueSkillMean = players.stream().mapToDouble(SimulatedPlayer::trueSkill).average().orElseThrow();
        double muMean = players.stream().mapToDouble(player -> player.profile().mu()).average().orElseThrow();
        double numerator = 0.0;
        double trueSkillSquares = 0.0;
        double muSquares = 0.0;
        for (SimulatedPlayer player : players) {
            double trueSkillOffset = player.trueSkill() - trueSkillMean;
            double muOffset = player.profile().mu() - muMean;
            numerator += trueSkillOffset * muOffset;
            trueSkillSquares += trueSkillOffset * trueSkillOffset;
            muSquares += muOffset * muOffset;
        }
        return numerator / Math.sqrt(trueSkillSquares * muSquares);
    }

    private static double trueSkillQuartileEloGap(List<SimulatedPlayer> players) {
        List<SimulatedPlayer> sortedBySkill = players.stream()
                .sorted(Comparator.comparingDouble(SimulatedPlayer::trueSkill))
                .toList();
        int quartileSize = sortedBySkill.size() / 4;
        double bottomAverage = sortedBySkill.subList(0, quartileSize).stream()
                .mapToDouble(player -> player.profile().mu())
                .average()
                .orElseThrow();
        double topAverage = sortedBySkill.subList(sortedBySkill.size() - quartileSize, sortedBySkill.size()).stream()
                .mapToDouble(player -> player.profile().mu())
                .average()
                .orElseThrow();
        return topAverage - bottomAverage;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record SimulatedPlayer(
            UUID playerId,
            String playerName,
            double trueSkill,
            PlayerRatingProfile profile
    ) {
        SimulatedPlayer withProfile(PlayerRatingProfile nextProfile) {
            return new SimulatedPlayer(playerId, playerName, trueSkill, nextProfile);
        }
    }

    private record MatchPerformance(SimulatedPlayer player, TeamId teamId, double performance, int placement) {
        MatchPerformance withPlacement(int nextPlacement) {
            return new MatchPerformance(player, teamId, performance, nextPlacement);
        }
    }

    private record DistributionSummary(
            double min,
            double p10,
            double p25,
            double median,
            double p75,
            double p90,
            double max,
            double mean,
            double stddev
    ) {
        @Override
        public String toString() {
            return "min=%.1f, p10=%.1f, p25=%.1f, median=%.1f, p75=%.1f, p90=%.1f, max=%.1f, mean=%.1f, stddev=%.1f"
                    .formatted(min, p10, p25, median, p75, p90, max, mean, stddev);
        }
    }
}
