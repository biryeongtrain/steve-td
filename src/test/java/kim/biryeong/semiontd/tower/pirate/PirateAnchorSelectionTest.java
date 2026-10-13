package kim.biryeong.semiontd.tower.pirate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class PirateAnchorSelectionTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID PARTNER = new UUID(0, 2);
    private TowerBalanceConfig previous;
    private PlayerLane lane;
    private Field towersField;
    private Field bonusField;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() throws ReflectiveOperationException {
        previous = TowerBalanceRuntime.current();
        configure(2);
        LaneRegionLayout layout = new LaneRegionLayout(1, new Vec3(.5, 64, .5),
                List.of(new Vec3(.5, 64, 4.5)), new Vec3(.5, 64, 10.5),
                BlockBounds.of(new BlockPos(-20, 60, -20), new BlockPos(20, 70, 20)),
                List.of(new GridPosition(0, 63, 10)));
        lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        towersField = PlayerLane.class.getDeclaredField("towers");
        towersField.setAccessible(true);
        bonusField = PirateTower.class.getDeclaredField("anchorBonus");
        bonusField.setAccessible(true);
    }

    @AfterEach
    void cleanup() {
        TowerBalanceRuntime.apply(previous);
    }

    @Test
    void stableTiesKeepTheFirstAnchorsAfterStrongerCandidates() throws ReflectiveOperationException {
        PirateTower first = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        PirateTower second = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 1, 0, 0);
        PirateTower strongest = anchor(PirateTowers.ANCIENT_ANCHOR, OWNER, TeamId.RED, 0, 0, 1);
        setTowers(List.of(first, second, strongest));
        assertTrue(first.isSelectedAnchorFor(lane, first));
        assertFalse(second.isSelectedAnchorFor(lane, first));
        assertTrue(strongest.isSelectedAnchorFor(lane, first));
        assertMatchesReference(first);

        setTowers(List.of(second, first, strongest));
        assertFalse(first.isSelectedAnchorFor(lane, first));
        assertTrue(second.isSelectedAnchorFor(lane, first));
        assertMatchesReference(first);
    }

    @Test
    void candidateFiltersKeepTeamOwnerAndThreeDimensionalBoundarySemantics() throws ReflectiveOperationException {
        configure(1);
        PirateTower selected = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        PirateTower partnerAtBoundary = anchor(PirateTowers.DEEP_ANCHOR, PARTNER, TeamId.RED, 0, 2, 0);
        PirateTower tooHigh = anchor(PirateTowers.ANCIENT_ANCHOR, OWNER, TeamId.RED, 0, 3, 0);
        PirateTower diagonalOutside = anchor(PirateTowers.ANCIENT_ANCHOR, OWNER, TeamId.RED, 2, 1, 0);
        PirateTower otherTeam = anchor(PirateTowers.ANCIENT_ANCHOR, OWNER, TeamId.BLUE, 0, 0, 0);
        PirateTower nonAnchor = anchor(PirateTowers.DECKHAND, OWNER, TeamId.RED, 0, 0, 0);
        partnerAtBoundary.syncHealth(0);
        setTowers(List.of(selected, tooHigh, diagonalOutside, otherTeam, nonAnchor, partnerAtBoundary));
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
        assertTrue(partnerAtBoundary.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);

        setTowers(List.of(selected, tooHigh, diagonalOutside, otherTeam, nonAnchor));
        assertTrue(selected.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);
    }

    @Test
    void duplicateIdentityUsesTheFirstEligibleOccurrence() throws ReflectiveOperationException {
        PirateTower selected = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        PirateTower earlierTie = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        setTowers(List.of(earlierTie, selected, selected, earlierTie));
        assertTrue(selected.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);
        setTowers(List.of(earlierTie, earlierTie, selected, selected));
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);
    }

    @Test
    void missingOrIneligibleSelfCannotBecomeSelected() throws ReflectiveOperationException {
        PirateTower selected = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        PirateTower other = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        setTowers(List.of(other));
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
        PirateTower outside = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 3, 0);
        setTowers(List.of(selected));
        assertFalse(selected.isSelectedAnchorFor(lane, outside));
        assertEquals(reference(selected, outside), selected.isSelectedAnchorFor(lane, outside));
        setTowers(List.of());
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
    }

    @Test
    void zeroLimitAndReloadTakeEffectWithoutCachedSelections() throws ReflectiveOperationException {
        PirateTower selected = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        PirateTower stronger = anchor(PirateTowers.DEEP_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
        setTowers(List.of(selected, stronger));
        configure(0);
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
        assertFalse(stronger.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);
        configure(1);
        assertFalse(selected.isSelectedAnchorFor(lane, selected));
        bonusField.setDouble(selected, .1);
        assertTrue(selected.isSelectedAnchorFor(lane, selected));
        assertMatchesReference(selected);
        assertThrows(IllegalArgumentException.class, () -> configure(-1));
        assertTrue(selected.isSelectedAnchorFor(lane, selected));
    }

    @Test
    void seededRostersMatchTheFormerStableSortAcrossLimits() throws ReflectiveOperationException {
        Random random = new Random(20261009);
        TowerType[] types = {PirateTowers.DROPPED_ANCHOR, PirateTowers.DEEP_ANCHOR,
                PirateTowers.ANCIENT_ANCHOR, PirateTowers.DECKHAND};
        for (int maxStacks : new int[] {0, 1, 2, 5, 100}) {
            configure(maxStacks);
            for (int trial = 0; trial < 30; trial++) {
                List<Tower> roster = new ArrayList<>();
                PirateTower recipient = anchor(PirateTowers.DROPPED_ANCHOR, OWNER, TeamId.RED, 0, 0, 0);
                roster.add(recipient);
                for (int index = 0; index < 35; index++) {
                    PirateTower tower = anchor(types[random.nextInt(types.length)],
                            random.nextBoolean() ? OWNER : PARTNER,
                            random.nextInt(5) == 0 ? TeamId.BLUE : TeamId.RED,
                            random.nextInt(7) - 3, random.nextInt(5) - 2, random.nextInt(7) - 3);
                    bonusField.setDouble(tower, random.nextInt(3) * .01);
                    roster.add(tower);
                    if (random.nextInt(7) == 0) {
                        roster.add(tower);
                    }
                }
                Collections.shuffle(roster, random);
                setTowers(roster);
                assertMatchesReference(recipient);
            }
        }
    }

    private void assertMatchesReference(Tower recipient) throws ReflectiveOperationException {
        for (Tower tower : lane.towers()) {
            if (tower instanceof PirateTower candidate) {
                assertEquals(reference(candidate, recipient), candidate.isSelectedAnchorFor(lane, recipient),
                        candidate.type().id() + " at " + candidate.position());
            }
        }
    }

    private boolean reference(PirateTower selected, Tower recipient) {
        return lane.towers().stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast)
                .filter(anchor -> PirateTowers.isAnchor(anchor.type()) && anchor.teamId() == selected.teamId()
                        && distanceSquared(anchor, recipient) <= ability(anchor, "radius", 2) * ability(anchor, "radius", 2))
                .sorted((left, right) -> Double.compare(ability(right, "damageReduction", 0) + bonus(right),
                        ability(left, "damageReduction", 0) + bonus(left)))
                .limit(TowerBalanceRuntime.abilityInt(selected.type().id(), "maxStacks", 2))
                .anyMatch(anchor -> anchor == selected);
    }

    private double bonus(PirateTower tower) {
        try {
            return bonusField.getDouble(tower);
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        }
    }

    private void setTowers(List<Tower> roster) throws IllegalAccessException {
        @SuppressWarnings("unchecked")
        List<Tower> towers = (List<Tower>) towersField.get(lane);
        towers.clear();
        towers.addAll(roster);
    }

    private static PirateTower anchor(TowerType type, UUID owner, TeamId team, int x, int y, int z) {
        return new PirateTower(type, owner, team, 1, new GridPosition(x, y, z));
    }

    private static double distanceSquared(Tower first, Tower second) {
        double x = first.position().x() - second.position().x();
        double y = first.position().y() - second.position().y();
        double z = first.position().z() - second.position().z();
        return x * x + y * y + z * z;
    }

    private static double ability(PirateTower tower, String key, double fallback) {
        return TowerBalanceRuntime.ability(tower.type().id(), key, fallback);
    }

    private static void configure(int maxStacks) {
        TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(), Map.of(
                PirateTowers.DROPPED_ANCHOR.id(), Map.of("radius", 2.0, "damageReduction", .01, "maxStacks", (double) maxStacks),
                PirateTowers.DEEP_ANCHOR.id(), Map.of("radius", 2.0, "damageReduction", .02, "maxStacks", (double) maxStacks),
                PirateTowers.ANCIENT_ANCHOR.id(), Map.of("radius", 2.0, "damageReduction", .05, "maxStacks", (double) maxStacks))));
    }
}
