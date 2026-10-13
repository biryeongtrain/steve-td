package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Cow.class)
public interface CowAccessor {
    @Accessor("DATA_VARIANT_ID")
    static EntityDataAccessor<Holder<CowVariant>> semiontd$dataVariantId() {
        throw new AssertionError();
    }
}
