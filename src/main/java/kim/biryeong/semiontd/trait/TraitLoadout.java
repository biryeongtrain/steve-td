package kim.biryeong.semiontd.trait;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record TraitLoadout(ResourceLocation primaryTraitId, ResourceLocation secondaryTraitId) {
    public TraitLoadout {
        primaryTraitId = primaryTraitId == null ? BuiltInTraits.NONE_ID : primaryTraitId;
        secondaryTraitId = secondaryTraitId == null ? BuiltInTraits.NONE_ID : secondaryTraitId;
    }

    public static TraitLoadout none() {
        return new TraitLoadout(BuiltInTraits.NONE_ID, BuiltInTraits.NONE_ID);
    }

    public boolean hasDuplicateNonNoneTrait() {
        return !isNone(primaryTraitId) && Objects.equals(primaryTraitId, secondaryTraitId);
    }

    public ResourceLocation traitId(TraitSlot slot) {
        return slot == TraitSlot.PRIMARY ? primaryTraitId : secondaryTraitId;
    }

    public TraitLoadout with(TraitSlot slot, ResourceLocation traitId) {
        ResourceLocation normalized = traitId == null ? BuiltInTraits.NONE_ID : traitId;
        return slot == TraitSlot.PRIMARY
                ? new TraitLoadout(normalized, secondaryTraitId)
                : new TraitLoadout(primaryTraitId, normalized);
    }

    public static boolean isNone(ResourceLocation traitId) {
        return traitId == null || BuiltInTraits.NONE_ID.equals(traitId);
    }
}
