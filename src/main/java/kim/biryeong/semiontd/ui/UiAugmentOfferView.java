package kim.biryeong.semiontd.ui;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.augment.AugmentDisplayRole;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import kim.biryeong.semiontd.ui.rp.AugmentCardIcons;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

final class UiAugmentOfferView {
    static final int CARD_WIDTH = 132;
    static final int GAP = 10;
    static final int BODY_WIDTH = CARD_WIDTH * 3 + GAP * 2 + 8;
    private static final int TEXT_WIDTH = CARD_WIDTH - 20;

    private UiAugmentOfferView() {
    }

    static List<DialogBody> bodies(List<AugmentService.CardLine> cards) {
        if (cards.size() != 3) throw new IllegalArgumentException("An augment offer needs three cards");
        List<DialogBody> result = new ArrayList<>();
        if (AugmentCardIcons.available()) {
            MutableComponent icons = Component.literal("\n".repeat(5));
            for (int index = 0; index < cards.size(); index++) {
                var card = cards.get(index).definition();
                var icon = AugmentCardIcons.icon(card.rarity(), AugmentDisplayRole.of(card));
                if (index > 0) icons.append(SemionUiFont.space(GAP));
                icons.append(UiDialogTableLayout.centeredTableCell(icon.text(), CARD_WIDTH, icon.width()));
            }
            result.add(new PlainMessage(icons.append("\n"), BODY_WIDTH));
        }
        List<List<Component>> titles = new ArrayList<>();
        List<List<Component>> descriptions = new ArrayList<>();
        for (int index = 0; index < cards.size(); index++) {
            var card = cards.get(index);
            titles.add(TextUncenterer.splitLines(SemionText.mini(card.definition().rarity()
                    .markup((index + 1) + ". " + card.definition().displayName())), TEXT_WIDTH, "ko_kr"));
            descriptions.add(TextUncenterer.splitLines(Component.literal(card.summary()), TEXT_WIDTH, "ko_kr"));
        }
        MutableComponent table = Component.empty();
        appendRules(table, cards);
        appendRows(table, cards, titles);
        List<List<Component>> labels = cards.stream().map(card -> List.<Component>of(SemionText.mini(card.definition().rarity()
                .markup(rarityName(card.definition().rarity())))
                .copy().append(" · " + AugmentDisplayRole.of(card.definition()).label()))).toList();
        appendRows(table, cards, labels);
        appendRows(table, cards, cards.stream().map(card -> TextUncenterer.splitLines(
                Component.literal(card.definition().requiredJobId() == null ? "공용" : kim.biryeong.semiontd.job.JobRegistry
                        .find(net.minecraft.resources.Identifier.parse(card.definition().requiredJobId()))
                        .map(job -> job.displayName().getString()).orElse(card.definition().requiredJobId())),
                TEXT_WIDTH, "ko_kr")).toList());
        appendRows(table, cards, cards.stream().map(card -> List.<Component>of(Component.literal(" "))).toList());
        appendRows(table, cards, descriptions);
        appendRules(table, cards);
        result.add(new PlainMessage(table, BODY_WIDTH));
        return List.copyOf(result);
    }

    private static void appendRows(MutableComponent table, List<AugmentService.CardLine> cards,
                                   List<List<Component>> columns) {
        int rows = columns.stream().mapToInt(List::size).max().orElse(0);
        for (int row = 0; row < rows; row++) {
            newline(table);
            for (int column = 0; column < 3; column++) {
                if (column > 0) table.append(SemionUiFont.space(GAP));
                Component edge = Component.literal("│").withColor(color(cards.get(column).definition().rarity()));
                int edgeWidth = TextUncenterer.width(edge);
                Component value = row < columns.get(column).size() ? columns.get(column).get(row) : Component.empty();
                table.append(edge).append(centeredCell(value, CARD_WIDTH - edgeWidth * 2)).append(edge);
            }
        }
    }

    static Component centeredCell(Component value, int width) {
        double remaining = Math.max(0, width - TextUncenterer.preciseWidth(value));
        int left = (int) Math.floor(remaining / 2);
        double right = remaining - left;
        MutableComponent result = Component.empty().append(SemionUiFont.space(left)).append(value)
                .append(SemionUiFont.space((int) Math.floor(right)));
        if (right - Math.floor(right) > 0.25) result.append(SemionUiFont.halfPixel());
        return result;
    }

    private static void appendRules(MutableComponent table, List<AugmentService.CardLine> cards) {
        newline(table);
        for (int index = 0; index < cards.size(); index++) {
            if (index > 0) table.append(SemionUiFont.space(GAP));
            table.append(HeaderMessage.dividerComponent(CARD_WIDTH).copy().withColor(color(cards.get(index).definition().rarity())));
        }
    }

    private static void newline(MutableComponent text) {
        if (!text.getSiblings().isEmpty()) text.append("\n");
    }

    static int color(AugmentRarity rarity) {
        return switch (rarity) {
            case SILVER -> 0xCBD5E1;
            case GOLD -> 0xFBBF24;
            case PRISMATIC -> 0xC084FC;
        };
    }

    static String rarityName(AugmentRarity rarity) {
        return switch (rarity) {
            case SILVER -> "실버";
            case GOLD -> "골드";
            case PRISMATIC -> "프리즘";
        };
    }
}
