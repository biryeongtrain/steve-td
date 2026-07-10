package kim.biryeong.semiontd.tower.legion;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;

public class LegionGoatTower extends SupportTower {
    private static final int MAX_STACKS = 3;
    private static final ResourceLocation[] DAMAGE_SOURCES = stackSources("goat_damage");
    private static final ResourceLocation[] CLONE_DAMAGE_SOURCES = stackSources("goat_clone_damage");
    private static final ResourceLocation[] CLONE_DAMAGE_REDUCTION_SOURCES = stackSources("goat_clone_damage_reduction");
    private static final Comparator<LegionGoatTower> STACK_ORDER = Comparator
            .comparingInt((LegionGoatTower tower) -> tower.originalPosition().x())
            .thenComparingInt(tower -> tower.originalPosition().y())
            .thenComparingInt(tower -> tower.originalPosition().z())
            .thenComparing(tower -> tower.type().id());

    public LegionGoatTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public LegionGoatTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        SemionTowerEntity source = towerEntity(this, lane).orElse(null);
        if (source == null) {
            return false;
        }
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "goat_buff"),
                source,
                radius(),
                TowerAreaTargetMode.REGISTERED_AND_CLONES,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        ).withFilter(target -> isBuffTarget(target.tower()) && stackIndexFor(target.tower(), lane).isPresent());
        return SemionTdApi.areaEffects().applyToTowers(request, target -> {
            OptionalInt stackIndex = stackIndexFor(target.tower(), lane);
            if (stackIndex.isEmpty() || target.entity().isEmpty()) {
                return AreaEffectOutcome.UNCHANGED;
            }
            SemionTowerEntity entity = target.entity().orElseThrow();
            boolean applied;
            if (target.illusion()) {
                applied = applyEffect(
                        entity,
                        TimedEffectType.TOWER_DAMAGE_BONUS,
                        CLONE_DAMAGE_SOURCES[stackIndex.getAsInt()],
                        value("cloneDamageBonus")
                );
                applied |= applyEffect(
                        entity,
                        TimedEffectType.TOWER_DAMAGE_REDUCTION,
                        CLONE_DAMAGE_REDUCTION_SOURCES[stackIndex.getAsInt()],
                        value("cloneDamageReduction")
                );
            } else {
                applied = applyEffect(
                        entity,
                        TimedEffectType.TOWER_DAMAGE_BONUS,
                        DAMAGE_SOURCES[stackIndex.getAsInt()],
                        value("damageBonus")
                );
            }
            return applied ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        }).appliedCount() > 0;
    }

    private boolean isBuffTarget(Tower target) {
        return target != null
                && !(target instanceof LegionGoatTower)
                && target.health() > 0.0
                && target.ownerPlayer().equals(ownerPlayer())
                && target.teamId() == teamId()
                && target.laneId() == laneId()
                && LegionTowers.isLegionTower(target.type())
                && isWithinRange(target.position());
    }

    private OptionalInt stackIndexFor(Tower target, PlayerLane lane) {
        List<LegionGoatTower> stackProviders = lane.towers().stream()
                .filter(LegionGoatTower.class::isInstance)
                .map(LegionGoatTower.class::cast)
                .filter(goat -> goat.canBuff(target))
                .sorted(STACK_ORDER)
                .limit(maxStacks())
                .toList();
        for (int index = 0; index < stackProviders.size(); index++) {
            if (stackProviders.get(index) == this) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private boolean canBuff(Tower target) {
        return target != null
                && target.health() > 0.0
                && target.ownerPlayer().equals(ownerPlayer())
                && target.teamId() == teamId()
                && target.laneId() == laneId()
                && isWithinRange(target.position());
    }

    private boolean isWithinRange(GridPosition targetPosition) {
        double radius = radius();
        double dx = targetPosition.x() - position().x();
        double dy = targetPosition.y() - position().y();
        double dz = targetPosition.z() - position().z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private boolean applyEffect(SemionTowerEntity entity, TimedEffectType type, ResourceLocation source, double magnitude) {
        if (magnitude <= 0.0) {
            return false;
        }
        return entity.applyTimedEffect(type, source, magnitude, ticks("buffDurationTicks"));
    }

    private Optional<SemionTowerEntity> towerEntity(Tower target, PlayerLane lane) {
        if (!(target instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private double radius() {
        return value("radius");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private int maxStacks() {
        return Math.max(1, Math.min(MAX_STACKS, TowerBalanceRuntime.abilityInt(type().id(), "maxStacks")));
    }

    private static ResourceLocation supportId(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "tower_support/" + path);
    }

    private static ResourceLocation[] stackSources(String path) {
        ResourceLocation[] sources = new ResourceLocation[MAX_STACKS];
        for (int index = 0; index < MAX_STACKS; index++) {
            sources[index] = supportId(path + "_" + (index + 1));
        }
        return sources;
    }
}
