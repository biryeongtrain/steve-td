package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.panda.Panda;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Panda.class)
public interface PandaAccessor {
    @Accessor("MAIN_GENE_ID")
    static EntityDataAccessor<Byte> semiontd$mainGeneId() {
        throw new AssertionError();
    }

    @Accessor("HIDDEN_GENE_ID")
    static EntityDataAccessor<Byte> semiontd$hiddenGeneId() {
        throw new AssertionError();
    }

    /** 판다 상태 비트(재채기 2, 구르기 4, 앉기 8, 등 대고 눕기 16). */
    @Accessor("DATA_ID_FLAGS")
    static EntityDataAccessor<Byte> semiontd$dataIdFlags() {
        throw new AssertionError();
    }
}
