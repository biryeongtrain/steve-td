package kim.biryeong.semiontd.tower.queen;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

public final class QueenTower extends ProductionTower {
    private transient PlayerLane lane;
    private boolean waveActive;
    private boolean accelerationActive;
    private int rangePulseTicks;
    private transient ArmorStand equipmentVisual;

    public QueenTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
                      GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override public boolean canBeSold() {return false;}
    @Override public boolean supportsForcedAttackTargeting() {return true;}

    @Override
    public Optional<SemionMonsterEntity> selectForcedAttackTarget(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        return candidates.stream().filter(target -> target.runtimeMonster() != null)
                .max(Comparator.comparingDouble(target -> target.runtimeMonster().maxHealth()));
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        this.lane = lane;
        super.onPlaced(lane);
        syncEquipmentVisual();
        showAccelerationRange(lane);
        rangePulseTicks = QueenBalance.rangeVfxIntervalTicks();
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26));
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        entity.setItemSlot(EquipmentSlot.CHEST, chestplate);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        entity.setCustomName(Component.literal("붉은 여왕"));
        entity.setCustomNameVisible(true);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        syncEquipmentVisual();
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        QueenEquipmentVisual.remove(equipmentVisual);
        equipmentVisual = null;
        super.onRemoved(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        this.lane = lane;
        waveActive = true;
        QueenPoker.snapshot(lane, ownerPlayer());
        showAccelerationRange(lane);
        rangePulseTicks = QueenBalance.rangeVfxIntervalTicks();
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        accelerationActive = false;
        QueenStates.state(ownerPlayer()).endRunner();
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        this.lane = lane;
        super.tick(lane);
        syncEquipmentVisual();
        if (waveActive && !isDestroyed(lane) && --rangePulseTicks <= 0) {
            showAccelerationRange(lane);
            rangePulseTicks = QueenBalance.rangeVfxIntervalTicks();
        }
        QueenStates.PlayerState state = QueenStates.state(ownerPlayer());
        if (state.runnerActive()) {
            state.runner().tick(this, lane, state);
        }
        accelerationActive = false;
        if (!waveActive || isDestroyed(lane) || !hasActiveEnemies(state, lane)) return;
        long now = lane.arenaWorld().getGameTime();
        double radiusSqr = QueenBalance.giantAccelerationRadius() * QueenBalance.giantAccelerationRadius();
        accelerationActive = lane.towers().stream().filter(QueenCardTower.class::isInstance)
                .map(QueenCardTower.class::cast).filter(card -> card.ownerPlayer().equals(ownerPlayer()))
                .anyMatch(card -> card.recentlyActive(now) && distanceSqr(card) <= radiusSqr);
        state.addCharge(accelerationActive ? 2.0 : 1.0);
        if (state.ready() && !state.runnerActive() && QueenGiantRunner.dispatch(this, lane, state)) {
            state.consumeCharge();
        }
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        QueenShrink.apply(target, QueenBalance.queenShrinkPoints());
    }

    @Override
    public List<String> runtimeDetailLines() {
        QueenStates.PlayerState state = QueenStates.state(ownerPlayer());
        int required = QueenBalance.giantChargeTicks();
        int current = Math.min(required, (int) Math.floor(state.charge()));
        return List.of(
                "축소 위력: " + oneDecimal(QueenBalance.queenShrinkPoints())
                        + "점 (점당 " + percentInteger(1.0 - QueenBalance.shrinkFactorPerPoint()) + " 감소)",
                "능력치 하한: 원본의 " + percentInteger(QueenBalance.minimumStatScale()),
                "외형 하한: 원본의 " + percentInteger(QueenBalance.minimumVisualScale()),
                "처형선: 현재 체력 " + oneDecimal(state.executionHealth()),
                "저놈의 목을 쳐라!: " + current + "/" + required,
                "남은 충전: " + oneDecimal(Math.max(0.0, required - state.charge()) / 20.0) + "초",
                "가속: " + (accelerationActive ? "활성 (2배)" : "비활성")
        );
    }

    Optional<SemionTowerEntity> entity() {
        if (lane == null || entityId().isEmpty()) return Optional.empty();
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance).map(SemionTowerEntity.class::cast);
    }

    private double distanceSqr(QueenCardTower card) {
        double dx = card.position().x() - position().x();
        double dy = card.position().y() - position().y();
        double dz = card.position().z() - position().z();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean hasActiveEnemies(QueenStates.PlayerState state, PlayerLane ownLane) {
        if (!deployedAtFinalDefense() || state.laneGroup() == null) {
            return ownLane.activeMonsters().stream().anyMatch(monster -> monster.isAlive());
        }
        return state.laneGroup().lanes().stream()
                .flatMap(teamLane -> teamLane.activeMonsters().stream())
                .anyMatch(monster -> monster.isAlive());
    }

    private void showAccelerationRange(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) return;
        entity().ifPresent(source -> TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "giant_charge_range"),
                AreaVfxStyles.BUFF,
                source.position().add(0.0, 0.08, 0.0),
                QueenBalance.giantAccelerationRadius(),
                List.of(), 0, 0, 0
        ));
    }

    private void syncEquipmentVisual() {
        equipmentVisual = QueenEquipmentVisual.sync(equipmentVisual, entity().orElse(null));
    }
}
