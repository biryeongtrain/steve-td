package kim.biryeong.semiontd.ui;

import java.util.List;
import kim.biryeong.semiontd.ui.dialog.body.HeaderMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

final class UiDialogBodyRenderer {
    private UiDialogBodyRenderer() {
    }

    static Component miniMessage(String text) {
        try {
            return SemionText.mini(text);
        } catch (RuntimeException exception) {
            return Component.literal(text);
        }
    }

    static MutableComponent mutableMiniMessage(String text) {
        try {
            return SemionText.mutableMini(text);
        } catch (RuntimeException exception) {
            return Component.empty().append(Component.literal(text));
        }
    }

    static PlainMessage decoratedHeader(Component title, int width) {
        return decoratedHeader(title, width, ChatFormatting.DARK_GRAY);
    }

    static PlainMessage decoratedHeader(Component title, int width, ChatFormatting sideColor) {
        return new PlainMessage(HeaderMessage.headerComponent(title, width, Component.empty().withStyle(sideColor).getStyle().getColor().getValue()), width);
    }

    static List<DialogBody> actionDialogBodies(String body, int width) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String normalized = body.replace("\r\n", "\n");
        int split = normalized.indexOf('\n');
        if (split < 0) {
            return List.of(new HeaderMessage(miniMessage(normalized), width));
        }
        MutableComponent contents = Component.empty();
        String header = normalized.substring(0, split);
        if (!header.isBlank()) {
            contents.append(new HeaderMessage(miniMessage(header), width).asVanillaComponent());
        }
        String[] sections = normalized.substring(split + 1).split("(?m)^<divider>\\n?", -1);
        for (int index = 0; index < sections.length; index++) {
            String section = sections[index].strip();
            if (!section.isBlank()) {
                if (!contents.getSiblings().isEmpty()) {
                    contents.append("\n");
                }
                contents.append(miniMessage(section));
            }
            if (index < sections.length - 1) {
                if (!contents.getSiblings().isEmpty()) {
                    contents.append("\n");
                }
                contents.append(HeaderMessage.dividerComponent(HeaderMessage.contentWidth(width)));
            }
        }
        return contents.getSiblings().isEmpty() ? List.of() : List.of(new PlainMessage(contents, width));
    }
}
