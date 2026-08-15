package kim.biryeong.semiontd.tower.insect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.damagesource.DamageSource;

public final class InsectUnitTower extends ProductionTower {
    private int deathsThisRound;
    private int reviveTicksRemaining = -1;
    private GridPosition revivePosition;
    private List<GridPosition> reviveSpawnerKeys = List.of();
    private boolean freshPowerActive;
    private boolean waveActive;
    private boolean permanentDeath;

    public InsectUnitTower(
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
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        permanentDeath = false;
        freshPowerActive = InsectTowers.tier(type()) == 1 && placedRound() == currentRound;
        syncMaxHealth(type().maxHealth() * (freshPowerActive ? InsectBalance.freshPowerMultiplier() : 1.0), true);
        syncHealth(currentMaxHealth());
        onStateChanged(lane);
    }

    @Override
    public EntityVisual visual() {
        EntityVisual visual = super.visual();
        return freshPowerActive ? visual.withScale(visual.scale() * InsectBalance.freshPowerScale()) : visual;
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        return waveStartedAfterPlacement();
    }

    @Override
    public double modifyAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        return freshPowerActive ? damageAmount * InsectBalance.freshPowerMultiplier() : damageAmount;
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        double reduction = InsectTowers.line(type()) == InsectTowers.UnitLine.SPIDER
                ? InsectBalance.spiderDamageReduction(InsectTowers.tier(type()))
                : 0.0;
        return damageAmount * (1.0 - reduction)
                * (1.0 + deathsThisRound * InsectBalance.deathDamageTakenPerStack());
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        if (permanentDeath) {
            return true;
        }
        if (reviveTicksRemaining >= 0) {
            if (livingLinkedSpawners(lane).isEmpty()) {
                cancelRevival();
                permanentDeath = true;
                return true;
            }
            return false;
        }
        if (!super.isDestroyed(lane)) {
            return false;
        }
        GridPosition deathPosition = position();
        List<Tower> spawners = livingSpawnersNear(lane, deathPosition);
        if (!waveActive || spawners.isEmpty()) {
            permanentDeath = true;
            return true;
        }
        deathsThisRound++;
        revivePosition = deathPosition;
        reviveSpawnerKeys = spawners.stream().map(Tower::originalPosition).distinct().toList();
        reviveTicksRemaining = InsectBalance.reviveBaseTicks()
                + (deathsThisRound - 1) * InsectBalance.reviveIncrementTicks();
        return false;
    }

    @Override
    public void tick(PlayerLane lane) {
        if (reviveTicksRemaining >= 0) {
            if (livingLinkedSpawners(lane).isEmpty()) {
                cancelRevival();
                permanentDeath = true;
                return;
            }
            if (reviveTicksRemaining > 0) {
                reviveTicksRemaining--;
                if (reviveTicksRemaining > 0) {
                    return;
                }
            }
            revive(lane);
            return;
        }
        super.tick(lane);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        super.moveToFinalDefense(lane, position);
        if (reviveTicksRemaining >= 0) {
            revivePosition = position;
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        freshPowerActive = false;
        deathsThisRound = 0;
        permanentDeath = false;
        cancelRevival();
        syncMaxHealth(type().maxHealth(), false);
        super.resetForRound(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof InsectUnitTower previous)) {
            return;
        }
        deathsThisRound = previous.deathsThisRound;
        reviveTicksRemaining = previous.reviveTicksRemaining;
        revivePosition = previous.revivePosition;
        reviveSpawnerKeys = List.copyOf(previous.reviveSpawnerKeys);
        freshPowerActive = previous.freshPowerActive;
        waveActive = previous.waveActive;
        permanentDeath = previous.permanentDeath;
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        String freshStatus = freshPowerActive
                ? "<green>활성</green>"
                : freshPowerPending() ? "<yellow>첫 웨이브 대기</yellow>" : "<gray>종료</gray>";
        lines.add("<gold>첫 배치 강화</gold> " + freshStatus);
        lines.add("<red>이번 라운드 사망</red> <white>" + deathsThisRound + "회</white>");
        lines.add("<red>받는 피해 증가</red> <white>"
                + percent(deathsThisRound * InsectBalance.deathDamageTakenPerStack()) + "</white>");
        if (reviveTicksRemaining >= 0) {
            lines.add("<green>부활 대기</green> <white>" + oneDecimal(reviveTicksRemaining / 20.0) + "초</white>");
        }
        int livingSpawners = reviveTicksRemaining >= 0
                ? livingLinkedSpawners(lastLaneForDetails).size()
                : livingSpawnersNear(lastLaneForDetails, position()).size();
        lines.add("<light_purple>스포너 연결</light_purple> "
                + (livingSpawners > 0
                ? "<green>" + livingSpawners + "기 연결</green>" : "<red>없음</red>"));
        return List.copyOf(lines);
    }

    private transient PlayerLane lastLaneForDetails;

    @Override
    public void onPlaced(PlayerLane lane) {
        lastLaneForDetails = lane;
        if (!waveActive && InsectTowers.tier(type()) == 1 && !waveStartedAfterPlacement()) {
            freshPowerActive = true;
            syncMaxHealth(type().maxHealth() * InsectBalance.freshPowerMultiplier(), true);
            syncHealth(currentMaxHealth());
        }
        super.onPlaced(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        if (lane != null) {
            lastLaneForDetails = lane;
        }
    }

    int deathsThisRound() {
        return deathsThisRound;
    }

    int reviveTicksRemaining() {
        return reviveTicksRemaining;
    }

    boolean freshPowerActive() {
        return freshPowerActive;
    }

    boolean freshPowerPending() {
        return !freshPowerActive && InsectTowers.tier(type()) == 1 && !waveStartedAfterPlacement();
    }

    public boolean showDebugRevivalVfx(PlayerLane lane) {
        List<Tower> spawners = livingSpawnersNear(lane, position());
        return !spawners.isEmpty() && showRevivalVfx(lane, spawners.getFirst());
    }

    private void revive(PlayerLane lane) {
        GridPosition destination = revivePosition;
        Tower linkedSpawner = livingLinkedSpawners(lane).stream().findFirst().orElse(null);
        onRemoved(lane);
        syncPosition(destination);
        syncHealth(currentMaxHealth());
        reviveTicksRemaining = -1;
        revivePosition = null;
        onPlaced(lane);
        if (linkedSpawner != null) {
            showRevivalVfx(lane, linkedSpawner);
        }
        reviveSpawnerKeys = List.of();
    }

    private void cancelRevival() {
        reviveTicksRemaining = -1;
        revivePosition = null;
        reviveSpawnerKeys = List.of();
    }

    private List<Tower> livingSpawnersNear(PlayerLane lane, GridPosition center) {
        if (lane == null || center == null) {
            return List.of();
        }
        double radiusSquared = InsectBalance.spawnerRadius() * InsectBalance.spawnerRadius();
        return lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> InsectTowers.isSpawner(tower.type()))
                .filter(tower -> !tower.isDestroyed(lane))
                .filter(tower -> distanceSquared(center, tower.position()) <= radiusSquared)
                .toList();
    }

    private List<Tower> livingLinkedSpawners(PlayerLane lane) {
        if (lane == null || reviveSpawnerKeys.isEmpty()) {
            return List.of();
        }
        return lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> InsectTowers.isSpawner(tower.type()))
                .filter(tower -> reviveSpawnerKeys.contains(tower.originalPosition()))
                .filter(tower -> !tower.isDestroyed(lane))
                .toList();
    }

    private boolean showRevivalVfx(PlayerLane lane, Tower spawner) {
        SemionTowerEntity spawnerEntity = towerEntity(lane, spawner);
        SemionTowerEntity revivedEntity = towerEntity(lane, this);
        if (spawnerEntity == null || revivedEntity == null || !spawnerEntity.isAlive() || !revivedEntity.isAlive()) {
            return false;
        }
        TowerVfxService.showSecondaryAttack(spawnerEntity, revivedEntity.position());
        TowerVfxService.showAreaEffect(
                revivedEntity,
                AreaEffectIds.tower(this, "revive"),
                AreaVfxStyles.BUFF,
                revivedEntity.position().add(0.0, 0.08, 0.0),
                1.5,
                List.of(revivedEntity.position()),
                1,
                1,
                0
        );
        return true;
    }

    private static SemionTowerEntity towerEntity(PlayerLane lane, Tower tower) {
        if (lane == null || lane.arenaWorld() == null || !(tower instanceof EntityBackedTower backed)
                || backed.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity
                ? entity
                : null;
    }

    private static double distanceSquared(GridPosition first, GridPosition second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return x * x + y * y + z * z;
    }
}
