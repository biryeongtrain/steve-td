package kim.biryeong.semiontd.tower.end;

import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.DRAGON_FINAL_DAMAGE;
import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.DRAGON_RANGE_BONUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnderDragonScaleTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void oneTowerIsAnEggDuringPreparationAndSwitchesToPhantomAtWaveStart() {
        EndTower tower = tower();
        assertEquals(EndTowerState.EGG, tower.state());
        assertTrue(BlockDisplayVisual.matches(tower.visual()));
        tower.onWaveStarted(null, 1);
        assertEquals(EndTowerState.PHANTOM, tower.state());
        assertEquals(EndTowers.BASE_END_TOWER, tower.type());
        assertEquals("minecraft:phantom", tower.visual().entityTypeId());
        assertTrue(tower.visual().blockbenchModel().isEmpty());
    }

    @Test
    void phantomScaleStartsAtOneAndGrowsByPointTwoPerHundredMaxHealth() {
        EndTower tower = tower();
        assertEquals(1.2, tower.phantomScaleForMaxHealth(100.0), 0.0001);
        assertEquals(1.3, tower.phantomScaleForMaxHealth(150.0), 0.0001);
        assertEquals(1.4, tower.phantomScaleForMaxHealth(200.0), 0.0001);
        assertEquals(1.6, tower.phantomScaleForMaxHealth(300.0), 0.0001);
        assertEquals(5.0, tower.phantomScaleForMaxHealth(5000.0), 0.0001);
        assertEquals(1.0, tower.phantomScaleForMaxHealth(Double.NaN), 0.0001);
    }

    @Test
    void phantomScaleFormulaComesFromEndGlobalConfig() {
        applyEndAbilities(Map.of(
                "phantomScaleHealth", 50.0,
                "phantomScaleStep", 0.25,
                "phantomScaleBase", 0.5,
                "phantomScaleCap", 1.25
        ));
        EndTower tower = tower();
        assertEquals(1.0, tower.phantomScaleForMaxHealth(100.0), 0.0001);
        assertEquals(1.25, tower.phantomScaleForMaxHealth(1000.0), 0.0001);
    }

    @Test
    void phantomBecomesVanillaDragonWhenMaxHealthReachesThreshold() {
        double baseMaxHealth = EndTowers.BASE_END_TOWER.maxHealth();
        applyStateConfig(baseMaxHealth + 0.01);
        EndTower tower = tower();
        tower.onWaveStarted(null, 1);
        tower.tick(null);
        assertEquals(EndTowerState.PHANTOM, tower.state());
        applyStateConfig(baseMaxHealth);
        tower.tick(null);
        assertEquals(EndTowerState.DRAGON, tower.state());
        assertEquals("minecraft:ender_dragon", tower.visual().entityTypeId());
        assertTrue(tower.visual().blockbenchModel().isEmpty());
        assertEquals(1.0, tower.visual().scale(), 0.0001);
        assertEquals(EndTowers.BASE_END_TOWER.range() + EndConfig.RUNTIME.value(DRAGON_RANGE_BONUS), tower.adjustAttackRange(EndTowers.BASE_END_TOWER.range()), 0.0001);
        assertEquals(EndTowers.BASE_END_TOWER.damage(), tower.modifyAttackDamage(null, null, EndTowers.BASE_END_TOWER.damage()), 0.0001);
        assertEquals(EndConfig.RUNTIME.value(DRAGON_FINAL_DAMAGE), tower.finalDamageBonus(), 0.0001);
    }

    @Test
    void finalAttackLineTowerUsesEndCrystalVisual() {
        assertEquals("minecraft:enderman", EndTowers.T2_ENDERMAN_TOWER.visual().entityTypeId());
        assertEquals("minecraft:end_crystal", EndTowers.T3_END_CRYSTAL_TOWER.visual().entityTypeId());
    }

    private static EndTower tower() {
        return new EndTower(
                EndTowers.BASE_END_TOWER,
                UUID.nameUUIDFromBytes("end-state-owner".getBytes()),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
    }

    private static void applyStateConfig(double evolutionMaxHealth) {
        applyEndAbilities(Map.of("dragonEvolution", evolutionMaxHealth));
    }

    private static void applyEndAbilities(Map<String, Double> overrides) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.putAll(overrides);
        abilities.put(EndTower.CONFIG_ID, end);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
    }
}