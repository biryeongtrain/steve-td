package kim.biryeong.semiontd.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SemionEntityTypes {
    public static final EntityType<SemionMonsterEntity> MONSTER = register(
            "monster",
            EntityType.Builder.of(SemionMonsterEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
    );
    public static final EntityType<SemionBossEntity> BOSS = register(
            "boss",
            EntityType.Builder.of(SemionBossEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 2.9F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
    );
    public static final EntityType<SemionTestTowerEntity> TEST_TOWER = register(
            "test_tower",
            EntityType.Builder.of(SemionTestTowerEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
    );

    private SemionEntityTypes() {
    }

    public static void register() {
        PolymerEntityUtils.registerType(MONSTER);
        PolymerEntityUtils.registerType(BOSS);
        PolymerEntityUtils.registerType(TEST_TOWER);
        FabricDefaultAttributeRegistry.register(
                MONSTER,
                PathfinderMob.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 20.0)
                        .add(Attributes.ATTACK_DAMAGE, 3)
                        .add(Attributes.FOLLOW_RANGE, 3.5)
                        .add(Attributes.MOVEMENT_SPEED, 0.28)
                        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
        );
        FabricDefaultAttributeRegistry.register(
                BOSS,
                PathfinderMob.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 1000.0)
                        .add(Attributes.ATTACK_DAMAGE, 18)
                        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
        );
        FabricDefaultAttributeRegistry.register(
                TEST_TOWER,
                PathfinderMob.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 50.0)
                        .add(Attributes.ATTACK_DAMAGE, 8)
                        .add(Attributes.FOLLOW_RANGE, 8.0)
                        .add(Attributes.MOVEMENT_SPEED, 0.0)
                        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
        );
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String path,
            EntityType.Builder<T> builder
    ) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}


