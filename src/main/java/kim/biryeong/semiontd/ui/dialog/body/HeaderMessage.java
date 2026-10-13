package kim.biryeong.semiontd.ui.dialog.body;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import kim.biryeong.semiontd.ui.UiTextDivider;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;


public record HeaderMessage(Component contents, int width) implements DialogBody {

    public static final MapCodec<HeaderMessage> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ComponentSerialization.CODEC.fieldOf("contents").forGetter(HeaderMessage::contents),
                    Dialog.WIDTH_CODEC.optionalFieldOf("width", 310).forGetter(HeaderMessage::width)
            ).apply(instance, HeaderMessage::new)),
            HeaderMessage::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> mapCodec() {
        return MAP_CODEC;
    }

    public Component asVanillaComponent() {
        return headerComponent(contents, width, 0xFFFFFF);
    }

    public static int contentWidth(int bodyWidth) {
        return Math.max(0, bodyWidth - 8);
    }

    public static Component headerComponent(Component contents, int bodyWidth, int color) {
        Component title = Component.literal(" ").append(contents).append(" ");
        int remaining = Math.max(0, contentWidth(bodyWidth) - TextUncenterer.width(title));
        return Component.empty()
                .append(dividerComponent(remaining / 2).copy().withColor(color))
                .append(title)
                .append(dividerComponent(remaining - remaining / 2).copy().withColor(color));
    }

    public PlainMessage asVanillaBody(PacketContext context) {
        return new PlainMessage(asVanillaComponent(), this.width);
    }

    public static Component dividerComponent(int width) {
        return UiTextDivider.line(width);
    }

    public static PlainMessage divider(int width) {
        return new PlainMessage(dividerComponent(contentWidth(width)), width);
    }

}
