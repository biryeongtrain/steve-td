package kim.biryeong.semiontd.ui;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.attackDamageText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.formatMagicDamage;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.magicDamageText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.tutorial.TutorialService.HighlightTarget;
import org.junit.jupiter.api.Test;

final class SemionHudTextServiceTest {
    @Test
    void activePlayerEconomyActionbarMarkupKeepsExistingElements() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(123, 45, 67, 8);
        SemionPlayer player = new SemionPlayer(playerId, "player", TeamId.RED, 1, economy);
        game.players().put(playerId, player);

        String actionbar = SemionHudTextService.actionbarMarkupFor(player, game);

        assertTrue(actionbar.contains("◆ 다이아 123"));
        assertTrue(actionbar.contains("⬢ 에메랄드 45"));
        assertTrue(actionbar.contains("+ 수입 67"));
        assertTrue(actionbar.contains("에메랄드/s 8"));
        assertTrue(actionbar.contains("▣ 타워"));
    }

    @Test
    void tutorialActionbarHighlightsOnlyTheExplainedEconomyElement() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(123, 45, 67, 8);
        SemionPlayer player = new SemionPlayer(playerId, "player", TeamId.RED, 1, economy);
        game.players().put(playerId, player);

        String normal = SemionHudTextService.actionbarMarkupFor(player, game);
        assertEquals(normal, SemionHudTextService.actionbarMarkupFor(player, game, HighlightTarget.DIAMOND, false));
        assertTrue(SemionHudTextService.actionbarMarkupFor(player, game, HighlightTarget.DIAMOND, true)
                .contains("<white><bold>◆ 다이아 123</bold></white>"));
        assertTrue(SemionHudTextService.actionbarMarkupFor(player, game, HighlightTarget.EMERALD, true)
                .contains("<white><bold>⬢ 에메랄드 45</bold></white>"));
        assertTrue(SemionHudTextService.actionbarMarkupFor(player, game, HighlightTarget.EMERALD_RATE, true)
                .contains("<white><bold>↗ 에메랄드/s 8</bold></white>"));
        String incomeHighlight = SemionHudTextService.actionbarMarkupFor(player, game, HighlightTarget.INCOME, true);
        assertTrue(incomeHighlight.contains("<white><bold>+ 수입 67</bold></white>"));
        assertTrue(incomeHighlight.contains("<aqua>◆ 다이아 123</aqua>"));
        assertTrue(incomeHighlight.contains("<green>⬢ 에메랄드 45</green>"));
    }

    @Test
    void damageNumbersUseCompactSidebarUnits() {
        assertEquals("0", SemionHudTextService.formatDamage(0.0));
        assertEquals("999", SemionHudTextService.formatDamage(999.4));
        assertEquals("1.0K", SemionHudTextService.formatDamage(1_000.0));
        assertEquals("12.3M", SemionHudTextService.formatDamage(12_345_678.0));
        assertEquals("1.2B", SemionHudTextService.formatDamage(1_234_567_890.0));
    }

    @Test
    void damageTypesUseIconsAndSharedHudColors() {
        assertEquals("<#ec8d34>🪓 123</#ec8d34>", attackDamageText("🪓 123"));
        assertEquals("<#796CFF>🔥 456</#796CFF>", magicDamageText("🔥 456"));
        assertEquals(
                "<#796CFF>🔥 피해</#796CFF><white>: </white><#796CFF>42</#796CFF>",
                formatMagicDamage(42.0, "")
        );
    }

    @Test
    void damageSidebarViewTogglesInMemory() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        SemionSidebarHudService service = new SemionSidebarHudService();

        assertTrue(service.toggleDamageView(playerId));
        assertTrue(service.damageViewEnabled(playerId));
        assertFalse(service.toggleDamageView(playerId));
        assertFalse(service.damageViewEnabled(playerId));
    }

    @Test
    void sidebarKeepsItsRealTimeRefreshRateDuringCombatSpeedup() {
        assertEquals(10, SemionSidebarHudService.updateIntervalTicks(20.0F));
        assertEquals(20, SemionSidebarHudService.updateIntervalTicks(40.0F));
    }

    @Test
    void tutorialHighlightAlternatesEveryHalfSecond() {
        assertTrue(SemionSidebarHudService.tutorialHighlightOn(0));
        assertTrue(SemionSidebarHudService.tutorialHighlightOn(9));
        assertFalse(SemionSidebarHudService.tutorialHighlightOn(10));
        assertFalse(SemionSidebarHudService.tutorialHighlightOn(19));
        assertTrue(SemionSidebarHudService.tutorialHighlightOn(20));
    }
}
