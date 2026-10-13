package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("DATA_VILLAGER_DATA")
    static EntityDataAccessor<VillagerData> semiontd$dataVillagerData() {
        throw new AssertionError();
    }
}
