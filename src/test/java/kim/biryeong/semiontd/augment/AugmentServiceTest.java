package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AugmentServiceTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rarityMarkupRendersDistinctBoldColorsAndEscapesInputTags() {
        int[] starts = {0xcbd5e1, 0xfbbf24, 0xc084fc};
        for (AugmentRarity rarity : AugmentRarity.values()) {
            var text = SemionText.mini(rarity.markup("증강색"));
            assertEquals("증강색", text.getString());
            List<Integer> colors = new ArrayList<>();
            text.visit((style, value) -> {
                value.codePoints().forEach(ignored -> {
                    assertTrue(style.isBold());
                    colors.add(style.getColor().getValue());
                });
                return Optional.empty();
            }, Style.EMPTY);
            assertEquals(3, colors.size());
            assertEquals(starts[rarity.ordinal()], colors.getFirst());
            assertEquals(rarity == AugmentRarity.SILVER ? starts[0] : 0xffffff, colors.getLast());
            assertEquals(rarity == AugmentRarity.SILVER ? 1 : 3, colors.stream().distinct().count());
            assertEquals("<red>증강</red>", SemionText.mini(rarity.markup("<red>증강</red>")).getString());
        }
    }

    @Test
    void permanentTargetCardsCannotBeReconfigured() {
        for (String id : List.of("semiontd:one_man_show", "semiontd:battlefield_mastery")) {
            assertEquals(1, AugmentService.targetCount(id));
            assertFalse(AugmentService.configurable(id));
        }
        assertTrue(AugmentService.configurable("semiontd:overheat_core"));
        assertEquals(2, AugmentService.targetCount("semiontd:frontline_specialization"));
    }

    @Test
    void modeButtonsUseTheCombatContractAndNeverOfferBothAtOnce() {
        assertEquals(List.of("ASSAULT", "COVER"), AugmentService.modes("semiontd:tactical_designation_3"));
        assertEquals(List.of("QUICK", "LONG"), AugmentService.modes("semiontd:engagement_plan"));
        assertEquals(List.of("PHYSICAL", "MAGIC"), AugmentService.modes("semiontd:biased_armor"));
        assertTrue(AugmentService.modes("semiontd:one_man_show").isEmpty());
    }
}
