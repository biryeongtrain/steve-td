package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class SemionPlayerTraitTest {
    @Test
    void playerTraitLoadoutDefaultsToNoneAndCanBeAssigned() {
        SemionPlayer player = new SemionPlayer(UUID.nameUUIDFromBytes("trait-player".getBytes()), "trait-player", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig()));

        assertEquals(TraitLoadout.none(), player.traitLoadout());

        TraitLoadout loadout = new TraitLoadout(
                Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "test_trait"),
                BuiltInTraits.NONE_ID
        );
        player.assignTraitLoadout(loadout);

        assertEquals(loadout, player.traitLoadout());

        player.assignTraitLoadout(null);
        assertEquals(TraitLoadout.none(), player.traitLoadout());
    }
}
