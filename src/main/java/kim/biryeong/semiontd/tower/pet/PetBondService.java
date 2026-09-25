package kim.biryeong.semiontd.tower.pet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

/**
 * Resolves the pet builder's yard layout: which owner each companion is loyal to, how crowded that
 * yard is, and the adjacency facts the companion species need.
 *
 * <p>Loyalty is bound to the owner's grid position rather than to the owner instance, so an owner
 * upgrading to its next tier does not break the bond of everything around it.
 */
public final class PetBondService {
    private PetBondService() {
    }

    public static void refresh(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        refresh(lane.towers());
    }

    public static void refresh(Collection<Tower> towers) {
        if (towers == null || towers.isEmpty()) {
            return;
        }
        List<PetTower> pets = towers.stream()
                .filter(PetTower.class::isInstance)
                .map(PetTower.class::cast)
                .toList();
        if (pets.isEmpty()) {
            return;
        }

        // Final defense teleports every tower onto a handful of shared slots, and towers may even
        // land on the same one. Grid distance stops describing the yard the player actually built,
        // so re-deriving loyalty there would strand every companion as lost. Freeze what they earned
        // until resetForRound puts them back on their own tiles.
        if (pets.stream().anyMatch(Tower::deployedAtFinalDefense)) {
            return;
        }

        List<PetTower> owners = pets.stream().filter(PetTower::isOwner).toList();
        List<PetTower> companions = pets.stream().filter(PetTower::isCompanion).toList();

        bindLoyalty(companions, owners);

        // Only living companions occupy a yard, matching how packs and solo cats are counted.
        Map<GridPosition, List<PetTower>> yards = new HashMap<>();
        for (PetTower companion : companions) {
            GridPosition loyalPosition = companion.loyalOwnerPosition();
            if (loyalPosition != null && companion.health() > 0.0) {
                yards.computeIfAbsent(loyalPosition, key -> new ArrayList<>()).add(companion);
            }
        }

        Map<PetTower, Integer> packSizes = resolvePacks(companions, yards);
        for (PetTower companion : companions) {
            applyYardState(companion, packSizes, owners, yards);
        }
    }

    /**
     * Dogs form a pack by touching, and the pack grows through the chain: a dog adjacent to a dog
     * that is adjacent to a third is in a pack of three. Packs are pure geometry, so a pack can span
     * two yards. Every member of a pack sees the same size, itself included.
     */
    private static Map<PetTower, Integer> resolvePacks(List<PetTower> companions,
                                                     Map<GridPosition, List<PetTower>> yards) {
        List<PetTower> dogs = companions.stream()
                .filter(companion -> companion.role() == PetRole.DOG || familyYardActive(companion, yards))
                .filter(companion -> companion.health() > 0.0)
                .toList();
        Map<PetTower, Integer> sizes = new HashMap<>();
        Set<PetTower> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PetTower start : dogs) {
            if (!visited.add(start)) {
                continue;
            }
            List<PetTower> pack = new ArrayList<>();
            Deque<PetTower> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                PetTower current = pending.poll();
                pack.add(current);
                for (PetTower candidate : dogs) {
                    if (visited.contains(candidate)
                            || !sameOwnerLane(current, candidate)
                            || !isAdjacent(current.position(), candidate.position())) {
                        continue;
                    }
                    visited.add(candidate);
                    pending.add(candidate);
                }
            }
            pack.forEach(member -> sizes.put(member, pack.size()));
        }
        return sizes;
    }

    /**
     * Companions imprint on an owner the first time one is adjacent, and never re-target afterwards.
     * A loyalty whose owner tower is gone from the board is released so the companion can imprint again.
     */
    private static void bindLoyalty(List<PetTower> companions, List<PetTower> owners) {
        for (PetTower companion : companions) {
            GridPosition bound = companion.loyalOwnerPosition();
            if (bound != null && findOwnerAt(owners, companion, bound) != null) {
                continue;
            }
            companion.bindLoyalOwner(null);
            PetTower imprinted = owners.stream()
                    .filter(owner -> sameOwnerLane(companion, owner))
                    .filter(owner -> distance(companion.position(), owner.position()) > 0
                            && distance(companion.position(), owner.position()) <= owner.yardRadius())
                    .min(Comparator.comparingInt(owner -> distance(companion.position(), owner.position())))
                    .orElse(null);
            if (imprinted != null) {
                companion.bindLoyalOwner(imprinted.position());
            }
        }
    }

    private static void applyYardState(
            PetTower companion,
            Map<PetTower, Integer> packSizes,
            List<PetTower> owners,
            Map<GridPosition, List<PetTower>> yards
    ) {
        GridPosition loyalPosition = companion.loyalOwnerPosition();
        List<PetTower> yard = loyalPosition == null ? List.of() : yards.getOrDefault(loyalPosition, List.of()).stream()
                .filter(other -> sameOwnerLane(companion, other)).toList();
        PetTower owner = loyalPosition == null ? null : findOwnerAt(owners, companion, loyalPosition);
        boolean ownerActive = owner != null && owner.health() > 0.0;

        int packSize = packSizes.getOrDefault(companion, 0);

        boolean familyYard = familyYardActive(companion, yards);
        boolean soloCat = companion.role() == PetRole.CAT && (familyYard || yard.stream()
                .noneMatch(other -> other != companion && other.role() == PetRole.CAT && other.health() > 0.0));

        companion.updateYardState(yard.size(), packSize, soloCat, ownerActive,
                owner == null ? null : owner.type(), familyYard);
    }

    private static boolean familyYardActive(PetTower companion, Map<GridPosition, List<PetTower>> yards) {
        if (!companion.augmentSnapshot().has(PetTower.FAMILY) || companion.loyalOwnerPosition() == null) {
            return false;
        }
        return yards.getOrDefault(companion.loyalOwnerPosition(), List.of()).stream()
                .filter(other -> sameOwnerLane(companion, other))
                .filter(PetTower::isAdult)
                .map(PetTower::role)
                .distinct().count() == 3;
    }

    private static PetTower findOwnerAt(List<PetTower> owners, PetTower companion, GridPosition position) {
        return owners.stream()
                .filter(owner -> sameOwnerLane(companion, owner))
                .filter(owner -> Objects.equals(owner.position(), position))
                .findFirst()
                .orElse(null);
    }

    static boolean isAdjacent(GridPosition first, GridPosition second) {
        int distance = distance(first, second);
        return distance > 0 && distance <= PetBalance.YARD_RADIUS;
    }

    static int distance(GridPosition first, GridPosition second) {
        if (first == null || second == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(
                Math.abs(first.x() - second.x()),
                Math.max(Math.abs(first.y() - second.y()), Math.abs(first.z() - second.z()))
        );
    }

    private static boolean sameOwnerLane(PetTower tower, PetTower candidate) {
        return Objects.equals(tower.ownerPlayer(), candidate.ownerPlayer())
                && tower.teamId() == candidate.teamId()
                && tower.laneId() == candidate.laneId();
    }
}
