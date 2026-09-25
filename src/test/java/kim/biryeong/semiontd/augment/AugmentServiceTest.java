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
    @Test
    void historyOnlyRevealsRarityOnceThatMilestoneHasBeenOffered() {
        var state = new PlayerAugmentState(java.util.UUID.randomUUID());
        var schedule = List.of(AugmentRarity.SILVER, AugmentRarity.GOLD, AugmentRarity.PRISMATIC);
        var names = List.of("실버", "골드", "프리즘");
        state.initialize(42L, AugmentConfig.defaults(), schedule);
        for (int index = 0; index < AugmentCatalog.MILESTONES.size(); index++) {
            int milestone = AugmentCatalog.MILESTONES.get(index);
            for (int future : AugmentCatalog.MILESTONES.subList(index, AugmentCatalog.MILESTONES.size())) {
                assertEquals("R" + future + " [미공개] ", AugmentService.historyMilestoneLabel(state, future));
            }
            var offer = state.offer(milestone, milestone, AugmentService.PREPARE_TICKS, ignored -> true);
            String expected = "R" + milestone + " [" + names.get(index) + "] ";
            assertEquals(expected, SemionText.mini(AugmentService.historyMilestoneLabel(state, milestone)).getString());
            if (index == 1) {
                assertTrue(state.expire(offer.deadlineTickExclusive(), ignored -> true,
                        ignored -> AugmentChoice.none(), (card, choice) -> true));
                assertTrue(state.selections().stream().anyMatch(selection -> selection.milestoneRound() == milestone
                        && selection.outcome() == PlayerAugmentState.Outcome.SELECTED));
            } else {
                assertTrue(state.skip(milestone, offer.revision(), java.util.UUID.randomUUID(),
                        offer.inputAllowedTick(), PlayerAugmentState.SkipReason.EXPLICIT).successful());
            }
            for (int past = 0; past <= index; past++) {
                assertEquals("R" + AugmentCatalog.MILESTONES.get(past) + " [" + names.get(past) + "] ",
                        SemionText.mini(AugmentService.historyMilestoneLabel(state, AugmentCatalog.MILESTONES.get(past))).getString());
            }
        }
        assertEquals(schedule, state.raritySchedule());
    }

    @Test
    void offerHeaderOnlyShowsCompactAcquisitionAndTargetToolGuidance() {
        var state = new PlayerAugmentState(java.util.UUID.randomUUID());
        assertEquals("선택한 증강 0/3 · 전체 리롤 5/5회\n증강은 즉시 획득합니다."
                        + "\n지정형 증강은 도구로 타워를 선택할 수 있습니다.",
                SemionText.mini(AugmentService.offerHeader(state)).getString());
    }

    @Test
    void jobCardHoverShowsJobRarityAndConfiguredEffect() {
        var card = AugmentCatalog.find("job_illager_towers_s").orElseThrow();
        var message = AugmentService.cardWithHover(card, AugmentConfig.defaults());
        assertTrue(message.getString().contains("흉조"));
        assertTrue(message.getString().contains("실버"));
        assertTrue(message.getString().contains("우민"));
        var hover = (net.minecraft.network.chat.HoverEvent.ShowText) message.getStyle().getHoverEvent();
        assertTrue(hover.value().getString().contains("20%"));
        assertTrue(hover.value().getString().contains("4초"));
    }
    @Test
    void selectionErrorsDistinguishStaleButtonsFromChangedConditions() {
        assertTrue(AugmentService.selectionError(PlayerAugmentState.Status.COMMIT_REJECTED).contains("무작위"));
        assertTrue(AugmentService.selectionError(PlayerAugmentState.Status.STALE_REVISION).contains("이전 화면"));
    }
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
    void allTargetCardsCanBeReconfigured() {
        for (String id : List.of("semiontd:one_man_show", "semiontd:battlefield_mastery")) {
            assertEquals(1, AugmentService.targetCount(id));
            assertTrue(AugmentService.configurable(id));
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
        var fixedCards = AugmentCatalog.normalDefinitions().stream()
                .filter(card -> !AugmentCatalog.fixedMode(card.id()).isEmpty()).toList();
        assertEquals(10, fixedCards.size());
        for (var card : fixedCards) {
            assertTrue(AugmentService.modes(card.id()).isEmpty(), card.id());
            boolean tactical = card.id().contains("tactical_designation");
            assertEquals(tactical ? 1 : 0, AugmentService.targetCount(card.id()), card.id());
            assertEquals(tactical, AugmentService.configurable(card.id()), card.id());
            assertFalse(AugmentDescriptions.describe(card, AugmentConfig.defaults()).contains("또는"), card.id());
        }
    }
}
