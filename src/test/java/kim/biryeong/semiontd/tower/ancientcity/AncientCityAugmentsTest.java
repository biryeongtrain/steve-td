package kim.biryeong.semiontd.tower.ancientcity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AncientCityAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void balance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AncientCityStates.clearAllForTesting();
    }

    @Test
    void chainSonicAddsThreeTargetsAndRestoresFullSecondaryDamage() {
        AncientCityTower tower = new AncientCityTower(AncientCityTowers.WARDEN_T4,
                UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 64, 0));
        int original = tower.sonicTargetCount();
        assertEquals(.75, tower.sonicSecondaryRatio());
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, "semiontd:job_ancient_city_g2", PlayerAugmentState.Outcome.SELECTED,
                null, AugmentChoice.none()))), null);
        assertEquals(original + 3, tower.sonicTargetCount());
        assertEquals(1, tower.sonicSecondaryRatio());
    }

    @Test
    void sameSharedMarkRefreshesInsteadOfStacking() {
        AncientCityMarks.MarkSet marks = new AncientCityMarks.MarkSet();
        UUID owner = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        marks.apply(owner, source, .25, 80);
        marks.apply(owner, source, .25, 120);
        marks.apply(owner, UUID.randomUUID(), .25, 100);
        assertEquals(.25, marks.damageBonus(owner, 100));
        assertEquals(.25, marks.damageBonus(owner, 119));
        assertEquals(0, marks.damageBonus(owner, 120));
        assertFalse(AncientCityStates.claimCityPulse(UUID.randomUUID(), 100, 160));
    }
}
