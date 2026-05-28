package kim.biryeong.semiontd.entity;

import eu.pb4.polymer.common.impl.entity.InternalEntityHelpers;
import java.util.LinkedHashSet;
import java.util.Set;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

public final class SemionPolymerEntityDataWarmup {
    private SemionPolymerEntityDataWarmup() {
    }

    public static void warm(LoadedConfigs configs, Logger logger) {
        Set<EntityType<?>> entityTypes = new LinkedHashSet<>();
        addBaseEntityTypes(entityTypes);
        addConfiguredWaveEntityTypes(entityTypes, configs);
        addSummonEntityTypes(entityTypes);
        addTowerEntityTypes(entityTypes);
        warmBilModels(configs, logger);

        int warmed = 0;
        for (EntityType<?> entityType : entityTypes) {
            try {
                InternalEntityHelpers.getExampleTrackedDataOfEntityType(entityType);
                warmed++;
            } catch (RuntimeException exception) {
                logger.warn("Failed to warm Polymer tracked data for {}.", BuiltInRegistries.ENTITY_TYPE.getKey(entityType), exception);
            }
        }
        logger.info("Warmed Polymer tracked data for {} entity types.", warmed);
    }

    private static void warmBilModels(LoadedConfigs configs, Logger logger) {
        Set<String> modelIds = new LinkedHashSet<>();
        addConfiguredWaveBilModels(modelIds, configs);
        addSummonBilModels(modelIds);
        addTowerBilModels(modelIds);

        int warmed = 0;
        for (String modelId : modelIds) {
            if (SemionBilModelCache.load(modelId).isPresent()) {
                warmed++;
            } else {
                logger.warn("Failed to warm BIL model {}.", modelId);
            }
        }
        logger.info("Warmed {} BIL models for generated resource pack.", warmed);
    }

    private static void addBaseEntityTypes(Set<EntityType<?>> entityTypes) {
        entityTypes.add(EntityType.ARMOR_STAND);
        entityTypes.add(EntityType.HUSK);
        entityTypes.add(EntityType.IRON_GOLEM);
        entityTypes.add(EntityType.SKELETON);
        entityTypes.add(EntityType.VILLAGER);
        entityTypes.add(EntityType.ZOMBIE);
        entityTypes.add(EntityType.ZOMBIE_VILLAGER);
    }

    private static void addConfiguredWaveEntityTypes(Set<EntityType<?>> entityTypes, LoadedConfigs configs) {
        if (configs == null || configs.waves() == null) {
            return;
        }
        configs.waves().rounds().forEach(round -> addRoundEntityTypes(entityTypes, round));
        addRoundEntityTypes(entityTypes, configs.waves().infinite());
    }

    private static void addRoundEntityTypes(Set<EntityType<?>> entityTypes, RoundWaveConfig round) {
        if (round == null) {
            return;
        }
        round.lanes().values().stream()
                .filter(entries -> entries != null)
                .forEach(entries -> entries.forEach(entry -> addWaveEntryEntityType(entityTypes, entry)));
    }

    private static void addWaveEntryEntityType(Set<EntityType<?>> entityTypes, WaveMonsterEntry entry) {
        if (entry == null || (entry.blockbenchModelId() != null && !entry.blockbenchModelId().isBlank())) {
            return;
        }
        addEntityType(entityTypes, entry.entityType());
    }

    private static void addSummonEntityTypes(Set<EntityType<?>> entityTypes) {
        SummonRegistry.all().forEach(summonType -> {
            if (summonType.blockbenchModelId().isEmpty()) {
                addEntityType(entityTypes, summonType.entityTypeId());
            }
        });
    }

    private static void addTowerEntityTypes(Set<EntityType<?>> entityTypes) {
        ProductionTowerCatalog.all().forEach(entry -> {
            if (entry.type().blockbenchModel().isEmpty()) {
                addEntityType(entityTypes, entry.type().entityTypeId());
            }
        });
    }

    private static void addEntityType(Set<EntityType<?>> entityTypes, String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            return;
        }
        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(entityTypes::add);
    }

    private static void addConfiguredWaveBilModels(Set<String> modelIds, LoadedConfigs configs) {
        if (configs == null || configs.waves() == null) {
            return;
        }
        configs.waves().rounds().forEach(round -> addRoundBilModels(modelIds, round));
        addRoundBilModels(modelIds, configs.waves().infinite());
    }

    private static void addRoundBilModels(Set<String> modelIds, RoundWaveConfig round) {
        if (round == null) {
            return;
        }
        round.lanes().values().stream()
                .filter(entries -> entries != null)
                .forEach(entries -> entries.forEach(entry -> addBilModel(modelIds, entry.blockbenchModelId())));
    }

    private static void addSummonBilModels(Set<String> modelIds) {
        SummonRegistry.all().forEach(summonType -> summonType.blockbenchModelId().ifPresent(modelId -> addBilModel(modelIds, modelId)));
    }

    private static void addTowerBilModels(Set<String> modelIds) {
        ProductionTowerCatalog.all().forEach(entry -> entry.type().blockbenchModel().ifPresent(modelId -> addBilModel(modelIds, modelId)));
    }

    private static void addBilModel(Set<String> modelIds, String modelId) {
        String normalized = SemionBilModelCache.normalize(modelId);
        if (normalized != null) {
            modelIds.add(normalized);
        }
    }
}
