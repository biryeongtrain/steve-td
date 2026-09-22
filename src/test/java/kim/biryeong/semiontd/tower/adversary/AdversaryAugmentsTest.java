package kim.biryeong.semiontd.tower.adversary;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
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

class AdversaryAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void adaptationCapsAgainstOpeningStatsInsteadOfGrowingCap() {
        assertEquals(20, AdversaryAugments.absorb(0, 100, 1, 100, .2), .001);
        assertEquals(100, AdversaryAugments.absorb(90, 100, 1, 100, .2), .001);
        assertEquals(100, AdversaryAugments.absorb(100, 100, 1, 100, .2), .001);
        assertEquals(150, AdversaryAugments.absorb(140, 100, 1.5, 100, .5), .001);
        assertEquals(20, AdversaryAugments.absorb(20, 100, 1, -100, .2), .001);
    }

    @Test
    void finalePassiveAffectsEveryFinalFormAndNoEarlierForm() {
        var snapshot = new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.PRISMATIC, AdversaryAugments.FINALE,
                PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
        for (FoxForm form : FoxForm.values()) {
            UUID owner = UUID.randomUUID();
            AdversaryFoxTower fox = new AdversaryFoxTower(AdversaryTowers.typeFor(form), owner, TeamId.RED, 1,
                    new GridPosition(0, 64, 0));
            fox.syncHealth(fox.currentMaxHealth() * .4);
            fox.syncAugments(snapshot, null);
            assertEquals(form.maxHealth() * (form.isFinal() ? 2 : 1), fox.effectBaseMaxHealth(), .001);
            assertEquals(form.maxHealth() * (form.isFinal() ? 2 : 1), fox.currentMaxHealth(), .001);
            assertEquals(fox.currentMaxHealth() * .4, fox.health(), .001);
            assertEquals(form.isFinal() ? 220 : 100, fox.modifyResolvedAttackDamage(null, null, 100), .001);
            fox.syncAugments(AugmentSnapshot.none(), null);
            assertEquals(form.maxHealth(), fox.currentMaxHealth(), .001);
            assertEquals(form.maxHealth() * .4, fox.health(), .001);
            AdversaryProgressStates.clear(owner);
        }
    }
}
