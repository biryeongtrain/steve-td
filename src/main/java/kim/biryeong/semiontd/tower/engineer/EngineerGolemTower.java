package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class EngineerGolemTower extends Tower {
    private static final ResourceLocation COPPER_GOLEM_ID = ResourceLocation.parse("friendsandfoes:copper_golem");
    private final Map<GridPosition, Integer> plateCooldowns = new HashMap<>();
    private UUID entityUuid;
    private GridPosition targetPlate;
    private GridPosition lastPressedPlate;
    private boolean waveActive;
    private int pressesThisWave;

    public EngineerGolemTower(
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
    public int slotWeight() {
        return 0;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    @Override
    public boolean targetableByMonsters() {
        return false;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        spawnGolem(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        Mob golem = golem(lane);
        if (golem != null) {
            golem.discard();
        }
        entityUuid = null;
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        targetPlate = null;
        lastPressedPlate = null;
        plateCooldowns.clear();
        pressesThisWave = 0;
        Mob golem = ensureGolem(lane);
        if (golem != null) {
            acquireAndMove(lane, golem);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        targetPlate = null;
        lastPressedPlate = null;
        plateCooldowns.clear();
        pressesThisWave = 0;
        super.resetForRound(lane);
        Mob golem = golem(lane);
        if (golem == null) {
            spawnGolem(lane);
        } else {
            golem.teleportTo(home().x, home().y, home().z);
            golem.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        waveActive = false;
        targetPlate = null;
        lastPressedPlate = null;
        Mob golem = golem(lane);
        if (golem != null) {
            golem.teleportTo(home().x, home().y, home().z);
            golem.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        return false;
    }

    @Override
    public void tick(PlayerLane lane) {
        Mob golem = ensureGolem(lane);
        if (golem == null) {
            return;
        }
        golem.setInvulnerable(true);
        golem.setNoAi(true);
        golem.setCustomName(Component.literal(type().displayName()));
        golem.setCustomNameVisible(true);
        plateCooldowns.replaceAll((position, ticks) -> Math.max(0, ticks - 1));
        plateCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);

        if (!waveActive) {
            moveToward(golem, home());
            return;
        }
        if (lastPressedPlate != null) {
            EngineerCircuitTower lastPlate = findPlate(lane, lastPressedPlate);
            if (lastPlate == null || !lastPlate.platePressed(lane)) {
                lastPressedPlate = null;
            }
        }
        acquireAndMove(lane, golem);
    }

    private void acquireAndMove(PlayerLane lane, Mob golem) {
        if (targetPlate == null || plateCooldowns.containsKey(targetPlate) || findPlate(lane, targetPlate) == null) {
            targetPlate = choosePlate(lane, golem.position());
        }
        if (targetPlate == null) {
            return;
        }
        EngineerCircuitTower plate = findPlate(lane, targetPlate);
        if (plate == null) {
            targetPlate = null;
            return;
        }
        Vec3 target = plateCenter(plate);
        Vec3 horizontal = new Vec3(target.x - golem.getX(), 0.0, target.z - golem.getZ());
        if (horizontal.lengthSqr() <= 0.01) {
            faceToward(golem, horizontal);
            if (!plate.platePressed(lane) && !plate.pressPlate(lane)) {
                moveToward(golem, target);
                return;
            }
            plateCooldowns.put(targetPlate, EngineerBalance.plateCooldownTicks());
            lastPressedPlate = targetPlate;
            pressesThisWave++;
            showPlatePressed(lane, plate);
            targetPlate = null;
            return;
        }
        moveToward(golem, target);
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("<green>무적 · 비공격 · 비표적 · 슬롯 0</green>");
        lines.add(targetPlate == null
                ? "<gray>다음 <aqua>발판</aqua> 탐색 중</gray>"
                : "<aqua>이동 목표</aqua> <white>" + targetPlate.x() + ", " + targetPlate.z() + "</white>");
        lines.add("<gold>이번 웨이브 발판 작동</gold> <white>" + pressesThisWave + "회</white>");
        return List.copyOf(lines);
    }

    private GridPosition choosePlate(PlayerLane lane, Vec3 origin) {
        return lane.towers().stream()
                .filter(EngineerCircuitTower.class::isInstance)
                .map(EngineerCircuitTower.class::cast)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> tower.plateKind() != null)
                .filter(tower -> !plateCooldowns.containsKey(tower.originalPosition()))
                .filter(tower -> !tower.originalPosition().equals(lastPressedPlate))
                .sorted(Comparator
                        .comparingInt((EngineerCircuitTower tower) -> tower.plateKind().priority()).reversed()
                        .thenComparingDouble(tower -> plateCenter(tower).distanceToSqr(origin))
                        .thenComparingInt(tower -> tower.originalPosition().x())
                        .thenComparingInt(tower -> tower.originalPosition().z()))
                .map(Tower::originalPosition)
                .findFirst()
                .orElse(null);
    }

    private EngineerCircuitTower findPlate(PlayerLane lane, GridPosition position) {
        Tower tower = lane.towerAt(position);
        if (!(tower instanceof EngineerCircuitTower circuit)
                || !ownerPlayer().equals(circuit.ownerPlayer())
                || circuit.plateKind() == null) {
            return null;
        }
        return circuit;
    }

    private void moveToward(Mob golem, Vec3 target) {
        Vec3 offset = target.subtract(golem.position());
        Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
        double distance = horizontal.length();
        if (distance <= 0.04) {
            golem.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double speed = Math.min(distance, Math.max(0.01, EngineerBalance.golemMoveSpeed()));
        Vec3 movement = horizontal.scale(speed / distance);
        faceToward(golem, movement);
        golem.setPos(golem.getX() + movement.x, golem.getY(), golem.getZ() + movement.z);
        golem.setDeltaMovement(Vec3.ZERO);
    }

    private static void faceToward(Mob golem, Vec3 direction) {
        if (direction.lengthSqr() <= 1.0E-8) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        golem.setYRot(yaw);
        golem.setYHeadRot(yaw);
        golem.setYBodyRot(yaw);
    }

    private void spawnGolem(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || golem(lane) != null) {
            return;
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(COPPER_GOLEM_ID).orElse(null);
        if (type == null) {
            return;
        }
        Entity created = type.create(lane.arenaWorld(), EntitySpawnReason.TRIGGERED);
        if (!(created instanceof Mob mob)) {
            return;
        }
        Vec3 home = home();
        mob.setPos(home.x, home.y, home.z);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setCustomName(Component.literal(type().displayName()));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        if (lane.arenaWorld().addFreshEntity(mob)) {
            entityUuid = mob.getUUID();
        }
    }

    Mob golemEntity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || entityUuid == null) {
            return null;
        }
        Entity entity = lane.arenaWorld().getEntity(entityUuid);
        return entity instanceof Mob mob && !mob.isRemoved() ? mob : null;
    }

    public boolean ownsGolemEntity(Entity entity) {
        return entity != null && entityUuid != null && entityUuid.equals(entity.getUUID());
    }

    GridPosition targetPlate() {
        return targetPlate;
    }

    int pressesThisWave() {
        return pressesThisWave;
    }

    private Mob golem(PlayerLane lane) {
        return golemEntity(lane);
    }

    private Mob ensureGolem(PlayerLane lane) {
        Mob golem = golem(lane);
        if (golem == null) {
            spawnGolem(lane);
            golem = golem(lane);
        }
        return golem;
    }

    private static void showPlatePressed(PlayerLane lane, EngineerCircuitTower plate) {
        if (!(lane.arenaWorld() instanceof ServerLevel level)) {
            return;
        }
        BlockPos position = plate.circuitPosition();
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                position.getX() + 0.5,
                position.getY() + 0.18,
                position.getZ() + 0.5,
                8,
                0.35,
                0.08,
                0.35,
                0.02
        );
    }

    private Vec3 home() {
        return new Vec3(originalPosition().x() + 0.5, originalPosition().y() + 1.0, originalPosition().z() + 0.5);
    }

    private static Vec3 plateCenter(EngineerCircuitTower plate) {
        BlockPos block = plate.circuitPosition();
        return new Vec3(block.getX() + 0.5, block.getY() + 0.0625, block.getZ() + 0.5);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }
}
