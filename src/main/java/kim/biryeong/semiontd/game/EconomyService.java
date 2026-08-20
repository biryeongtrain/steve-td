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
        long multiplier = economyConfig.emeraldIncomeMultiplierForRound(currentRound);
        for (SemionPlayer player : players) {
            if (isEconomyEligible(player, teams)) {
                player.economy().addEmerald(player.economy().emeraldPerSec() * multiplier, emeraldCap);
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

    public boolean transferDiamond(SemionPlayer sender, SemionPlayer receiver, long amount) {
        long boundedAmount = Math.max(0, amount);
        if (sender == null || receiver == null || boundedAmount <= 0) {
            return false;
        }
        if (!sender.economy().spendDiamond(boundedAmount)) {
            return false;
        }
        receiver.economy().addDiamond(boundedAmount);
        return true;
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
        long finalReward = adjustedKillReward(player, monster, reward);
        long ownerCut = laneOwnerCut(player, monster, finalReward);
        // 주인을 못 찾으면(탈락했거나 나갔거나) 잡은 사람이 다 가집니다. 갈 곳 없는 몫을 그냥
        // 태우면 남의 레인을 도와줄 이유가 사라집니다.
        if (ownerCut > 0 && !payLaneOwner(players, monster, ownerCut)) {
            ownerCut = 0L;
        }
        long killerReward = finalReward - ownerCut;
        player.economy().addDiamond(killerReward);
        if (player.teamId() == monster.targetTeam() && player.laneId() == monster.targetLaneId()) {
            player.matchStats().recordOwnLaneMonsterKill(killerReward, monster.attributionThreat());
        } else {
            player.matchStats().recordAssistMonsterKill(killerReward, monster.attributionThreat());
        }
        if (jobContext != null) {
            player.job().ifPresent(job -> job.onMonsterKilled(jobContext, monster, finalReward));
        }
        monster.markRewardGranted();
    }

    /**
     * 남의 레인에서 잡았을 때 그 레인 주인에게 넘길 몫.
     *
     * <p>레인을 벗어나 남의 레인을 청소해 주는 것은 도움이지만, 그 레인의 수입까지 가져가면
     * 도움이 아니라 강탈입니다. 잡은 사람은 수고비만 가지고 나머지는 주인에게 갑니다.
     *
     * <p>최종 방어 구간은 빼 둡니다. 거기는 원래 모두가 같이 막는 자리라 "남의 레인"이라는 말이
     * 성립하지 않고, 이미 {@code crossLaneFinalDefenseWaveMultiplier} 가 따로 다루고 있습니다.
     * 둘을 겹쳐 물리면 같은 상황에 벌이 두 번 들어갑니다.
     */
    private long laneOwnerCut(SemionPlayer killer, Monster monster, long reward) {
        if (reward <= 0 || killer.teamId() != monster.targetTeam() || killer.laneId() == monster.targetLaneId()) {
            return 0L;
        }
        EconomyConfig.KillRewardConfig killReward = economyConfig.killReward();
        if (monster.laneProgress() >= killReward.finalDefenseProgressThreshold()) {
            return 0L;
        }
        return Math.min(reward, Math.round(reward * killReward.crossLaneOwnerShare()));
    }

    /**
     * 레인 주인에게 몫을 넘깁니다. 주인이 자리에 없으면 {@code false}.
     *
     * <p>전과 기록은 건드리지 않습니다. 주인이 잡은 게 아니라 받은 것이고, 받은 다이아를 처치
     * 기여로 세면 가만히 있어도 레이팅이 오릅니다.
     */
    private boolean payLaneOwner(Map<UUID, SemionPlayer> players, Monster monster, long amount) {
        for (SemionPlayer candidate : players.values()) {
            if (candidate.teamId() == monster.targetTeam() && candidate.laneId() == monster.targetLaneId()) {
                candidate.economy().addDiamond(amount);
                return true;
            }
        }
        return false;
    }

    private long adjustedKillReward(SemionPlayer player, Monster monster, long reward) {
        long boundedReward = Math.max(0, reward);
        if (boundedReward <= 0) {
            return boundedReward;
        }
        EconomyConfig.KillRewardConfig killReward = economyConfig.killReward();
        if (!killReward.crossLaneWaveReductionEnabled()) {
            return boundedReward;
        }

        boolean sameTeamCrossLaneKill = player.teamId() == monster.targetTeam()
                && player.laneId() != monster.targetLaneId();
        boolean eligibleMonster = monster.ownerPlayer().isEmpty() || killReward.applyToIncomeUnits();
        boolean nearFinalDefense = monster.laneProgress() >= killReward.finalDefenseProgressThreshold();
        if (sameTeamCrossLaneKill && eligibleMonster && nearFinalDefense) {
            return Math.max(1, Math.round(boundedReward * killReward.crossLaneFinalDefenseWaveMultiplier()));
        }
        return boundedReward;
    }

    private boolean isEconomyEligible(SemionPlayer player, Map<TeamId, SemionTeam> teams) {
        SemionTeam team = teams.get(player.teamId());
        return team != null && team.active() && !team.eliminated();
    }
}
