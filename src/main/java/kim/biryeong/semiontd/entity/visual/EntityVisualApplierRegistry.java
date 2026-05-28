package kim.biryeong.semiontd.entity.visual;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.ToIntFunction;
import kim.biryeong.semiontd.mixin.accessor.AxolotlAccessor;
import kim.biryeong.semiontd.mixin.accessor.CatAccessor;
import kim.biryeong.semiontd.mixin.accessor.ChickenAccessor;
import kim.biryeong.semiontd.mixin.accessor.CowAccessor;
import kim.biryeong.semiontd.mixin.accessor.FoxAccessor;
import kim.biryeong.semiontd.mixin.accessor.FrogAccessor;
import kim.biryeong.semiontd.mixin.accessor.HorseAccessor;
import kim.biryeong.semiontd.mixin.accessor.LlamaAccessor;
import kim.biryeong.semiontd.mixin.accessor.MushroomCowAccessor;
import kim.biryeong.semiontd.mixin.accessor.ParrotAccessor;
import kim.biryeong.semiontd.mixin.accessor.PigAccessor;
import kim.biryeong.semiontd.mixin.accessor.RabbitAccessor;
import kim.biryeong.semiontd.mixin.accessor.SalmonAccessor;
import kim.biryeong.semiontd.mixin.accessor.SheepAccessor;
import kim.biryeong.semiontd.mixin.accessor.SlimeAccessor;
import kim.biryeong.semiontd.mixin.accessor.TamableAnimalAccessor;
import kim.biryeong.semiontd.mixin.accessor.TropicalFishAccessor;
import kim.biryeong.semiontd.mixin.accessor.VillagerAccessor;
import kim.biryeong.semiontd.mixin.accessor.WolfAccessor;
import kim.biryeong.semiontd.mixin.accessor.ZombieVillagerAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;

public final class EntityVisualApplierRegistry {
    private EntityVisualApplierRegistry() {
    }

    public static void apply(
            EntityVisual visual,
            EntityType<?> entityType,
            RegistryAccess registryAccess,
            List<SynchedEntityData.DataValue<?>> data
    ) {
        if (visual == null || visual.blockbenchModel().isPresent() || entityType == null || registryAccess == null || data == null) {
            return;
        }

        applyVillagerData(visual, entityType, registryAccess, data);
        applyHolderVariants(visual, entityType, registryAccess, data);
        applyIntegerVariants(visual, entityType, data);
        applyTamableState(visual, entityType, data);
    }

