package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertFalse(description.contains("null"), card.id());
            assertFalse(description.contains("{"), card.id());
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
        assertTrue(tactical.contains("모드를 고릅니다"));
        assertTrue(tactical.contains("돌격:"));
        assertTrue(tactical.contains("엄호:"));
        String heat = describe("overheat_core", defaults);
        assertTrue(heat.contains("최종 피해 6%가 영구 감소"));
        assertFalse(heat.contains("공격 간격"));
        String mastery = describe("battlefield_mastery", defaults);
        assertTrue(mastery.contains("체력 피해를 받고 생존"));
        assertTrue(mastery.contains("최종 피해와 최대 체력 +4%"));
        assertTrue(describe("twin_squadron", defaults).contains("정확히 두 기"));
    }

    private static String describe(String id, AugmentConfig config) {
        return AugmentDescriptions.describe(AugmentCatalog.find(id).orElseThrow(), config);
    }
}
