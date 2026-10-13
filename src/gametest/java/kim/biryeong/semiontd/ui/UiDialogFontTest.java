package kim.biryeong.semiontd.ui;

import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UiDialogFontTest {
    @GameTest
    public void fontAdvanceDoesNotDependOnComponentColorBoundaries(GameTestHelper context) {
        Component plain = Component.literal("ABC");
        Component colored = Component.empty()
                .append(Component.literal("A").withStyle(ChatFormatting.RED))
                .append(Component.literal("B").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("C").withStyle(ChatFormatting.BLUE));
        equal(18, TextUncenterer.width(plain));
        equal(TextUncenterer.width(plain), TextUncenterer.width(colored));
        equal(21, TextUncenterer.width(plain.copy().withStyle(ChatFormatting.BOLD)));
        context.succeed();
    }

    @GameTest
    public void gradientAndBoldHeadersFitWithoutDroppingTheRightRule(GameTestHelper context) {
        for (String title : List.of("<bold>ABC</bold>", "<gradient:red:blue><bold>타워 상세 정보</bold></gradient>")) {
            Component contents = UiDialogBodyRenderer.miniMessage(title);
            for (int width : List.of(200, 256, 420, 460)) {
                Component header = new HeaderMessage(contents, width).asVanillaComponent();
                equal(width - 8, TextUncenterer.width(header));
                check(TextUncenterer.width(header.getSiblings().getFirst()) > 0);
                check(TextUncenterer.width(header.getSiblings().getLast()) > 0);
            }
        }
        context.succeed();
    }

    @GameTest
    public void oversizedTitlesKeepTheirTextWithoutNegativeRules(GameTestHelper context) {
        Component title = Component.literal("W".repeat(60));
        Component header = new HeaderMessage(title, 200).asVanillaComponent();
        equal(" " + title.getString() + " ", header.getString());
        equal(0, TextUncenterer.width(header.getSiblings().getFirst()));
        equal(0, TextUncenterer.width(header.getSiblings().getLast()));
        context.succeed();
    }

    @GameTest
    public void alternateHeaderKeepsItsRequestedColor(GameTestHelper context) {
        PlainMessage body = UiDialogBodyRenderer.decoratedHeader(Component.literal("ABC"), 200, ChatFormatting.DARK_GRAY);
        equal(192, TextUncenterer.width(body.contents()));
        equal(net.minecraft.network.chat.TextColor.DARK_GRAY.getValue(), body.contents().getSiblings().getFirst().getStyle().getColor().getValue());
        context.succeed();
    }

    @GameTest
    public void koreanAndBoxGlyphAdvancesMatchClientFont(GameTestHelper context) {
        equal(8, TextUncenterer.width(Component.literal("가")));
        equal(9, TextUncenterer.width(Component.literal("─")));
        equal(18, TextUncenterer.width(Component.literal("가A ")));
        equal(17, TextUncenterer.width(Component.literal("가나").withStyle(ChatFormatting.BOLD)));
        equal(TextUncenterer.width(Component.literal("────")), TextUncenterer.width(SemionText.mini(UiTextDivider.hudMarkup())));
        context.succeed();
    }

    @GameTest
    public void encodedBodiesPreserveContainerWidthAndRuleStyle(GameTestHelper context) {
        var ops = context.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for (int width : List.of(256, 420, 460, 480)) {
            PlainMessage original = HeaderMessage.divider(width);
            var encoded = PlainMessage.CODEC.encodeStart(ops, original).getOrThrow();
            PlainMessage decoded = PlainMessage.CODEC.parse(ops, encoded).getOrThrow();
            equal(width, decoded.width());
            equal(width - 8, TextUncenterer.width(decoded.contents()));
            check(decoded.contents().getStyle().isStrikethrough());
            equal(original.contents(), decoded.contents());
        }
        context.succeed();
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException("Expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Dialog layout invariant failed");
        }
    }
}
