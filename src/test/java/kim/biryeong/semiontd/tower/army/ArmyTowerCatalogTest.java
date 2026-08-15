package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.ArmyTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArmyTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("army-owner".getBytes());

    /**
     * {@code ArmyBalance} reads through {@code TowerBalanceRuntime}, whose static initializer builds
     * the default config and therefore touches {@code EntityType}. Without the bootstrap that
     * initializer throws, and a class that failed to initialize stays unusable for the rest of the
     * JVM — which takes every later test in the same fork down with it.
     */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reload() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetState() {
        ArmyStates.clearAll();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsThirteenTowersWithThreeStarters() {
        assertEquals(13, ArmyTowers.all().size());
        long starters = ArmyTowers.all().stream()
                .map(type -> ProductionTowerCatalog.find(type.id()).orElseThrow())
                .filter(entry -> entry.tier() == 1)
                .count();
        assertEquals(3, starters, "one starter per line: 본부, 경계, 전투");
    }

    @Test
    void everyTowerBelongsToTheArmyBuilderOnly() {
        ArmyTowerJob job = (ArmyTowerJob) JobRegistry.find(ArmyTowerJob.ID).orElseThrow();
        for (TowerType type : ArmyTowers.all()) {
            assertTrue(job.includesTowerInCatalog(type), type.id() + " must be listed for the army builder");
        }
        for (var entry : ProductionTowerCatalog.all()) {
            if (ArmyTowers.isArmyTower(entry.type())) {
                continue;
            }
            assertFalse(job.includesTowerInCatalog(entry.type()),
                    entry.type().id() + " leaked into the army catalog");
        }
    }

    @Test
    void branchesHappenAtTierOne() {
        assertUpgradeExists(ArmyTowers.CLERK, ArmyTowers.DRILL_SERGEANT);
        assertUpgradeExists(ArmyTowers.CLERK, ArmyTowers.QUARTERMASTER);
        assertUpgradeExists(ArmyTowers.GUARD, ArmyTowers.MILITARY_POLICE);
        assertUpgradeExists(ArmyTowers.GUARD, ArmyTowers.GOP_SENTRY);
        assertUpgradeExists(ArmyTowers.RECRUIT, ArmyTowers.SPECIALIST);
        assertUpgradeExists(ArmyTowers.RECRUIT, ArmyTowers.GUNNER);
    }

    @Test
    void everyUpgradeCostsMineral() {
        assertUpgradeCost(ArmyTowers.CLERK, ArmyTowers.DRILL_SERGEANT, 75);
        assertUpgradeCost(ArmyTowers.CLERK, ArmyTowers.QUARTERMASTER, 75);
        assertUpgradeCost(ArmyTowers.GUARD, ArmyTowers.MILITARY_POLICE, 105);
        assertUpgradeCost(ArmyTowers.GUARD, ArmyTowers.GOP_SENTRY, 100);
        assertUpgradeCost(ArmyTowers.MILITARY_POLICE, ArmyTowers.MP_COMMANDER, 220);
        assertUpgradeCost(ArmyTowers.GOP_SENTRY, ArmyTowers.OUTPOST_CHIEF, 215);
        assertUpgradeCost(ArmyTowers.RECRUIT, ArmyTowers.SPECIALIST, 130);
        assertUpgradeCost(ArmyTowers.RECRUIT, ArmyTowers.GUNNER, 130);
        assertUpgradeCost(ArmyTowers.SPECIALIST, ArmyTowers.PLATOON_LEADER, 280);
        assertUpgradeCost(ArmyTowers.GUNNER, ArmyTowers.BATTERY_CHIEF, 280);
    }

    @Test
    void catalogBuildsArmyRuntimeTowers() {
        for (TowerType type : ArmyTowers.all()) {
            var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
            GridPosition position = new GridPosition(0, 64, 0);
            var tower = entry.factory().create(entry.type(), OWNER, TeamId.RED, 0, position, position);
            assertInstanceOf(ArmyTower.class, tower, type.id() + " must build an ArmyTower");
        }
    }

    @Test
    void onlyTheCombatLineGainsRank() {
        for (TowerType type : ArmyTowers.all()) {
            boolean combat = ArmyTowers.isCombat(type);
            assertEquals(combat, ArmyTowers.ranks(type),
                    type.id() + " rank eligibility must follow the combat line exactly");
        }
        assertFalse(ArmyTowers.ranks(ArmyTowers.MP_COMMANDER),
                "the guard line is the family's escape hatch and must never decay");
        assertFalse(ArmyTowers.ranks(ArmyTowers.CLERK));
        assertTrue(ArmyTowers.ranks(ArmyTowers.PLATOON_LEADER));
    }

    /**
     * 조교 and 초소장 are the same dial turned opposite ways, so their magnitudes have to stay
     * mirrored — otherwise running both is a net gain or loss rather than the intended cancellation.
     */
    @Test
    void tempoDialsAreExactOpposites() {
        assertEquals(
                ArmyBalance.serviceRateBonus(ArmyTowers.DRILL_SERGEANT.id()),
                -ArmyBalance.serviceRateBonus(ArmyTowers.OUTPOST_CHIEF.id()),
                1.0E-9);
        assertEquals(
                ArmyBalance.serviceRateBonus(ArmyTowers.CLERK.id()),
                -ArmyBalance.serviceRateBonus(ArmyTowers.GOP_SENTRY.id()),
                1.0E-9);
    }

    @Test
    void onlyTheCombatLineUsesArmourRenderingEntities() {
        for (TowerType type : ArmyTowers.all()) {
            String entityId = type.entityTypeId();
            if (ArmyTowers.isCombat(type)) {
                assertTrue(entityId.startsWith("minecraft:"),
                        type.id() + " ranks up, so it must use a vanilla humanoid that renders a helmet");
            }
        }
    }

    private static void assertUpgradeExists(TowerType from, TowerType to) {
        assertTrue(ProductionTowerCatalog.upgrade(from, to.id()).isPresent(),
                from.id() + " -> " + to.id() + " upgrade is missing");
    }

    private static void assertUpgradeCost(TowerType from, TowerType to, long expected) {
        assertEquals(expected, TowerBalanceRuntime.upgradeCost(from, to.id()),
                from.id() + " -> " + to.id() + " cost drifted from the bundled defaults");
    }
}
