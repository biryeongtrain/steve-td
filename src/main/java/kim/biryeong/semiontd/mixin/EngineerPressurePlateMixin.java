package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PressurePlateBlock.class, WeightedPressurePlateBlock.class})
abstract class EngineerPressurePlateMixin {
    @Inject(method = "getSignalStrength", at = @At("HEAD"), cancellable = true)
    private void semionTd$ignoreNonGolemPressure(Level level, BlockPos position, CallbackInfoReturnable<Integer> cir) {
        if (EngineerCircuitTower.isEngineerPlate(level, position)) {
            cir.setReturnValue(0);
        }
    }
}
