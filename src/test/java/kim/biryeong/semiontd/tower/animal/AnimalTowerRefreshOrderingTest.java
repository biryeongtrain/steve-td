package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class AnimalTowerRefreshOrderingTest {
    private static final UUID OWNER = new UUID(0, 8401);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void laterAnimalTickObservesLeaderDeathAndRevivalWithoutAdvancingALaneClock() {
        PlayerLane lane = lane(snapshot(AnimalStackTower.UNION));
        PigTower pig = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 0, 0));
        WolfTower wolf = new WolfTower(AnimalTowers.T4_WOLF_LEADER_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(100, 0, 0));
        RabbitTower rabbit = new RabbitTower(AnimalTowers.T4_RABBIT_LEADER_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(200, 0, 0));
        FoxTower receiver = new FoxTower(AnimalTowers.T1_FOX_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(300, 0, 0));
        for (AnimalStackTower tower : List.of(pig, wolf, rabbit, receiver)) {
            lane.addTower(tower);
        }
        assertNull(lane.arenaWorld());
        double unbuffedMaxHealth = receiver.maxHealth();
        double buffedMaxHealth = receiver.currentMaxHealth();
        assertTrue(buffedMaxHealth > unbuffedMaxHealth);
        receiver.syncHealth(buffedMaxHealth * 0.5);
        pig.tick(lane);

        rabbit.syncHealth(0.0);
        assertEquals(buffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        wolf.tick(lane);
        assertEquals(unbuffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        assertEquals(unbuffedMaxHealth * 0.5, receiver.health(), 1.0E-9);
        assertEquals(20, receiver.adjustAttackInterval(20));
        assertEquals(5.0, receiver.adjustAttackRange(5.0), 1.0E-9);

        rabbit.syncHealth(rabbit.currentMaxHealth());
        assertEquals(unbuffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        receiver.tick(lane);
        assertEquals(buffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        assertEquals(buffedMaxHealth * 0.5, receiver.health(), 1.0E-9);
        assertTrue(receiver.adjustAttackInterval(20) < 20);
        assertTrue(receiver.adjustAttackRange(5.0) > 5.0);

        receiver.syncAugments(AugmentSnapshot.none(), lane);
        receiver.tick(lane);
        assertEquals(unbuffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        assertEquals(unbuffedMaxHealth * 0.5, receiver.health(), 1.0E-9);
        receiver.syncAugments(snapshot(AnimalStackTower.UNION), lane);
        pig.tick(lane);
        assertEquals(buffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        assertEquals(buffedMaxHealth * 0.5, receiver.health(), 1.0E-9);
    }

    @Test
    void laterAnimalTickRechecksInclusiveAuraBoundaryAndRemovalClearsTheProvider() {
        PlayerLane lane = lane(AugmentSnapshot.none());
        PigTower leader = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 0, 0));
        double radius = TowerBalanceRuntime.ability(AnimalTowers.T4_PIG_LEADER_TOWER.id(), "leaderAuraRadius");
        int gridRadius = (int) radius;
        assertEquals(radius, gridRadius, 0.0);
        PigTower receiver = new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(gridRadius, 0, 0));
        lane.addTower(leader);
        lane.addTower(receiver);
        for (int i = 1; i < leader.maxStacks(); i++) {
            lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1,
                    new GridPosition(100 + i, 0, 0)));
        }
        assertTrue(leader.atMaxStacks());
        assertTrue(receiver.hasLeaderAura());
        double buffedMaxHealth = receiver.currentMaxHealth();
        receiver.syncHealth(buffedMaxHealth);

        leader.syncPosition(new GridPosition(-1, 0, 0));
        assertTrue(receiver.hasLeaderAura());
        leader.tick(lane);
        assertFalse(receiver.hasLeaderAura());
        double unbuffedMaxHealth = receiver.currentMaxHealth();
        assertTrue(unbuffedMaxHealth < buffedMaxHealth);
        assertEquals(unbuffedMaxHealth, receiver.health(), 1.0E-9);

        leader.syncPosition(new GridPosition(0, 0, 0));
        assertFalse(receiver.hasLeaderAura());
        receiver.tick(lane);
        assertTrue(receiver.hasLeaderAura());
        assertEquals(buffedMaxHealth, receiver.currentMaxHealth(), 1.0E-9);
        assertEquals(buffedMaxHealth, receiver.health(), 1.0E-9);

        assertTrue(lane.removeTower(leader));
        assertNull(leader.attachedLane());
        assertFalse(receiver.hasLeaderAura());
        assertFalse(lane.towers().contains(leader));
        receiver.tick(lane);
        assertFalse(receiver.hasLeaderAura());
        assertTrue(receiver.currentMaxHealth() < buffedMaxHealth);
    }

    @Test
    void unchangedPackIsNotRecountedUntilAnInputChanges() {
        PlayerLane lane = lane(AugmentSnapshot.none());
        CountingPig first = new CountingPig(new GridPosition(0, 0, 0));
        PigTower second = new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1, new GridPosition(2, 0, 0));
        lane.addTower(first);
        lane.addTower(second);
        int recounts = first.recounts;

        first.tick(lane);
        second.tick(lane);
        first.tick(lane);
        assertEquals(recounts, first.recounts);

        second.syncHealth(0.0);
        first.tick(lane);
        assertEquals(recounts + 1, first.recounts);
        first.tick(lane);
        assertEquals(recounts + 1, first.recounts);

        second.syncHealth(second.currentMaxHealth());
        second.tick(lane);
        assertEquals(recounts + 2, first.recounts);

        second.syncPosition(new GridPosition(3, 0, 0));
        second.tick(lane);
        assertEquals(recounts + 3, first.recounts);

        second.syncAugments(snapshot(AnimalStackTower.FRIENDSHIP), lane);
        first.tick(lane);
        assertEquals(recounts + 4, first.recounts);

        lane.addTower(new WolfTower(AnimalTowers.T1_WOLF_TOWER, OWNER, TeamId.RED, 1, new GridPosition(4, 0, 0)));
        assertEquals(recounts + 5, first.recounts);
        first.tick(lane);
        second.tick(lane);
        assertEquals(recounts + 5, first.recounts);
    }

    @Test
    void laterAnimalTickObservesABalanceReload() {
        PlayerLane lane = lane(AugmentSnapshot.none());
        PigTower leader = new PigTower(AnimalTowers.T4_PIG_LEADER_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 0, 0));
        PigTower receiver = new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(1, 0, 0));
        lane.addTower(leader);
        lane.addTower(receiver);
        for (int i = 1; i < leader.maxStacks(); i++) {
            lane.addTower(new PigTower(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1,
                    new GridPosition(100 + i, 0, 0)));
        }
        assertTrue(receiver.hasLeaderAura());

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new HashMap<>(defaults.abilities());
        Map<String, Double> leaderAbilities = new HashMap<>(abilities.get(AnimalTowers.T4_PIG_LEADER_TOWER.id()));
        leaderAbilities.put("leaderAuraRadius", 0.0);
        abilities.put(AnimalTowers.T4_PIG_LEADER_TOWER.id(), leaderAbilities);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        assertTrue(receiver.hasLeaderAura());
        receiver.tick(lane);
        assertFalse(receiver.hasLeaderAura());
    }

    private static final class CountingPig extends PigTower {
        private int recounts;

        private CountingPig(GridPosition position) {
            super(AnimalTowers.T1_PIG_TOWER, OWNER, TeamId.RED, 1, position);
        }

        @Override
        protected void onStacksChanged(PlayerLane lane, int previousStacks, int currentStacks) {
            recounts++;
            super.onStacksChanged(lane, previousStacks, currentStacks);
        }
    }

    private static PlayerLane lane(AugmentSnapshot augments) {
        var layout = new LaneRegionLayout(1, Vec3.ZERO, List.of(new Vec3(20, 0, 0)), new Vec3(20, 0, 20),
                BlockBounds.of(new BlockPos(-10, -2, -10), new BlockPos(400, 5, 40)),
                List.of(new GridPosition(30, 0, 30)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        lane.assignAugmentSnapshot(augments);
        return lane;
    }

    private static AugmentSnapshot snapshot(String card) {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }
}
