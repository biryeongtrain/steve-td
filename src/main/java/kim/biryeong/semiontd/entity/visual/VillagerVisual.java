package kim.biryeong.semiontd.entity.visual;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;

public final class VillagerVisual {
    private VillagerVisual() {
    }

    public static Builder builder() {
        return new Builder("minecraft:villager");
    }

    public static Builder zombieBuilder() {
        return new Builder("minecraft:zombie_villager");
    }

    public static final class Builder {
        private final EntityVisual.Builder visual;

        private Builder(String entityTypeId) {
            visual = EntityVisual.builder(entityTypeId);
        }

        public Builder type(ResourceKey<VillagerType> type) {
            visual.propertyValue(EntityVisualProperties.VILLAGER_TYPE, type);
            return this;
        }

        public Builder profession(ResourceKey<VillagerProfession> profession) {
            visual.propertyValue(EntityVisualProperties.VILLAGER_PROFESSION, profession);
            return this;
        }

        public Builder level(int level) {
            visual.propertyValue(EntityVisualProperties.VILLAGER_LEVEL, level);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
