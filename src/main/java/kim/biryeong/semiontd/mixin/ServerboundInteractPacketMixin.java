package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerboundInteractPacketMixin {
    @Shadow public ServerPlayer player;

    // Fabric 26.3's own interaction hook identifies this local as target.
    // Resolve virtual hero IDs before vanilla reach/interaction validation.
    @ModifyVariable(method = "handleInteract", at = @At("STORE"), name = "target")
    private Entity semiontd$resolveHeroFakePlayer(Entity target, ServerboundInteractPacket packet) {
        return target != null ? target : FakePlayerTowerVisuals.resolveInteractionAnchor(player.level(), packet.entityId());
    }
}