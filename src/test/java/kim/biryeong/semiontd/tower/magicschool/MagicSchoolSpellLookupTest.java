package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MagicSchoolSpellLookupTest {
    @Test
    void everyDeclaredIdMatchesTheFirstDeclarationAndIdsAreUnique() {
        var ids = new HashSet<String>();
        for (MagicSchoolSpell spell : MagicSchoolSpell.values()) {
            assertTrue(ids.add(spell.id()), spell.id());
            assertSame(Arrays.stream(MagicSchoolSpell.values())
                            .filter(candidate -> candidate.id().equals(spell.id())).findFirst().orElseThrow(),
                    MagicSchoolSpell.find(new String(spell.id())).orElseThrow());
        }
        assertEquals(MagicSchoolSpell.values().length, ids.size());
    }

    @Test
    void nullAndUnknownIdsStayEmpty() {
        assertTrue(MagicSchoolSpell.find(null).isEmpty());
        for (String id : new String[] {"", " ", "unknown", "magic_school_spell_lumos", "lumos_max", "0"}) {
            assertTrue(MagicSchoolSpell.find(id).isEmpty(), id);
        }
    }

    @Test
    void lookupDoesNotNormalizeCaseWhitespaceOrDisplayNames() {
        for (MagicSchoolSpell spell : MagicSchoolSpell.values()) {
            for (String id : new String[] {spell.id().toUpperCase(Locale.ROOT), " " + spell.id(),
                    spell.id() + " ", "\t" + spell.id(), spell.displayName()}) {
                assertTrue(MagicSchoolSpell.find(id).isEmpty(), id);
            }
        }
    }
}
