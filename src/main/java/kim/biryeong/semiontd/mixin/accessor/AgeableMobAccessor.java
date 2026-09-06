package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AgeableMob.class)
public interface AgeableMobAccessor {
    @Accessor("DATA_BABY_ID")
    static EntityDataAccessor<Boolean> semiontd$dataBabyId() {
        throw new AssertionError();
    }
}
