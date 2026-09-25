package kim.biryeong.semiontd.tower.mage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MageAugmentsTest {
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}

    @Test void refundsActualSpendOnceAndNeverRefundsReplayOrSupport() {
        UUID owner = UUID.randomUUID();
        MageWizardTower wizard = wizard(owner, MageAugments.REFUND, MageAugments.WORLD);
        try {
            MageStates.state(owner).addMana(900);
            assertTrue(wizard.tryBeginCast(MageSpell.WIND_CUTTER));
            assertEquals(600, MageStates.state(owner).mana());
            AugmentCombat.runWithoutTriggers(wizard::refundSpellKill);
            assertEquals(600, MageStates.state(owner).mana());
            wizard.refundSpellKill();
            wizard.refundSpellKill();
            assertEquals(660, MageStates.state(owner).mana());
            assertTrue(wizard.tryBeginCast(MageSpell.MAGIC_AMPLIFICATION));
            wizard.refundSpellKill();
            assertEquals(640, MageStates.state(owner).mana());
            MageStates.state(owner).clearMana();
            MageStates.state(owner).addMana(20);
            assertFalse(wizard.worldCastAvailable(MageSpell.WIND_CUTTER));
            assertTrue(wizard.tryBeginCast(MageSpell.WIND_CUTTER));
            wizard.refundSpellKill();
            assertEquals(4, MageStates.state(owner).mana());
            assertFalse(wizard.tryBeginCast(MageSpell.WIND_CUTTER));
            wizard.refundSpellKill();
            assertEquals(4, MageStates.state(owner).mana());
        } finally {MageStates.clear(owner);}
    }

    @Test void floodIncludesUsedWizardsAndWorldNeverChangesCollapseCost() {
        UUID owner = UUID.randomUUID();
        MageWizardTower wizard = wizard(owner, MageAugments.FLOOD, MageAugments.WORLD);
        try {
            MageStates.state(owner).addMana(1000);
            assertFalse(wizard.worldCastAvailable(MageSpell.DIMENSIONAL_COLLAPSE));
            assertTrue(wizard.tryBeginCast(MageSpell.DIMENSIONAL_COLLAPSE));
            assertEquals(600, MageStates.state(owner).mana());
            assertEquals(MageBalance.IDLE_WIZARD_MANA, wizard.naturalManaProduction());
        } finally {MageStates.clear(owner);}
    }

    private static MageWizardTower wizard(UUID owner, String... cards) {
        MageWizardTower wizard = new MageWizardTower(MageTowers.spellType(MageSpell.WIND_CUTTER),
                owner, TeamId.RED, 1, new GridPosition(0, 64, 0));
        wizard.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(card ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, "semiontd:" + card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()), null);
        return wizard;
    }
}
