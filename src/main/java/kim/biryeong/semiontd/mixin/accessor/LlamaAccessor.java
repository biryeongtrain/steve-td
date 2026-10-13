package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Llama.class)
public interface LlamaAccessor {
    @Accessor("DATA_VARIANT_ID")
    static EntityDataAccessor<Integer> semiontd$dataVariantId() {
        throw new AssertionError();
    }
}
