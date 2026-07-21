package kim.biryeong.semiontd.tower.resonance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class ResonanceTowerTest {
    private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void applyDefaultBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void nearbyDifferentSpeciesRaiseResonanceByOneThreeFiveThresholds() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 0));
        List<ResonanceTower> links = nearbyDifferentSpecies();

        ResonanceService.refresh(List.of(focus));
        assertEquals(0, focus.resonanceLevel());
        assertEquals(0, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus, links.get(0)));
        assertEquals(1, focus.resonanceLevel());
        assertEquals(1, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus, links.get(0), links.get(1)));
        assertEquals(1, focus.resonanceLevel());
        assertEquals(2, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus, links.get(0), links.get(1), links.get(2)));
        assertEquals(2, focus.resonanceLevel());
        assertEquals(3, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus, links.get(0), links.get(1), links.get(2), links.get(3)));
        assertEquals(2, focus.resonanceLevel());
        assertEquals(4, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus, links.get(0), links.get(1), links.get(2), links.get(3), links.get(4)));
        assertEquals(3, focus.resonanceLevel());
        assertEquals(5, focus.resonanceLinks());
    }

    @Test
    void sameSpeciesDifferentOwnerAndOutOfRangeTowersDoNotContribute() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(0, 0, 0));
        ResonanceTower sameSpecies = tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(1, 0, 0));
        ResonanceTower otherOwner = new ResonanceTower(
                ResonanceTowers.WAVE_CRYSTAL,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                TeamId.RED,
                1,
                new GridPosition(1, 0, 0),
                new GridPosition(1, 0, 0)
        );
        ResonanceTower outOfRange = tower(ResonanceTowers.FROST_CRYSTAL, new GridPosition(2, 0, 0));

        ResonanceService.refresh(List.of(focus, sameSpecies, otherOwner, outOfRange));

        assertEquals(0, focus.resonanceLevel());
        assertEquals(0, focus.resonanceLinks());
    }

    @Test
    void allTowerTiersCanReachLevelThree() {
        ResonanceTower focusT1 = tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(0, 0, 0));
        ResonanceTower focusT2 = tower(ResonanceTowers.FOCUS_PRISM, new GridPosition(0, 0, 0));
        ResonanceTower focusT3 = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 0));
        List<ResonanceTower> links = nearbyDifferentSpecies();

        ResonanceService.refresh(concat(focusT1, links.subList(0, 5)));
        ResonanceService.refresh(concat(focusT2, links.subList(0, 5)));
        ResonanceService.refresh(concat(focusT3, links.subList(0, 5)));

        assertEquals(3, focusT1.resonanceLevel());
        assertEquals(3, focusT2.resonanceLevel());
        assertEquals(3, focusT3.resonanceLevel());
    }

    @Test
    void resonanceLevelAdjustsDamageAndAttackInterval() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 0));
        ResonanceService.refresh(concat(focus, nearbyDifferentSpecies()));

        assertEquals(3, focus.resonanceLevel());
        assertEquals(0.50, focus.auraAttackSpeedBonus(), 0.0001);
        assertEquals(180.0, focus.modifyAttackDamage(null, null, 100.0), 0.0001);
        assertEquals(9, focus.adjustAttackInterval(20));
        assertEquals(44, focus.adjustAttackInterval(100));
    }

    @Test
    void resonanceBonusesDifferByTree() {
        ResonanceTower wave = tower(ResonanceTowers.WAVE_CORE, new GridPosition(0, 0, 0));
        ResonanceTower bloom = tower(ResonanceTowers.AMPLIFY_PRISM, new GridPosition(0, 0, 0));

        ResonanceService.refresh(concat(wave, nearbyNonWaveSpecies()));
        ResonanceService.refresh(concat(bloom, nearbyDifferentSpecies()));

        assertEquals(3, wave.resonanceLevel());
        assertEquals(100.0, wave.modifyAttackDamage(null, null, 100.0), 0.0001);
        assertEquals(72, wave.adjustAttackInterval(100));
        assertEquals(3, bloom.resonanceLevel());
        assertEquals(65.0, bloom.modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    @Test
    void resonanceStateDoesNotDowngradeAfterLinksDisappear() {
        ResonanceTower focus = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 0));
        ResonanceService.refresh(concat(focus, nearbyDifferentSpecies()));

        assertEquals(3, focus.resonanceLevel());
        assertEquals(6, focus.resonanceLinks());

        ResonanceService.refresh(List.of(focus));

        assertEquals(3, focus.resonanceLevel());
        assertEquals(6, focus.resonanceLinks());
    }

    @Test
    void auraBonusDoesNotDowngradeAfterProviderDisappears() {
        ResonanceTower recipient = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(1, 0, 0));
        ResonanceTower bloom = tower(ResonanceTowers.AMPLIFY_CORE, new GridPosition(0, 0, 0));
        ResonanceService.refresh(concat(bloom, nearbyDifferentSpecies()));
        ResonanceService.refresh(List.of(recipient, bloom));

        assertEquals(0.35, recipient.auraAttackSpeedBonus(), 0.0001);

        ResonanceService.refresh(List.of(recipient));

        assertEquals(0.35, recipient.auraAttackSpeedBonus(), 0.0001);
    }

    @Test
    void frostAuraGivesNearbyMoobloomsDamageBonusAgainstDebuffedTargets() {
        ResonanceTower recipient = tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(1, 0, 0));
        ResonanceTower frost = tower(ResonanceTowers.FROST_CORE, new GridPosition(0, 0, 0));
        ResonanceService.refresh(concat(frost, nearbyNonFrostSpecies()));
        ResonanceService.refresh(List.of(recipient, frost));

        assertEquals(3, frost.resonanceLevel());
        assertEquals(1.00, recipient.auraDamageVsSlowedBonus(), 0.0001);
        assertEquals(0.0, frost.auraDamageVsSlowedBonus(), 0.0001);

        ResonanceService.refresh(List.of(recipient));

        assertEquals(1.00, recipient.auraDamageVsSlowedBonus(), 0.0001);
    }

    @Test
    void upgradedResonanceTowerCopiesPermanentState() {
        ResonanceTower previous = tower(ResonanceTowers.FOCUS_PRISM, new GridPosition(0, 0, 0));
        ResonanceService.refresh(concat(previous, nearbyDifferentSpecies()));

        ResonanceTower upgraded = tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 0));
        upgraded.copyFrom(previous, 180);
        ResonanceService.refresh(List.of(upgraded));

        assertEquals(3, upgraded.resonanceLevel());
        assertEquals(6, upgraded.resonanceLinks());
        assertEquals(previous.auraDamageVsSlowedBonus(), upgraded.auraDamageVsSlowedBonus(), 0.0001);
    }

    private List<ResonanceTower> nearbyDifferentSpecies() {
        return List.of(
                tower(ResonanceTowers.WAVE_CRYSTAL, new GridPosition(1, 0, 0)),
                tower(ResonanceTowers.FROST_CRYSTAL, new GridPosition(-1, 0, 0)),
                tower(ResonanceTowers.AMPLIFY_CRYSTAL, new GridPosition(0, 0, 1)),
                tower(ResonanceTowers.WAVE_PRISM, new GridPosition(1, 0, -1)),
                tower(ResonanceTowers.FROST_PRISM, new GridPosition(-1, 0, -1)),
                tower(ResonanceTowers.AMPLIFY_PRISM, new GridPosition(0, 0, -1))
        );
    }

    private List<ResonanceTower> nearbyNonWaveSpecies() {
        return List.of(
                tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(1, 0, 0)),
                tower(ResonanceTowers.FOCUS_PRISM, new GridPosition(-1, 0, 0)),
                tower(ResonanceTowers.FOCUS_CORE, new GridPosition(0, 0, 1)),
                tower(ResonanceTowers.FROST_CRYSTAL, new GridPosition(0, 0, -1)),
                tower(ResonanceTowers.FROST_PRISM, new GridPosition(1, 0, 1)),
                tower(ResonanceTowers.FROST_CORE, new GridPosition(-1, 0, -1))
        );
    }

    private List<ResonanceTower> nearbyNonFrostSpecies() {
        return List.of(
                tower(ResonanceTowers.FOCUS_CRYSTAL, new GridPosition(1, 0, 0)),
                tower(ResonanceTowers.FOCUS_PRISM, new GridPosition(-1, 0, 0)),
                tower(ResonanceTowers.WAVE_CRYSTAL, new GridPosition(0, 0, 1)),
                tower(ResonanceTowers.WAVE_PRISM, new GridPosition(0, 0, -1)),
                tower(ResonanceTowers.AMPLIFY_CRYSTAL, new GridPosition(1, 0, 1)),
                tower(ResonanceTowers.AMPLIFY_PRISM, new GridPosition(-1, 0, -1))
        );
    }

    private List<kim.biryeong.semiontd.tower.Tower> concat(ResonanceTower focus, List<ResonanceTower> links) {
        java.util.ArrayList<kim.biryeong.semiontd.tower.Tower> towers = new java.util.ArrayList<>();
        towers.add(focus);
        towers.addAll(links);
        return towers;
    }

    private ResonanceTower tower(kim.biryeong.semiontd.tower.TowerType type, GridPosition position) {
        return new ResonanceTower(type, owner, TeamId.RED, 1, position, position);
    }
}
