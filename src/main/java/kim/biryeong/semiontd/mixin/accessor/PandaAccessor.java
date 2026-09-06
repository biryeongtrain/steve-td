package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.animal.Panda;
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
}
