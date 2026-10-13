package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SnowGolem.class)
public interface SnowGolemAccessor {
    @Accessor("DATA_PUMPKIN_ID")
    static EntityDataAccessor<Byte> semiontd$dataPumpkinId() {
        throw new AssertionError();
    }
}
