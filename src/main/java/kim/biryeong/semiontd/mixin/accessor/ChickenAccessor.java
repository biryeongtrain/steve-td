package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Chicken.class)
public interface ChickenAccessor {
    @Accessor("DATA_VARIANT_ID")
    static EntityDataAccessor<Holder<ChickenVariant>> semiontd$dataVariantId() {
        throw new AssertionError();
    }
}
