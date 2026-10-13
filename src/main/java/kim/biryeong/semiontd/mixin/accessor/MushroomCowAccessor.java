package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MushroomCow.class)
public interface MushroomCowAccessor {
    @Accessor("DATA_TYPE")
    static EntityDataAccessor<Integer> semiontd$dataType() {
        throw new AssertionError();
    }
}
