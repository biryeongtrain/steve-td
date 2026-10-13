package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.tower.demonlord.DemonLordKitItems;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Player inherits this drop implementation in 26.3.
@Mixin(LivingEntity.class)
abstract class LivingEntityDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void semionTd$preventCosmeticDrop(
            ItemStack stack,
            boolean includeThrowerName,
            Prediction prediction,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if ((Object) this instanceof net.minecraft.world.entity.player.Player && (CosmeticItemSupport.isLockedOffhandCosmetic(stack)
                || DemonLordKitItems.isKitItem(stack)
                || FrostFullOperationService.isActivationItem(stack))) {
            cir.setReturnValue(null);
        }
    }
}
