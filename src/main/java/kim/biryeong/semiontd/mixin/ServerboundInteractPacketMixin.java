package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.tower.hero.HeroPlayerVisuals;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerboundInteractPacket.class)
public abstract class ServerboundInteractPacketMixin {
    @Shadow
    @Final
    private int entityId;

    @Inject(method = "getTarget", at = @At("RETURN"), cancellable = true)
    private void semiontd$resolveHeroFakePlayer(
            ServerLevel level,
            CallbackInfoReturnable<Entity> callback
    ) {
        if (callback.getReturnValue() == null) {
            callback.setReturnValue(HeroPlayerVisuals.resolveInteractionAnchor(level, entityId));
        }
    }
}
