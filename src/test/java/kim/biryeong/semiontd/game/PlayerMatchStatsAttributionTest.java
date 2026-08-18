package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import org.junit.jupiter.api.Test;

final class PlayerMatchStatsAttributionTest {
    @Test
    void snapshotSeparatesLaneDefenseIncomePressureEconomySourcesAndAssist() {
        PlayerMatchStats stats = new PlayerMatchStats();

        stats.recordOwnLaneIncomingThreat(100.0, false);
        stats.recordOwnLaneIncomingThreat(50.0, true);
        stats.recordOwnLaneLeakedThreat(30.0);
        stats.recordSentIncomeThreat(80.0);
        stats.recordIncomeAttackSuccessThreat(20.0);
        stats.recordOwnLaneMonsterKill(10, 100.0);
        stats.recordAssistMonsterKill(5, 50.0);
        stats.recordIncomeGenerated(7);

        PlayerMatchStatsSnapshot snapshot = stats.snapshot(42);

        assertEquals(150.0, snapshot.ownLaneIncomingThreat(), 0.0001);
        assertEquals(50.0, snapshot.incomingIncomeThreat(), 0.0001);
        assertEquals(30.0, snapshot.ownLaneLeakedThreat(), 0.0001);
        assertEquals(80.0, snapshot.sentIncomeThreat(), 0.0001);
        assertEquals(20.0, snapshot.incomeAttackSuccessThreat(), 0.0001);
        assertEquals(10, snapshot.ownLaneDiamondGain());
        assertEquals(5, snapshot.assistClearDiamondGain());
        assertEquals(7, snapshot.incomeGenerated());
        assertEquals(50.0, snapshot.assistClearThreat(), 0.0001);
    }

    @Test
    void killAttributionRequiresMatchingTeamAndLaneForOwnLaneCredit() {
        UUID playerId = UUID.nameUUIDFromBytes("cross-team-lane-killer".getBytes());
        SemionPlayer player = player(playerId, TeamId.BLUE, 1);
        Monster monster = naturalWaveMonster("red-lane-income-unit", TeamId.RED, 1, 3L);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        PlayerMatchStatsSnapshot snapshot = player.matchStats().snapshot(player.economy().income());
        assertEquals(0, snapshot.ownLaneDiamondGain());
        assertEquals(3, snapshot.assistClearDiamondGain());
        assertEquals(monster.attributionThreat(), snapshot.assistClearThreat(), 0.0001);
    }

    @Test
    void sameTeamCrossLaneFinalDefenseWaveKillReceivesReducedRewardButKeepsAssistThreat() {
        UUID playerId = UUID.nameUUIDFromBytes("cross-lane-final-defense-killer".getBytes());
        SemionPlayer player = player(playerId, TeamId.BLUE, 1);
        Monster monster = naturalWaveMonster("blue-lane-two-final-defense-wave", TeamId.BLUE, 2, 10L);
        monster.syncLaneProgress(0.90);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        PlayerMatchStatsSnapshot snapshot = player.matchStats().snapshot(player.economy().income());
        assertEquals(0, snapshot.ownLaneDiamondGain());
        assertEquals(3, snapshot.assistClearDiamondGain());
        assertEquals(monster.attributionThreat(), snapshot.assistClearThreat(), 0.0001);
        assertEquals(EconomyConfig.defaultConfig().startingDiamond() + 3, player.economy().diamond());
    }

    @Test
    void ownLaneFinalDefenseWaveKillKeepsFullReward() {
        UUID playerId = UUID.nameUUIDFromBytes("own-lane-final-defense-killer".getBytes());
        SemionPlayer player = player(playerId, TeamId.BLUE, 1);
        Monster monster = naturalWaveMonster("blue-lane-one-final-defense-wave", TeamId.BLUE, 1, 10L);
        monster.syncLaneProgress(0.90);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        PlayerMatchStatsSnapshot snapshot = player.matchStats().snapshot(player.economy().income());
        assertEquals(10, snapshot.ownLaneDiamondGain());
        assertEquals(0, snapshot.assistClearDiamondGain());
        assertEquals(EconomyConfig.defaultConfig().startingDiamond() + 10, player.economy().diamond());
    }

