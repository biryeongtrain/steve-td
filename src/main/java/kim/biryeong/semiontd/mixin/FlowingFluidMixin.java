package kim.biryeong.semiontd.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.fantasy.RuntimeWorld;

@Mixin(FlowingFluid.class)
abstract class FlowingFluidMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void semiontd$stopRuntimeWorldWaterFlow(
            ServerLevel level,
            BlockPos pos,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo callback
    ) {
        if (level instanceof RuntimeWorld && fluidState.is(FluidTags.WATER)) {
            callback.cancel();
        }
    }
}
