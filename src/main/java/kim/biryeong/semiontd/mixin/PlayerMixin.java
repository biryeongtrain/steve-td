package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void semionTd$preventCosmeticDeathDrop(ServerLevel level, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (CosmeticItemSupport.isCosmetic(player.getItemBySlot(EquipmentSlot.HEAD))) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }
}
