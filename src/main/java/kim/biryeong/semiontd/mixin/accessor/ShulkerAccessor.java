package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Shulker.class)
public interface ShulkerAccessor {
    @Accessor("DATA_COLOR_ID")
    static EntityDataAccessor<Byte> semiontd$dataColorId() {
        throw new AssertionError();
    }
}
