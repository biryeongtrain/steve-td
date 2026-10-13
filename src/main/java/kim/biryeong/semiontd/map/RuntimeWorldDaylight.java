package kim.biryeong.semiontd.map;

import net.minecraft.world.clock.ClockState;
import net.minecraft.world.level.gamerules.GameRules;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;

public final class RuntimeWorldDaylight {
    public static final int NOON = 6000;

    private RuntimeWorldDaylight() {
    }

    public static RuntimeLevelConfig configure(RuntimeLevelConfig config) {
        return config.setShouldTickTime(false)
                .setMirrorOverworldClocks(false)
                .setClockTime(Fantasy.DEFAULT_CLOCK, new ClockState(NOON, 0, 1, true))
                .setGameRule(GameRules.ADVANCE_TIME, false);
    }
}
