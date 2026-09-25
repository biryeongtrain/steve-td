package kim.biryeong.semiontd.tower.resonance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResonanceAugmentsTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000407");
    @BeforeAll static void bootstrap() {SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();}
    @BeforeEach void balance() {TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());}

    @Test void remoteLinksReachThreeBlocksAndGrantStatsOnlyAtThreeLinks() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CRYSTAL, 0, 0, ResonanceTower.REMOTE);
        ResonanceTower wave = tower(ResonanceTowers.WAVE_CRYSTAL, 3, 0);
        ResonanceTower frost = tower(ResonanceTowers.FROST_CRYSTAL, 0, 3);
        ResonanceTower bloom = tower(ResonanceTowers.AMPLIFY_CRYSTAL, -3, 0);
        ResonanceService.refresh(List.of(focus, wave, frost));
        assertEquals(2, focus.resonanceLinks());
        assertEquals(focus.maxHealth(), focus.currentMaxHealth(), 1e-6);
        ResonanceService.refresh(List.of(focus, wave, frost, bloom));
        assertEquals(3, focus.resonanceLinks());
        assertEquals(focus.maxHealth() * 1.5, focus.currentMaxHealth(), 1e-6);
        assertEquals(150, focus.modifyAttackDamage(null, null, 100), 1e-6);
        ResonanceService.refresh(List.of(focus));
        assertEquals(focus.maxHealth(), focus.currentMaxHealth(), 1e-6);
    }

    @Test void internetFriendUsesHighestUnlinkedNearestCandidateAndDoesNotAccumulate() {
        ResonanceTower low = tower(ResonanceTowers.FOCUS_CRYSTAL, 0, 0, ResonanceTower.FRIEND);
        ResonanceTower near = tower(ResonanceTowers.WAVE_CRYSTAL, 5, 0);
        ResonanceTower far = tower(ResonanceTowers.WAVE_CRYSTAL, 10, 0);
        ResonanceService.refresh(List.of(low, near, far));
        assertEquals(1, low.resonanceLinks());
        assertEquals(near.logicalId(), low.internetFriend());
        ResonanceService.refresh(List.of(low, near, far));
        assertEquals(1, low.resonanceLinks());
        ResonanceService.refresh(List.of(low));
        assertEquals(0, low.resonanceLinks());
        assertNull(low.internetFriend());
    }

    @Test void internetFriendNeverCountsAnExistingConnectionTwice() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CRYSTAL, 0, 0, ResonanceTower.FRIEND);
        ResonanceTower wave = tower(ResonanceTowers.WAVE_CRYSTAL, 1, 0);
        ResonanceService.refresh(List.of(focus, wave));
        assertEquals(1, focus.resonanceLinks());
        assertNull(focus.internetFriend());
    }

    @Test void cycleWaitsSixSecondsDoesNotRepeatSameTickAndKeepsUpgradeDeadline() {
        ResonanceTower tower = tower(ResonanceTowers.FOCUS_CRYSTAL, 0, 0, ResonanceTower.CYCLE);
        tower.startAugmentWave(40);
        assertFalse(tower.cycleDue(159));
        assertTrue(tower.cycleDue(160));
        assertFalse(tower.cycleDue(160));
        ResonanceTower upgraded = tower(ResonanceTowers.FOCUS_PRISM, 0, 0, ResonanceTower.CYCLE);
        upgraded.copyFrom(tower, 100);
        assertFalse(upgraded.cycleDue(279));
        assertTrue(upgraded.cycleDue(280));
        upgraded.startAugmentWave(500);
        assertFalse(upgraded.cycleDue(500));
        assertTrue(upgraded.cycleDue(620));
    }

    private static ResonanceTower tower(TowerType type, int x, int z, String... cards) {
        GridPosition position = new GridPosition(x, 0, z);
        ResonanceTower tower = new ResonanceTower(type, OWNER, TeamId.RED, 1, position, position);
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), java.util.Arrays.stream(cards)
                .map(card -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()), null);
        return tower;
    }
}
