package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DemonLordPassiveTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        DemonLordStates.clearAllForTesting();
    }

    @Test
    void passivesAreBoughtIntoSlotsEightAndNineAndRefundedInFull() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        long cleaveCost = DemonLordPassive.BLOOD_CLEAVE.cost();

        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.BLOOD_CLEAVE));
        assertTrue(state.loadout().hasPassive(DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(1000 - cleaveCost, economy.diamond());

        assertEquals(DemonLordSkillShop.Result.PASSIVE_ALREADY_SLOTTED, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(DemonLordSkillShop.Result.SLOT_OCCUPIED, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.DREAD));
        double damageBefore = state.damageMultiplier();
        double reductionBefore = state.damageReduction();
        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.DREAD));
        assertTrue(state.damageMultiplier() > damageBefore, "Dread raises damage.");
        assertTrue(state.damageReduction() > reductionBefore, "Dread raises damage reduction.");

        assertEquals(DemonLordSkillShop.Result.SUCCESS,
                DemonLordSkillShop.removePassive(state, economy, DemonLordPassiveSlot.EIGHT));
        assertFalse(state.loadout().hasPassive(DemonLordPassive.BLOOD_CLEAVE));
        assertEquals(1000 - DemonLordPassive.DREAD.cost(), economy.diamond(), "Removing refunds everything paid.");
    }

    @Test
    void passivesCannotBeChangedDuringCombat() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        state.enterCombat();
        assertEquals(DemonLordSkillShop.Result.IN_COMBAT, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.LEGION_ECHO));
        assertEquals(1000, economy.diamond());
    }

    @Test
    void passivesSurviveTheStateBeingRebuilt() {
        UUID owner = UUID.randomUUID();
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        PlayerEconomy economy = economy(1000);
        DemonLordSkillShop.buyPassive(state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.LEGION_ECHO);
        DemonLordStates.clear(owner);
        DemonLordState rebuilt = DemonLordStates.getOrCreate(owner);
        assertEquals(DemonLordPassive.LEGION_ECHO,
                rebuilt.loadout().passive(DemonLordPassiveSlot.NINE).orElseThrow().passive(),
                "A passive is paid for with diamonds and must not vanish on reconnect.");
    }

    @Test
    void everyPassivePublishesItsPriceInTheBalanceFile() {
        TowerBalanceConfig bundled = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig code = TowerBalanceConfig.codeDefaults();
        for (DemonLordPassive passive : DemonLordPassive.values()) {
            assertTrue(bundled.ability(passive.configId(), "cost", -1) > 0, passive + " must have a price");
            assertEquals(code.abilities().get(passive.configId()), bundled.abilities().get(passive.configId()),
                    passive + " drifted between code and the bundled resource");
            assertFalse(passive.description().isEmpty());
        }
        assertEquals(5, (int) bundled.ability(DemonLordPassive.LEGION_ECHO.configId(), "copies", -1));
    }

    @Test
    void doomPactBoostsEveryStatThenResetsTheDemonLordToLevelOne() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        state.addExperience(10_000.0);
        assertTrue(state.level() > 1);
        state.allocate(DemonLordStat.ATTACK);
        double damageBefore = state.damageMultiplier();
        double healthBefore = state.maxHealth();
        double cooldownBefore = state.cooldownMultiplier();

        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.buyPassive(
                state, economy, DemonLordPassiveSlot.EIGHT, DemonLordPassive.DOOM_PACT));
        assertEquals(damageBefore * 2.5, state.damageMultiplier(), 1.0e-6);
        assertEquals(healthBefore * 2.5, state.maxHealth(), 1.0e-6);
        assertEquals(cooldownBefore * 0.6, state.cooldownMultiplier(), 1.0e-6);
        assertTrue(state.damageReduction() >= 0.3);

        for (int round = 1; round <= 5; round++) {
            state.countPactRound();
            state.enterCombat();
            if (round == 1) {
                state.standDown();
                assertEquals(DemonLordSkillShop.Result.PACT_LOCKED,
                        DemonLordSkillShop.removePassive(state, economy, DemonLordPassiveSlot.EIGHT),
                        "A started pact cannot be refunded.");
                continue;
            }
            state.standDown();
            assertEquals(round == 5, state.settlePact(), "The pact ends exactly after its fifth round.");
        }
        assertEquals(1, state.level());
        assertEquals(0, state.points(DemonLordStat.ATTACK));
        assertEquals(0, state.unspentPoints());
        assertFalse(state.loadout().hasPassive(DemonLordPassive.DOOM_PACT), "The pact is consumed.");
        assertEquals(1.0, state.damageMultiplier(), 1.0e-6);
    }

    @Test
    void doomPactCanBeRefundedBeforeItsFirstWave() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        PlayerEconomy economy = economy(1000);
        DemonLordSkillShop.buyPassive(state, economy, DemonLordPassiveSlot.NINE, DemonLordPassive.DOOM_PACT);
        assertEquals(DemonLordSkillShop.Result.SUCCESS, DemonLordSkillShop.removePassive(state, economy, DemonLordPassiveSlot.NINE));
        assertEquals(1000, economy.diamond());
    }

    @Test
    void doomPactProgressSurvivesTheStateBeingRebuilt() {
        UUID owner = UUID.randomUUID();
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        DemonLordSkillShop.buyPassive(state, economy(1000), DemonLordPassiveSlot.EIGHT, DemonLordPassive.DOOM_PACT);
        state.countPactRound();
        state.countPactRound();
        DemonLordStates.clear(owner);
        assertEquals(2, DemonLordStates.getOrCreate(owner).pactRoundsServed());
    }

    @Test
    void bladeWaveSegmentsHitBoxesTheyPassThroughOrStartIn() {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(4, 0, -1, 5, 2, 1);
        net.minecraft.world.phys.Vec3 origin = new net.minecraft.world.phys.Vec3(0, 1, 0);
        assertTrue(DemonLordPassives.crosses(box, origin, new net.minecraft.world.phys.Vec3(10, 1, 0)));
        assertFalse(DemonLordPassives.crosses(box, origin, new net.minecraft.world.phys.Vec3(3, 1, 0)));
        assertTrue(DemonLordPassives.crosses(box, new net.minecraft.world.phys.Vec3(4.5, 1, 0), new net.minecraft.world.phys.Vec3(6, 1, 0)));
    }

    @Test
    void occupiedColumnsKeepAllBitsOfBothCoordinates() {
        int[] coordinates = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -67_108_864, -1, 0, 1,
                67_108_864, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (int x : coordinates) {
            for (int z : coordinates) {
                assertTrue(columns.add(DemonLordPassives.columnKey(x, z)),
                        "Distinct signed x/z coordinates must never share a column key: " + x + ", " + z);
            }
        }
        assertEquals(coordinates.length * coordinates.length, columns.size());
    }

    private static PlayerEconomy economy(long diamonds) {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(diamonds, 0, 0, 0);
        return economy;
    }
}
