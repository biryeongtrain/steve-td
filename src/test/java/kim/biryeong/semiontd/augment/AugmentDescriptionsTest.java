package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class AugmentDescriptionsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rendersEveryApprovedCardAndReserveReward() {
        AugmentConfig config = AugmentConfig.defaults();
        for (AugmentDefinition card : AugmentCatalog.definitions()) {
            String description = AugmentDescriptions.describe(card, config);
            assertFalse(description.isBlank(), card.id());
            assertEquals(description, AugmentService.offerSummary(card, config), card.id());
            assertFalse(description.contains("…"), card.id());
            assertFalse(description.contains("null"), card.id());
            assertFalse(description.contains("{"), card.id());
            assertFalse(description.contains("}"), card.id());
            assertFalse(description.contains("%%"), card.id());
            assertFalse(description.contains("초초"), card.id());
            assertFalse(description.contains("T1 기준"), card.id());
            assertFalse(description.contains("일반 영구"), card.id());
            assertFalse(AugmentService.offerSummary(card, config).contains("일반 영구"), card.id());
            assertFalse(description.contains("더합니다"), card.id());
            assertFalse(description.contains("더하며"), card.id());
            assertFalse(description.matches("(?s).*(유료|표준|적격|공격형) 인컴.*"), card.id());
            assertFalse(AugmentService.offerSummary(card, config).matches("(?s).*(표준|공격형|공격) 인컴.*"), card.id());
        }
    }

    @Test
    void usesConfiguredValuesAndDescribesTheActualRiskAndTargetRules() {
        AugmentConfig defaults = AugmentConfig.defaults();
        var json = defaults.toJson();
        json.getAsJsonObject("parameters").getAsJsonObject("semiontd:reserve_diamonds_silver")
                .addProperty("amount", 73);
        AugmentConfig changed = AugmentConfig.fromJson(json);
        String reserve = describe("reserve_diamonds_silver", changed);
        assertTrue(reserve.contains("+73"));
        assertFalse(reserve.contains("+60"));
        String tactical = describe("tactical_designation_1", defaults);
        assertTrue(tactical.contains("모드 선택"));
        assertTrue(tactical.contains("돌격:"));
        assertTrue(tactical.contains("엄호:"));
        String heat = describe("overheat_core", defaults);
        assertTrue(heat.contains("피해 -6% 영구 누적"));
        assertFalse(heat.contains("공격 간격"));
        String mastery = describe("battlefield_mastery", defaults);
        assertTrue(mastery.contains("피해 후 생존"));
        assertTrue(mastery.contains("최종 피해·최대 체력 +15%"));
        assertTrue(mastery.contains("최대 체력 20%"));
        assertTrue(describe("twin_squadron", defaults).contains("정확히 2기"));
        String barricade = describe("folding_barricade_blueprint", defaults);
        assertTrue(barricade.contains("한 번에 받는 피해 최대 15"));
        assertTrue(barricade.contains("회복 불가"));
        json.getAsJsonObject("parameters").getAsJsonObject("semiontd:folding_barricade_blueprint")
                .addProperty("damagePerHitCap", 12);
        assertTrue(describe("folding_barricade_blueprint", AugmentConfig.fromJson(json)).contains("최대 12"));
        assertTrue(barricade.contains("R15/R25 자동 강화"));
        assertTrue(describe("pulse_relay_blueprint", defaults).contains("다음 기본 공격 추가 피해 200%"));
        assertTrue(describe("starlight_cocoon_call", defaults).contains("1웨이브"));
        assertTrue(describe("support_performance", defaults).contains("경기 합계 최대 +50"));
        assertTrue(describe("giant_hunter_call", defaults).contains("적 최대 체력 12%(자연 보스 3%)"));
        assertTrue(describe("capacitor_post_blueprint", defaults).contains("충전당 추가 피해 110"));
        assertTrue(describe("cash_settlement", defaults).startsWith("매 라운드 첫 인컴"));
        assertTrue(describe("low_pressure_high_yield", defaults).startsWith("매 라운드 첫 인컴"));
        assertEquals("전투 시작 후 8초 동안 타워의 최종 피해 +35%.", describe("engagement_plan_quick", defaults));
        for (int tier = 1; tier <= 3; tier++) {
            String percent = tier * 5 + "%";
            assertEquals("타워의 최종 피해 +" + percent + ", 공격 속도 +" + percent + ", 최대 체력 +" + percent + ".",
                    describe("beneficial_effect_" + tier, defaults));
        }
        assertTrue(describe("additional_payload", defaults).startsWith("다음 유틸 인컴의 비용"));
        assertTrue(describe("support_performance", defaults).startsWith("유틸 인컴 1기"));
    }

    private static String describe(String id, AugmentConfig config) {
        return AugmentDescriptions.describe(AugmentCatalog.find(id).orElseThrow(), config);
    }

    @Test
    void jobTemplatesRenderChangedCountsPercentagesAndSecondsFromConfiguration() {
        AugmentConfig changed = new AugmentConfig(false, false, null, Map.of(
                "job_pet_towers_g1", Map.of("hitsRequired", 7.0, "damageRatio", .73),
                "job_thunder_s", Map.of("damageRatio", .73),
                "job_ocean_p", Map.of("periodTicks", 300.0, "durationTicks", 100.0)), java.util.Set.of());
        String leader = describe("job_pet_towers_g1", changed);
        assertTrue(leader.contains("7적중"));
        assertTrue(leader.contains("73%"));
        assertFalse(leader.contains("100%"));
        assertTrue(describe("job_thunder_s", changed).contains("73% 피해"));
        String tide = describe("job_ocean_p", changed);
        assertTrue(tide.contains("15초부터 15초마다 5초"), tide);
        assertEquals("7명, 73%, 1.5초", AugmentDescriptions.renderTemplate(
                "{count}명, {ratio:percent}, {ticks:seconds}초", Map.of("count", 7.0, "ratio", .73, "ticks", 30.0)));
    }

    @Test
    void unknownAndMalformedJobTemplateParametersAreRejected() {
        for (String template : java.util.List.of("{missing}", "{count:unknown}", "{count", "count}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> AugmentDescriptions.renderTemplate(template, Map.of("count", 1.0)), template);
        }
    }
}
