package kim.biryeong.semiontd.tower.warlock;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class WarlockIntegrationGameTest {
    @GameTest
    public void progressClearsOnMatchStartEliminationCloseAndPlayerReuse(GameTestHelper context) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        UUID owner = stableUuid("warlock-integration-lifecycle-owner");
        SemionGame firstGame = null;
        SemionGame secondGame = null;
        boolean firstClosed = false;
        boolean secondClosed = false;
        try {
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            recordKills(owner, 3);
            firstGame = startedGame(context, owner, "warlock-first");
            require(WarlockAwakeningProgress.snapshot(owner).kills() == 0,
                    "Warlock match start must clear progress left by a previous match.");

            recordKills(owner, 5);
            require(firstGame.killBoss(context.getLevel().getServer(), TeamId.RED),
                    "Warlock lifecycle test must eliminate the active team.");
            require(WarlockAwakeningProgress.snapshot(owner).kills() == 0,
                    "Warlock elimination must clear awakening progress.");

            recordKills(owner, 7);
            firstGame.close();
            firstClosed = true;
            require(WarlockAwakeningProgress.snapshot(owner).kills() == 0,
                    "Warlock game close must clear progress even after elimination.");

            recordKills(owner, 11);
            secondGame = startedGame(context, owner, "warlock-reuse");
            require(WarlockAwakeningProgress.snapshot(owner).kills() == 0,
                    "Reusing a player in a second match must not retain Warlock progress.");
            recordKills(owner, 1);
            secondGame.close();
            secondClosed = true;
            require(WarlockAwakeningProgress.snapshot(owner).kills() == 0,
                    "The reused player's second game close must clear Warlock progress.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            failure.printStackTrace();
            context.fail(Component.literal("Warlock integration lifecycle failed: " + failure.getMessage()));
        } finally {
            if (firstGame != null && !firstClosed) {
                firstGame.close();
            }
            if (secondGame != null && !secondClosed) {
                secondGame.close();
            }
            WarlockAwakeningProgress.clear(owner);
            TowerBalanceRuntime.apply(defaults);
        }
    }

    private static SemionGame startedGame(GameTestHelper context, UUID owner, String playerName) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        require(game.selectJob(owner, WarlockTowerJob.ID), "Warlock builder selection must succeed.");
        require(game.start(
                context.getLevel().getServer(),
                new ParticipantSelectionPlan(
                        MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, playerName, TeamId.RED, 1)),
                        Set.of(),
                        1
                )
        ), "Warlock game start must succeed.");
        return game;
    }

    private static void recordKills(UUID owner, int count) {
        for (int index = 0; index < count; index++) {
            WarlockAwakeningProgress.recordKill(owner);
        }
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
