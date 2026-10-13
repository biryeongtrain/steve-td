package kim.biryeong.semiontd.tower.developer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeveloperBugLookupTest {
    private static final UUID OWNER = new UUID(0, 1);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void membershipMatchesExistingParserForEveryBugAndTokenShape() {
        DeveloperTower tower = tower(DeveloperTowers.ALPHA);
        List<String> inputs = new ArrayList<>(List.of("", " ", ",", ",,,", "unknown", "unknown,, ,", "null"));
        for (DeveloperBug bug : DeveloperBug.values()) {
            inputs.add(bug.key());
            inputs.add("  " + bug.key().toUpperCase(Locale.ROOT) + " ,unknown,, " + bug.key() + ",");
            inputs.add("unknown," + bug.key() + "_suffix," + bug.key() + ",unknown");
            inputs.add("\t" + bug.key() + "\t");
        }
        inputs.add(Arrays.stream(DeveloperBug.values()).map(DeveloperBug::key).collect(Collectors.joining(",")));
        for (String encoded : inputs) {
            tower.setData(DeveloperTowerData.BUGS, encoded);
            for (DeveloperBug bug : DeveloperBug.values()) {
                assertEquals(DeveloperTowerData.bugs(tower).contains(bug), DeveloperTowerData.hasBug(tower, bug),
                        encoded + " / " + bug.key());
            }
            assertFalse(DeveloperTowerData.hasBug(tower, null));
        }
    }

    @Test
    void nullTowerBugAndEncodedValuesKeepEmptyMembership() {
        assertFalse(DeveloperTowerData.hasBug(null, DeveloperBug.INFINITE_LOOP));
        assertFalse(DeveloperTowerData.hasBug(null, null));
        DeveloperTower missing = tower(DeveloperTowers.ALPHA);
        assertFalse(DeveloperTowerData.hasBug(missing, DeveloperBug.INFINITE_LOOP));
        DeveloperTower nullEncoded = new DeveloperTower(DeveloperTowers.ALPHA, OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0)) {
            @Override
            public <T> T getDataOrDefault(TowerDataKey<T> key, T fallback) {
                return key.equals(DeveloperTowerData.BUGS) ? null : super.getDataOrDefault(key, fallback);
            }
        };
        assertFalse(DeveloperTowerData.hasBug(nullEncoded, DeveloperBug.INFINITE_LOOP));
        assertFalse(DeveloperTowerData.hasBug(nullEncoded, null));
    }

    @Test
    void nullBugDoesNotReadTowerData() {
        DeveloperTower unreadable = new DeveloperTower(DeveloperTowers.ALPHA, OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0)) {
            @Override
            public <T> T getDataOrDefault(TowerDataKey<T> key, T fallback) {
                if (key.equals(DeveloperTowerData.BUGS)) {
                    throw new AssertionError("A null bug must not read encoded data");
                }
                return super.getDataOrDefault(key, fallback);
            }
        };
        assertFalse(DeveloperTowerData.hasBug(unreadable, null));
    }

    @Test
    void directMutationUpgradeCopyAndReloadAlwaysReadCurrentData() {
        TowerBalanceConfig previous = TowerBalanceRuntime.current();
        try {
            DeveloperTower original = tower(DeveloperTowers.ALPHA);
            original.setData(DeveloperTowerData.BUGS, DeveloperBug.INFINITE_LOOP.key());
            assertTrue(DeveloperTowerData.hasBug(original, DeveloperBug.INFINITE_LOOP));
            DeveloperTower upgraded = tower(DeveloperTowers.BETA);
            upgraded.copyFrom(original, 100);
            assertTrue(DeveloperTowerData.hasBug(upgraded, DeveloperBug.INFINITE_LOOP));

            original.setData(DeveloperTowerData.BUGS, DeveloperBug.REVERSE_SORT.key());
            assertFalse(DeveloperTowerData.hasBug(original, DeveloperBug.INFINITE_LOOP));
            assertTrue(DeveloperTowerData.hasBug(original, DeveloperBug.REVERSE_SORT));
            assertTrue(DeveloperTowerData.hasBug(upgraded, DeveloperBug.INFINITE_LOOP));

            TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(), Map.of()));
            assertTrue(DeveloperTowerData.hasBug(upgraded, DeveloperBug.INFINITE_LOOP));
            upgraded.setData(DeveloperTowerData.BUGS, DeveloperBug.REVERSE_SORT.key());
            assertFalse(DeveloperTowerData.hasBug(upgraded, DeveloperBug.INFINITE_LOOP));
            assertTrue(DeveloperTowerData.hasBug(upgraded, DeveloperBug.REVERSE_SORT));
            upgraded.removeData(DeveloperTowerData.BUGS);
            assertFalse(DeveloperTowerData.hasBug(upgraded, DeveloperBug.REVERSE_SORT));
        } finally {
            TowerBalanceRuntime.apply(previous);
        }
    }

    @Test
    void fullBugSetStillRetainsEncounterOrderAndDetachedMutability() {
        DeveloperTower tower = tower(DeveloperTowers.ALPHA);
        tower.setData(DeveloperTowerData.BUGS, DeveloperBug.REVERSE_SORT.key() + ","
                + DeveloperBug.INFINITE_LOOP.key() + "," + DeveloperBug.REVERSE_SORT.key());
        var bugs = DeveloperTowerData.bugs(tower);
        assertEquals(List.of(DeveloperBug.REVERSE_SORT, DeveloperBug.INFINITE_LOOP), List.copyOf(bugs));
        bugs.clear();
        assertTrue(DeveloperTowerData.hasBug(tower, DeveloperBug.REVERSE_SORT));
        assertTrue(DeveloperTowerData.hasBug(tower, DeveloperBug.INFINITE_LOOP));
    }

    private static DeveloperTower tower(TowerType type) {
        return new DeveloperTower(type, OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0));
    }
}
