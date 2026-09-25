package kim.biryeong.semiontd.tower.hero;

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
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HeroAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void protagonistUnlockDoesNotUpgradeDamagePriceOrIntervalAndAmplifiesAtActualFive() {
        UUID owner = UUID.randomUUID();
        HeroPartyState state = HeroPartyStates.state(owner);
        GridPosition p = new GridPosition(0, 64, 0);
        HeroTower hero = new HeroTower(HeroPartyTowers.HERO, owner, TeamId.RED, 1, p, p);
        try {
            hero.syncAugments(snapshot(HeroTower.PROTAGONIST), null);
            for (HeroWeapon weapon : HeroWeapon.values()) {
                state.addWeapon(weapon);
                state.equip(weapon);
                long price = HeroPartyBalance.weaponUpgradeCost(1);
                assertEquals(5, hero.weaponEffectLevel());
                assertEquals(1, hero.weaponEffectStrength());
                assertEquals(0, state.weaponLevel(weapon));
                assertEquals(HeroPartyBalance.weaponDamage(weapon),
                        hero.modifyAttackDamage(null, null, hero.type().damage()), .001);
                assertEquals(HeroPartyBalance.weaponAttackInterval(weapon, 0), hero.adjustAttackInterval(1));
                for (int i = 0; i < 4; i++) assertTrue(state.upgradeWeapon(weapon));
                assertEquals(1, hero.weaponEffectStrength());
                assertTrue(state.upgradeWeapon(weapon));
                assertEquals(1.5, hero.weaponEffectStrength());
                assertEquals(price, HeroPartyBalance.weaponUpgradeCost(1));
                assertEquals(HeroPartyBalance.weaponRange(weapon), hero.adjustAttackRange(1));
            }
        } finally {
            HeroPartyStates.clear(owner);
        }
    }

    @Test
    void legendIncreasesOnlyHeroWeaponDamageAndHealth() {
        UUID owner = UUID.randomUUID();
        GridPosition p = new GridPosition(0, 64, 0);
        HeroTower hero = new HeroTower(HeroPartyTowers.HERO, owner, TeamId.RED, 1, p, p);
        HeroCompanionTower companion = companion(owner, HeroCompanionRole.KNIGHT, p);
        hero.onPlaced(null);
        hero.syncHealth(hero.currentMaxHealth() * .4);
        double damage = hero.modifyAttackDamage(null, null, hero.type().damage());
        double health = hero.effectBaseMaxHealth();
        double companionHealth = companion.effectBaseMaxHealth();
        hero.syncAugments(snapshot(HeroTower.LEGEND), null);
        companion.syncAugments(snapshot(HeroTower.LEGEND), null);
        assertEquals(damage * 3, hero.modifyAttackDamage(null, null, hero.type().damage()), .001);
        assertEquals(health * 2.5, hero.effectBaseMaxHealth(), .001);
        assertEquals(health * 2.5, hero.currentMaxHealth(), .001);
        assertEquals(hero.currentMaxHealth() * .4, hero.health(), .001);
        hero.syncAugments(AugmentSnapshot.none(), null);
        assertEquals(health, hero.currentMaxHealth(), .001);
        assertEquals(health * .4, hero.health(), .001);
        assertEquals(companionHealth, companion.effectBaseMaxHealth(), .001);
        HeroPartyStates.clear(owner);
    }

    @Test
    void companionRotationKeepsPlacementOrderSkipsDeadAndPreservesUpgradeIdentity() {
        UUID owner = UUID.randomUUID();
        GridPosition p = new GridPosition(0, 64, 0);
        HeroCompanionTower first = companion(owner, HeroCompanionRole.KNIGHT, p);
        HeroCompanionTower second = companion(owner, HeroCompanionRole.ARCHER, p);
        HeroCompanionTower third = companion(owner, HeroCompanionRole.MAGE, p);
        List<HeroCompanionTower> companions = List.of(first, second, third);
        assertSame(first, HeroTower.nextCompanion(companions, null));
        assertSame(second, HeroTower.nextCompanion(companions, first.logicalId()));
        second.syncHealth(0);
        assertSame(third, HeroTower.nextCompanion(companions, first.logicalId()));
        assertSame(first, HeroTower.nextCompanion(companions, third.logicalId()));
        HeroCompanionTower upgraded = new HeroCompanionTower(HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 2),
                owner, TeamId.RED, 1, p, p);
        upgraded.copyFrom(first, 100);
        assertSame(third, HeroTower.nextCompanion(List.of(upgraded, second, third), first.logicalId()));
        assertNull(HeroTower.nextCompanion(List.of(), null));
        HeroPartyStates.clear(owner);
    }

    @Test
    void legendarySlashUsesTwelveByThreeRectangleWithoutHittingBehind() {
        Vec3 direction = new Vec3(1, 0, 0);
        assertTrue(HeroTower.insideSlash(Vec3.ZERO, direction, new Vec3(12, 0, 1.5), 12, 3));
        assertFalse(HeroTower.insideSlash(Vec3.ZERO, direction, new Vec3(12.01, 0, 0), 12, 3));
        assertFalse(HeroTower.insideSlash(Vec3.ZERO, direction, new Vec3(5, 0, 1.51), 12, 3));
        assertFalse(HeroTower.insideSlash(Vec3.ZERO, direction, new Vec3(-1, 0, 0), 12, 3));
    }

    private static HeroCompanionTower companion(UUID owner, HeroCompanionRole role, GridPosition p) {
        return new HeroCompanionTower(HeroPartyTowers.companion(role, 1), owner, TeamId.RED, 1, p, p);
    }

    private static AugmentSnapshot snapshot(String card) {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }
}
