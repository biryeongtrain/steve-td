package kim.biryeong.semiontd.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class TraitLoadoutTest {
    private static final ResourceLocation MINER = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "starter_mineral_training");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "other");

    @Test
    void traitSlotScalesPrimaryAndSecondaryEffects() {
        assertEquals(1.0D, TraitSlot.PRIMARY.effectScale());
        assertEquals(0.5D, TraitSlot.SECONDARY.effectScale());
    }

    @Test
    void noneLoadoutUsesNoneTraitInBothSlots() {
        TraitLoadout loadout = TraitLoadout.none();

        assertEquals(BuiltInTraits.NONE_ID, loadout.primaryTraitId());
        assertEquals(BuiltInTraits.NONE_ID, loadout.secondaryTraitId());
        assertFalse(loadout.hasDuplicateNonNoneTrait());
    }

    @Test
    void detectsDuplicateNonNoneTraitsOnly() {
        assertTrue(new TraitLoadout(MINER, MINER).hasDuplicateNonNoneTrait());
        assertFalse(new TraitLoadout(BuiltInTraits.NONE_ID, BuiltInTraits.NONE_ID).hasDuplicateNonNoneTrait());
        assertFalse(new TraitLoadout(MINER, OTHER).hasDuplicateNonNoneTrait());
    }
}
