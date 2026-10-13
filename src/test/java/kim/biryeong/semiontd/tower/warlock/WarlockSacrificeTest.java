package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockSacrificeTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requestedPermanentGrowthSurvivesRoundResetWithoutChangingRoundGrowth() {
        kim.biryeong.semiontd.config.TowerBalanceRuntime.apply(
                kim.biryeong.semiontd.config.TowerBalanceConfig.defaultConfig());
        WarlockPath[] paths = {WarlockPath.BASE, WarlockPath.RANGED, WarlockPath.MELEE};
        double[] health = {20.0, 40.0, 70.0};
        double[] damage = {2.0, 7.0, 4.0};
        double[] roundHealth = {0.0, 500.0, 600.0};
        double[] roundDamage = {0.0, 50.0, 60.0};
        for (int index = 0; index < paths.length; index++) {
            WarlockState state = new WarlockState();
            state.recordSacrifice(WarlockSacrifice.calculate(paths[index],
                    new WarlockSacrifice.Snapshot(1000.0, 100.0, 12),
                    WarlockConfig.RUNTIME.path(paths[index]), WarlockConfig.RUNTIME.combat(), 20));
            assertEquals(health[index], state.permanentHealthBonus(), .0001);
            assertEquals(damage[index], state.permanentDamageBonus(), .0001);
            assertEquals(roundHealth[index], state.roundHealthBonus(), .0001);
            assertEquals(roundDamage[index], state.roundDamageBonus(), .0001);
            state.resetRound();
            assertEquals(health[index], state.permanentHealthBonus(), .0001);
            assertEquals(damage[index], state.permanentDamageBonus(), .0001);
            assertEquals(0, state.roundHealthBonus(), .0001);
            assertEquals(0, state.roundDamageBonus(), .0001);
        }
    }

    @Test
    void baseSacrificeRecordsProgressAndPermanentStatsAtomically() {
        WarlockConfig config = WarlockConfig.RUNTIME;
        WarlockSacrifice.Snapshot snapshot = new WarlockSacrifice.Snapshot(100.0, 20.0, 12);
        WarlockSacrifice.Gain gain = WarlockSacrifice.calculate(
                WarlockPath.BASE,
                snapshot,
                config.path(WarlockPath.BASE),
                config.combat(),
                20
        );
        WarlockState state = new WarlockState();

        state.recordSacrifice(gain);

        assertEquals(1, state.totalSacrificeCount());
        assertEquals(1, state.roundSacrificeCount());
        assertEquals(100.0 * config.path(WarlockPath.BASE).absorption().permanentHealthRatio(),
                state.permanentHealthBonus(), 0.0001);
        assertEquals(20.0 * config.path(WarlockPath.BASE).absorption().permanentDamageRatio(),
                state.permanentDamageBonus(), 0.0001);
        assertEquals(0.0, state.roundHealthBonus(), 0.0001);
        assertEquals(0.0, state.roundDamageBonus(), 0.0001);
    }

    @Test
    void snapshotUsesStableTowerStatsWithoutCombatContext() {
        Tower tower = new Tower(
                WarlockTowers.T3_SLAVE,
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        ) {
            @Override
            public double modifyAttackDamage(
                    SemionTowerEntity towerEntity,
                    SemionMonsterEntity target,
                    double damageAmount
            ) {
                throw new AssertionError("Combat damage hooks must not run while capturing sacrifice stats.");
            }

            @Override
            public double sacrificeAttackDamage() {
                return 37.0;
            }

            @Override
            protected boolean execute(PlayerLane lane) {
                return false;
            }
        };
        tower.syncMaxHealth(222.0, false);

        WarlockSacrifice.Snapshot snapshot = WarlockSacrifice.snapshot(tower);

        assertEquals(222.0, snapshot.maxHealth(), 0.0001);
        assertEquals(37.0, snapshot.attackDamage(), 0.0001);
        assertEquals(tower.type().attackIntervalTicks(), snapshot.attackIntervalTicks());
    }

    @Test
    void configuredNonPositiveRadiusHasExplicitUnlimitedMeaning() {
        WarlockRules.SacrificeRule rule = WarlockRules.SacrificeRule.fromConfiguredRadius(0.0, 30.0);

        assertTrue(Double.isInfinite(rule.radius()));
        assertTrue(rule.includes(Double.MAX_VALUE));
        assertEquals(30.0, rule.completionHealing(), 0.0001);
    }

    @Test
    void failedKillLeavesEverySacrificeStateFieldUnchanged() {
        WarlockState state = new WarlockState();
        WarlockSacrifice.Gain gain = new WarlockSacrifice.Gain(
                5.0,
                3.0,
                50.0,
                30.0,
                4.0,
                15.0
        );

        WarlockSacrifice.commit(false, state, gain);
        assertEquals(0, state.totalSacrificeCount());
        assertEquals(0, state.roundSacrificeCount());
        assertEquals(0.0, state.permanentHealthBonus(), 0.0001);
        assertEquals(0.0, state.permanentDamageBonus(), 0.0001);
        assertEquals(0.0, state.roundHealthBonus(), 0.0001);
        assertEquals(0.0, state.roundDamageBonus(), 0.0001);
        assertEquals(0.0, state.roundIntervalReduction(), 0.0001);
    }
}
