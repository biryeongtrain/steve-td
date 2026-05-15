package kim.biryeong.semiontd.ui.dialog.body;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.PlainMessage;

public record HeaderMessage(Component contents, int width) {
    public PlainMessage asBody() {
        return new PlainMessage(
                Component.empty()
                        .append(Component.literal("    "))
                        .append(contents)
                        .append(Component.literal("    ")),
                width
        );
    }

    public static PlainMessage body(Component contents, int width) {
        return new HeaderMessage(contents, width).asBody();
    }
}
