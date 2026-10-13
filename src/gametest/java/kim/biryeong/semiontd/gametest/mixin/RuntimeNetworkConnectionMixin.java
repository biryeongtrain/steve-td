package kim.biryeong.semiontd.gametest.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class RuntimeNetworkConnectionMixin {
    @Shadow private Channel channel;
    @Shadow private volatile PacketListener packetListener;

    @Shadow
    private void sendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        throw new AssertionError();
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void semionRuntimeSendOnServerThread(Packet<?> packet, ChannelFutureListener listener,
            boolean flush, CallbackInfo info) {
        if (!(channel instanceof EmbeddedChannel)
                || !(packetListener instanceof ServerGamePacketListenerImpl handler)) {
            return;
        }
        var server = handler.player.level().getServer();
        if (server instanceof GameTestServer && !server.isSameThread()) {
            server.execute(() -> sendPacket(packet, listener, flush));
            info.cancel();
        }
    }
}
