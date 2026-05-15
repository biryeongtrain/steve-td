package kim.biryeong.semiontd.ui.dialog.body;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.body.PlainMessage;

public record ImageBody(ResourceLocation id, Optional<Component> description, int width) {
    public PlainMessage asFallbackBody() {
        Component text = Component.literal("[image:" + id + "]");
        if (description.isPresent()) {
            text = Component.empty().append(text).append(Component.literal("\n")).append(description.get());
        }
        return new PlainMessage(text, width);
    }
}
