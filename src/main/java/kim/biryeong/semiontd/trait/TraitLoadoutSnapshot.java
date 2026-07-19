package kim.biryeong.semiontd.trait;

import net.minecraft.resources.ResourceLocation;

public record TraitLoadoutSnapshot(
        String primaryTraitId,
        int primaryTraitVersion,
        String secondaryTraitId,
        int secondaryTraitVersion
) {
    public TraitLoadoutSnapshot {
        primaryTraitId = normalizeId(primaryTraitId);
        secondaryTraitId = normalizeId(secondaryTraitId);
        primaryTraitVersion = Math.max(0, primaryTraitVersion);
        secondaryTraitVersion = Math.max(0, secondaryTraitVersion);
    }

    public static TraitLoadoutSnapshot none() {
        String noneId = BuiltInTraits.NONE_ID.toString();
        return new TraitLoadoutSnapshot(noneId, 0, noneId, 0);
    }

    public static TraitLoadoutSnapshot from(TraitLoadout loadout) {
        TraitLoadout safeLoadout = loadout == null ? TraitLoadout.none() : loadout;
        return new TraitLoadoutSnapshot(
                safeLoadout.primaryTraitId().toString(),
                version(safeLoadout.primaryTraitId()),
                safeLoadout.secondaryTraitId().toString(),
                version(safeLoadout.secondaryTraitId())
        );
    }

    private static int version(ResourceLocation traitId) {
        if (TraitLoadout.isNone(traitId)) {
            return 0;
        }
        return TraitRegistry.find(traitId).map(SemionTrait::version).orElse(0);
    }

    private static String normalizeId(String traitId) {
        ResourceLocation parsed = traitId == null ? null : ResourceLocation.tryParse(traitId);
        return parsed == null ? BuiltInTraits.NONE_ID.toString() : parsed.toString();
    }
}
