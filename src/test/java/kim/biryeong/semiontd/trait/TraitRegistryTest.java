package kim.biryeong.semiontd.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class TraitRegistryTest {
    @Test
    void builtInRegistrationIsIdempotentAndContainsNone() {
        BuiltInTraits.register();
        BuiltInTraits.register();

        assertTrue(TraitRegistry.find(BuiltInTraits.NONE_ID).isPresent());
    }

    @Test
    void duplicateTraitIdsAreRejected() {
        Identifier id = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "duplicate_test_trait");
        TraitRegistry.register(new SemionTrait(id, Component.literal("중복 테스트"), List.of()) {});

        assertThrows(IllegalArgumentException.class, () ->
                TraitRegistry.register(new SemionTrait(id, Component.literal("중복 테스트 2"), List.of()) {}));
    }

    @Test
    void loadoutSnapshotFreezesRegisteredTraitVersion() {
        Identifier id = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "versioned_test_trait");
        TraitRegistry.register(new SemionTrait(id, 3, Component.literal("버전 테스트"), List.of()) {});

        TraitLoadoutSnapshot snapshot = TraitLoadoutSnapshot.from(
                new TraitLoadout(id, BuiltInTraits.NONE_ID)
        );

        assertEquals(id.toString(), snapshot.primaryTraitId());
        assertEquals(3, snapshot.primaryTraitVersion());
        assertEquals(BuiltInTraits.NONE_ID.toString(), snapshot.secondaryTraitId());
        assertEquals(0, snapshot.secondaryTraitVersion());
    }
}
