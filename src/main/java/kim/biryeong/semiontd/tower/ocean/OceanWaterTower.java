package kim.biryeong.semiontd.tower.ocean;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class OceanWaterTower extends EntityBackedTower {
    private static final TowerDataKey<UUID> SUPPLY_TARGET_ID = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ocean/water_supply_target"),
            UUID.class
    );
    private static final BlockState WATER_MARKER = Blocks.LIGHT.defaultBlockState()
            .setValue(LightBlock.LEVEL, 0)
            .setValue(LightBlock.WATERLOGGED, true);

    private boolean waveActive;
    private boolean supplyTargetsCaptured;
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
        BlockPos desired = waterBlockPos(position());
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
        lines.add("웨이브 시작 물 +" + oneDecimal(value("waveStartWater")));
        lines.add("초당 물 +" + oneDecimal(value("waterPerSupply") * 20.0 / Math.max(1, ticks("supplyIntervalTicks"))));
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof OceanWaterTower waterTower) {
            waveActive = waterTower.waveActive;
            supplyTargetsCaptured = waterTower.supplyTargetsCaptured;
            supplyTargetIds = Set.copyOf(waterTower.supplyTargetIds);
        }
    }

    private void captureSupplyTargets(PlayerLane lane) {
        if (supplyTargetsCaptured) {
            return;
        }
        supplyTargetsCaptured = true;
        supplyTargetIds = nearbyTargets(lane).stream()
                .map(OceanWaterTower::supplyTargetId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<OceanTower> supply(PlayerLane lane, double amount) {
        if (lane == null || amount <= 0.0 || supplyTargetIds.isEmpty()) {
            return List.of();
        }
        List<OceanTower> targets = lane.towers().stream()
                .filter(OceanTower.class::isInstance)
                .map(OceanTower.class::cast)
                .filter(target -> target.health() > 0.0)
                .filter(target -> target.getData(SUPPLY_TARGET_ID).filter(supplyTargetIds::contains).isPresent())
                .toList();
        targets.forEach(target -> target.addWater(amount));
        return targets;
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
        double x = target.position().x() - position().x();
        double y = target.position().y() - position().y();
        double z = target.position().z() - position().z();
        return x * x + y * y + z * z;
    }

    private void placeWater(PlayerLane lane) {
        if (lane == null || placedWaterPos != null) {
            return;
        }
        BlockPos target = waterBlockPos(position());
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
        BlockPos center = waterBlockPos(position());
        int tier = OceanTowers.tier(type());
        int bubbles = burst ? tier * 5 : tier * 2;
        lane.arenaWorld().sendParticles(
                ParticleTypes.BUBBLE,
                center.getX() + 0.5,
                center.getY() + 0.5,
                center.getZ() + 0.5,
                bubbles,
                0.35,
                0.35,
                0.35,
                0.02
        );
        if (tier >= 2) {
            lane.arenaWorld().sendParticles(
                    ParticleTypes.SPLASH,
                    center.getX() + 0.5,
                    center.getY() + 0.8,
                    center.getZ() + 0.5,
                    burst ? tier * 2 : tier,
                    0.3,
                    0.1,
                    0.3,
                    0.02
            );
        }
        if (tier == 3 && burst) {
            lane.arenaWorld().sendParticles(
                    ParticleTypes.NAUTILUS,
                    center.getX() + 0.5,
                    center.getY() + 0.5,
                    center.getZ() + 0.5,
                    8,
                    0.45,
                    0.2,
                    0.45,
                    0.05
            );
        }
        OceanVfx.showWaterTransfer(
                lane.arenaWorld(),
                new Vec3(center.getX() + 0.5, center.getY() + 0.55, center.getZ() + 0.5),
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
}
