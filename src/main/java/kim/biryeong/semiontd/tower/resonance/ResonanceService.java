package kim.biryeong.semiontd.tower.resonance;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;

public final class ResonanceService {
    private ResonanceService() {
    }

    public static void captureWaveStart(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        refresh(lane.towers());
        long now = lane.arenaWorld() == null ? 0 : lane.arenaWorld().getGameTime();
        for (Tower tower : lane.towers()) {
            if (tower instanceof ResonanceTower resonance) resonance.startAugmentWave(now);
        }
    }

    public static void tickAugments(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || !lane.augmentSnapshot().has(ResonanceTower.CYCLE)) return;
        long now = lane.arenaWorld().getGameTime();
        List<ResonanceTower> towers = lane.towers().stream().filter(ResonanceTower.class::isInstance)
                .map(ResonanceTower.class::cast).filter(tower -> tower.health() > 0)
                .sorted(Comparator.comparingInt(ResonanceTower::resonanceLevel).reversed()
                        .thenComparing(Tower::logicalId)).toList();
        int count = 0;
        for (ResonanceTower tower : towers) {
            if (!tower.cycleDue(now)) continue;
            if (count++ >= (int) tower.augmentSnapshot().parameter(ResonanceTower.CYCLE, "maxTowers", 5)) continue;
            if (tower.entityId().isPresent()
                    && lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity source) {
                instantAttack(source);
            }
        }
    }

    public static void refresh(Collection<Tower> towers) {
        if (towers == null || towers.isEmpty()) {
            return;
        }
        List<ResonanceTower> resonanceTowers = towers.stream()
                .filter(ResonanceTower.class::isInstance)
                .map(ResonanceTower.class::cast)
                .filter(tower -> tower.health() > 0.0)
                .toList();
        Map<ResonanceTower, List<ResonanceTower>> links = new HashMap<>();
        for (ResonanceTower tower : resonanceTowers) links.put(tower, linkedTowers(tower, resonanceTowers));
        for (ResonanceTower tower : resonanceTowers) applyState(tower, links.get(tower).size());
        Map<java.util.UUID, ResonanceTower> friends = new HashMap<>();
        Comparator<ResonanceTower> leastLinks = Comparator.comparingInt(ResonanceTower::resonanceLinks)
                .thenComparing(Tower::logicalId);
        for (ResonanceTower tower : resonanceTowers) {
            if (tower.augmentSnapshot().has(ResonanceTower.FRIEND)) {
                friends.merge(tower.ownerPlayer(), tower, (first, second) -> leastLinks.compare(first, second) <= 0 ? first : second);
            }
        }
        for (ResonanceTower tower : friends.values()) {
            ResonanceTower friend = resonanceTowers.stream()
                    .filter(other -> other != tower && sameOwnerLane(tower, other))
                    .filter(other -> other.aspect() != tower.aspect() && !links.get(tower).contains(other))
                    .sorted(Comparator.comparingInt(ResonanceTower::resonanceLinks).reversed()
                            .thenComparingInt(other -> distance(tower.position(), other.position()))
                            .thenComparing(Tower::logicalId)).findFirst().orElse(null);
            if (friend != null) {
                applyState(tower, Math.min(abilityInt(tower, "maxLinksPerTower"), tower.resonanceLinks() + 1));
                tower.setInternetFriend(friend.logicalId());
            }
        }
        for (ResonanceTower tower : resonanceTowers) {
            applyAura(tower, resonanceTowers);
        }
    }

    private static List<ResonanceTower> linkedTowers(ResonanceTower tower, List<ResonanceTower> towers) {
        int maxLinks = Math.max(0, abilityInt(tower, "maxLinksPerTower"));
        double range = tower.augmentSnapshot().has(ResonanceTower.REMOTE)
                ? tower.augmentSnapshot().parameter(ResonanceTower.REMOTE, "linkRange", 3)
                : ability(tower, "linkRange");
        return towers.stream()
                .filter(candidate -> candidate != tower)
                .filter(candidate -> sameOwnerLane(tower, candidate))
                .filter(candidate -> distance(tower.position(), candidate.position()) <= range)
                .filter(candidate -> candidate.aspect() != tower.aspect())
                .limit(maxLinks)
                .toList();
    }

    private static void applyState(ResonanceTower tower, int linkedTowers) {
        int maxLevel = Math.max(0, abilityInt(tower, "maxResonanceLevel"));
        tower.updateResonanceState(resonanceLevel(tower, linkedTowers, maxLevel), linkedTowers);
    }

    private static void instantAttack(SemionTowerEntity source) {
        var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(source.runtimeTower(), "resonance_cycle"),
                source, source.position(), source.attackRange(), Set.of(), source::isValidAttackTarget,
                AreaVfxSpec.none()).nearestTargets(1);
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            AugmentCombat.additionalAttack(source, target, 1.0);
            return AreaEffectOutcome.APPLIED;
        });
    }

    private static void applyAura(ResonanceTower tower, List<ResonanceTower> towers) {
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
        tower.updateAuraAttackSpeedBonus(attackSpeedAuraBonus);
        tower.updateAuraDamageVsSlowedBonus(damageVsSlowedAuraBonus);
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
