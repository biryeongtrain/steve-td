package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.fish.Salmon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Salmon.class)
public interface SalmonAccessor {
    @Accessor("DATA_TYPE")
    static EntityDataAccessor<Integer> semiontd$dataType() {
        throw new AssertionError();
    }
}
