package kim.biryeong.semiontd.test.tower;

import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;

public final class TestTowerTypes {
    public static final TowerType TEST_DEADEYE = new TowerType(
            "test_deadeye",
            "Test Deadeye Tower",
            TowerCategory.DIRECT,
            420,
            75.0,
            18.0,
            24,
            22,
            -20
    );

    public static final TowerType TEST_SNIPER = new TowerType(
            "test_sniper",
            "Test Sniper Tower",
            TowerCategory.DIRECT,
            250,
            65.0,
            14.0,
            17,
            19,
            -10,
            java.util.List.of(
                    new TowerUpgradeOption("deadeye", "Deadeye Evolution", TEST_DEADEYE.id(), 170)
            )
    );

    public static final TowerType TEST_BASTION = new TowerType(
            "test_bastion",
            "Test Bastion Tower",
            TowerCategory.DIRECT,
            400,
            170.0,
            7.0,
            13,
            7,
            75
    );

    public static final TowerType TEST_GUARD = new TowerType(
            "test_guard",
            "Test Guard Tower",
            TowerCategory.DIRECT,
            240,
            110.0,
            6.0,
            7,
            8,
            35,
            java.util.List.of(
                    new TowerUpgradeOption("bastion", "Bastion Evolution", TEST_BASTION.id(), 180)
            )
    );

    public static final TowerType TEST_DIRECT = new TowerType(
            "test_direct",
            "Test Direct Tower",
            TowerCategory.DIRECT,
            100,
            50.0,
            8.0,
            8,
            13,
            0,
            java.util.List.of(
                    new TowerUpgradeOption("sniper", "Sniper Evolution", TEST_SNIPER.id(), 150),
                    new TowerUpgradeOption("guard", "Guard Evolution", TEST_GUARD.id(), 140)
            )
    );

    private static final Map<String, TowerType> TYPES = Map.of(
            TEST_DIRECT.id(), TEST_DIRECT,
            TEST_DEADEYE.id(), TEST_DEADEYE,
            TEST_SNIPER.id(), TEST_SNIPER,
            TEST_GUARD.id(), TEST_GUARD,
            TEST_BASTION.id(), TEST_BASTION
    );

    private TestTowerTypes() {
    }

    public static Optional<TowerType> find(String typeId) {
        return Optional.ofNullable(TYPES.get(typeId));
    }
}
