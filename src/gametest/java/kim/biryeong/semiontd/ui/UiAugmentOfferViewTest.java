package kim.biryeong.semiontd.ui;

import java.util.List;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UiAugmentOfferViewTest {

    @GameTest
    public void threeColumnsKeepAllTitlesRolesDescriptionsAndRarityLabels(GameTestHelper context) {
        var cards = List.of(card("tactical_designation_1_assault", "피해 증가와 긴 조건 설명을 줄바꿈해서 보존합니다."),
                card("biased_armor_magic", "마법 피해 감소"), card("reserve_income_prismatic", "정기 인컴 증가"));
        var bodies = UiAugmentOfferView.bodies(cards);
        var table = (PlainMessage) bodies.getLast();
        String text = table.contents().getString();
        for (var card : cards) {
            for (var part : TextUncenterer.splitLines(SemionText.mini(card.definition().rarity()
                    .markup((cards.indexOf(card) + 1) + ". " + card.definition().displayName())), 112, "ko_kr")) {
                check(text.contains(part.getString()));
            }
        }
        for (String label : List.of("실버", "골드", "프리즘", "공격", "방어", "기타")) check(text.contains(label));
        for (Component line : TextUncenterer.splitLines(table.contents(), 2000, "ko_kr")) {
            check(UiAugmentOfferView.BODY_WIDTH - 8 == TextUncenterer.width(line));
        }
        context.succeed();
    }

    @GameTest
    public void allCatalogDescriptionsStayWithinThreeColumns(GameTestHelper context) {
        for (var definition : AugmentCatalog.definitions()) {
            var card = new AugmentService.CardLine(definition.category().name(), "", definition,
                    AugmentService.offerSummary(definition, kim.biryeong.semiontd.augment.AugmentConfig.defaults()));
            var table = (PlainMessage) UiAugmentOfferView.bodies(List.of(card, card, card)).getLast();
            for (Component line : TextUncenterer.splitLines(table.contents(), 2000, "ko_kr")) {
                if (UiAugmentOfferView.BODY_WIDTH - 8 != TextUncenterer.width(line)) {
                    throw new IllegalStateException("Card row width for " + definition.id() + ": " + TextUncenterer.width(line));
                }
            }
        }
        check(UiAugmentOfferView.color(AugmentRarity.SILVER) != UiAugmentOfferView.color(AugmentRarity.GOLD));
        boolean rejected = false;
        try { UiAugmentOfferView.bodies(List.of()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
        context.succeed();
    }

    @GameTest
    public void oddLengthBoldKoreanTitlesKeepExactCellAndCombinedWidths(GameTestHelper context) {
        Component title = Component.literal("삼각진").withStyle(net.minecraft.ChatFormatting.BOLD);
        check(TextUncenterer.preciseWidth(title) == 25.5);
        Component cell = UiAugmentOfferView.centeredCell(title, 132);
        check(TextUncenterer.preciseWidth(cell) == 132.0);
        Component row = Component.empty().append(cell).append(cell).append(cell);
        check(TextUncenterer.preciseWidth(row) == 396.0);
        check(TextUncenterer.width(row) == 396);
        context.succeed();
    }

    private static void check(boolean condition) {
        if (!condition) throw new IllegalStateException("Augment card layout invariant failed");
    }

    private static AugmentService.CardLine card(String id, String summary) {
        var definition = AugmentCatalog.find(id).orElseThrow();
        return new AugmentService.CardLine(definition.category().name(), "", definition, summary);
    }
}
