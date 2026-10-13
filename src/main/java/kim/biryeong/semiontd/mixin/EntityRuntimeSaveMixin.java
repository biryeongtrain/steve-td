package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.entity.SemionEntityTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityRuntimeSaveMixin {
    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void semiontd$skipRuntimeVisualSave(CallbackInfoReturnable<Boolean> callback) {
        if (((Entity) (Object) this).entityTags().contains(SemionEntityTypes.RUNTIME_NO_SAVE_TAG)) {
            callback.setReturnValue(false);
        }
    }
}
