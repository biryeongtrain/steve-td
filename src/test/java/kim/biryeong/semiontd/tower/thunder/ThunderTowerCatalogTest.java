package kim.biryeong.semiontd.tower.thunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.ThunderTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThunderTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("thunder-owner".getBytes());

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
        ThunderStates.clearAll();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsThirteenTowersWithThreeStarters() {
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> ThunderTowers.isThunderTower(entry.type()))
                .toList();

        assertEquals(13, entries.size(), "generation 3, tanks 5, damage 5");
        assertEquals(3, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(),
                "only the three tier-one entries are starters");
    }

    @Test
    void everyTowerBelongsToTheThunderBuilderOnly() {
        var thunder = JobRegistry.find(ThunderTowerJob.ID).orElseThrow();
        for (TowerType type : ThunderTowers.all()) {
            assertTrue(thunder.includesTowerInCatalog(type), type.id() + " must be owned by the thunder builder");
            long owners = JobRegistry.all().stream()
                    .filter(job -> job.includesTowerInCatalog(type))
                    .count();
            assertEquals(1, owners, type.id() + " must have exactly one owning builder");
        }
    }

    /**
     * Guards the bundled-defaults trap: {@code TowerBalanceConfig.defaultConfig()} returns the
     * bundled JSON as-is rather than merging the Java fallback, so a family added only in Java
     * silently resolves every upgrade to 0 mineral.
     */
    @Test
    void everyUpgradeCostsMineral() {
        assertUpgradeCost(ThunderTowers.ROD_T1, ThunderTowers.ROD_COPPER, 75);
        assertUpgradeCost(ThunderTowers.ROD_T1, ThunderTowers.ROD_STORM, 75);
        assertUpgradeCost(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_INSULATED, 105);
        assertUpgradeCost(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_GROUNDED, 100);
        assertUpgradeCost(ThunderTowers.ARMADILLO_INSULATED, ThunderTowers.ARMADILLO_RUBBER, 220);
        assertUpgradeCost(ThunderTowers.ARMADILLO_GROUNDED, ThunderTowers.ARMADILLO_EARTH, 215);
        assertUpgradeCost(ThunderTowers.SQUIRREL_T1, ThunderTowers.SQUIRREL_T2, 130);
        assertUpgradeCost(ThunderTowers.SQUIRREL_T1, ThunderTowers.SURGE_T2, 130);
        assertUpgradeCost(ThunderTowers.SQUIRREL_T2, ThunderTowers.SQUIRREL_T3, 280);
        assertUpgradeCost(ThunderTowers.SURGE_T2, ThunderTowers.SURGE_T3, 280);
    }

    private static void assertUpgradeCost(TowerType from, TowerType to, long expected) {
        long cost = TowerBalanceRuntime.upgradeCost(from, to.id());
        assertEquals(expected, cost, from.id() + " -> " + to.id() + " must cost mineral");
    }

    /** Both branches happen at tier one, matching every other builder in the repository. */
    @Test
    void branchesHappenAtTierOne() {
        assertUpgradeExists(ThunderTowers.ROD_T1, ThunderTowers.ROD_COPPER);
        assertUpgradeExists(ThunderTowers.ROD_T1, ThunderTowers.ROD_STORM);
        assertUpgradeExists(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_INSULATED);
        assertUpgradeExists(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_GROUNDED);
        assertUpgradeExists(ThunderTowers.SQUIRREL_T1, ThunderTowers.SQUIRREL_T2);
        assertUpgradeExists(ThunderTowers.SQUIRREL_T1, ThunderTowers.SURGE_T2);
    }

    private static void assertUpgradeExists(TowerType from, TowerType to) {
        assertTrue(ProductionTowerCatalog.upgrade(from, to.id()).isPresent(),
                from.id() + " must offer " + to.id());
    }

    @Test
    void catalogBuildsThunderRuntimeTowers() {
        var entry = ProductionTowerCatalog.find(ThunderTowers.SQUIRREL_T1.id()).orElseThrow();
        GridPosition origin = new GridPosition(0, 0, 0);
        var tower = entry.factory().create(entry.type(), OWNER, TeamId.BLUE, 1, origin, origin);
        assertInstanceOf(ThunderTower.class, tower, "thunder towers need the family runtime for power scaling");
    }

    /** The damage line is the only group that uses a squirrel model. */
    @Test
    void onlyTheDamageLineUsesASquirrelModel() {
        for (TowerType type : ThunderTowers.all()) {
            boolean modeled = type.visual() != null
                    && type.visual().blockbenchModel()
                    .filter(model -> ThunderTowers.SQUIRREL_MODEL.equals(model)
                            || ThunderTowers.SURGE_MODEL.equals(model))
                    .isPresent();
            assertEquals(ThunderTowers.isSquirrel(type), modeled,
                    type.id() + " model assignment must match its role");
        }
    }

    /**
     * The two T2 branches come off the same T1 and can stand side by side, so they must not share
     * an appearance.
     */
    @Test
    void surgeBranchIsVisuallyDistinct() {
        for (TowerType type : ThunderTowers.all()) {
            if (!ThunderTowers.isSquirrel(type)) {
                continue;
            }
            String expected = ThunderTowers.isSurge(type)
                    ? ThunderTowers.SURGE_MODEL
                    : ThunderTowers.SQUIRREL_MODEL;
            assertEquals(expected, type.visual().blockbenchModel().orElseThrow(),
                    type.id() + " must use the model for its branch");
        }
    }

    @Test
    void tunedDefaultsMergeAndMatchTheBundle() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(20, defaults.abilityTicks(ThunderBalance.CONFIG_ID, "stunTicks", -1));
        assertEquals(50, defaults.abilityTicks(ThunderBalance.CONFIG_ID, "stunImmunityTicks", -1));
        assertEquals(0.22, defaults.ability(ThunderBalance.CONFIG_ID, "surplusDamageBonus", -1), 0.0001);
        assertEquals(2, defaults.abilityInt(ThunderTowers.SQUIRREL_T2.id(), "chainTargets", -1));
        assertEquals(3.0, defaults.ability(ThunderTowers.SQUIRREL_T2.id(), "chainRadius", -1), 0.0001);
        assertEquals(0.35, defaults.ability(ThunderTowers.SQUIRREL_T2.id(), "chainDamageRatio", -1), 0.0001);
        assertEquals(4, defaults.abilityInt(ThunderTowers.SQUIRREL_T3.id(), "chainTargets", -1));
        assertEquals(4.0, defaults.ability(ThunderTowers.SQUIRREL_T3.id(), "chainRadius", -1), 0.0001);

        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(ThunderBalance.CONFIG_ID, Map.of("stunTicks", 18.0))
        ).withMissingDefaults(defaults);
        assertEquals(18, partial.abilityTicks(ThunderBalance.CONFIG_ID, "stunTicks", -1));
        assertEquals(50, partial.abilityTicks(ThunderBalance.CONFIG_ID, "stunImmunityTicks", -1));

        try (var input = ThunderTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var bundled = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("abilities");
            for (var entry : defaults.abilities().entrySet()) {
                if (!entry.getKey().startsWith("thunder_")) {
                    continue;
                }
                var values = bundled.getAsJsonObject(entry.getKey());
                assertEquals(entry.getValue().keySet(), values.keySet(), entry.getKey());
                entry.getValue().forEach((key, value) ->
                        assertEquals(value, values.get(key).getAsDouble(), 0.0001, entry.getKey() + "." + key));
            }
        }
    }

    @Test
    void invalidThunderTimingAndTargetCountsAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, ThunderBalance.CONFIG_ID, "stunImmunityTicks", 10.0);
        assertInvalidAbility(defaults, ThunderTowers.SQUIRREL_T3.id(), "chainTargets", 2.5);
        assertInvalidAbility(defaults, ThunderTowers.SURGE_T3.id(), "surgeMaxMultiplier", 0.9);
    }

    @Test
    void tunedDamageStaysWithinThePlannedCeilings() {
        TowerType chain = TowerBalanceRuntime.resolve(ThunderTowers.SQUIRREL_T3);
        double primary = chain.damage() * 20.0 / chain.attackIntervalTicks()
                * new ThunderPower.Snapshot(80.0, ThunderBalance.powerDraw(chain.id()), 0.3).damageMultiplier();
        assertEquals(128.6545, primary, 0.001);
        double ratio = ThunderBalance.chainDamageRatio(chain.id());
        assertEquals(252.1629, primary * (1.0 + 2.0 * ratio), 0.001);
        assertEquals(313.9171, primary * (1.0 + 3.0 * ratio), 0.001);

        assertEquals(94.4382, surgeDps(ThunderTowers.SURGE_T2), 0.001);
        assertEquals(138.775, surgeDps(ThunderTowers.SURGE_T3), 0.001);
    }

    private static double surgeDps(TowerType type) {
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        double draw = ThunderBalance.powerDraw(type.id());
        ThunderPower.Snapshot grid = new ThunderPower.Snapshot(ThunderBalance.basePower(), draw,
                draw / ThunderBalance.basePower());
        return resolved.damage() * 20.0 / resolved.attackIntervalTicks() * grid.damageMultiplier()
                * (1.0 + (ThunderBalance.surgeMaxMultiplier(type.id()) - 1.0) * grid.headroom());
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults,
            String configId,
            String key,
            double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(abilities.get(configId));
        values.put(key, value);
        abilities.put(configId, values);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }
}
