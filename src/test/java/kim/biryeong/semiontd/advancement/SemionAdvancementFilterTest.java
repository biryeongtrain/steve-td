package kim.biryeong.semiontd.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class SemionAdvancementFilterTest {
    @Test
    void keepsValidTreeAndRecursivelyRemovesForeignParentsAndOrphans() {
        Identifier root = id("semion-td:root");
        Identifier child = id("semion-td:child");
        Identifier grandchild = id("semion-td:grandchild");
        Identifier foreign = id("minecraft:root");
        Identifier orphan = id("semion-td:orphan");
        Identifier descendant = id("semion-td:orphan_child");
        Identifier missing = id("semion-td:missing_parent");
        Map<Identifier, Optional<Identifier>> parents = Map.of(
                root, Optional.empty(), child, Optional.of(root), grandchild, Optional.of(child),
                foreign, Optional.empty(), orphan, Optional.of(foreign), descendant, Optional.of(orphan),
                missing, Optional.of(id("semion-td:absent"))
        );
        assertEquals(Set.of(root, child, grandchild), SemionAdvancementFilter.retainedIds(parents));
        assertEquals(7, parents.size(), "The resource manager's original input must remain intact.");
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }
}
