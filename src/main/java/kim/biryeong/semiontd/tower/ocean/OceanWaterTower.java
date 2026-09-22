package kim.biryeong.semiontd.tower.ocean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class OceanWaterTower extends EntityBackedTower {
    private static final double EPSILON = 1.0E-9;
    private static final TowerDataKey<UUID> SUPPLY_TARGET_ID = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ocean/water_supply_target"),
            UUID.class
    );
    private static final BlockState WATER_MARKER = Blocks.LIGHT.defaultBlockState()
            .setValue(LightBlock.LEVEL, 0)
            .setValue(LightBlock.WATERLOGGED, true);

    private boolean waveActive;
    private Set<UUID> supplyTargetIds = Set.of();
    private BlockPos placedWaterPos;
    private BlockState replacedState;

    public OceanWaterTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public OceanWaterTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public static BlockPos waterBlockPos(GridPosition position) {
        return new BlockPos(position.x(), position.y() + 1, position.z());
    }

    public static BlockState waterMarker() {
        return WATER_MARKER;
    }

    public static boolean canPlaceAt(PlayerLane lane, GridPosition position) {
        return lane != null && position != null && lane.arenaWorld().getBlockState(waterBlockPos(position)).isAir();
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        placeWater(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        entity.setNoAi(true);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        restoreWater(lane);
        super.onRemoved(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        restoreWater(lane);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        BlockPos desired = waterBlockPos(originalPosition());
        if (entityId().isPresent() && !desired.equals(placedWaterPos)) {
            restoreWater(lane);
            placeWater(lane);
        }
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        captureSupplyTargets(lane);
        List<OceanTower> targets = supply(lane, value("waveStartWater"));
        if (!targets.isEmpty()) {
            showSupplyVfx(lane, targets, true);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        super.resetForRound(lane);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (!waveActive) {
            return false;
        }
        List<OceanTower> targets = supply(lane, value("waterPerSupply"));
        if (targets.isEmpty()) {
            return false;
        }
        showSupplyVfx(lane, targets, false);
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, TowerBalanceRuntime.abilityTicks(type().id(), "supplyIntervalTicks"));
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("공급 반경 " + oneDecimal(value("supplyRadius")) + "블록");
        lines.add("웨이브 시작 물 +" + oneDecimal(value("waveStartWater") * supplyMultiplier()));
        lines.add("초당 물 +" + oneDecimal(value("waterPerSupply") * supplyMultiplier()
                * 20.0 / Math.max(1, ticks("supplyIntervalTicks"))));
        lines.add("중첩 체감 계수 " + percent(global("waterSupplyStackDecay"))
                + " (같은 대상 연결 수 증가 시 타워당 공급 효율 감소)");
        lines.add("물 " + oneDecimal(global("waterSoftCap") * supplyMultiplier()) + "부터 공급 감소, "
                + oneDecimal(global("waterSupplyStopThreshold")) + " 이상 공급 중단");
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof OceanWaterTower waterTower) {
            waveActive = waterTower.waveActive;
            supplyTargetIds = Set.copyOf(waterTower.supplyTargetIds);
        }
    }

    private void captureSupplyTargets(PlayerLane lane) {
        supplyTargetIds = nearbyTargets(lane).stream()
                .map(OceanWaterTower::supplyTargetId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<OceanTower> supply(PlayerLane lane, double amount) {
        if (deployedAtFinalDefense() || lane == null || amount <= 0.0 || supplyTargetIds.isEmpty()) {
            return List.of();
        }
        List<OceanTower> targets = lane.towers().stream()
                .filter(OceanTower.class::isInstance)
                .map(OceanTower.class::cast)
                .filter(target -> target.health() > 0.0)
                .filter(target -> target.getData(SUPPLY_TARGET_ID).filter(supplyTargetIds::contains).isPresent())
                .toList();
        ArrayList<OceanTower> suppliedTargets = new ArrayList<>();
        Map<OceanTower, Double> allocations = supplyAllocations(targets, amount * supplyMultiplier(),
                augmentSnapshot().has("job_ocean_s"));
        for (Map.Entry<OceanTower, Double> allocation : allocations.entrySet()) {
            OceanTower target = allocation.getKey();
            double remainingCapacity = Math.max(0.0, global("waterSupplyStopThreshold") - target.water());
            double supplied = Math.min(
                    remainingCapacity,
                    allocation.getValue() * supplyStackMultiplier(lane, target)
                            * supplyEfficiency(target.water(), target.waterSoftCap())
            );
            if (supplied > EPSILON) {
                target.addWater(supplied);
                suppliedTargets.add(target);
            }
        }
        return List.copyOf(suppliedTargets);
    }

    static Map<OceanTower, Double> supplyAllocations(List<OceanTower> targets, double amount, boolean recycle) {
        Map<OceanTower, Double> allocations = new LinkedHashMap<>();
        for (OceanTower target : targets) {
            OceanTower recipient = target;
            if (recycle && target.water() + EPSILON >= target.waterSoftCap()) {
                recipient = targets.stream().filter(candidate -> candidate.water() + EPSILON < candidate.waterSoftCap())
                        .min(Comparator.comparingDouble(OceanTower::water).thenComparing(Tower::logicalId))
                        .orElse(null);
            }
            if (recipient != null) {
                allocations.merge(recipient, amount, Double::sum);
            }
        }
        return allocations;
    }

    double supplyMultiplier() {
        return augmentSnapshot().has("job_ocean_g1")
                ? augmentSnapshot().parameter("job_ocean_g1", "supplyMultiplier", 1.5) : 1.0;
    }

    private List<OceanTower> nearbyTargets(PlayerLane lane) {
        if (lane == null) {
            return List.of();
        }
        double radiusSqr = value("supplyRadius") * value("supplyRadius");
        return lane.towers().stream()
                .filter(OceanTower.class::isInstance)
                .map(OceanTower.class::cast)
                .filter(target -> target.health() > 0.0 && distanceSqr(target) <= radiusSqr)
                .toList();
    }

    private static UUID supplyTargetId(OceanTower target) {
        return target.getData(SUPPLY_TARGET_ID).orElseGet(() -> {
            UUID id = UUID.randomUUID();
            target.setData(SUPPLY_TARGET_ID, id);
            return id;
        });
    }

    private double distanceSqr(Tower target) {
        double x = target.originalPosition().x() - originalPosition().x();
        double y = target.originalPosition().y() - originalPosition().y();
        double z = target.originalPosition().z() - originalPosition().z();
        return x * x + y * y + z * z;
    }

    private double supplyStackMultiplier(PlayerLane lane, OceanTower target) {
        int sourceCount = (int) lane.towers().stream()
                .filter(OceanWaterTower.class::isInstance)
                .map(OceanWaterTower.class::cast)
                .filter(source -> source.health() > 0.0 && !source.deployedAtFinalDefense())
                .filter(source -> source.distanceSqr(target) <= source.value("supplyRadius") * source.value("supplyRadius"))
                .count();
        return stackedSupplyMultiplier(Math.max(1, sourceCount), global("waterSupplyStackDecay"));
    }

    static double stackedSupplyMultiplier(int sourceCount, double decay) {
        int sources = Math.max(1, sourceCount);
        double clampedDecay = Math.max(0.0, Math.min(1.0, decay));
        if (sources == 1 || 1.0 - clampedDecay <= EPSILON) {
            return 1.0;
        }
        return (1.0 - Math.pow(clampedDecay, sources)) / (sources * (1.0 - clampedDecay));
    }

    private double supplyEfficiency(double water, double softCap) {
        double stopThreshold = global("waterSupplyStopThreshold");
        double efficiency = (stopThreshold - water) / Math.max(EPSILON, stopThreshold - softCap);
        return Math.max(0.0, Math.min(1.0, efficiency));
    }

    private void placeWater(PlayerLane lane) {
        if (lane == null || placedWaterPos != null) {
            return;
        }
        BlockPos target = waterBlockPos(originalPosition());
        BlockState current = lane.arenaWorld().getBlockState(target);
        if (!current.isAir()) {
            return;
        }
        replacedState = current;
        if (lane.arenaWorld().setBlock(target, WATER_MARKER, Block.UPDATE_CLIENTS)) {
            placedWaterPos = target;
        } else {
            replacedState = null;
        }
    }

    private void restoreWater(PlayerLane lane) {
        if (lane == null || placedWaterPos == null) {
            return;
        }
        if (lane.arenaWorld().getBlockState(placedWaterPos).equals(WATER_MARKER) && replacedState != null) {
            lane.arenaWorld().setBlock(placedWaterPos, replacedState, Block.UPDATE_CLIENTS);
        }
        placedWaterPos = null;
        replacedState = null;
    }

    private void showSupplyVfx(PlayerLane lane, List<OceanTower> targets, boolean burst) {
        BlockPos center = waterBlockPos(originalPosition());
        int tier = OceanTowers.tier(type());
        Vec3 source = new Vec3(center.getX() + 0.5, center.getY() + 1.03, center.getZ() + 0.5);
        OceanVfx.showWaterSourcePulse(lane.arenaWorld(), source, tier, burst);
        OceanVfx.showWaterSupply(
                lane.arenaWorld(),
                source,
                targets,
                burst
        );
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private double global(String key) {
        return TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, key);
    }
}
