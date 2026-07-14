package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
abstract class ArmorSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void semionTd$lockCosmeticSlot(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (CosmeticItemSupport.isCosmetic(((Slot) (Object) this).getItem())) {
            cir.setReturnValue(false);
        }
    }
}
