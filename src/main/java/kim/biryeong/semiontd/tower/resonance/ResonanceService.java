package kim.biryeong.semiontd.tower.resonance;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
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
        ResonanceTowerLinkController.refresh(towers);
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

    static int distance(GridPosition first, GridPosition second) {
        return ResonanceTowerLinkController.distance(first, second);
    }
}
