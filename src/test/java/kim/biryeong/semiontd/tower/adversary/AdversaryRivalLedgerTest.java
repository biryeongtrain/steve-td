package kim.biryeong.semiontd.tower.adversary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class AdversaryRivalLedgerTest {
    private static final UUID OWNER = id("owner");
    private static final UUID OTHER = id("other");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetRuntime() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AdversaryProgressStates.clearAllForTesting();
    }

    @AfterEach
    void clearProgress() {
        AdversaryProgressStates.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void nearbyDeathAttributionCannotPretendAnotherOwnedTowerWasTheFox() {
        PlayerLane lane = testLane();
        AdversaryRivalTower rival = rival(RivalKind.PHANTOM, false);
        lane.addTower(rival);
        Monster proxy = rival.createProxy(1);
        proxy.recordLastHit(OWNER, KillSourceKind.TOWER);

        rival.onNearbyMonsterDeath(lane, proxy, Vec3.ZERO);
        assertEquals(0, rival.contributedScore());
        assertFalse(AdversaryProgressStates.recordFoxKill(OTHER, proxy, lane));
        assertEquals(0, rival.contributedScore());

        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, proxy, lane));
        assertEquals(1, rival.contributedScore());
        assertEquals(1, AdversaryProgressStates.state(OWNER).score(RivalKind.PHANTOM));
        assertFalse(AdversaryProgressStates.recordFoxKill(OWNER, proxy, lane));
        assertEquals(1, rival.contributedScore());
    }

    @Test
    void upgradeKeepsUuidAndPastLedgerWhileFutureKillsUseEnhancedScore() {
        PlayerLane lane = testLane();
        AdversaryRivalTower base = rival(RivalKind.BREEZE, false);
        lane.addTower(base);
        UUID logicalId = base.rivalId();
        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, base.createProxy(1), lane));

        AdversaryRivalTower enhanced = rival(RivalKind.BREEZE, true);
        enhanced.copyFrom(base, RivalKind.BREEZE.enhancementCost());
        assertTrue(lane.replaceTower(base, enhanced));

        assertEquals(logicalId, enhanced.rivalId());
        assertEquals(1, enhanced.contributedScore());
        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, enhanced.createProxy(1), lane));
        assertEquals(3, enhanced.contributedScore());
        assertEquals(3, AdversaryProgressStates.state(OWNER).score(RivalKind.BREEZE));
    }

    @Test
    void sellingAContributorSubtractsItsLedgerAndDemotesWithoutUnlockingAnotherRoute() {
        PlayerLane lane = testLane();
        AdversaryRivalTower rival = rival(RivalKind.BREEZE, false);
        lane.addTower(rival);
        for (int kill = 0; kill < 50; kill++) {
            assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, rival.createProxy(1), lane));
        }

        AdversaryProgressState state = AdversaryProgressStates.state(OWNER);
        assertEquals(FoxForm.BREEZE, state.applyPreparationTransition().orElseThrow().current());
        state.recordCompletedWave();
        assertEquals(FoxForm.GOLDEN_FANG, state.applyPreparationTransition().orElseThrow().current());

        assertTrue(lane.removeTower(rival));
        assertEquals(0, state.score(RivalKind.BREEZE));
        assertEquals(FoxForm.BASE, state.currentForm());
        assertEquals(FoxRoute.RAPID, state.lockedRoute().orElseThrow());
        assertEquals(FoxForm.GOLDEN_FANG, state.lockedFinalForm().orElseThrow());
    }

    @Test
    void foxKillBoundaryIgnoresAllNonRivalMonstersIncludingIncome() {
        Monster naturalWarden = monster("minecraft:warden", Optional.empty(), Optional.empty());
        assertFalse(AdversaryProgressStates.recordFoxKill(OWNER, naturalWarden, testLane()));
        assertTrue(AdversaryProgressStates.state(OWNER).pendingForm().isEmpty());

        Monster sentWarden = monster("minecraft:warden", Optional.empty(), Optional.of(TeamId.BLUE));
        assertFalse(AdversaryProgressStates.recordFoxKill(OWNER, sentWarden, testLane()));
        assertTrue(AdversaryProgressStates.state(OWNER).pendingForm().isEmpty());
    }

    @Test
    void closingGameClearsPlayerProgressEvenWithoutElimination() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        game.players().put(OWNER, new SemionPlayer(
                OWNER,
                "adversary-close",
                TeamId.RED,
                1,
                new PlayerEconomy(economy)
        ));
        AdversaryProgressStates.state(OWNER).recordRivalKill(
                UUID.randomUUID(),
                RivalKind.BREEZE,
                false
        );
        assertTrue(AdversaryProgressStates.find(OWNER).isPresent());

        game.close();

        assertTrue(AdversaryProgressStates.find(OWNER).isEmpty());
    }

    private static AdversaryRivalTower rival(RivalKind kind, boolean enhanced) {
        return new AdversaryRivalTower(
                enhanced ? AdversaryTowers.enhancedRival(kind) : AdversaryTowers.baseRival(kind),
                OWNER,
                TeamId.RED,
                1,
                new GridPosition(2, 64, 2)
        );
    }

    private static PlayerLane testLane() {
        Vec3 spawn = new Vec3(0.5, 64.0, 0.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(new Vec3(0.5, 64.0, 5.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(6, 67, 12)),
                List.of(new GridPosition(4, 64, 10))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }

    private static Monster monster(
            String entityType,
            Optional<UUID> owner,
            Optional<TeamId> sender
    ) {
        return new Monster(
                "test-income",
                TeamId.RED,
                1,
                owner,
                sender,
                10.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                entityType,
                1L
        );
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
