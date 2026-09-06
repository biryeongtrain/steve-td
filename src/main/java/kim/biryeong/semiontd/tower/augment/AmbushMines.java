package kim.biryeong.semiontd.tower.augment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class AmbushMines {
    private static final TowerDataKey<Mines> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_mines"), Mines.class);
    private static final Mines EMPTY = new Mines(-1, List.of(), List.of(), Set.of(), Set.of());
    private AmbushMines() {}

    /** Fixed map path, not the placement-to-entrance vector. Vertical/zero segments do not choose a direction. */
    static Optional<Vec3> entranceDirection(List<Vec3> path) {
        for (int i = 1; i < path.size(); i++) {
            Vec3 delta = path.get(i).subtract(path.get(i - 1));
            double length = Math.hypot(delta.x, delta.z);
            if (length > 1.0e-9) return Optional.of(new Vec3(-delta.x / length, 0, -delta.z / length));
        }
        return Optional.empty();
    }

    static List<Vec3> offsets(PlayerLane lane) {
        return entranceDirection(lane.laneLayout().pathPoints())
                .map(direction -> List.of(direction.scale(2), direction.scale(4), direction.scale(6))).orElse(List.of());
    }

    static boolean canPlace(PlayerLane lane, GridPosition position) {
        List<Vec3> offsets = offsets(lane);
        return offsets.size() == 3 && offsets.stream().allMatch(offset -> ground(lane, position, offset, false).isPresent());
    }

    static void placed(AugmentTower tower, PlayerLane lane) {
        if (tower.hasData(STATE)) return;
        List<Vec3> offsets = offsets(lane);
        List<Vec3> points = offsets.stream().map(offset -> ground(lane, tower.position(), offset, false).orElse(null)).toList();
        if (points.size() == 3 && points.stream().noneMatch(java.util.Objects::isNull)) {
            tower.setData(STATE, new Mines(-1, offsets, points, Set.of(), Set.of()));
        }
    }

    static void reload(AugmentTower tower, PlayerLane lane, int round) {
        placed(tower, lane);
        Mines old = tower.getDataOrDefault(STATE, EMPTY);
        if (old.round() == round) return;
        List<Vec3> points = new ArrayList<>();
        Set<Integer> disabled = new HashSet<>();
        for (int i = 0; i < old.offsets().size(); i++) {
            var point = ground(lane, tower.position(), old.offsets().get(i), false);
            points.add(point.orElse(AugmentTower.location(tower)));
            if (point.isEmpty()) disabled.add(i);
        }
        tower.setData(STATE, new Mines(round, old.offsets(), points, disabled, Set.of()));
    }

    static void move(AugmentTower tower, PlayerLane lane) {
        Mines old = tower.getDataOrDefault(STATE, EMPTY);
        List<Vec3> points = new ArrayList<>();
        Set<Integer> spent = new HashSet<>(old.spent());
        for (int i = 0; i < old.offsets().size(); i++) {
            var point = ground(lane, tower.position(), old.offsets().get(i), true);
            points.add(point.orElse(AugmentTower.location(tower)));
            if (point.isEmpty()) spent.add(i);
        }
        tower.setData(STATE, new Mines(old.round(), old.offsets(), points, spent, old.hitIds()));
    }

    static void tick(AugmentTower tower, PlayerLane lane) {
        SemionTowerEntity source = tower.runtimeEntity(lane).orElse(null);
        if (source == null || !source.isAlive()) return;
        Mines current = tower.getDataOrDefault(STATE, EMPTY);
        if (current.round() != tower.currentRound()) return;
        Set<Integer> spent = new HashSet<>(current.spent());
        Set<UUID> hit = new HashSet<>(current.hitIds());
        for (int i = 0; i < current.points().size(); i++) {
            if (spent.contains(i)) continue;
            Vec3 point = current.points().get(i);
            var trigger = request(tower, source, point, tower.value("triggerRadius", 1.25), hit, AreaVfxSpec.none());
            List<SemionMonsterEntity> triggering = new ArrayList<>();
            SemionTdApi.areaEffects().applyToMonsters(trigger.withFilter(trigger.targetFilter().and(
                    target -> !AugmentEconomyService.isLowPressure(target.runtimeMonster()))).nearestTargets(1), target -> {
                triggering.add(target); return AreaEffectOutcome.UNCHANGED;
            });
            if (triggering.isEmpty()) continue;
            var explosion = request(tower, source, point, tower.value("damageRadius", 2), hit,
                    AreaVfxSpec.onChange(AreaVfxStyles.SPLASH)).nearestTargets((int) tower.value("mineTargets", 2));
            List<SemionMonsterEntity> targets = new ArrayList<>();
            SemionTdApi.areaEffects().applyToMonsters(explosion, target -> {
                targets.add(target); return AreaEffectOutcome.UNCHANGED;
            });
            if (targets.isEmpty()) continue;
            Set<UUID> selected = new HashSet<>();
            for (var target : targets) selected.add(target.runtimeMonster().logicalId());
            hit.addAll(selected);
            spent.add(i);
            // Record attempts before applying damage: shields and synchronous kill callbacks cannot re-arm a mine.
            tower.setData(STATE, new Mines(current.round(), current.offsets(), current.points(), spent, hit));
            tower.recordMineExplosion();
            TowerAreaDamage.apply(tower, source, explosion.withFilter(target -> groundTarget(target)
                    && target.runtimeMonster() != null && selected.contains(target.runtimeMonster().logicalId())),
                    target -> tower.value("mineDamage", 100), true,
                    (target, dealt, killed) -> tower.recordAugmentSpecialDamage(dealt));
            TowerVfxService.showSecondaryAttack(source, point);
        }
        tower.setData(STATE, new Mines(current.round(), current.offsets(), current.points(), spent, hit));
    }

    private static MonsterAreaEffectRequest request(AugmentTower tower, SemionTowerEntity source, Vec3 point,
            double radius, Set<UUID> hitIds, AreaVfxSpec vfx) {
        return new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, "mine"), source, point, radius, Set.of(),
                target -> groundTarget(target) && target.runtimeMonster() != null
                        && !hitIds.contains(target.runtimeMonster().logicalId()), vfx);
    }

    private static boolean groundTarget(SemionMonsterEntity target) {
        return target.isAlive() && target.onGround() && !target.isNoGravity();
    }

    private static Optional<Vec3> ground(PlayerLane lane, GridPosition anchor, Vec3 offset, boolean finalDefense) {
        if (lane == null || lane.arenaWorld() == null) return Optional.empty();
        double x = anchor.x() + .5 + offset.x, z = anchor.z() + .5 + offset.z;
        var bounds = lane.laneLayout().laneArea();
        Vec3 location = new Vec3(x, anchor.y() + 1, z);
        if (finalDefense) {
            if (!lane.laneLayout().isInsideFinalDefenseTowerArea(location)) return Optional.empty();
        } else if (x < bounds.min().getX() || x >= bounds.max().getX() + 1
                || z < bounds.min().getZ() || z >= bounds.max().getZ() + 1) return Optional.empty();
        int top = finalDefense ? anchor.y() + 2 : bounds.max().getY() + 1;
        int bottom = finalDefense ? anchor.y() - 4 : bounds.min().getY() - 4;
        for (int y = top; y >= bottom; y--) {
            BlockPos floor = BlockPos.containing(x, y, z);
            var state = lane.arenaWorld().getBlockState(floor);
            if (!state.getCollisionShape(lane.arenaWorld(), floor).isEmpty()
                    && lane.arenaWorld().getBlockState(floor.above()).getCollisionShape(lane.arenaWorld(), floor.above()).isEmpty()) {
                return Optional.of(new Vec3(x, y + 1, z));
            }
        }
        return Optional.empty();
    }

    static List<String> details(AugmentTower tower) {
        Mines state = tower.getDataOrDefault(STATE, EMPTY);
        return List.of("지뢰 " + (state.points().size() - state.spent().size()) + "/3", "피해 시도 대상 " + state.hitIds().size());
    }

    static List<Vec3> preview(PlayerLane lane, GridPosition position) {
        return offsets(lane).stream().map(offset -> ground(lane, position, offset, false)).flatMap(Optional::stream).toList();
    }

    private record Mines(int round, List<Vec3> offsets, List<Vec3> points, Set<Integer> spent, Set<UUID> hitIds) {
        Mines { offsets = List.copyOf(offsets); points = List.copyOf(points); spent = Set.copyOf(spent); hitIds = Set.copyOf(hitIds); }
    }
}
