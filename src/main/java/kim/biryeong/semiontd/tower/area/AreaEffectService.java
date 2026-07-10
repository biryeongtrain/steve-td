package kim.biryeong.semiontd.tower.area;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import kim.biryeong.semiontd.api.area.AreaEffectAction;
import kim.biryeong.semiontd.api.area.AreaEffectApi;
import kim.biryeong.semiontd.api.area.AreaEffectHit;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxRenderPolicy;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AreaEffectService implements AreaEffectApi {
    private final SemionGameManager gameManager;

    public AreaEffectService(SemionGameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    @Override
    public AreaEffectResult<SemionMonsterEntity> applyToMonsters(
            MonsterAreaEffectRequest request,
            AreaEffectAction<SemionMonsterEntity> action
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        PlayerLane lane = activeLane(request.source());
        if (lane == null || !(request.source().level() instanceof ServerLevel level)) {
            return AreaEffectResult.empty();
        }

        AABB box = searchBox(request.center(), request.radius());
        double radiusSqr = request.radius() * request.radius();
        List<SemionMonsterEntity> candidates = level.getEntities(request.source(), box, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && !monster.isRemoved()
                                && monster.runtimeMonster() != null
                                && request.source().defendsLane(monster.runtimeMonster().targetLaneId())
                                && !request.excludedTargetIds().contains(monster.getUUID())
                                && monster.position().distanceToSqr(request.center()) <= radiusSqr
                                && request.targetFilter().test(monster))
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .toList();
        return apply(request.source(), request.effectId(), request.center(), request.radius(), request.vfx(), candidates,
                SemionMonsterEntity::position, action);
    }

    @Override
    public AreaEffectResult<AreaTowerTarget> applyToTowers(
            TowerAreaEffectRequest request,
            AreaEffectAction<AreaTowerTarget> action
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        PlayerLane lane = activeLane(request.source());
        if (lane == null || !(request.source().level() instanceof ServerLevel level)) {
            return AreaEffectResult.empty();
        }

        Map<Tower, AreaTowerTarget> candidates = new LinkedHashMap<>();
        if (request.targetMode() != TowerAreaTargetMode.ENTITIES) {
            for (Tower tower : lane.towers()) {
                Optional<SemionTowerEntity> entity = towerEntity(tower, level);
                candidates.put(tower, new AreaTowerTarget(tower, entity, false));
            }
        }
        if (request.targetMode() != TowerAreaTargetMode.REGISTERED) {
            for (SemionTowerEntity entity : level.getEntitiesOfClass(
                    SemionTowerEntity.class,
                    searchBox(request.center(), request.radius()),
                    target -> validTowerEntity(request.source(), target)
            )) {
                Tower tower = entity.runtimeTower();
                boolean illusion = entity.isIllusionClone() || tower instanceof IllusionRuntimeTower;
                if (request.targetMode() == TowerAreaTargetMode.REGISTERED_AND_CLONES && !illusion) {
                    continue;
                }
                candidates.put(tower, new AreaTowerTarget(tower, Optional.of(entity), illusion));
            }
        }

        double radiusSqr = request.radius() * request.radius();
        List<AreaTowerTarget> filtered = candidates.values().stream()
                .filter(target -> request.includeSource() || target.tower() != request.source().runtimeTower())
                .filter(target -> validTowerTarget(request.source(), target))
                .filter(target -> towerPosition(target).distanceToSqr(request.center()) <= radiusSqr)
                .filter(request.targetFilter())
                .toList();
        return apply(request.source(), request.effectId(), request.center(), request.radius(), request.vfx(), filtered,
                AreaEffectService::towerPosition, action);
    }

    private PlayerLane activeLane(SemionTowerEntity source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return null;
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Semion TD area effects must run on the server thread");
        }
        if (source.ownerPlayer() == null || source.runtimeTower() == null) {
            return null;
        }
        SemionGame game = gameManager.playableGame(source.ownerPlayer()).orElse(null);
        if (game != null) {
            PlayerLane lane = game.playerLane(source.ownerPlayer()).orElse(null);
            if (lane != null) {
                return lane;
            }
        }
        return AreaEffectLaneIndex.find(source).orElse(null);
    }

    private static <T> AreaEffectResult<T> apply(
            SemionTowerEntity source,
            net.minecraft.resources.ResourceLocation effectId,
            Vec3 center,
            double radius,
            kim.biryeong.semiontd.api.area.AreaVfxSpec vfx,
            List<T> candidates,
            java.util.function.Function<T, Vec3> position,
            AreaEffectAction<T> action
    ) {
        List<AreaEffectHit<T>> hits = new ArrayList<>();
        int killedCount = 0;
        for (T target : candidates) {
            Vec3 snapshot = position.apply(target);
            AreaEffectOutcome outcome = Objects.requireNonNull(action.apply(target), "area-effect outcome");
            if (!outcome.changed()) {
                continue;
            }
            hits.add(new AreaEffectHit<>(target, snapshot, outcome));
            if (outcome.killed()) {
                killedCount++;
            }
        }
        AreaEffectResult<T> result = new AreaEffectResult<>(candidates.size(), hits, hits.size(), killedCount);
        boolean render = shouldRender(vfx, result.appliedCount());
        if (render) {
            TowerVfxService.showAreaEffect(source, effectId, vfx.styleId(), center, radius,
                    hits.stream().map(AreaEffectHit::position).toList(), result.candidateCount(), result.appliedCount(), result.killedCount());
        }
        return result;
    }

    static boolean shouldRender(kim.biryeong.semiontd.api.area.AreaVfxSpec vfx, int appliedCount) {
        return vfx != null
                && !vfx.styleId().equals(AreaVfxStyles.NONE)
                && (vfx.renderPolicy() == AreaVfxRenderPolicy.ON_TRIGGER || appliedCount > 0);
    }

    private static boolean validTowerEntity(SemionTowerEntity source, SemionTowerEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.runtimeTower() != null
                && source.ownerPlayer().equals(target.ownerPlayer())
                && source.teamId() == target.teamId()
                && source.laneId() == target.laneId();
    }

    private static boolean validTowerTarget(SemionTowerEntity source, AreaTowerTarget target) {
        Tower tower = target.tower();
        return tower.health() > 0.0
                && source.ownerPlayer().equals(tower.ownerPlayer())
                && source.teamId() == tower.teamId()
                && source.laneId() == tower.laneId();
    }

    private static Optional<SemionTowerEntity> towerEntity(Tower tower, ServerLevel level) {
        if (!(tower instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static Vec3 towerPosition(AreaTowerTarget target) {
        if (target.entity().isPresent()) {
            return target.entity().get().position();
        }
        GridPosition position = target.tower().position();
        return new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    private static AABB searchBox(Vec3 center, double radius) {
        return new AABB(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius
        );
    }
}
