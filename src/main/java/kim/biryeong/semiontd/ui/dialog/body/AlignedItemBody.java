package kim.biryeong.semiontd.ui.dialog.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

public record AlignedItemBody(
        ItemStack item,
        AlignedMessage description,
        boolean showDecorations,
        boolean showTooltip,
        int width,
        int height
) implements DialogBody {
    public static final MapCodec<AlignedItemBody> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ItemStack.STRICT_CODEC.fieldOf("item").forGetter(AlignedItemBody::item),
                    AlignedMessage.DIALOG_BODY_CODEC.fieldOf("description").forGetter(AlignedItemBody::description),
                    Codec.BOOL.optionalFieldOf("show_decorations", true).forGetter(AlignedItemBody::showDecorations),
                    Codec.BOOL.optionalFieldOf("show_tooltip", true).forGetter(AlignedItemBody::showTooltip),
                    ExtraCodecs.intRange(1, 256).optionalFieldOf("width", 16).forGetter(AlignedItemBody::width),
                    ExtraCodecs.intRange(1, 256).optionalFieldOf("height", 16).forGetter(AlignedItemBody::height)
            ).apply(instance, AlignedItemBody::new)),
            AlignedItemBody::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> mapCodec() {
        return MAP_CODEC;
    }

    public ItemBody asVanillaBody(PacketContext context) {
        return new ItemBody(
                this.item,
                Optional.of(this.description.asVanillaBody(context)),
                this.showDecorations,
                this.showTooltip,
                this.width,
                this.height
        );
    }
}