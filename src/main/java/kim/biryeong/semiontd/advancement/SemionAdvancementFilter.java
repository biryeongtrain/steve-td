package kim.biryeong.semiontd.advancement;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.Identifier;

/** Preserves the Semion-only advancement tree before registry registration. */
public final class SemionAdvancementFilter {
    private SemionAdvancementFilter() {
    }

    public static Set<Identifier> retainedIds(Map<Identifier, Optional<Identifier>> parents) {
        Set<Identifier> retained = new LinkedHashSet<>(parents.keySet());
        retained.removeIf(id -> !id.getNamespace().equals(SemionTd.MOD_ID));
        while (retained.removeIf(id -> parents.get(id).filter(parent -> !retained.contains(parent)).isPresent())) {
            // Removing one orphan may make a descendant orphaned on the next pass.
        }
        return Set.copyOf(retained);
    }
}
