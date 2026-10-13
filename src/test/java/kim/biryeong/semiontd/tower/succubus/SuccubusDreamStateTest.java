package kim.biryeong.semiontd.tower.succubus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SuccubusDreamStateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void countersAdvanceOnlyTheActiveTimerAndNeverMakeImmunityNegative() {
        SuccubusDreamState state = new SuccubusDreamState();
        state.stacks = 3;
        state.remainingTicks = 2;
        state.immunityTicks = 1;

        state.tickCounters();

        assertEquals(1, state.remainingTicks);
        assertEquals(0, state.immunityTicks);
        state.asleep = true;
        state.asleepTicks = 1;
        state.tickCounters();
        assertEquals(0, state.asleepTicks);
        assertEquals(1, state.remainingTicks);
        assertEquals(0, state.immunityTicks);
        state.tickCounters();
        assertEquals(-1, state.asleepTicks);
    }

    @Test
    void reachingTheCapSleepsOnceAndPreservesTheFirstOwnersAttribution() {
        SuccubusTower first = source(new UUID(0, 1));
        SuccubusTower last = source(new UUID(0, 2));
        SuccubusDreamState state = new SuccubusDreamState();

        assertTrue(state.add(null, first, 1, 37, true));
        assertTrue(state.add(null, last, SuccubusBalance.maxStacks(), 37, true));

        assertEquals(SuccubusBalance.maxStacks(), state.stacks);
        assertEquals(first.ownerPlayer(), state.sourceOwner);
        assertSame(last, state.lastSource);
        assertTrue(state.asleep);
        assertEquals(37, state.asleepTicks);
        assertEquals(1, state.sleepCount);
        assertFalse(state.add(null, first, 1, 99, true));
        assertEquals(37, state.asleepTicks);
        assertEquals(1, state.sleepCount);
    }

    @Test
    void lucidStacksCanRefreshAtTheCapWithoutEnteringSleep() {
        SuccubusDreamState state = new SuccubusDreamState();
        SuccubusTower source = source(new UUID(0, 1));

        assertTrue(state.add(null, source, SuccubusBalance.maxStacks(), 37, false));
        state.remainingTicks = 1;
        assertFalse(state.add(null, source, 1, 37, false));

        assertFalse(state.asleep);
        assertEquals(0, state.sleepCount);
        assertEquals(SuccubusBalance.stackDurationTicks(), state.remainingTicks);
    }

    @Test
    void wakingClearsTransientStacksButRetainsAttributionAndSleepHistory() {
        SuccubusDreamState state = new SuccubusDreamState();
        SuccubusTower source = source(new UUID(0, 1));
        state.add(null, source, SuccubusBalance.maxStacks(), 37, true);
        state.sleepAttackTicks = 11;
        state.sleepLostHealth = 23.5;
        state.contagionDepth = 2;
        state.deathHandled = true;

        state.clearStacksForWake();

        assertEquals(0, state.stacks);
        assertEquals(0, state.remainingTicks);
        assertEquals(0, state.asleepTicks);
        assertEquals(0, state.sleepAttackTicks);
        assertEquals(0, state.sleepLostHealth);
        assertEquals(0, state.contagionDepth);
        assertFalse(state.asleep);
        assertEquals(1, state.sleepCount);
        assertTrue(state.deathHandled);
        assertSame(source, state.lastSource);
        assertEquals(source.ownerPlayer(), state.sourceOwner);
        assertEquals(SuccubusBalance.awakenedImmunityTicks(), state.immunityTicks);
        assertFalse(state.add(null, source, 1, 37, true));
        assertEquals(0, state.stacks);
    }

    private static SuccubusTower source(UUID owner) {
        GridPosition position = new GridPosition(0, 80, 0);
        return new SuccubusTower(SuccubusTowers.SUCCUBUS, owner, TeamId.RED, 1, position, position);
    }
}
