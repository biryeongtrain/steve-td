package kim.biryeong.semiontd.tower.resonance;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;

public final class ResonanceService {
    private ResonanceService() {
    }

    public static void refreshLane(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        refresh(lane.towers());
    }

    public static void refreshLaneAfterTowerSale(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        refreshAfterTowerSale(lane.towers());
    }

    public static void refresh(Collection<Tower> towers) {
        refresh(towers, false);
    }

    static void refreshAfterTowerSale(Collection<Tower> towers) {
        refresh(towers, true);
    }

    private static void refresh(Collection<Tower> towers, boolean allowDecrease) {
        if (towers == null || towers.isEmpty()) {
            return;
        }
        List<ResonanceTower> resonanceTowers = towers.stream()
                .filter(ResonanceTower.class::isInstance)
                .map(ResonanceTower.class::cast)
                .toList();
        for (ResonanceTower tower : resonanceTowers) {
            applyState(tower, resonanceTowers, allowDecrease);
        }
        for (ResonanceTower tower : resonanceTowers) {
            applyAura(tower, resonanceTowers, allowDecrease);
        }
    }

    private static void applyState(ResonanceTower tower, List<ResonanceTower> towers, boolean allowDecrease) {
        int maxLinks = Math.max(0, abilityInt(tower, "maxLinksPerTower"));
        if (maxLinks <= 0) {
            tower.updateResonanceState(0, 0, allowDecrease);
            return;
        }

        int linkedTowers = (int) towers.stream()
                .filter(candidate -> candidate != tower)
                .filter(candidate -> sameOwnerLane(tower, candidate))
                .filter(candidate -> !candidate.type().id().equals(tower.type().id()))
                .filter(candidate -> distance(tower.position(), candidate.position()) <= ability(tower, "linkRange"))
                .sorted(Comparator
                        .comparingInt((ResonanceTower candidate) -> distance(tower.position(), candidate.position()))
                        .thenComparing(candidate -> candidate.type().id()))
                .limit(maxLinks)
                .count();

        int maxLevel = Math.max(0, abilityInt(tower, "maxResonanceLevel"));
        tower.updateResonanceState(resonanceLevel(tower, linkedTowers, maxLevel), linkedTowers, allowDecrease);
    }

    private static void applyAura(ResonanceTower tower, List<ResonanceTower> towers, boolean allowDecrease) {
        double attackSpeedAuraBonus = towers.stream()
                .filter(candidate -> candidate != tower)
                .filter(candidate -> candidate.aspect() == ResonanceAspect.AMPLIFY)
                .filter(candidate -> candidate.resonanceLevel() >= 2)
                .filter(candidate -> sameOwnerLane(tower, candidate))
                .filter(candidate -> distance(tower.position(), candidate.position()) <= ability(candidate, "bloomAuraRange"))
                .mapToDouble(ResonanceService::bloomAuraAttackSpeedBonus)
                .max()
                .orElse(0.0);
        double damageVsSlowedAuraBonus = towers.stream()
                .filter(candidate -> candidate != tower)
                .filter(candidate -> candidate.aspect() == ResonanceAspect.FROST)
                .filter(candidate -> candidate.resonanceLevel() >= 2)
                .filter(candidate -> sameOwnerLane(tower, candidate))
                .filter(candidate -> distance(tower.position(), candidate.position()) <= ability(candidate, "frostAuraRange"))
                .mapToDouble(ResonanceService::frostAuraDamageVsSlowedBonus)
                .max()
                .orElse(0.0);
        tower.updateAuraAttackSpeedBonus(attackSpeedAuraBonus, allowDecrease);
        tower.updateAuraDamageVsSlowedBonus(damageVsSlowedAuraBonus, allowDecrease);
    }

    private static double bloomAuraAttackSpeedBonus(ResonanceTower tower) {
        return tower.resonanceLevel() >= 3
                ? ability(tower, "bloomLevel3AuraAttackSpeedBonus")
                : ability(tower, "bloomLevel2AuraAttackSpeedBonus");
    }

    private static double frostAuraDamageVsSlowedBonus(ResonanceTower tower) {
        return tower.resonanceLevel() >= 3
                ? ability(tower, "frostLevel3AuraDamageVsSlowedBonus")
                : ability(tower, "frostLevel2AuraDamageVsSlowedBonus");
    }

    private static int resonanceLevel(ResonanceTower tower, int linkedTowers, int maxLevel) {
        if (maxLevel >= 3 && linkedTowers >= abilityInt(tower, "level3RequiredLinks")) {
            return 3;
        }
        if (maxLevel >= 2 && linkedTowers >= abilityInt(tower, "level2RequiredLinks")) {
            return 2;
        }
        if (maxLevel >= 1 && linkedTowers >= abilityInt(tower, "level1RequiredLinks")) {
            return 1;
        }
        return 0;
    }

    private static boolean sameOwnerLane(ResonanceTower tower, ResonanceTower candidate) {
        return Objects.equals(tower.ownerPlayer(), candidate.ownerPlayer())
                && tower.teamId() == candidate.teamId()
                && tower.laneId() == candidate.laneId();
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

    private static double ability(ResonanceTower tower, String key) {
        return TowerBalanceRuntime.ability(tower.type().id(), key);
    }

    private static int abilityInt(ResonanceTower tower, String key) {
        return TowerBalanceRuntime.abilityInt(tower.type().id(), key);
    }
}
