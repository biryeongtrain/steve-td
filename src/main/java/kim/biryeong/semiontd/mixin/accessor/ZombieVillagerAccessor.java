package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ZombieVillager.class)
public interface ZombieVillagerAccessor {
    @Accessor("DATA_VILLAGER_DATA")
    static EntityDataAccessor<VillagerData> semiontd$dataVillagerData() {
        throw new AssertionError();
    }
}
