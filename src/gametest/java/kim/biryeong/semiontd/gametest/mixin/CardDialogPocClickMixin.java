package kim.biryeong.semiontd.gametest.mixin;

import kim.biryeong.semiontd.ui.CardDialogPoc;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CardDialogPocClickMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void semionCaptureCardClick(ServerboundCustomClickActionPacket packet, CallbackInfo info) {
        if (!CardDialogPoc.accepts(packet.id()) || !((Object) this instanceof ServerGamePacketListenerImpl listener)
                || !listener.player.getGameProfile().name().equals("SemionCapture")) return;
        var player = listener.player;
        player.level().getServer().execute(() -> CardDialogPoc.handle(player, packet));
        info.cancel();
    }
}
