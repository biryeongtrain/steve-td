package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;

final class UiDialogTableLayout {
    private UiDialogTableLayout() {
    }

    static Component centeredTableCell(Component value, int width) {
        return centeredTableCell(value, width, TextUncenterer.width(value));
    }

    static Component centeredTableCell(Component value, int width, int valueWidth) {
        int remainingWidth = Math.max(0, width - valueWidth);
        int leftPaddingWidth = remainingWidth / 2;
        return Component.empty()
                .append(TextUncenterer.filler(leftPaddingWidth))
                .append(value)
                .append(TextUncenterer.filler(remainingWidth - leftPaddingWidth));
    }
}