    /**
     * 남의 레인을 청소해 주는 것은 도움이지 파밍이 아닙니다. 다이아는 전액 레인 주인에게 갑니다.
     *
     * <p>잡은 쪽이 빈손인 것은 아닙니다. 경험치는 그대로 들어가므로(마왕의 유일한 성장 수단)
     * 도우러 갈 이유는 남고, 남의 레인을 파밍터로 쓸 이유만 사라집니다.
     */
    @Test
    void sameTeamCrossLaneWaveKillBeforeFinalDefensePaysAllOfItToTheLaneOwner() {
        UUID killerId = UUID.nameUUIDFromBytes("cross-lane-early-killer".getBytes());
        UUID ownerId = UUID.nameUUIDFromBytes("cross-lane-early-owner".getBytes());
        SemionPlayer killer = player(killerId, TeamId.BLUE, 1);
        SemionPlayer owner = player(ownerId, TeamId.BLUE, 2);
        Monster monster = naturalWaveMonster("blue-lane-two-early-wave", TeamId.BLUE, 2, 10L);
        monster.syncLaneProgress(0.89);
        monster.recordLastHit(killerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig())
                .awardMonsterKillReward(monster, Map.of(killerId, killer, ownerId, owner));

        long starting = EconomyConfig.defaultConfig().startingDiamond();
        assertEquals(starting, killer.economy().diamond(), "남의 레인 처치로는 다이아를 받지 않습니다");
        assertEquals(starting + 10, owner.economy().diamond(), "전액이 레인 주인에게 갑니다");

        PlayerMatchStatsSnapshot killerStats = killer.matchStats().snapshot(killer.economy().income());
        assertEquals(0, killerStats.ownLaneDiamondGain());
        assertEquals(0, killerStats.assistClearDiamondGain(), "전과에도 실제로 받은 몫만 남습니다");

        PlayerMatchStatsSnapshot ownerStats = owner.matchStats().snapshot(owner.economy().income());
        assertEquals(0, ownerStats.ownLaneDiamondGain(),
                "받은 다이아를 처치 기여로 세면 가만히 있어도 레이팅이 오릅니다");
    }

    /** 주인이 자리에 없으면 갈 곳 없는 몫을 태우지 않고 잡은 사람이 다 가집니다. */
    @Test
    void crossLaneKillKeepsTheWholeRewardWhenTheLaneOwnerIsGone() {
        UUID killerId = UUID.nameUUIDFromBytes("cross-lane-orphan-killer".getBytes());
        SemionPlayer killer = player(killerId, TeamId.BLUE, 1);
        Monster monster = naturalWaveMonster("blue-lane-two-orphan-wave", TeamId.BLUE, 2, 10L);
        monster.syncLaneProgress(0.89);
        monster.recordLastHit(killerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig())
                .awardMonsterKillReward(monster, Map.of(killerId, killer));

        assertEquals(EconomyConfig.defaultConfig().startingDiamond() + 10, killer.economy().diamond());
    }

    @Test
    void sameTeamCrossLaneIncomeUnitKillKeepsFullRewardByDefault() {
        UUID playerId = UUID.nameUUIDFromBytes("cross-lane-income-killer".getBytes());
        UUID senderId = UUID.nameUUIDFromBytes("income-unit-sender".getBytes());
        SemionPlayer player = player(playerId, TeamId.BLUE, 1);
        Monster monster = incomeMonster("blue-lane-two-income-unit", TeamId.BLUE, 2, senderId, TeamId.RED, 10L);
        monster.syncLaneProgress(0.90);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        PlayerMatchStatsSnapshot snapshot = player.matchStats().snapshot(player.economy().income());
        assertEquals(0, snapshot.ownLaneDiamondGain());
        assertEquals(10, snapshot.assistClearDiamondGain());
        assertEquals(monster.attributionThreat(), snapshot.assistClearThreat(), 0.0001);
        assertEquals(EconomyConfig.defaultConfig().startingDiamond() + 10, player.economy().diamond());
    }

    private static SemionPlayer player(UUID playerId, TeamId teamId, int laneId) {
        return new SemionPlayer(
                playerId,
                "killer",
                teamId,
                laneId,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
    }

    private static Monster naturalWaveMonster(String id, TeamId targetTeam, int targetLaneId, long reward) {
        return new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                reward
        );
    }

    private static Monster incomeMonster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            UUID ownerPlayer,
            TeamId senderTeam,
            long reward
    ) {
        return new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.of(ownerPlayer),
                Optional.of(senderTeam),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                reward
        );
    }
}
