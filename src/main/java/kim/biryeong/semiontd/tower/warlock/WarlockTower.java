package kim.biryeong.semiontd.tower.warlock;

import java.util.Comparator;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;

public class WarlockTower extends EntityBackedTower {
    private double permanentHealthBonus;
    private double permanentDamageBonus;
    private double roundHealthBonus;
    private double roundDamageBonus;
    private double roundIntervalReduction;
    private int totalSacrificeCount;
    private int roundSacrificeCount;
    private PlayerLane currentLane;

    public WarlockTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public WarlockTower(
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
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
        refreshWarlockCoreStats(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        if (currentLane == lane) {
            currentLane = null;
        }
    }

    @Override
    public double currentMaxHealth() {
        return maxHealth() * (1.0 + passiveHealthBonus()) + permanentHealthBonus + roundHealthBonus;
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return (damageAmount + permanentDamageBonus + roundDamageBonus) * (1.0 + passiveDamageBonus());
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (damageAmount <= 0.0) {
            return damageAmount;
        }
        return damageAmount * (1.0 - damageReduction());
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (is(WarlockTowers.BASE_WARLOCK_TOWER) && currentHealth <= 0.0) {
            if (sacrifice(towerEntity, sacrificeRadius("baseSacrificeRadius"), Comparator.comparingInt(Tower::aggroPriority))) {
                heal(towerEntity, ability("baseFatalHealRatio") * currentMaxHealth());
            }
            return;
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER) && healthRatio(currentHealth) < ability("lowHealthSacrificeThreshold")) {
            sacrifice(
                    towerEntity,
                    sacrificeRadius("sacrificeRadius"),
                    Comparator.comparingInt(Tower::aggroPriority).reversed()
            );
        }
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER) && healthRatio(health()) < ability("lowHealthSacrificeThreshold")) {
            sacrifice(towerEntity, sacrificeRadius("sacrificeRadius"), Comparator.comparingInt(Tower::aggroPriority));
        }
        splash(towerEntity, target, damageAmount);
        heal(towerEntity, damageAmount * lifeStealRatio());
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (!is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return baseIntervalTicks;
        }
        return Math.max(1, (int) Math.ceil(baseIntervalTicks - roundIntervalReduction));
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        refreshWarlockCoreStats(lane);
        super.tick(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        currentLane = lane;
        roundHealthBonus = 0.0;
        roundDamageBonus = 0.0;
        roundIntervalReduction = 0.0;
        roundSacrificeCount = 0;
        super.resetForRound(lane);
        refreshWarlockCoreStats(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof WarlockTower warlockTower) {
            permanentHealthBonus = warlockTower.permanentHealthBonus;
            permanentDamageBonus = warlockTower.permanentDamageBonus;
            roundHealthBonus = warlockTower.roundHealthBonus;
            roundDamageBonus = warlockTower.roundDamageBonus;
            roundIntervalReduction = warlockTower.roundIntervalReduction;
            totalSacrificeCount = warlockTower.totalSacrificeCount;
            roundSacrificeCount = warlockTower.roundSacrificeCount;
        }
    }

    private boolean sacrifice(
            SemionTowerEntity towerEntity,
            double radius,
            Comparator<Tower> priority
    ) {
        if (towerEntity == null || priority == null) {
            return false;
        }
        PlayerLane lane = lane(towerEntity);
        if (lane == null) {
            return false;
        }
        Tower target = lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> !WarlockTowers.isWarlockCore(tower.type()))
                .filter(tower -> sameOwner(tower) && withinRadius(tower, radius))
                .sorted(priority)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return false;
        }

        double sacrificedHealth = target.currentMaxHealth();
        double sacrificedDamage = target.modifyAttackDamage(null, null, target.type().damage());
        int sacrificedInterval = target.type().attackIntervalTicks();
        if (!lane.killTower(target)) {
            return false;
        }

        applySacrificeStats(lane, towerEntity, sacrificedHealth, sacrificedDamage, sacrificedInterval);
        return true;
    }

    private void applySacrificeStats(
            PlayerLane lane,
            SemionTowerEntity towerEntity,
            double sacrificedHealth,
            double sacrificedDamage,
            int sacrificedIntervalTicks
    ) {
        double gainedHealth = 0.0;
        double immediateHeal = sacrificedHealth * ability("sacrificeHealRatio");
        if (is(WarlockTowers.BASE_WARLOCK_TOWER)) {
            gainedHealth += sacrificedHealth * ability("basePermanentHealthRatio");
            permanentHealthBonus += gainedHealth;
            permanentDamageBonus += sacrificedDamage * ability("basePermanentDamageRatio");
        } else if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            gainedHealth += absorbForRound(sacrificedHealth, sacrificedDamage);
            absorbPermanently(sacrificedHealth, sacrificedDamage);
            absorbAttackInterval(sacrificedIntervalTicks);
        } else if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            gainedHealth += absorbForRound(sacrificedHealth, sacrificedDamage);
            absorbPermanently(sacrificedHealth, sacrificedDamage);
        }
        onStateChanged(lane);
        heal(towerEntity, gainedHealth + immediateHeal);
        onStateChanged(lane);
    }

    private double absorbForRound(double sacrificedHealth, double sacrificedDamage) {
        totalSacrificeCount++;
        roundSacrificeCount++;
        double gainedHealth = sacrificedHealth * ability("roundStatRatio");
        roundHealthBonus += gainedHealth;
        roundDamageBonus += sacrificedDamage * ability("roundStatRatio");
        return gainedHealth;
    }

    private void absorbPermanently(double sacrificedHealth, double sacrificedDamage) {
        permanentHealthBonus += sacrificedHealth * ability("permanentHealthRatio");
        permanentDamageBonus += sacrificedDamage * ability("permanentDamageRatio");
    }

    private void absorbAttackInterval(int sacrificedIntervalTicks) {
        int baseInterval = type().attackIntervalTicks();
        if (sacrificedIntervalTicks >= baseInterval) {
            return;
        }
        double cap = ability("roundIntervalReductionCap");
        roundIntervalReduction = Math.min(cap, roundIntervalReduction + baseInterval - sacrificedIntervalTicks);
    }

    private void heal(SemionTowerEntity towerEntity, double amount) {
        if (towerEntity == null || amount <= 0.0) {
            return;
        }
        double nextHealth = Math.min(currentMaxHealth(), health() + amount);
        syncHealth(nextHealth);
        towerEntity.setHealth((float) nextHealth);
    }

    private double passiveHealthBonus() {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return passiveBonus("healthBonusPerStack", "maxHealthBonus");
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return passiveBonus("healthBonusPerStack", "maxHealthBonus");
        }
        return 0.0;
    }

    private double passiveDamageBonus() {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return passiveBonus("damageBonusPerStack", "maxDamageBonus");
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return passiveBonus("damageBonusPerStack", "maxDamageBonus");
        }
        return 0.0;
    }

    private double lifeStealRatio() {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            int every = abilityInt("lifeStealEvery");
            if (every <= 0) {
                return 0.0;
            }
            return Math.min(ability("lifeStealCap"), (totalSacrificeCount / every) * ability("lifeStealPerStep"));
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return Math.min(ability("lifeStealCap"), totalSacrificeCount * ability("lifeStealPerSacrifice"));
        }
        return 0.0;
    }

    private double damageReduction() {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)
                && roundSacrificeCount > abilityInt("roundAbsorbDefenseThreshold")) {
            return ability("roundDamageReduction");
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            int every = abilityInt("damageReductionEvery");
            if (every <= 0) {
                return 0.0;
            }
            return Math.min(ability("damageReductionCap"), (totalSacrificeCount / every) * ability("damageReductionPerStep"));
        }
        return 0.0;
    }

    private void splash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double radius = splashRadius();
        double ratio = ability("splashDamageRatio");
        if (towerEntity == null || target == null || radius <= 0.0 || ratio <= 0.0) {
            return;
        }
        double radiusSqr = radius * radius;
        AABB splashBox = target.getBoundingBox().inflate(radius);
        towerEntity.level().getEntities(towerEntity, splashBox, entity ->
                        entity instanceof SemionMonsterEntity splashTarget
                                && splashTarget.isAlive()
                                && splashTarget != target
                                && splashTarget.runtimeMonster() != null
                                && towerEntity.defendsLane(splashTarget.runtimeMonster().targetLaneId())
                                && splashTarget.distanceToSqr(target) <= radiusSqr
                )
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> {
                    double splashDamage = damageAmount * ratio;
                    if (damageTarget(towerEntity, monster, splashDamage)) {
                        onKill(towerEntity, monster, splashDamage);
                    }
                });
    }

    private double splashRadius() {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            int every = abilityInt("splashEvery");
            if (every <= 0) {
                return 0.0;
            }
            return (totalSacrificeCount / every) * ability("splashRadiusPerStep");
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return roundSacrificeCount * ability("roundSplashRadiusPerSacrifice");
        }
        return 0.0;
    }

    private double passiveBonus(String perStackKey, String capKey) {
        return Math.min(ability(capKey), passiveStackCount() * ability(perStackKey));
    }

    private int passiveStackCount() {
        PlayerLane lane = currentLane;
        if (lane == null) {
            return 0;
        }
        return (int) lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> tower.health() > 0.0)
                .filter(this::sameOwner)
                .filter(this::isPassiveStackTower)
                .count();
    }

    private boolean isPassiveStackTower(Tower tower) {
        if (tower == null) {
            return false;
        }
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return WarlockTowers.isRangedSlave(tower.type());
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return WarlockTowers.isMeleeSlave(tower.type());
        }
        return false;
    }

    static void refreshWarlockCoreStats(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof WarlockTower warlockTower) {
                warlockTower.syncHealth(warlockTower.health());
                warlockTower.onStateChanged(lane);
            }
        }
    }

    private boolean sameOwner(Tower tower) {
        return tower != null && ownerPlayer().equals(tower.ownerPlayer());
    }

    private boolean withinRadius(Tower tower, double radius) {
        if (tower == null) {
            return false;
        }
        if (radius <= 0.0) {
            return true;
        }
        double dx = tower.position().x() - position().x();
        double dy = tower.position().y() - position().y();
        double dz = tower.position().z() - position().z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private PlayerLane lane(SemionTowerEntity towerEntity) {
        if (towerEntity == null || towerEntity.runtimeTower() != this) {
            return null;
        }
        return currentLane;
    }

    private boolean is(TowerType towerType) {
        return type().id().equals(towerType.id());
    }

    private double sacrificeRadius(String key) {
        double radius = ability(key);
        return radius <= 0.0 ? Double.MAX_VALUE : radius;
    }

    private double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int abilityInt(String key) {
        return TowerBalanceRuntime.abilityInt(type().id(), key);
    }

    private double healthRatio(double currentHealth) {
        double maxHealth = currentMaxHealth();
        return maxHealth <= 0.0 ? 0.0 : currentHealth / maxHealth;
    }

}
