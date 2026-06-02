package kim.biryeong.semiontd.rating;

import java.util.List;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;

public final class RatingContributionCalculator {
    private final RatingConfig config;

    public RatingContributionCalculator(RatingConfig config) {
        this.config = config == null ? RatingConfig.defaultConfig() : config;
    }

    public RatingContributionBreakdown breakdown(RatingMatchInput input, RatingParticipant participant) {
        if (!config.contributionWeightingEnabled() || input == null || participant == null) {
            return RatingContributionBreakdown.neutral();
        }
        List<RatingParticipant> teamParticipants = input.participants().stream()
                .filter(candidate -> candidate.teamId() == participant.teamId())
                .toList();
        List<RatingParticipant> matchParticipants = input.participants();
        PlayerMatchStatsSnapshot stats = participant.stats();
        double defenseScore = defenseScore(stats, teamParticipants);
        double pressureScore = pressureScore(stats, matchParticipants);
        double economyScore = economyScore(stats, teamParticipants);
        double assistScore = assistScore(stats, matchParticipants);
        double rawMultiplier = (defenseScore * config.defenseContributionWeight())
                + (pressureScore * config.pressureContributionWeight())
                + (economyScore * config.economyContributionWeight())
                + (assistScore * config.assistContributionWeight());
        return new RatingContributionBreakdown(
                defenseScore,
                pressureScore,
                economyScore,
                assistScore,
                rawMultiplier,
                clamp(rawMultiplier, config.contributionMultiplierMin(), config.contributionMultiplierMax())
        );
    }

    private static double defenseScore(PlayerMatchStatsSnapshot stats, List<RatingParticipant> teamParticipants) {
        if (teamParticipants.stream().anyMatch(participant -> participant.stats().ownLaneIncomingThreat() > 0.0)) {
            double defenseRate = 1.0;
            if (stats.ownLaneIncomingThreat() > 0.0) {
                defenseRate = 1.0 - Math.min(stats.ownLaneLeakedThreat(), stats.ownLaneIncomingThreat())
                        / stats.ownLaneIncomingThreat();
            }
            double difficultyBonus = stats.ownLaneIncomingThreat() <= 0.0
                    ? 0.0
                    : Math.min(0.10, (stats.incomingIncomeThreat() / stats.ownLaneIncomingThreat()) * 0.15);
            double adjustedDefense = defenseRate + difficultyBonus;
            double teamAverage = teamParticipants.stream()
                    .mapToDouble(participant -> {
                        PlayerMatchStatsSnapshot candidate = participant.stats();
                        if (candidate.ownLaneIncomingThreat() <= 0.0) {
                            return 1.0;
                        }
                        double candidateDefenseRate = 1.0 - Math.min(candidate.ownLaneLeakedThreat(), candidate.ownLaneIncomingThreat())
                                / candidate.ownLaneIncomingThreat();
                        double candidateDifficultyBonus = Math.min(0.10,
                                (candidate.incomingIncomeThreat() / candidate.ownLaneIncomingThreat()) * 0.15);
                        return candidateDefenseRate + candidateDifficultyBonus;
                    })
                    .average()
                    .orElse(1.0);
            return ratio(adjustedDefense, teamAverage);
        }
        return weightedAverage(
                ratio(stats.killMinerals(), averageKillMinerals(teamParticipants)),
                0.70,
                ratio(stats.monsterKills(), averageMonsterKills(teamParticipants)),
                0.30
        );
    }

    private static double pressureScore(PlayerMatchStatsSnapshot stats, List<RatingParticipant> matchParticipants) {
        if (matchParticipants.stream().anyMatch(participant -> participant.stats().sentIncomeThreat() > 0.0)) {
            double value = stats.sentIncomeThreat() + (stats.incomeAttackSuccessThreat() * 0.50);
            double average = matchParticipants.stream()
                    .mapToDouble(participant -> participant.stats().sentIncomeThreat()
                            + (participant.stats().incomeAttackSuccessThreat() * 0.50))
                    .average()
                    .orElse(0.0);
            return ratio(value, average);
        }
        return ratio(stats.summonedMonsters(), averageSummonedMonsters(matchParticipants));
    }

    private static double economyScore(PlayerMatchStatsSnapshot stats, List<RatingParticipant> teamParticipants) {
        if (teamParticipants.stream().anyMatch(participant -> participant.stats().hasAttributionStats())) {
            double value = stats.ownLaneDiamondGain()
                    + (stats.assistClearDiamondGain() * 0.85)
                    + (stats.incomeGenerated() * 0.50);
            double average = teamParticipants.stream()
                    .mapToDouble(participant -> participant.stats().ownLaneDiamondGain()
                            + (participant.stats().assistClearDiamondGain() * 0.85)
                            + (participant.stats().incomeGenerated() * 0.50))
                    .average()
                    .orElse(0.0);
            return ratio(value, average);
        }
        return ratio(stats.finalIncome(), averageFinalIncome(teamParticipants));
    }

    private static double assistScore(PlayerMatchStatsSnapshot stats, List<RatingParticipant> matchParticipants) {
        double averageAssistThreat = matchParticipants.stream()
                .mapToDouble(participant -> participant.stats().assistClearThreat())
                .average()
                .orElse(0.0);
        return ratio(stats.assistClearThreat(), averageAssistThreat);
    }

    private static double weightedAverage(double firstValue, double firstWeight, double secondValue, double secondWeight) {
        return (firstValue * firstWeight) + (secondValue * secondWeight);
    }

    private static double ratio(double value, double average) {
        if (!Double.isFinite(value) || !Double.isFinite(average) || average <= 0.0) {
            return 1.0;
        }
        if (value <= 0.0) {
            return 0.0;
        }
        return value / average;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double averageKillMinerals(List<RatingParticipant> participants) {
        return participants.stream().mapToLong(participant -> participant.stats().killMinerals()).average().orElse(0.0);
    }

    private static double averageMonsterKills(List<RatingParticipant> participants) {
        return participants.stream().mapToLong(participant -> participant.stats().monsterKills()).average().orElse(0.0);
    }

    private static double averageSummonedMonsters(List<RatingParticipant> participants) {
        return participants.stream().mapToLong(participant -> participant.stats().summonedMonsters()).average().orElse(0.0);
    }

    private static double averageFinalIncome(List<RatingParticipant> participants) {
        return participants.stream().mapToLong(participant -> participant.stats().finalIncome()).average().orElse(0.0);
    }
}
