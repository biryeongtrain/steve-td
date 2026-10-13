package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class HeroCompanionSupportControllerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bardPulseAndCooldownKeepTheirDifferentRoundLifetimes() {
        var controller = controller(HeroCompanionRole.BARD);
        controller.tick(null);
        assertEquals(1, controller.pulseCount());
        assertEquals(20, controller.cooldownTicks());
        for (int tick = 0; tick < 20; tick++) {
            controller.tick(null);
        }
        assertEquals(1, controller.pulseCount());
        assertEquals(0, controller.cooldownTicks());
        controller.tick(null);
        assertEquals(2, controller.pulseCount());
        controller.resetRound();
        assertEquals(0, controller.pulseCount());
        assertEquals(20, controller.cooldownTicks());
    }

    @Test
    void upgradeCopiesSupportProgressWithoutSharingMutableControllerState() {
        var source = controller(HeroCompanionRole.BARD);
        source.tick(null);
        source.tick(null);
        var upgraded = controller(HeroCompanionRole.BARD);
        upgraded.copyFrom(source);
        assertEquals(19, upgraded.cooldownTicks());
        assertEquals(1, upgraded.pulseCount());
        upgraded.resetRound();
        upgraded.tick(null);
        assertEquals(18, upgraded.cooldownTicks());
        assertEquals(0, upgraded.pulseCount());
        assertEquals(19, source.cooldownTicks());
        assertEquals(1, source.pulseCount());
    }

    @Test
    void combatOnlyCompanionsDoNotRunSupportCadence() {
        for (HeroCompanionRole role : List.of(HeroCompanionRole.ARCHER, HeroCompanionRole.MAGE, HeroCompanionRole.ROGUE)) {
            var controller = controller(role);
            for (int tick = 0; tick < 25; tick++) {
                controller.tick(null);
            }
            assertEquals(0, controller.cooldownTicks());
            assertEquals(0, controller.pulseCount());
        }
    }

    @Test
    void abilityDetailsKeepTierUnlocksAndFallbackFormatting() {
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            assertTrue(HeroCompanionStatsView.abilities(role, 1, "test:missing").isEmpty());
            assertEquals(1, HeroCompanionStatsView.abilities(role, 2, "test:missing").size());
            assertEquals(2, HeroCompanionStatsView.abilities(role, 3, "test:missing").size());
            assertEquals(2, HeroCompanionStatsView.abilities(role, 4, "test:missing").size());
        }
        assertEquals(List.of(HeroPartyTowers.firstAbilityName(HeroCompanionRole.KNIGHT)
                        + ": 4번째 공격, 이동/공격 속도 -25% (2초)"),
                HeroCompanionStatsView.abilities(HeroCompanionRole.KNIGHT, 2, "test:missing"));
    }

    @Test
    void priestSelectsTheSameTwoTargetsAsTheStableFullSort() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000802");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000803");
        Random random = new Random(803L);
        List<Tower> towers = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            towers.add(wounded(index % 7 == 0 ? other : owner, random.nextInt(11) / 10.0, index % 9 != 0));
        }
        towers.add(wounded(owner, Double.NaN, true));
        towers.add(towers.get(1));
        for (int sample = 0; sample < 256; sample++) {
            Collections.shuffle(towers, random);
            List<Tower> expected = towers.stream()
                    .filter(tower -> tower.ownerPlayer().equals(owner))
                    .filter(tower -> HeroPartyTowers.isHeroPartyTower(tower.type()))
                    .filter(tower -> tower.health() > 0.0 && tower.health() < tower.currentMaxHealth())
                    .sorted(Comparator.comparingDouble(tower -> tower.health() / Math.max(1.0, tower.currentMaxHealth())))
                    .limit(2)
                    .toList();
            assertEquals(expected, HeroCompanionSupportController.lowestWoundedTargets(towers, owner));
        }
    }

    @Test
    void priestKeepsEncounterTiesAndBothTargetsBeforeHealingStarts() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000804");
        Tower first = wounded(owner, 0.2, true);
        Tower second = wounded(owner, 0.2, true);
        Tower third = wounded(owner, 0.2, true);
        List<Tower> selected = HeroCompanionSupportController.lowestWoundedTargets(List.of(first, second, third), owner);
        assertEquals(List.of(first, second), selected);
        first.syncHealth(first.currentMaxHealth());
        third.syncHealth(1.0);
        assertEquals(List.of(first, second), selected);
        assertEquals(List.of(third, second),
                HeroCompanionSupportController.lowestWoundedTargets(List.of(first, second, third), owner));
        assertEquals(List.of(second), HeroCompanionSupportController.lowestWoundedTargets(List.of(second), owner));
        assertTrue(HeroCompanionSupportController.lowestWoundedTargets(List.of(), owner).isEmpty());
        assertTrue(HeroCompanionSupportController.lowestWoundedTargets(List.of(first,
                wounded(owner, 0.0, true), wounded(owner, 0.1, false)), owner).isEmpty());
    }

    private static Tower wounded(UUID owner, double healthRatio, boolean party) {
        TowerType type = HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 3);
        if (!party) {
            type = new TowerType("test_support_other", "Other", type.category(), 0L,
                    type.maxHealth(), type.range(), type.damage(), type.attackIntervalTicks(), type.aggroPriority());
        }
        Tower tower = new ProductionTower(type, owner, TeamId.RED, 1, new GridPosition(0, 0, 0));
        tower.syncHealth(tower.currentMaxHealth() * healthRatio);
        return tower;
    }

    private static HeroCompanionSupportController controller(HeroCompanionRole role) {
        var position = new GridPosition(0, 0, 0);
        return new HeroCompanionSupportController(new HeroCompanionTower(HeroPartyTowers.companion(role, 3),
                UUID.fromString("00000000-0000-0000-0000-000000000801"), TeamId.RED, 1, position, position));
    }
}
