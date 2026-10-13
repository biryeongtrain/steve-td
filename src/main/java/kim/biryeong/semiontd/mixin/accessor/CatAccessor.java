package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Cat.class)
public interface CatAccessor {
    @Accessor("DATA_VARIANT_ID")
    static EntityDataAccessor<Holder<CatVariant>> semiontd$dataVariantId() {
        throw new AssertionError();
    }

    @Accessor("DATA_COLLAR_COLOR")
    static EntityDataAccessor<Integer> semiontd$dataCollarColor() {
        throw new AssertionError();
    }
}
