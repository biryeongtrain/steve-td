package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.tower.demonlord.DemonLordKitItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void semionTd$preventCosmeticDeathDrop(ServerLevel level, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        for (EquipmentSlot slot : CosmeticItemSupport.supportedSlots()) {
            if (CosmeticItemSupport.isCosmetic(player.getItemBySlot(slot))) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void semionTd$preventCosmeticDrop(
            ItemStack stack,
            boolean includeThrowerName,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (CosmeticItemSupport.isLockedOffhandCosmetic(stack) || DemonLordKitItems.isKitItem(stack)) {
            cir.setReturnValue(null);
        }
    }
}
