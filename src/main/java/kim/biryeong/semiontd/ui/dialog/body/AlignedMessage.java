package kim.biryeong.semiontd.ui.dialog.body;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;

public record AlignedMessage(Component contents, int width, Align align) {
    public PlainMessage asBody() {
        return new PlainMessage(contents, width);
    }

    public static PlainMessage body(Component contents, int width) {
        return new AlignedMessage(contents, width, Align.LEFT).asBody();
    }

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }
}
