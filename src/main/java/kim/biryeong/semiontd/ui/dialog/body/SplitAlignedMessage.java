package kim.biryeong.semiontd.ui.dialog.body;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

public record SplitAlignedMessage(Component left, Component right, int width) implements DialogBody {
    private static final int TEXT_SAFE_MARGIN = 56;

    public static final MapCodec<SplitAlignedMessage> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ComponentSerialization.CODEC.fieldOf("left").forGetter(SplitAlignedMessage::left),
                    ComponentSerialization.CODEC.fieldOf("right").forGetter(SplitAlignedMessage::right),
                    Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(SplitAlignedMessage::width)
            ).apply(instance, SplitAlignedMessage::new)),
            SplitAlignedMessage::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> mapCodec() {
        return MAP_CODEC;
    }

    public PlainMessage asVanillaBody(PacketContext context) {
        String language = xyz.nucleoid.server.translations.api.LocalizationTarget.of(context).getLanguageCode();
        int textWidth = Math.max(0, this.width - TEXT_SAFE_MARGIN);
        List<Component> lines = new ArrayList<>();
        lines.addAll(TextUncenterer.getLeftAlignedNoWrap(this.left, textWidth, language));
        lines.addAll(TextUncenterer.getRightAlignedNoWrap(this.right, textWidth, language));
        return new PlainMessage(CommonComponents.joinLines(lines), this.width);
    }
}
