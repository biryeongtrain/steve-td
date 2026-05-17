package kim.biryeong.semiontd.ui.dialog.body;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerMapCodec;
import kim.biryeong.semiontd.ui.rp.ImageHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public record ImageBody(ResourceLocation id, Optional<Component> description) implements DialogBody {
    public static final MapCodec<ImageBody> MAP_CODEC = PolymerMapCodec.ofDialogBody(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("image").forGetter(ImageBody::id),
                    ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(ImageBody::description)
            ).apply(instance, ImageBody::new)),  ImageBody::asVanillaBody
    );

    @Override
    public MapCodec<? extends DialogBody> mapCodec() {
        return MAP_CODEC;
    }

    public PlainMessage asVanillaBody(PacketContext context) {
        var image = ImageHandler.getImage(this.id);
        Component text = image.text();
        if (description.isPresent()) {
            text = Component.empty().append(text).append("\n").append(description.get());
        }

        return new PlainMessage(text, Math.min(image.width(), 1024));
    }
}