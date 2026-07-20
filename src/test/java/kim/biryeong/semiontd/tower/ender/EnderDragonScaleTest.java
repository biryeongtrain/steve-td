package kim.biryeong.semiontd.tower.ender;

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
        EnderTower tower = tower();

        assertEquals(EnderTowerState.EGG, tower.state());
        assertTrue(BlockDisplayVisual.matches(tower.visual()));

        tower.onWaveStarted(null, 1);

        assertEquals(EnderTowerState.PHANTOM, tower.state());
        assertEquals(EnderTowers.BASE_ENDER_TOWER, tower.type());
        assertEquals("minecraft:phantom", tower.visual().entityTypeId());
        assertTrue(tower.visual().blockbenchModel().isEmpty());
    }

    @Test
    void phantomScaleGrowsLinearlyByPointOnePerHundredMaxHealth() {
        assertEquals(0.1, EnderTowers.phantomScaleForMaxHealth(100.0), 0.0001);
        assertEquals(0.15, EnderTowers.phantomScaleForMaxHealth(150.0), 0.0001);
        assertEquals(0.2, EnderTowers.phantomScaleForMaxHealth(200.0), 0.0001);
        assertEquals(0.3, EnderTowers.phantomScaleForMaxHealth(300.0), 0.0001);
    }

    @Test
    void phantomBecomesVanillaDragonWhenMaxHealthReachesThreshold() {
        double baseMaxHealth = EnderTowers.BASE_ENDER_TOWER.maxHealth();
        applyStateConfig(baseMaxHealth + 0.01);

        EnderTower tower = tower();
        tower.onWaveStarted(null, 1);
        tower.tick(null);

        assertEquals(EnderTowerState.PHANTOM, tower.state());

        applyStateConfig(baseMaxHealth);
        tower.tick(null);

        assertEquals(EnderTowerState.DRAGON, tower.state());
        assertEquals(
                "minecraft:ender_dragon",
                tower.visual().entityTypeId()
        );
        assertTrue(tower.visual().blockbenchModel().isEmpty());
        assertEquals(1.0, tower.visual().scale(), 0.0001);
    }

    @Test
    void finalAttackLineTowerUsesEndCrystalVisual() {
        assertEquals("minecraft:enderman", EnderTowers.T2_ENDERMAN_TOWER.visual().entityTypeId());
        assertEquals("minecraft:end_crystal", EnderTowers.T3_END_CRYSTAL_TOWER.visual().entityTypeId());
    }

    private static EnderTower tower() {
        return new EnderTower(
                EnderTowers.BASE_ENDER_TOWER,
                UUID.nameUUIDFromBytes("ender-state-owner".getBytes()),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
    }

    private static void applyStateConfig(double evolutionMaxHealth) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> ender = new LinkedHashMap<>(abilities.get(EnderTower.CONFIG_ID));
        ender.put("dragonEvolutionMaxHealth", evolutionMaxHealth);
        abilities.put(EnderTower.CONFIG_ID, ender);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
    }
}
