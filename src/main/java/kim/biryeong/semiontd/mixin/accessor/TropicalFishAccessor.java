package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TropicalFish.class)
public interface TropicalFishAccessor {
    @Accessor("DATA_ID_TYPE_VARIANT")
    static EntityDataAccessor<Integer> semiontd$dataIdTypeVariant() {
        throw new AssertionError();
    }
}
