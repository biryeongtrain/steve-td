package kim.biryeong.semiontd.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.tower.engineer.EngineerPressStates;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.thunder.ThunderStates;
import kim.biryeong.semiontd.tower.warlock.WarlockAwakeningProgress;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class JobLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFailedCloseCanRetryAndASuccessfulCloseDoesNotRepeatCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        SemionJob job = new SemionJob("test:close_retry", "retry") {
            @Override
            public void onMatchClosed(JobContext context) {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("first close fails");
                }
            }
        };
        JobContext context = context(job);
        try {
            EngineerPressStates.recordPress(context.player().uuid());
            assertThrows(IllegalStateException.class, context.game()::close);
            assertEquals(1, EngineerPressStates.count(context.player().uuid()));
            assertFalse(context.game().players().isEmpty());
            context.game().close();
            assertEquals(0, EngineerPressStates.count(context.player().uuid()));
            context.game().close();
            assertEquals(2, calls.get());
        } finally {
            clear(context.player().uuid());
            context.game().close();
        }
    }

    @Test
    void closingAnActiveIllagerMatchRemovesItsRaidState() {
        JobContext context = context(new IllagerTowerJob());
        try {
            context.player().job().orElseThrow().onRoundStarted(context, 3);
            assertTrue(IllagerRaidStates.get(context.player().uuid()).isPresent());
            context.game().close();
            assertTrue(IllagerRaidStates.get(context.player().uuid()).isEmpty());
        } finally {
            IllagerRaidStates.clear(context.player().uuid());
            context.game().close();
        }
    }

    @Test
    void closingAnActiveThunderMatchRemovesItsSharedStorm() {
        JobContext context = context(new ThunderTowerJob());
        try {
            ThunderStates.rollStorm(context.player().uuid(), 3, .25);
            assertEquals(3, ThunderStates.currentRound(context.player().uuid()));
            context.game().close();
            assertEquals(0, ThunderStates.currentRound(context.player().uuid()));
            assertEquals(.5, ThunderStates.stormRoll(context.player().uuid()));
        } finally {
            ThunderStates.clear(context.player().uuid());
            context.game().close();
        }
    }

    @Test
    void eliminatedThenClosedAndRepeatedCloseDoNotRecreateRuntimeState() {
        for (SemionJob job : new SemionJob[] {new IllagerTowerJob(), new ThunderTowerJob(),
                new WarlockTowerJob(), new EngineerTowerJob()}) {
            JobContext context = context(job);
            UUID owner = context.player().uuid();
            try {
                seed(context);
                job.onEliminated(context);
                assertOwnedStateCleared(job, owner);
                context.game().close();
                context.game().close();
                assertOwnedStateCleared(job, owner);
                assertTrue(context.game().players().isEmpty());
            } finally {
                clear(owner);
                context.game().close();
            }
        }
    }

    @Test
    void unselectedAndOtherBuildersStillClearCrossFamilyPressState() {
        for (SemionJob job : new SemionJob[] {null, new EndTowerJob()}) {
            JobContext context = context(job);
            try {
                EngineerPressStates.recordPress(context.player().uuid());
                context.game().close();
                assertEquals(0, EngineerPressStates.count(context.player().uuid()));
            } finally {
                clear(context.player().uuid());
                context.game().close();
            }
        }
    }

    @Test
    void commonHooksAffectOnlyTheirBuilderAndPlayer() {
        JobContext context = context(new IllagerTowerJob());
        JobContext other = context(new IllagerTowerJob());
        try {
            seed(context);
            seed(other);
            new IllagerTowerJob().onMatchStarted(context);
            assertTrue(IllagerRaidStates.get(context.player().uuid()).isEmpty());
            assertTrue(IllagerRaidStates.get(other.player().uuid()).isPresent());
            assertEquals(3, ThunderStates.currentRound(context.player().uuid()));
            assertEquals(1, EngineerPressStates.count(context.player().uuid()));
            new EndTowerJob().onMatchStarted(other);
            assertTrue(IllagerRaidStates.get(other.player().uuid()).isPresent());
            new IllagerTowerJob().onRoundEnded(other, 3);
            assertFalse(IllagerRaidStates.get(other.player().uuid()).isPresent());
        } finally {
            clear(context.player().uuid());
            clear(other.player().uuid());
            context.game().close();
            other.game().close();
        }
    }

    @Test
    void warlockKillDispatchIncrementsOnceWithoutARegisteredLane() {
        JobContext context = context(new WarlockTowerJob());
        try {
            SemionJob job = context.player().job().orElseThrow();
            job.onMonsterKilled(context, null, 0);
            assertEquals(1, WarlockAwakeningProgress.snapshot(context.player().uuid()).kills());
            job.onMonsterKilled(context, null, 0);
            assertEquals(2, WarlockAwakeningProgress.snapshot(context.player().uuid()).kills());
            job.onMatchStarted(context);
            assertEquals(0, WarlockAwakeningProgress.snapshot(context.player().uuid()).kills());
        } finally {
            clear(context.player().uuid());
            context.game().close();
        }
    }

    private static JobContext context(SemionJob job) {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(UUID.randomUUID(), "lifecycle", TeamId.RED, 1,
                new PlayerEconomy(economy));
        if (job != null) {
            player.assignJob(job);
        }
        game.players().put(player.uuid(), player);
        return new JobContext(game, player);
    }

    private static void seed(JobContext context) {
        IllagerRaidStates.onRoundStarted(context);
        ThunderStates.rollStorm(context.player().uuid(), 3, .25);
        WarlockAwakeningProgress.recordKill(context.player().uuid());
        EngineerPressStates.recordPress(context.player().uuid());
    }

    private static void assertOwnedStateCleared(SemionJob job, UUID owner) {
        if (job instanceof IllagerTowerJob) {
            assertTrue(IllagerRaidStates.get(owner).isEmpty());
        } else if (job instanceof ThunderTowerJob) {
            assertEquals(0, ThunderStates.currentRound(owner));
        } else if (job instanceof WarlockTowerJob) {
            assertEquals(0, WarlockAwakeningProgress.snapshot(owner).kills());
        } else if (job instanceof EngineerTowerJob) {
            assertEquals(0, EngineerPressStates.count(owner));
        }
    }

    private static void clear(UUID owner) {
        IllagerRaidStates.clear(owner);
        ThunderStates.clear(owner);
        WarlockAwakeningProgress.clear(owner);
        EngineerPressStates.clear(owner);
    }
}
