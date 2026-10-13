package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void semionTd$preventLockedOffhandCosmeticClick(
            int slotId,
            int button,
            ContainerInput clickType,
            Player player,
            CallbackInfo ci
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (CosmeticItemSupport.isLockedOffhandCosmetic(menu.getCarried())
                || FrostFullOperationService.isActivationItem(menu.getCarried())
                || slotId >= 0 && slotId < menu.slots.size()
                && (CosmeticItemSupport.isLockedOffhandCosmetic(menu.getSlot(slotId).getItem())
                        || FrostFullOperationService.isActivationItem(menu.getSlot(slotId).getItem()))) {
            ci.cancel();
        }
    }
}
