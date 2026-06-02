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
        SemionPlayer player = new SemionPlayer(
                playerId,
                "killer",
                TeamId.BLUE,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        Monster monster = new Monster(
                "red-lane-income-unit",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                3L
        );
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        PlayerMatchStatsSnapshot snapshot = player.matchStats().snapshot(player.economy().income());
        assertEquals(0, snapshot.ownLaneDiamondGain());
        assertEquals(3, snapshot.assistClearDiamondGain());
        assertEquals(monster.attributionThreat(), snapshot.assistClearThreat(), 0.0001);
    }
}
