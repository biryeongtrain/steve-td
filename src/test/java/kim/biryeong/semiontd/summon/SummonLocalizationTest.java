package kim.biryeong.semiontd.summon;

import static org.junit.jupiter.api.Assertions.*;

import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.tower.income.IncomeTowerService;
import org.junit.jupiter.api.Test;

final class SummonLocalizationTest {
    @Test
    void packagedNamesAndDescriptionsAreKoreanAndExistingAliasesRemainIntact() {
        var defaults = SummonConfig.defaultConfig();
        for (var definition : defaults.summons().values()) {
            assertTrue(definition.displayName().codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3), definition.id());
        }
        assertEquals("메시", defaults.summons().get("goat").displayName());
        assertEquals("샌즈", defaults.summons().get("skeleton").displayName());
        assertEquals("늑구(적)", defaults.summons().get("wolf").displayName());
        String elder = SummonDescriptionFactory.describe(defaults.summons().get("elder_guardian")).getFirst();
        assertTrue(elder.contains("25% 곱연산"));
        assertTrue(elder.contains("20% 곱연산"));
        assertFalse(SummonDescriptionFactory.describe(defaults.summons().get("goblin_scout")).isEmpty());
    }

    @Test
    void legacyDisplayTranslationPreservesCustomNamesAndStableIds() {
        assertEquals("엘더 가디언", SummonDisplayNames.localize("elder_guardian", "Elder Guardian"));
        assertEquals("메시", SummonDisplayNames.localize("goat", "Messi"));
        assertEquals("My Guardian", SummonDisplayNames.localize("elder_guardian", "My Guardian"));
        assertEquals("운영자 이름", SummonDisplayNames.localize("elder_guardian", "운영자 이름"));
        assertEquals("Elder Guardian", SummonDisplayNames.localize("custom_summon", "Elder Guardian"));
    }

    @Test
    void incomeTooltipDescribesHalfSplashAndKeepsDwarfSingleTarget() {
        for (var unit : IncomeSummons.build(SummonConfig.defaultConfig())) {
            if (java.util.List.of("dark_priest", "dwarf_gunner", "ogre_champion").contains(unit.id())) {
                if (unit.id().equals("dwarf_gunner")) {
                    assertTrue(IncomeTowerService.description(unit).getFirst().contains("한 대상"), unit.id());
                } else {
                    String text = String.join(" ", IncomeTowerService.description(unit));
                    assertTrue(text.contains("최대 5기") && text.contains("100%") && text.contains("50%"), text);
                    if (unit.id().equals("dark_priest")) assertTrue(text.contains("2%"), text);
                }
                assertNotEquals(unit.description(), IncomeTowerService.description(unit), unit.id());
            }
        }
    }
}