    private static void applyVillagerData(
            EntityVisual visual,
            EntityType<?> entityType,
            RegistryAccess registryAccess,
            List<SynchedEntityData.DataValue<?>> data
    ) {
        if (entityType != EntityType.VILLAGER && entityType != EntityType.ZOMBIE_VILLAGER) {
            return;
        }
        if (!hasAnyProperty(
                visual,
                EntityVisualProperties.VILLAGER_TYPE,
                EntityVisualProperties.VILLAGER_PROFESSION,
                EntityVisualProperties.VILLAGER_LEVEL
        )) {
            return;
        }

        Holder<VillagerType> type = holder(
                registryAccess,
                Registries.VILLAGER_TYPE,
                firstResourceKey(visual, Registries.VILLAGER_TYPE, EntityVisualProperties.VILLAGER_TYPE)
                        .orElse(VillagerType.PLAINS)
        ).orElseGet(() -> BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS));
        Holder<VillagerProfession> profession = holder(
                registryAccess,
                Registries.VILLAGER_PROFESSION,
                firstResourceKey(visual, Registries.VILLAGER_PROFESSION, EntityVisualProperties.VILLAGER_PROFESSION)
                        .orElse(VillagerProfession.NONE)
        ).orElseGet(() -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE));
        int level = intProperty(visual, EntityVisualProperties.VILLAGER_LEVEL)
                .map(value -> Math.max(1, Math.min(5, value)))
                .orElse(1);

        EntityDataAccessor<VillagerData> accessor = entityType == EntityType.ZOMBIE_VILLAGER
                ? ZombieVillagerAccessor.semiontd$dataVillagerData()
                : VillagerAccessor.semiontd$dataVillagerData();
        put(data, accessor, new VillagerData(type, profession, level));
    }

    private static void applyHolderVariants(
            EntityVisual visual,
            EntityType<?> entityType,
            RegistryAccess registryAccess,
            List<SynchedEntityData.DataValue<?>> data
    ) {
        if (entityType == EntityType.COW) {
            applyHolderVariant(visual, registryAccess, data, CowAccessor.semiontd$dataVariantId(), Registries.COW_VARIANT, EntityVisualProperties.COW_VARIANT, "variant");
        } else if (entityType == EntityType.PIG) {
            applyHolderVariant(visual, registryAccess, data, PigAccessor.semiontd$dataVariantId(), Registries.PIG_VARIANT, EntityVisualProperties.PIG_VARIANT, "variant");
        } else if (entityType == EntityType.CHICKEN) {
            applyHolderVariant(visual, registryAccess, data, ChickenAccessor.semiontd$dataVariantId(), Registries.CHICKEN_VARIANT, EntityVisualProperties.CHICKEN_VARIANT, "variant");
        } else if (entityType == EntityType.WOLF) {
            applyHolderVariant(visual, registryAccess, data, WolfAccessor.semiontd$dataVariantId(), Registries.WOLF_VARIANT, EntityVisualProperties.WOLF_VARIANT, "variant");
            applyHolderVariant(visual, registryAccess, data, WolfAccessor.semiontd$dataSoundVariantId(), Registries.WOLF_SOUND_VARIANT, EntityVisualProperties.WOLF_SOUND_VARIANT, "sound_variant");
        } else if (entityType == EntityType.CAT) {
            applyHolderVariant(visual, registryAccess, data, CatAccessor.semiontd$dataVariantId(), Registries.CAT_VARIANT, EntityVisualProperties.CAT_VARIANT, "variant");
        } else if (entityType == EntityType.FROG) {
            applyHolderVariant(visual, registryAccess, data, FrogAccessor.semiontd$dataVariantId(), Registries.FROG_VARIANT, EntityVisualProperties.FROG_VARIANT, "variant");
        }
    }

    private static <T> void applyHolderVariant(
            EntityVisual visual,
            RegistryAccess registryAccess,
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<Holder<T>> accessor,
            ResourceKey<? extends Registry<T>> registryKey,
            String... propertyKeys
    ) {
        firstResourceKey(visual, registryKey, propertyKeys)
                .flatMap(value -> holder(registryAccess, registryKey, value))
                .ifPresent(holder -> put(data, accessor, holder));
    }

    private static void applyIntegerVariants(
            EntityVisual visual,
            EntityType<?> entityType,
            List<SynchedEntityData.DataValue<?>> data
    ) {
        if (entityType == EntityType.HORSE) {
            applyHorse(visual, data);
        } else if (entityType == EntityType.LLAMA || entityType == EntityType.TRADER_LLAMA) {
            applyEnumInteger(visual, data, LlamaAccessor.semiontd$dataVariantId(), Llama.Variant.class, Llama.Variant::getId, EntityVisualProperties.LLAMA_VARIANT, "variant");
        } else if (entityType == EntityType.FOX) {
            applyEnumInteger(visual, data, FoxAccessor.semiontd$dataTypeId(), Fox.Variant.class, Fox.Variant::getId, EntityVisualProperties.FOX_VARIANT, "variant");
        } else if (entityType == EntityType.RABBIT) {
            applyEnumInteger(visual, data, RabbitAccessor.semiontd$dataTypeId(), Rabbit.Variant.class, Rabbit.Variant::id, EntityVisualProperties.RABBIT_VARIANT, "variant");
        } else if (entityType == EntityType.PARROT) {
            applyEnumInteger(visual, data, ParrotAccessor.semiontd$dataVariantId(), Parrot.Variant.class, Parrot.Variant::getId, EntityVisualProperties.PARROT_VARIANT, "variant");
        } else if (entityType == EntityType.AXOLOTL) {
            applyEnumInteger(visual, data, AxolotlAccessor.semiontd$dataVariant(), Axolotl.Variant.class, Axolotl.Variant::getId, EntityVisualProperties.AXOLOTL_VARIANT, "variant");
        } else if (entityType == EntityType.MOOSHROOM) {
            applyEnumInteger(visual, data, MushroomCowAccessor.semiontd$dataType(), MushroomCow.Variant.class, EntityVisualApplierRegistry::mooshroomVariantId, EntityVisualProperties.MOOSHROOM_VARIANT, "variant");
        } else if (entityType == EntityType.SALMON) {
            applyEnumInteger(visual, data, SalmonAccessor.semiontd$dataType(), Salmon.Variant.class, EntityVisualApplierRegistry::salmonVariantId, EntityVisualProperties.SALMON_SIZE, "variant");
        } else if (entityType == EntityType.TROPICAL_FISH) {
            applyTropicalFish(visual, data);
        } else if (entityType == EntityType.SHEEP) {
            applySheep(visual, data);
        } else if (entityType == EntityType.SLIME) {
            applySlime(visual, data);
        } else if (entityType == EntityType.CAT) {
            applyDyeColor(visual, data, CatAccessor.semiontd$dataCollarColor(), EntityVisualProperties.COLLAR_COLOR);
        } else if (entityType == EntityType.WOLF) {
            applyDyeColor(visual, data, WolfAccessor.semiontd$dataCollarColor(), EntityVisualProperties.COLLAR_COLOR);
        }
    }

    private static void applyHorse(EntityVisual visual, List<SynchedEntityData.DataValue<?>> data) {
        Optional<Variant> variant = firstEnum(visual, Variant.class, EntityVisualProperties.HORSE_VARIANT, "variant");
        Optional<Markings> markings = firstEnum(visual, Markings.class, EntityVisualProperties.HORSE_MARKINGS, "markings");
        if (variant.isEmpty() && markings.isEmpty()) {
            return;
        }

        int variantId = variant.map(Variant::getId).orElse(Variant.WHITE.getId());
        int markingsId = markings.map(Markings::getId).orElse(Markings.NONE.getId());
        int packed = variantId & 0xFF | markingsId << 8 & 0xFF00;
        put(data, HorseAccessor.semiontd$dataIdTypeVariant(), packed);
    }

    private static <E extends Enum<E>> void applyEnumInteger(
            EntityVisual visual,
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<Integer> accessor,
            Class<E> enumClass,
            ToIntFunction<E> idGetter,
            String... propertyKeys
    ) {
        firstEnum(visual, enumClass, propertyKeys)
                .map(idGetter::applyAsInt)
                .ifPresent(id -> put(data, accessor, id));
    }

    private static void applyTropicalFish(EntityVisual visual, List<SynchedEntityData.DataValue<?>> data) {
        Optional<TropicalFish.Pattern> pattern = firstEnum(visual, TropicalFish.Pattern.class, EntityVisualProperties.TROPICAL_FISH_PATTERN, "pattern", "variant");
        Optional<DyeColor> baseColor = dyeColor(visual, EntityVisualProperties.BASE_COLOR, "tropical_fish_base_color");
        Optional<DyeColor> patternColor = dyeColor(visual, EntityVisualProperties.PATTERN_COLOR, "tropical_fish_pattern_color");
        if (pattern.isEmpty() && baseColor.isEmpty() && patternColor.isEmpty()) {
            return;
        }

        int packed = pattern.orElse(TropicalFish.Pattern.KOB).getPackedId() & 65535
                | (baseColor.orElse(DyeColor.WHITE).getId() & 0xFF) << 16
                | (patternColor.orElse(DyeColor.WHITE).getId() & 0xFF) << 24;
        put(data, TropicalFishAccessor.semiontd$dataIdTypeVariant(), packed);
    }

    private static void applySheep(EntityVisual visual, List<SynchedEntityData.DataValue<?>> data) {
        Optional<DyeColor> color = dyeColor(visual, EntityVisualProperties.SHEEP_COLOR, "color");
        Optional<Boolean> sheared = booleanProperty(visual, EntityVisualProperties.SHEARED);
        if (color.isEmpty() && sheared.isEmpty()) {
            return;
        }

        byte wool = (byte)(color.orElse(DyeColor.WHITE).getId() & 15);
        if (sheared.orElse(false)) {
            wool = (byte)(wool | 16);
        }
        put(data, SheepAccessor.semiontd$dataWoolId(), wool);
    }

    private static void applySlime(EntityVisual visual, List<SynchedEntityData.DataValue<?>> data) {
        intProperty(visual, EntityVisualProperties.SLIME_SIZE, "size")
                .map(size -> Math.max(1, Math.min(127, size)))
                .ifPresent(size -> put(data, SlimeAccessor.semiontd$idSize(), size));
    }

    private static void applyDyeColor(
            EntityVisual visual,
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<Integer> accessor,
            String propertyKey
    ) {
        visual.property(propertyKey)
                .flatMap(EntityVisualApplierRegistry::dyeColor)
                .or(() -> visual.property(propertyKey, DyeColor.class))
                .ifPresent(color -> put(data, accessor, color.getId()));
    }

    private static void applyTamableState(
            EntityVisual visual,
            EntityType<?> entityType,
            List<SynchedEntityData.DataValue<?>> data
    ) {
        if (entityType != EntityType.CAT && entityType != EntityType.WOLF) {
            return;
        }

        Optional<Boolean> tame = booleanProperty(visual, EntityVisualProperties.TAME);
        Optional<Boolean> sitting = booleanProperty(visual, EntityVisualProperties.SITTING);
        if (tame.isEmpty() && sitting.isEmpty()) {
            return;
        }

        byte flags = 0;
        if (tame.orElse(false)) {
            flags |= 4;
        }
        if (sitting.orElse(false)) {
            flags |= 1;
        }
        put(data, TamableAnimalAccessor.semiontd$dataFlagsId(), flags);
    }

    private static <T> Optional<Holder<T>> holder(
            RegistryAccess registryAccess,
            ResourceKey<? extends Registry<T>> registryKey,
            ResourceKey<T> key
    ) {
        return registryAccess.lookupOrThrow(registryKey).get(key).map(holder -> holder);
    }

    private static boolean hasAnyProperty(EntityVisual visual, String... keys) {
        for (String key : keys) {
            if (visual.propertyValue(key).isPresent()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<ResourceKey<T>> firstResourceKey(
            EntityVisual visual,
            ResourceKey<? extends Registry<T>> registryKey,
            String... propertyKeys
    ) {
        for (String propertyKey : propertyKeys) {
            Optional<Object> value = visual.propertyValue(propertyKey);
            if (value.isEmpty()) {
                continue;
            }
            Object propertyValue = value.get();
            if (propertyValue instanceof ResourceKey<?> resourceKey && resourceKey.registryKey().equals(registryKey)) {
                return Optional.of((ResourceKey<T>)resourceKey);
            }
            if (propertyValue instanceof String stringValue) {
                ResourceLocation id = parseId(stringValue);
                if (id != null) {
                    return Optional.of(ResourceKey.create(registryKey, id));
                }
            }
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> Optional<E> firstEnum(EntityVisual visual, Class<E> enumClass, String... propertyKeys) {
        for (String propertyKey : propertyKeys) {
            Optional<Object> value = visual.propertyValue(propertyKey);
            if (value.isEmpty()) {
                continue;
            }
            Object propertyValue = value.get();
            if (enumClass.isInstance(propertyValue)) {
                return Optional.of(enumClass.cast(propertyValue));
            }
            if (propertyValue instanceof String stringValue) {
                return enumValue(enumClass, stringValue);
            }
        }
        return Optional.empty();
    }

    private static Optional<DyeColor> dyeColor(EntityVisual visual, String... propertyKeys) {
        for (String propertyKey : propertyKeys) {
            Optional<Object> value = visual.propertyValue(propertyKey);
            if (value.isEmpty()) {
                continue;
            }
            Object propertyValue = value.get();
            if (propertyValue instanceof DyeColor color) {
                return Optional.of(color);
            }
            if (propertyValue instanceof String stringValue) {
                return dyeColor(stringValue);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> intProperty(EntityVisual visual, String... propertyKeys) {
        for (String propertyKey : propertyKeys) {
            Optional<Object> value = visual.propertyValue(propertyKey);
            if (value.isEmpty()) {
                continue;
            }
            Object propertyValue = value.get();
            if (propertyValue instanceof Number number) {
                return Optional.of(number.intValue());
            }
            if (propertyValue instanceof String stringValue) {
                return parseInt(stringValue);
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> booleanProperty(EntityVisual visual, String propertyKey) {
        Optional<Object> value = visual.propertyValue(propertyKey);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Object propertyValue = value.get();
        if (propertyValue instanceof Boolean booleanValue) {
            return Optional.of(booleanValue);
        }
        if (propertyValue instanceof String stringValue) {
            return parseBoolean(stringValue);
        }
        return Optional.empty();
    }

    private static ResourceLocation parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.indexOf(':') >= 0
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.fromNamespaceAndPath("minecraft", trimmed);
    }

    private static Optional<DyeColor> dyeColor(String value) {
        return enumValue(DyeColor.class, value);
    }

    private static int mooshroomVariantId(MushroomCow.Variant variant) {
        return switch (variant) {
            case RED -> 0;
            case BROWN -> 1;
        };
    }

    private static int salmonVariantId(Salmon.Variant variant) {
        return switch (variant) {
            case SMALL -> 0;
            case MEDIUM -> 1;
            case LARGE -> 2;
        };
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> parseBoolean(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> Optional.of(true);
            case "false", "no", "0", "off" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private static <E extends Enum<E>> Optional<E> enumValue(Class<E> enumClass, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(constant);
            }
            if (constant instanceof StringRepresentable representable
                    && representable.getSerializedName().equalsIgnoreCase(normalized)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    private static <T> void put(
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<T> accessor,
            T value
    ) {
        SynchedEntityData.DataValue<T> dataValue = SynchedEntityData.DataValue.create(accessor, value);
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).id() == dataValue.id()) {
                data.set(i, dataValue);
                return;
            }
        }
        data.add(dataValue);
    }
}
