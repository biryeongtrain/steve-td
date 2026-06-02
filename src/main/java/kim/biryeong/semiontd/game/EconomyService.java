package kim.biryeong.semiontd.game;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;

public final class EconomyService {
    private EconomyConfig economyConfig;
    private final SemionGame game;

    public EconomyService(EconomyConfig economyConfig) {
        this(economyConfig, null);
    }

    public EconomyService(EconomyConfig economyConfig, SemionGame game) {
        this.economyConfig = economyConfig;
        this.game = game;
    }

    public EconomyConfig economyConfig() {
        return economyConfig;
    }

    public void configure(EconomyConfig economyConfig) {
        this.economyConfig = economyConfig;
    }

    public void tickEmerald(Collection<SemionPlayer> players, Map<TeamId, SemionTeam> teams, int currentRound) {
        long emeraldCap = economyConfig.emeraldCapForRound(currentRound);
        for (SemionPlayer player : players) {
            if (isEconomyEligible(player, teams)) {
                player.economy().addEmerald(player.economy().emeraldPerSec(), emeraldCap);
            }
        }
    }

    public void tickGas(Collection<SemionPlayer> players, Map<TeamId, SemionTeam> teams, int currentRound) {
        tickEmerald(players, teams, currentRound);
    }

    public void payRoundIncome(Collection<SemionPlayer> players, Map<TeamId, SemionTeam> teams) {
        for (SemionPlayer player : players) {
            if (isEconomyEligible(player, teams)) {
                player.economy().payIncome();
            }
        }
    }

    public boolean upgradeGasProduction(SemionPlayer player, SemionTeam team) {
        if (player == null || team == null || team.eliminated()) {
            return false;
        }
        return player.economy().upgradeGasProduction(economyConfig.gasProduction());
    }

    public boolean spendForSummon(SemionPlayer player, SummonMonsterType type) {
        return player != null && type != null && spendForSummon(player, type.gasCost());
    }

    public boolean spendForSummon(SemionPlayer player, long gasCost) {
        return player != null && player.economy().spendGas(Math.max(0, gasCost));
    }

    public void refundSummon(SemionPlayer player, SummonMonsterType type, int currentRound) {
        if (player == null || type == null) {
            return;
        }
        refundSummon(player, type.gasCost(), currentRound);
    }

    public void refundSummon(SemionPlayer player, long gasCost, int currentRound) {
        if (player == null) {
            return;
        }
        player.economy().addEmerald(Math.max(0, gasCost), economyConfig.emeraldCapForRound(currentRound));
    }

    public void applySummonIncome(SemionPlayer player, SummonMonsterType type) {
        if (player == null || type == null) {
            return;
        }
        applySummonIncome(player, type.incomeGain());
    }

    public void applySummonIncome(SemionPlayer player, long incomeGain) {
        if (player == null) {
            return;
        }
        player.economy().addIncome(Math.max(0, incomeGain));
    }

    public void awardMonsterKillReward(Monster monster, Map<UUID, SemionPlayer> players) {
        if (monster == null || monster.rewardGranted() || monster.mineralReward() <= 0) {
            return;
        }
        if (monster.lastHitSourceKind() != KillSourceKind.TOWER && monster.lastHitSourceKind() != KillSourceKind.DEFENDER) {
            return;
        }

        Optional<UUID> killerId = monster.lastHitPlayerId();
        if (killerId.isEmpty()) {
            return;
        }

        SemionPlayer player = players.get(killerId.get());
        if (player == null) {
            return;
        }

        JobContext jobContext = game == null ? null : new JobContext(game, player);
        long reward = jobContext == null
                ? monster.mineralReward()
                : player.job()
                        .map(job -> Math.max(0, job.modifyKillMineralReward(jobContext, monster, monster.mineralReward())))
                        .orElse(monster.mineralReward());
        player.economy().addDiamond(reward);
        if (player.teamId() == monster.targetTeam() && player.laneId() == monster.targetLaneId()) {
            player.matchStats().recordOwnLaneMonsterKill(reward, monster.attributionThreat());
        } else {
            player.matchStats().recordAssistMonsterKill(reward, monster.attributionThreat());
        }
        if (jobContext != null) {
            player.job().ifPresent(job -> job.onMonsterKilled(jobContext, monster, reward));
        }
        monster.markRewardGranted();
    }

    private boolean isEconomyEligible(SemionPlayer player, Map<TeamId, SemionTeam> teams) {
        SemionTeam team = teams.get(player.teamId());
        return team != null && team.active() && !team.eliminated();
    }
}
