package kim.biryeong.semiontd.tower.insect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.DamageType;
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
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

public final class InsectUnitTower extends ProductionTower {
    private int deathsThisRound;
    private int revivalsThisRound;
    private int reviveTicksRemaining = -1;
    private GridPosition revivePosition;
    private List<GridPosition> reviveSpawnerKeys = List.of();
    private boolean freshPowerActive;
    private boolean waveActive;
    private boolean permanentDeath;
    private boolean larva;
    private boolean shellReady;

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
        if (!larva) InsectAugments.beginWave(ownerPlayer(), currentRound);
        waveActive = true;
        permanentDeath = false;
        freshPowerActive = !larva && InsectTowers.tier(type()) == 1 && placedRound() == currentRound;
        shellReady = false;
        syncMaxHealth(effectBaseMaxHealth(), true);
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
        return !larva && waveStartedAfterPlacement();
    }

    void markLarva(InsectUnitTower source) {
        larva = true;
        waveActive = true;
        markTemporaryCopy(source.logicalId());
    }

    boolean isLarva() {return larva;}

    @Override
    protected double builderCurrentMaxHealth() {
        return Math.max(1.0, super.builderCurrentMaxHealth() * healthRetention()
                * (freshPowerActive ? InsectBalance.freshPowerMultiplier() : 1.0));
    }

    public boolean usesContactDetonation() {
        return InsectTowers.line(type()) == InsectTowers.UnitLine.BEE;
    }

    public void detonateOnContact(SemionTowerEntity source, SemionMonsterEntity target) {
        double range = InsectBalance.contactDetonationRange();
        if (!usesContactDetonation() || !waveActive || !source.isAlive()
                || !source.isValidAttackTarget(target) || source.distanceToSqr(target) > range * range) {
            return;
        }
        // A voluntary death must not be reduced or blocked by defensive effects.
        source.setHealth(0.0F);
        source.die(source.damageSources().generic());
        isDestroyed(lastLaneForDetails);
    }

    private double healthRetention() {
        return Math.pow(1.0 - InsectBalance.reviveHealthLossRatio(), revivalsThisRound);
    }

    private double incomingDamageMultiplier() {
        return (freshPowerActive ? InsectBalance.freshDamageTakenMultiplier() : 1.0)
                * (1.0 + revivalsThisRound * InsectBalance.deathDamageTakenPerStack());
    }

    double deathExplosionDamage() {
        return currentMaxHealth() * InsectBalance.deathExplosionHealthRatio(type());
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        if (blockFirstRevivedHit(damageAmount)) return 0;
        double reduction = InsectTowers.line(type()) == InsectTowers.UnitLine.SPIDER
                ? InsectBalance.spiderDamageReduction(InsectTowers.tier(type()))
                : 0.0;
        return damageAmount * (1.0 - reduction)
                * incomingDamageMultiplier();
    }

    @Override
    public double modifyIncomingDamageIgnoringReductions(
            SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount
    ) {
        if (blockFirstRevivedHit(damageAmount)) return 0;
        return damageAmount * incomingDamageMultiplier();
    }

    private boolean blockFirstRevivedHit(double amount) {
        if (amount <= 0 || !shellReady || !AugmentCombat.allowsTriggers()) return false;
        shellReady = false;
        return true;
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
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        double explosionDamage = deathExplosionDamage();
        List<Tower> spawners = livingSpawnersNear(lane, deathPosition);
        if (!waveActive || larva || spawners.isEmpty()) {
            permanentDeath = true;
        } else {
            revivePosition = deathPosition;
            reviveSpawnerKeys = spawners.stream().map(Tower::originalPosition).distinct().toList();
            reviveTicksRemaining = (int) Math.min(Integer.MAX_VALUE,
                    (long) InsectBalance.reviveBaseTicks(type())
                            + (long) revivalsThisRound * InsectBalance.reviveIncrementTicks(type()));
            if (fastHatch(revivalsThisRound)) {
                reviveTicksRemaining = Math.max(1, (int) Math.ceil(reviveTicksRemaining
                        * (1 - augmentSnapshot().parameter(InsectAugments.HATCH, "waitReduction", .5))));
            }
        }
        if (waveActive) {
            deathsThisRound++;
            if (deathsThisRound == 1) InsectAugments.reserveLarvae(this, lane);
            // Commit the dead/waiting state before damage callbacks can query isDestroyed again.
            if (source != null) {
                MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                        AreaEffectIds.tower(this, "death_explosion"), source,
                        InsectBalance.deathExplosionRadius(type()), AreaVfxSpec.onTrigger(AreaVfxStyles.INSECT_EXPLOSION))
                        .withFilter(source::isValidAttackTarget);
                TowerAreaDamage.apply(this, source, request, target -> explosionDamage, true,
                        (target, damage, killed) -> {}, DamageType.MAGIC);
            }
        }
        return permanentDeath;
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
        revivalsThisRound = 0;
        permanentDeath = false;
        shellReady = false;
        cancelRevival();
        syncMaxHealth(effectBaseMaxHealth(), false);
        super.resetForRound(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof InsectUnitTower previous)) {
            return;
        }
        deathsThisRound = previous.deathsThisRound;
        revivalsThisRound = previous.revivalsThisRound;
        reviveTicksRemaining = previous.reviveTicksRemaining;
        revivePosition = previous.revivePosition;
        reviveSpawnerKeys = List.copyOf(previous.reviveSpawnerKeys);
        freshPowerActive = previous.freshPowerActive;
        waveActive = previous.waveActive;
        permanentDeath = previous.permanentDeath;
        larva = previous.larva;
        shellReady = previous.shellReady;
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        String freshStatus = freshPowerActive
                ? "<green>활성</green>"
                : freshPowerPending() ? "<yellow>첫 웨이브 대기</yellow>" : "<gray>종료</gray>";
        lines.add("<gold>첫 배치 강화</gold> " + freshStatus);
        lines.add("<red>이번 라운드 사망</red> <white>" + deathsThisRound + "회</white>");
        if (shellReady) lines.add("<aqua>알껍질 방패</aqua> <white>다음 피해 1회 차단</white>");
        if (augmentSnapshot().has(InsectAugments.MARCH)) {
            lines.add("<green>유충 생성 예약</green> <white>" + InsectAugments.reserved(ownerPlayer())
                    + "/" + (int) augmentSnapshot().parameter(InsectAugments.MARCH, "roundCap", 6) + "</white>");
        }
        lines.add("<light_purple>폭발 기본 마법 피해</light_purple> <white>" + oneDecimal(deathExplosionDamage())
                + "</white> · 반경 " + oneDecimal(InsectBalance.deathExplosionRadius(type())) + "칸");
        lines.add("<green>부활 후 체력 유지율</green> <white>" + percent(healthRetention()) + "</white>");
        lines.add("<red>현재 받는 피해</red> <white>"
                + oneDecimal(modifyIncomingDamage(null, null, 1.0)) + "배</white>");
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
        if (!larva && !waveActive && InsectTowers.tier(type()) == 1 && !waveStartedAfterPlacement()) {
            freshPowerActive = true;
            syncMaxHealth(effectBaseMaxHealth(), true);
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

    public boolean showDebugExplosionVfx(PlayerLane lane) {
        SemionTowerEntity source = towerEntity(lane, this);
        if (source == null || !source.isAlive()) return false;
        TowerVfxService.showAreaEffect(source, AreaEffectIds.tower(this, "death_explosion"),
                AreaVfxStyles.INSECT_EXPLOSION, source.position(), InsectBalance.deathExplosionRadius(type()),
                List.of(), 0, 0, 0);
        return true;
    }

    private void revive(PlayerLane lane) {
        GridPosition destination = revivePosition;
        Tower linkedSpawner = livingLinkedSpawners(lane).stream().findFirst().orElse(null);
        onRemoved(lane);
        syncPosition(destination);
        revivalsThisRound++;
        shellReady = !larva && AugmentCombat.allowsTriggers() && augmentSnapshot().has(InsectAugments.SHELL);
        // The new entity has no effects from the old corpse; auras refresh through the normal path.
        syncEffectMaxHealth(effectBaseMaxHealth(), 0.0, false);
        syncHealth(currentMaxHealth());
        reviveTicksRemaining = -1;
        revivePosition = null;
        onPlaced(lane);
        if (linkedSpawner != null) {
            showRevivalVfx(lane, linkedSpawner);
        }
        reviveSpawnerKeys = List.of();
        if (fastHatch(revivalsThisRound - 1) && AugmentCombat.allowsTriggers()) {
            double radius = augmentSnapshot().parameter(InsectAugments.HATCH, "radius", 6);
            int reduction = (int) augmentSnapshot().parameter(InsectAugments.HATCH, "neighborReductionTicks", 40);
            lane.towers().stream().filter(InsectUnitTower.class::isInstance).map(InsectUnitTower.class::cast)
                    .filter(other -> other != this && !other.larva && ownerPlayer().equals(other.ownerPlayer()))
                    .filter(other -> other.reviveTicksRemaining > 0)
                    .filter(other -> distanceSquared(position(), other.revivePosition) <= radius * radius)
                    .sorted(Comparator.comparingDouble(other -> distanceSquared(position(), other.revivePosition)))
                    .limit((int) augmentSnapshot().parameter(InsectAugments.HATCH, "maxNeighbors", 2))
                    .forEach(other -> other.reviveTicksRemaining = Math.max(0, other.reviveTicksRemaining - reduction));
        }
    }

    private boolean fastHatch(int completedRevivals) {
        return !larva && AugmentCombat.allowsTriggers() && augmentSnapshot().has(InsectAugments.HATCH)
                && completedRevivals < (int) augmentSnapshot().parameter(InsectAugments.HATCH, "revivalCount", 3);
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
                .filter(tower -> isLivingAnchor(lane, tower))
                .filter(tower -> distanceSquared(center, tower.position()) <= radiusSquared)
                .toList();
    }

    private List<Tower> livingLinkedSpawners(PlayerLane lane) {
        if (lane == null || revivePosition == null) {
            return List.of();
        }
        return lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> isLivingAnchor(lane, tower))
                .filter(tower -> InsectTowers.isSpawner(tower.type())
                        ? reviveSpawnerKeys.contains(tower.originalPosition())
                        : distanceSquared(revivePosition, tower.position()) <= InsectBalance.spawnerRadius() * InsectBalance.spawnerRadius())
                .toList();
    }

    private boolean isLivingAnchor(PlayerLane lane, Tower tower) {
        if (InsectTowers.isSpawner(tower.type())) return !tower.isDestroyed(lane);
        return !larva && tower != this && augmentSnapshot().has(InsectAugments.COLONY)
                && tower instanceof InsectUnitTower unit && !unit.larva
                && unit.health() > 0 && unit.reviveTicksRemaining < 0 && !unit.permanentDeath
                && unit.runtimeEntity(lane).map(SemionTowerEntity::isAlive).orElse(lane.arenaWorld() == null);
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
