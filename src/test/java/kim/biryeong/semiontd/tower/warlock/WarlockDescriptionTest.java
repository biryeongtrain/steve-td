package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockDescriptionTest {
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
    void descriptionsMatchPullRequestSeventySevenStructure() {
        List<String> rangedDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER).description();
        String rangedMarkup = String.join("\n", rangedDescriptionLines);
        String description = rangedMarkup.replaceAll("<[^>]+>", "");
        assertTrue(description.contains("체력 65% 이하이면"));
        assertTrue(description.contains("흡수 시 최대 체력 증가분에 체력 30을 더해 회복"));
        assertTrue(description.contains("주위 25블록 내 아군 타워를 흡수합니다."));
        assertTrue(description.contains("흡수한 타워 체력과 피해의 50%"));
        assertTrue(description.contains("체력 +4%"));
        assertTrue(description.contains("피해 +7%"));
        assertTrue(description.contains("생존 중인 개구리 계열마다 체력 +4%, 피해 +10%"));
        assertTrue(description.contains("최대 체력 +20%, 피해 +50%까지 증가"));
        assertTrue(description.contains("체력 40% 이하"));
        assertTrue(description.contains("각성 시 체력을 회복하고, 재생이 증가합니다."));
        assertEquals(9, rangedDescriptionLines.size());
        assertFalse(description.contains("생명력 흡수 효율:"));
        assertEquals("능력치는 높아질수록 증가 효율이 감소합니다.",
                rangedDescriptionLines.getLast().replaceAll("<[^>]+>", ""));
        assertFalse(rangedMarkup.contains("{ability."));

        List<String> meleeDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER).description();
        String meleeMarkup = String.join("\n", meleeDescriptionLines);
        String meleeDescription = meleeMarkup.replaceAll("<[^>]+>", "");
        assertTrue(meleeDescription.contains("체력 65% 이하이면"));
        assertTrue(meleeDescription.contains("흡수 시 최대 체력 증가분에 체력 30을 더해 회복"));
        assertTrue(meleeDescription.contains("주위 25블록 내 아군 타워를 흡수합니다."));
        assertTrue(meleeDescription.contains("흡수한 타워 체력과 피해의 60%"));
        assertTrue(meleeDescription.contains("체력 +7%"));
        assertTrue(meleeDescription.contains("피해 +4%"));
        assertTrue(meleeDescription.contains("생존 중인 양 계열마다 체력 +10%, 피해 +4%"));
        assertTrue(meleeDescription.contains("최대 체력 +50%, 피해 +20%까지 증가"));
        assertTrue(meleeDescription.contains("체력 40% 이하"));
        assertTrue(meleeDescription.contains("각성 시 체력을 회복하고, 추가 피해와 이동 속도가 증가합니다."));
        assertEquals(9, meleeDescriptionLines.size());
        assertFalse(meleeDescription.contains("생명력 흡수 효율:"));
        assertFalse(meleeDescription.contains("아군 타워가 남아 있어도 현재 라운드 흡수 스택"));
        assertEquals("능력치는 높아질수록 증가 효율이 감소합니다.",
                meleeDescriptionLines.getLast().replaceAll("<[^>]+>", ""));
        assertFalse(meleeMarkup.contains("{ability."));

        List<String> baseDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER).description();
        String baseMarkup = String.join("\n", baseDescriptionLines);
        String baseDescription = baseMarkup.replaceAll("<[^>]+>", "");
        assertEquals(2, baseDescriptionLines.size());
        assertTrue(baseDescription.contains("원거리 또는 근거리 흑마법사를 선택할 수 있습니다."));
        assertTrue(baseDescription.contains("흑마법사 타워는 1기만 설치할 수 있습니다."));
        assertFalse(baseMarkup.contains("{ability."));
    }

    @Test
    void sacrificeDescriptionsStateTheDeathDebuffDirectly() {
        String meleeTierTwo = plainDescription(WarlockTowers.T2_SLAVE);
        String meleeTierThree = plainDescription(WarlockTowers.T3_SLAVE);
        String rangedTierTwo = plainDescription(WarlockTowers.T2_RANGED_SLAVE);
        String rangedTierThree = plainDescription(WarlockTowers.T3_RANGED_SLAVE);

        assertTrue(meleeTierTwo.contains("사망 시 주위 20블록 내 적이 받는 피해를 10% 증가시킵니다."));
        assertTrue(meleeTierThree.contains("사망 시 주위 20블록 내 적이 받는 피해를 10% 증가시킵니다."));
        assertTrue(rangedTierTwo.contains("사망 시 주위 20블록 내 적의 공격 속도를 10% 감소시킵니다."));
        assertTrue(rangedTierThree.contains("사망 시 주위 20블록 내 적의 공격 속도를 10% 감소시킵니다."));
    }

    @Test
    void jobDescriptionCommunicatesTheCompleteBuilderFantasy() {
        String description = new WarlockTowerJob().description().stream()
                .map(component -> component.getString())
                .collect(Collectors.joining("\n"));

        assertTrue(description.contains("희생으로 성장해"));
        assertTrue(description.contains("원거리·근거리 중 선택"));
        assertTrue(description.contains("1400킬 후 최후 생존·저체력에서 각성"));
        assertTrue(new WarlockTowerJob().description().stream()
                .allMatch(component -> component.getString().length() <= 31));

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> global = new LinkedHashMap<>(abilities.get(WarlockTowers.CONFIG_ID));
        global.put("awakeningKills", 42.0);
        abilities.put(WarlockTowers.CONFIG_ID, global);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        String configuredDescription = new WarlockTowerJob().description().stream()
                .map(component -> component.getString())
                .collect(Collectors.joining("\n"));
        assertTrue(configuredDescription.contains("42킬 후 최후 생존·저체력에서 각성"));
    }

    private static String plainDescription(TowerType type) {
        return String.join("\n", TowerBalanceRuntime.resolve(type).description()).replaceAll("<[^>]+>", "");
    }

    @Test
    void lifeStealDisplayShowsTheEffectiveRateAndOriginalProgress() {
        for (boolean ranged : new boolean[] {true, false}) {
            double maximum = ranged ? .07 : .12;
            int stages = ranged ? 14 : 12;
            int every = ranged ? 10 : 1;
            double step = ranged ? .005 : .01;
            for (int stage = 0; stage <= stages; stage++) {
                for (double damage : new double[] {40, 400, 4000}) {
                    double expectedPercent = stage * (ranged ? 5 : 10) * (damage == 40 ? 1 : damage == 400 ? .1 : .01);
                    String expectedText = kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate
                            .formatNumber(Math.round(expectedPercent * 10) / 10.0) + "%";
                    String details = String.join("\n", WarlockStatsView.core(new WarlockStatsView.CoreStats(
                            stage * every, stage * every, false, false, null,
                            new WarlockStatsView.CombatStats(0, 0, 15, 0, 8, false),
                            new WarlockStatsView.DefenseStats(0, stage * step, maximum, damage, 0, .3, 0),
                            new WarlockStatsView.ProgressionStats(true, stage * every, every, 0, 10,
                                    false, false, 0, 1, 0, 2, false))))
                            .replaceAll("<[^>]+>", "");
                    String progress = stage == stages ? "(MAX)" : "(" + ((stage + 1) * every) + ")";
                    assertTrue(details.contains("생명력 흡수: " + expectedText + " " + progress), details);
                    assertFalse(details.contains("표시값은 현재 피해"), details);
                    assertFalse(details.contains("생명력 흡수 효율:"), details);
                    assertFalse(details.contains("실제 회복률은"), details);
                }
            }
        }
    }
}
