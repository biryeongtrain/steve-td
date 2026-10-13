package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import kim.biryeong.semiontd.tower.TowerType;
import org.junit.jupiter.api.Test;

class EndLifeStealDisplayTest extends EndTestFixture {
    @Test
    void lowDamageDisplaysEachOriginalStepAsTenPercentAndKeepsProgressThresholds() {
        for (int step = 0; step <= 10; step++) {
            List<String> lines = details(step * 30, 30);
            String lifeSteal = lines.stream().filter(line -> line.contains("생명력 흡수:")).findFirst().orElseThrow();
            assertTrue(lifeSteal.contains("생명력 흡수: " + step * 10 + "%"), lifeSteal);
            assertTrue(lifeSteal.contains(step == 10 ? "(MAX)" : "(" + ((step + 1) * 30) + ")"), lifeSteal);
        }
    }

    @Test
    void sixStepsAtTwoHundredFiftyDamageDisplaySevenPointTwoPercentMatchesActualHealing() {
        List<String> lines = details(180, 250);
        assertTrue(lines.stream().anyMatch(line -> line.contains("생명력 흡수: 7.2% (210)")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("표시값은 현재 피해")
                || line.contains("생명력 흡수 효율:") || line.contains("실제 회복률은")));
        double ratio = new EndCombat(EndConfig.RUNTIME).lifeStealRatio(new EndTransferStacks(180, 0, 0));
        assertEquals(.06, ratio, .000001);
        assertEquals(.072, ratio / .1 * kim.biryeong.semiontd.tower.DamageLifeSteal.efficiency(250, 30), .000001);
        assertEquals(18, new EndCombat(EndConfig.RUNTIME).lifeStealHealing(250, new EndTransferStacks(180, 0, 0)), .000001);
    }

    private static List<String> details(int stacks, double damage) {
        TowerType base = EndTowers.BASE_END_TOWER;
        TowerType type = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                base.maxHealth(), base.range(), damage, base.attackIntervalTicks(), base.aggroPriority(),
                base.description(), base.visual(), base.upgradeOptions());
        EndTower tower = tower(type, 0);
        EndConfig config = EndConfig.RUNTIME;
        EndStatsAssembler assembler = new EndStatsAssembler(config, new EndCombat(config),
                new EndEvolutionController(config, type.maxHealth()));
        return assembler.create(tower, false,
                new EndTransferSnapshot(new EndTransferStacks(stacks, 0, 0), 0, 0, 0, 0))
                .stream().map(line -> line.replaceAll("<[^>]+>", "")).toList();
    }

    @Test
    void efficiencyDisplayRoundsOnlyThisStatToOneDecimalPlace() {
        String lower = kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate
                .formatLifeStealEfficiency(.07249, "").replaceAll("<[^>]+>", "");
        String upper = kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate
                .formatLifeStealEfficiency(.07251, "").replaceAll("<[^>]+>", "");
        assertTrue(lower.contains("생명력 흡수: 7.2%"));
        assertTrue(upper.contains("생명력 흡수: 7.3%"));
        String ordinary = kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate
                .formatLifeSteal(.07249, "").replaceAll("<[^>]+>", "");
        assertTrue(ordinary.contains("생명력 흡수: +7.25%"));
    }

}
