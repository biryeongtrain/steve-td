package kim.biryeong.semiontd.tower.engineer;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class EngineerTrapTower extends EntityBackedTower {
    private static final MonsterDataKey<Long> PISTON_IMMUNITY_UNTIL = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "engineer_piston_immunity_until"), Long.class
    );

    private final EngineerTowers.TrapKind kind;
    private final int tier;
    private boolean waveActive;
    private boolean armed = true;
    private int activeTicks;
    private int actionCooldown;
    private int fuseTicks = -1;
    private boolean tntUsed;
    private boolean poweredLastTick;
    private int activationPlateDistance;
    private EngineerTowers.PlateKind activationPlateKind;
    private EngineerTowers.PlateKind tntPlateKind;
    private EntityVisual placedVisual;
    private ElementHolder upperDoorHolder;

    public EngineerTrapTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.kind = EngineerTowers.trapKind(type).orElseThrow();
        this.tier = EngineerTowers.trapTier(type);
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    @Override
    public boolean targetableByMonsters() {
        return kind == EngineerTowers.TrapKind.DOOR && waveActive && activeTicks > 0 && health() > 0.0;
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount
    ) {
        double damage = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        if (kind != EngineerTowers.TrapKind.DOOR || !waveActive || activeTicks <= 0) {
            return damage;
        }
        return damage * (1.0 - EngineerBalance.doorDamageReduction(pressCount()));
    }

    @Override
    public EntityVisual visual() {
        return placedVisual == null ? super.visual() : placedVisual;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        Direction facing = incomingDirection(lane);
        placedVisual = switch (kind) {
            case DISPENSER -> BlockDisplayVisual.builder(Blocks.DISPENSER.defaultBlockState()
                    .setValue(DispenserBlock.FACING, facing)).build();
            case PISTON -> BlockDisplayVisual.builder(Blocks.PISTON.defaultBlockState()
                    .setValue(PistonBaseBlock.FACING, facing)).build();
            default -> null;
        };
        super.onPlaced(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        if (kind != EngineerTowers.TrapKind.DOOR) {
            return;
        }
        BlockDisplayElement upperDoor = new BlockDisplayElement(
                Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
        );
        upperDoor.setTranslation(new Vector3f(-0.5F, 1.0F, -0.5F));
        upperDoor.setShadowRadius(0.5F);
        upperDoor.setShadowStrength(1.0F);
        upperDoorHolder = new ElementHolder();
        upperDoorHolder.addElement(upperDoor);
        EntityAttachment.ofTicking(upperDoorHolder, entity);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        disable(lane);
        discardUpperDoorVisual();
        super.onRemoved(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        armed = true;
        activeTicks = 0;
        actionCooldown = 0;
        fuseTicks = -1;
        tntUsed = false;
        poweredLastTick = false;
        activationPlateDistance = 0;
        activationPlateKind = null;
        tntPlateKind = null;
        updateActiveName(source(lane), false);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        disable(lane);
        armed = true;
        tntUsed = false;
        super.resetForRound(lane);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        disable(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (!waveActive || health() <= 0.0 || lane == null || lane.arenaWorld() == null) {
            return;
        }
        SemionTowerEntity source = source(lane);
        if (source == null) {
            return;
        }
        boolean physicalPower = lane.arenaWorld().hasNeighborSignal(signalPosition());
        Optional<PlateActivation> plateActivation = physicalPower
                ? recentPlateActivation(lane)
                : Optional.empty();
        boolean powered = plateActivation.isPresent();
        if (!powered) {
            armed = true;
        }
        if (powered && !poweredLastTick) {
            PlateActivation activation = plateActivation.orElseThrow();
            activationPlateDistance = activation.distance();
            activationPlateKind = activation.kind();
            if (activeTicks > 0) {
                activeTicks = activationDurationTicks();
                armed = false;
                updateActiveName(source, true);
                showActivationVfx(source);
            } else if (armed) {
                activate(lane, source);
            }
        }
        poweredLastTick = powered;

        if (fuseTicks >= 0) {
            showTntFuseVfx(source);
            if (--fuseTicks <= 0) {
                explodeTnt(source);
                fuseTicks = -1;
            }
        }
        if (activeTicks <= 0) {
            return;
        }
        boolean periodicVfx = activeTicks < activationDurationTicks()
                && activeTicks % Math.max(1, EngineerBalance.activeVfxIntervalTicks()) == 0;
        if (periodicVfx && kind != EngineerTowers.TrapKind.TNT) {
            showActivationVfx(source);
        }
        activeTicks--;
        switch (kind) {
            case DOOR -> tickDoor(lane, source);
            case DISPENSER -> tickDispenser(lane, source);
            case SLIME -> tickSlime(source, periodicVfx);
            case TNT, PISTON -> { }
        }
        if (activeTicks <= 0) {
            if (kind == EngineerTowers.TrapKind.DOOR) {
                clearDoorTargets(lane, source);
            }
            updateActiveName(source, false);
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("<gold>전력</gold> <white>" + (activeTicks > 0 ? "작동 중" : "대기") + "</white>");
        lines.add("<yellow>" + (kind == EngineerTowers.TrapKind.DOOR ? "도발" : "작동")
                + " 잔여시간</yellow> <white>"
                + String.format(java.util.Locale.ROOT, "%.1f초", activeTicks / 20.0) + "</white>");
        lines.add("<red>재무장</red> <white>" + (armed ? "완료" : "신호 해제 필요") + "</white>");
        int presses = pressCount();
        lines.add("<yellow>이번 매치 발판 작동</yellow> <white>" + presses + "회</white>");
        if (kind == EngineerTowers.TrapKind.DOOR) {
            lines.add("<green>누적 피해 감소</green> <white>"
                    + precisePercent(EngineerBalance.doorDamageReduction(presses)) + " / "
                    + precisePercent(EngineerBalance.doorDamageReductionCap()) + "</white>");
        }
        if (kind == EngineerTowers.TrapKind.TNT) {
            lines.add("<red>라운드 폭발</red> <white>" + (tntUsed ? "사용함" : "준비됨") + "</white>");
            int extraTargets = EngineerBalance.tntExtraTargets(presses);
            lines.add("<green>누적 추가 대상</green> <white>+" + extraTargets + "/"
                    + EngineerBalance.tntExtraTargetCap() + "기 · 현재 최대 "
                    + ((long) intAbility("maxTargets", tntMaxTargets(tier)) + extraTargets) + "기</white>");
            if (fuseTicks >= 0) {
                lines.add("<yellow>점화 잔여시간</yellow> <white>"
                        + String.format(java.util.Locale.ROOT, "%.1f초", fuseTicks / 20.0) + "</white>");
            }
        }
        if ((kind == EngineerTowers.TrapKind.TNT || kind == EngineerTowers.TrapKind.DISPENSER)
                && damagePlateKind() != null) {
            EngineerTowers.PlateKind plateKind = damagePlateKind();
            lines.add("<aqua>발동 발판</aqua> <white>" + EngineerTowers.plate(plateKind).displayName() + "</white>");
            lines.add("<gold>발판 피해 보너스</gold> <green>+"
                    + Math.round((EngineerBalance.plateDamageMultiplier(plateKind) - 1.0) * 100.0)
                    + "%</green>");
        }
        if (kind == EngineerTowers.TrapKind.DISPENSER) {
            int appliedDistance = Math.min(activationPlateDistance, EngineerBalance.dispenserMaxPlateDistance());
            lines.add("<aqua>적용 회로 거리</aqua> <white>" + appliedDistance + "/"
                    + EngineerBalance.dispenserMaxPlateDistance() + "칸</white>"
                    + (activationPlateDistance > appliedDistance
                    ? " <gray>(실제 " + activationPlateDistance + "칸)</gray>" : ""));
            lines.add("<gold>거리 피해 보너스</gold> <green>+"
                    + Math.round((EngineerBalance.dispenserDamageMultiplier(activationPlateDistance) - 1.0) * 100.0)
                    + "%</green>");
            lines.add("<green>누적 피해 보너스</green> <white>+"
                    + Math.round((EngineerBalance.dispenserPressDamageMultiplier(presses) - 1.0) * 100.0)
                    + "%/" + Math.round(EngineerBalance.dispenserDamageBonusCap() * 100.0) + "%</white>");
        }
        if (kind == EngineerTowers.TrapKind.PISTON) {
            int extraTargets = EngineerBalance.pistonExtraTargets(presses);
            lines.add("<green>누적 추가 대상</green> <white>+" + extraTargets + "/"
                    + EngineerBalance.pistonExtraTargetCap() + "기 · 현재 최대 "
                    + ((long) intAbility("maxTargets", pistonMaxTargets(tier)) + extraTargets) + "기</white>");
            lines.add("<aqua>설치 제한</aqua> <white>플레이어당 " + EngineerBalance.maxPistons() + "기</white>");
            lines.add("<yellow>대상 면역</yellow> <white>"
                    + String.format(java.util.Locale.ROOT, "%.1f초", EngineerBalance.pistonImmunityTicks() / 20.0)
                    + "</white>");
        }
        if (kind == EngineerTowers.TrapKind.SLIME) {
            double baseSlow = ability("slow", slimeSlow(tier));
            double slow = EngineerBalance.slimeSlow(baseSlow, presses);
            lines.add("<green>누적 둔화 보너스</green> <white>+" + precisePercent(slow - baseSlow)
                    + "p · 현재 " + precisePercent(slow) + " / "
                    + precisePercent(EngineerBalance.slimeSlowCap()) + "</white>");
        }
        return List.copyOf(lines);
    }

    public int activeTicksRemaining() {
        return activeTicks;
    }

    public boolean armed() {
        return armed;
    }

    private void activate(PlayerLane lane, SemionTowerEntity source) {
        armed = false;
        activeTicks = activationDurationTicks();
        actionCooldown = 0;
        updateActiveName(source, true);
        showActivationVfx(source);
        switch (kind) {
            case TNT -> {
                if (!tntUsed) {
                    tntUsed = true;
                    tntPlateKind = activationPlateKind;
                    fuseTicks = EngineerBalance.tntFuseTicks();
                }
            }
            case PISTON -> firePiston(lane, source);
            case DOOR -> tickDoor(lane, source);
            case DISPENSER -> tickDispenser(lane, source);
            case SLIME -> tickSlime(source, false);
        }
    }

    private void tickDoor(PlayerLane lane, SemionTowerEntity source) {
        if (actionCooldown-- > 0) {
            return;
        }
        actionCooldown = Math.max(1, EngineerBalance.doorRetargetTicks()) - 1;
        double radius = ability("radius", doorRadius(tier));
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "door_taunt"),
                source,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF)
        );
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            if (target.getTarget() != source) {
                target.setTarget(source);
                return AreaEffectOutcome.APPLIED;
            }
            return AreaEffectOutcome.UNCHANGED;
        });
    }

    private void tickDispenser(PlayerLane lane, SemionTowerEntity source) {
        if (actionCooldown-- > 0) {
            return;
        }
        actionCooldown = Math.max(1, intAbility("intervalTicks", dispenserInterval(tier))) - 1;
        double range = ability("range", dispenserRange(tier));
        liveMonsters(lane).stream()
                .filter(target -> target.position().distanceToSqr(source.position()) <= range * range)
                .max(Comparator.comparingDouble(target -> target.runtimeMonster().laneProgress()))
                .ifPresent(target -> {
                    double damage = ability("damage", dispenserDamage(tier))
                            * EngineerBalance.dispenserDamageMultiplier(activationPlateDistance)
                            * EngineerBalance.plateDamageMultiplier(activationPlateKind)
                            * EngineerBalance.dispenserPressDamageMultiplier(pressCount());
                    DamageResult result = damageTargetResult(source, target, damage, DamageType.PHYSICAL);
                    TowerVfxService.showSecondaryAttack(source, target);
                    if (result.killed()) {
                        onKill(source, target, damage);
                    }
                });
    }

    private void tickSlime(SemionTowerEntity source, boolean showVfx) {
        double radius = ability("radius", slimeRadius(tier));
        double slow = EngineerBalance.slimeSlow(ability("slow", slimeSlow(tier)), pressCount());
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "slime"), source, radius,
                showVfx ? AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF) : AreaVfxSpec.none()
        );
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, 3);
            return AreaEffectOutcome.APPLIED;
        });
    }

    private void explodeTnt(SemionTowerEntity source) {
        double radius = ability("radius", tntRadius(tier));
        int cap = intAbility("maxTargets", tntMaxTargets(tier))
                + EngineerBalance.tntExtraTargets(pressCount());
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "tnt"), source, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION)
        ).nearestTargets(cap);
        TowerAreaDamage.apply(
                this, source, request,
                ignored -> ability("damage", tntDamage(tier))
                        * EngineerBalance.plateDamageMultiplier(tntPlateKind),
                true,
                (target, amount, killed) -> {}, DamageType.PHYSICAL
        );
    }

    private void firePiston(PlayerLane lane, SemionTowerEntity source) {
        double radius = ability("radius", pistonRadius(tier));
        long cap = (long) intAbility("maxTargets", pistonMaxTargets(tier))
                + EngineerBalance.pistonExtraTargets(pressCount());
        long now = lane.arenaWorld().getGameTime();
        Vec3 start = lane.laneLayout().positionAt(0.0);
        liveMonsters(lane).stream()
                .filter(target -> !target.runtimeMonster().inFinalDefenseCombat())
                .filter(target -> target.position().distanceToSqr(source.position()) <= radius * radius)
                .filter(target -> target.runtimeMonster().getData(PISTON_IMMUNITY_UNTIL).orElse(0L) <= now)
                .sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(source.position())))
                .limit(cap)
                .forEach(target -> {
                    Monster monster = target.runtimeMonster();
                    monster.setData(PISTON_IMMUNITY_UNTIL, now + EngineerBalance.pistonImmunityTicks());
                    monster.syncLaneProgress(0.0);
                    target.teleportTo(start.x, start.y, start.z);
                    target.getNavigation().stop();
                    TowerVfxService.showSecondaryAttack(source, target);
                });
    }

    private void clearDoorTargets(PlayerLane lane, SemionTowerEntity source) {
        for (SemionMonsterEntity monster : liveMonsters(lane)) {
            if (monster.getTarget() == source) {
                monster.setTarget(null);
            }
        }
    }

    private void disable(PlayerLane lane) {
        SemionTowerEntity source = source(lane);
        if (source != null && kind == EngineerTowers.TrapKind.DOOR) {
            clearDoorTargets(lane, source);
        }
        waveActive = false;
        activeTicks = 0;
        actionCooldown = 0;
        fuseTicks = -1;
        poweredLastTick = false;
        activationPlateDistance = 0;
        activationPlateKind = null;
        tntPlateKind = null;
        updateActiveName(source, false);
    }

    private void showActivationVfx(SemionTowerEntity source) {
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "powered"),
                AreaVfxStyles.PULSE,
                source.position().add(0.0, 0.15, 0.0),
                0.85,
                List.of(),
                0,
                0,
                0
        );
    }

    private void showTntFuseVfx(SemionTowerEntity source) {
        if (kind != EngineerTowers.TrapKind.TNT || fuseTicks < 0
                || fuseTicks % Math.max(1, EngineerBalance.tntFuseVfxIntervalTicks()) != 0) {
            return;
        }
        int total = Math.max(1, EngineerBalance.tntFuseTicks());
        double progress = 1.0 - Math.min(total, fuseTicks) / (double) total;
        showTntFusePulse(source, progress);
    }

    private void showTntFusePulse(SemionTowerEntity source, double progress) {
        Vec3 center = source.position().add(0.0, 0.22, 0.0);
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "tnt_fuse"),
                AreaVfxStyles.PULSE,
                center,
                0.45 + Math.max(0.0, Math.min(1.0, progress)) * 0.4,
                List.of(),
                0, 0, 0
        );
        if (progress >= 0.8 && source.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.08, center.z,
                    2, 0.05, 0.03, 0.05, 0.005);
        }
    }

    private int activationDurationTicks() {
        return kind == EngineerTowers.TrapKind.DOOR
                ? EngineerBalance.doorActiveTicks()
                : EngineerBalance.activeTicks();
    }

    public boolean showDebugVfx(PlayerLane lane, boolean tnt) {
        SemionTowerEntity source = source(lane);
        if (source == null || (tnt && kind != EngineerTowers.TrapKind.TNT)) {
            return false;
        }
        if (tnt) {
            showTntFusePulse(source, 0.75);
        } else {
            showActivationVfx(source);
        }
        return true;
    }

    private void updateActiveName(SemionTowerEntity source, boolean active) {
        if (source != null) {
            source.setCustomName(Component.literal((active ? "활성화된 " : "") + type().displayName()));
        }
    }

    private Direction incomingDirection(PlayerLane lane) {
        if (lane == null || lane.laneLayout() == null) {
            return Direction.NORTH;
        }
        Vec3 incoming = lane.laneLayout().positionAt(0.0);
        double x = incoming.x - (position().x() + 0.5);
        double z = incoming.z - (position().z() + 0.5);
        if (Math.abs(x) >= Math.abs(z)) {
            return x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void discardUpperDoorVisual() {
        if (upperDoorHolder != null) {
            upperDoorHolder.destroy();
            upperDoorHolder = null;
        }
    }

    boolean hasUpperDoorVisual() {
        return upperDoorHolder != null;
    }

    private SemionTowerEntity source(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return null;
        }
        return entityId().stream()
                .mapToObj(lane.arenaWorld()::getEntity)
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .filter(entity -> entity.isAlive() && !entity.isRemoved())
                .findFirst()
                .orElse(null);
    }

    private static List<SemionMonsterEntity> liveMonsters(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return List.of();
        }
        return lane.activeMonsters().stream()
                .filter(monster -> monster != null && monster.hasMinecraftEntity() && monster.health() > 0.0)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .filter(entity -> entity.isAlive() && !entity.isRemoved() && entity.runtimeMonster() != null)
                .toList();
    }

    private BlockPos signalPosition() {
        return new BlockPos(originalPosition().x(), originalPosition().y() + 1, originalPosition().z());
    }

    OptionalInt recentPlateDistance(PlayerLane lane) {
        Optional<PlateActivation> activation = recentPlateActivation(lane);
        return activation.isPresent() ? OptionalInt.of(activation.orElseThrow().distance()) : OptionalInt.empty();
    }

    private Optional<PlateActivation> recentPlateActivation(PlayerLane lane) {
        long now = lane.arenaWorld().getGameTime();
        long oldestAccepted = now - EngineerBalance.activeTicks();
        Map<BlockPos, EngineerCircuitTower> circuits = new HashMap<>();
        for (var tower : lane.towers()) {
            if (tower instanceof EngineerCircuitTower circuit && ownerPlayer().equals(circuit.ownerPlayer())) {
                circuits.put(circuit.circuitPosition(), circuit);
            }
        }
        return circuits.values().stream()
                .filter(circuit -> circuit.plateKind() != null)
                .filter(circuit -> circuit.lastPressedGameTime() >= oldestAccepted
                        && circuit.lastPressedGameTime() <= now)
                .map(circuit -> new PlatePath(
                        circuit.lastPressedGameTime(),
                        shortestDirectedDistance(circuits, circuit.circuitPosition()),
                        circuit.circuitPosition(),
                        circuit.plateKind()
                ))
                .filter(path -> path.distance() > 0)
                .sorted(Comparator.comparingLong(PlatePath::pressedAt).reversed()
                        .thenComparingInt(PlatePath::distance)
                        .thenComparingInt(path -> path.position().getX())
                        .thenComparingInt(path -> path.position().getY())
                        .thenComparingInt(path -> path.position().getZ()))
                .map(path -> new PlateActivation(path.distance(), path.kind()))
                .findFirst();
    }

    private int shortestDirectedDistance(Map<BlockPos, EngineerCircuitTower> circuits, BlockPos start) {
        ArrayDeque<CircuitStep> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.addLast(new CircuitStep(start, 1));
        visited.add(start);
        while (!pending.isEmpty()) {
            CircuitStep step = pending.removeFirst();
            EngineerCircuitTower current = circuits.get(step.position());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!canLeave(current, direction)) {
                    continue;
                }
                BlockPos adjacent = step.position().relative(direction);
                if (adjacent.equals(signalPosition())) {
                    return step.distance();
                }
                EngineerCircuitTower next = circuits.get(adjacent);
                if (next != null && canEnter(next, direction) && visited.add(adjacent)) {
                    pending.addLast(new CircuitStep(adjacent, step.distance() + 1));
                }
            }
        }
        return -1;
    }

    private static boolean canLeave(EngineerCircuitTower circuit, Direction direction) {
        return circuit != null && EngineerTowers.repeaterDirection(circuit.type())
                .map(direction::equals)
                .orElse(true);
    }

    private static boolean canEnter(EngineerCircuitTower circuit, Direction travelDirection) {
        return EngineerTowers.repeaterDirection(circuit.type())
                .map(travelDirection::equals)
                .orElse(true);
    }

    int activationPlateDistance() {
        return activationPlateDistance;
    }

    private int pressCount() {
        return EngineerPressStates.count(ownerPlayer());
    }

    private static String precisePercent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", ratio * 100.0);
    }

    private EngineerTowers.PlateKind damagePlateKind() {
        return kind == EngineerTowers.TrapKind.TNT && tntPlateKind != null
                ? tntPlateKind
                : activationPlateKind;
    }

    private record CircuitStep(BlockPos position, int distance) {
    }

    private record PlatePath(
            long pressedAt,
            int distance,
            BlockPos position,
            EngineerTowers.PlateKind kind
    ) {
    }

    private record PlateActivation(int distance, EngineerTowers.PlateKind kind) {
    }

    private double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(type().id(), key, fallback);
    }

    private int intAbility(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(type().id(), key, fallback);
    }

    public static double doorRadius(int tier) { return new double[]{4.0, 5.5, 7.0}[tier - 1]; }
    public static double tntDamage(int tier) { return new double[]{120.0, 260.0, 480.0}[tier - 1]; }
    public static double tntRadius(int tier) { return new double[]{2.5, 3.25, 4.0}[tier - 1]; }
    public static int tntMaxTargets(int tier) { return new int[]{8, 12, 16}[tier - 1]; }
    public static double dispenserDamage(int tier) { return new double[]{18.0, 30.0, 45.0}[tier - 1]; }
    public static int dispenserInterval(int tier) { return new int[]{16, 13, 10}[tier - 1]; }
    public static double dispenserRange(int tier) { return new double[]{7.0, 9.0, 11.0}[tier - 1]; }
    public static double pistonRadius(int tier) { return new double[]{2.5, 3.0, 3.5}[tier - 1]; }
    public static int pistonMaxTargets(int tier) { return new int[]{1, 1, 2}[tier - 1]; }
    public static double slimeRadius(int tier) { return new double[]{2.5, 3.0, 3.5}[tier - 1]; }
    public static double slimeSlow(int tier) { return new double[]{0.30, 0.42, 0.55}[tier - 1]; }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }
}
