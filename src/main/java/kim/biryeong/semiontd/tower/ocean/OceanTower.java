package kim.biryeong.semiontd.tower.ocean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

public final class OceanTower extends EntityBackedTower {
    public static final String CONFIG_ID = "ocean_global";
    private static final double EPSILON = 1.0E-9;

    private double water;
    private boolean waveActive;
    private int dehydrationTicks;
    private PlayerLane currentLane;

    public OceanTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        water = global("initialWater");
    }

    public OceanTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        water = global("initialWater");
    }

    public double water() {
        return water;
    }

    public void addWater(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            water += amount;
        }
    }

    public boolean spendWater(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return true;
        }
        if (water + EPSILON < amount) {
            return false;
        }
        water = Math.max(0.0, water - amount);
        return true;
    }

    public double waterDamageMultiplier() {
        if (water <= 0.0) {
            return global("dehydratedDamageMultiplier");
        }
        return normalWaterMultiplier();
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        dehydrationTicks = 0;
        currentLane = lane;
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        super.tick(lane);
        tickDehydration(lane);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (!waveActive || !OceanTowers.isSupport(type())) {
            return false;
        }
        double cost = value("abilityWaterCost");
        if (water + EPSILON < cost) {
            return false;
        }

        List<SemionTowerEntity> targets = nearbyOceanCombatTowers(lane, value("supportRadius")).stream()
                .filter(target -> target != this && target.type().damage() > 0.0)
                .map(target -> towerEntity(target, lane).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (targets.isEmpty() || !spendWater(cost)) {
            return false;
        }

        int duration = ticks("buffDurationTicks");
        for (SemionTowerEntity target : targets) {
            target.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, value("damageBonus"), duration);
            target.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, value("attackSpeedBonus"), duration);
        }
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return OceanTowers.isSupport(type()) ? Math.max(1, ticks("supportIntervalTicks")) : super.cooldownTicksAfterExecute(lane);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (!OceanTowers.isHunter(type()) || water <= 0.0) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.runtimeMonster() != null)
                .max(Comparator.comparingDouble(candidate -> candidate.runtimeMonster().health()));
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (water <= 0.0) {
            return damageAmount * global("dehydratedDamageMultiplier");
        }
        if (OceanTowers.isHunter(type()) && isIncomeTarget(target) && canPayAttackAndExtra("incomeWaterCost")) {
            return damageAmount * incomeWaterMultiplier();
        }
        return damageAmount * normalWaterMultiplier();
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (water > 0.0 || type().damage() <= 0.0) {
            return baseIntervalTicks;
        }
        double remainingSpeed = Math.max(0.01, 1.0 - global("dehydratedAttackSpeedReduction"));
        return Math.max(1, (int) Math.ceil(baseIntervalTicks / remainingSpeed));
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (!OceanTowers.isTank(type()) || water <= 0.0) {
            return damageAmount;
        }
        return damageAmount * Math.max(0.0, 1.0 - value("damageReduction"));
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (!OceanTowers.isTank(type()) || currentLane == null || water <= 0.0) {
            return;
        }
        double received = Math.max(0.0, previousHealth - currentHealth);
        double pool = Math.min(received, value("transferCap"));
        if (pool <= 0.0) {
            return;
        }
        List<OceanTower> recipients = nearbyOceanCombatTowers(currentLane, value("transferRadius")).stream()
                .filter(target -> target != this)
                .toList();
        if (recipients.isEmpty() || !spendWater(value("transferWaterCost"))) {
            return;
        }
        double share = pool / recipients.size();
        recipients.forEach(target -> target.addWater(share));
        OceanVfx.showWaterTransfer(
                currentLane.arenaWorld(),
                new net.minecraft.world.phys.Vec3(
                        towerEntity.getX(),
                        towerEntity.getY() + towerEntity.getBbHeight() * 0.5,
                        towerEntity.getZ()
                ),
                recipients,
                true
        );
    }

    @Override
    public void onAttack(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount,
            boolean killedTarget
    ) {
        double baseCost = value("attackWaterCost");
        double extraCost = 0.0;
        if (OceanTowers.isSplash(type()) && water + EPSILON >= baseCost + value("splashWaterCost")) {
            extraCost = value("splashWaterCost");
            splash(towerEntity, target, damageAmount);
        } else if (OceanTowers.isHunter(type()) && isIncomeTarget(target)
                && water + EPSILON >= baseCost + value("incomeWaterCost")) {
            extraCost = value("incomeWaterCost");
        }
        drainWater(baseCost + extraCost);
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("물 " + oneDecimal(water));
        if (type().damage() > 0.0) {
            lines.add("물 공격력 " + percent(waterDamageMultiplier() - 1.0));
            lines.add("공격당 물 -" + oneDecimal(value("attackWaterCost")));
        }
        if (OceanTowers.isSupport(type())) {
            lines.add("능력당 물 -" + oneDecimal(value("abilityWaterCost")));
        }
        if (water <= 0.0) {
            lines.add("탈수: 능력 정지, 공격력·공격 속도 감소");
        }
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof OceanTower oceanTower)) {
            return;
        }
        water = oceanTower.water;
        waveActive = oceanTower.waveActive;
        dehydrationTicks = oceanTower.dehydrationTicks;
    }

    private void splash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "ocean_splash"),
                towerEntity,
                target,
                value("splashRadius"),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(this, towerEntity, request, ignored -> damageAmount * value("splashDamageRatio"), true);
    }

    private void tickDehydration(PlayerLane lane) {
        if (!waveActive || water > 0.0 || health() <= 0.0) {
            dehydrationTicks = 0;
            return;
        }
        dehydrationTicks++;
        if (dehydrationTicks < 20) {
            return;
        }
        dehydrationTicks = 0;
        syncHealth(health() - currentMaxHealth() * global("dehydrationMaxHealthDamagePerSecond"));
        towerEntity(this, lane).ifPresent(entity -> entity.setHealth((float) health()));
    }

    private List<OceanTower> nearbyOceanCombatTowers(PlayerLane lane, double radius) {
        if (lane == null || radius <= 0.0) {
            return List.of();
        }
        double radiusSqr = radius * radius;
        return lane.towers().stream()
                .filter(OceanTower.class::isInstance)
                .map(OceanTower.class::cast)
                .filter(target -> target.health() > 0.0 && distanceSqr(target) <= radiusSqr)
                .toList();
    }

    private double distanceSqr(Tower target) {
        double x = target.position().x() - position().x();
        double y = target.position().y() - position().y();
        double z = target.position().z() - position().z();
        return x * x + y * y + z * z;
    }

    private Optional<SemionTowerEntity> towerEntity(OceanTower target, PlayerLane lane) {
        if (target == null || lane == null || target.entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(target.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private void drainWater(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            water = Math.max(0.0, water - amount);
        }
    }

    private boolean canPayAttackAndExtra(String extraCostKey) {
        return water + EPSILON >= value("attackWaterCost") + value(extraCostKey);
    }

    private double normalWaterMultiplier() {
        return 1.0 + value("waterDamageCoefficient") * waterRoot();
    }

    private double incomeWaterMultiplier() {
        return 1.0 + global("incomeCoefficientMultiplier") * value("waterDamageCoefficient") * waterRoot();
    }

    private double waterRoot() {
        return Math.sqrt(Math.max(0.0, water) / Math.max(EPSILON, global("waterScale")));
    }

    private boolean isIncomeTarget(SemionMonsterEntity target) {
        Monster monster = target == null ? null : target.runtimeMonster();
        return monster != null && monster.senderTeam().isPresent();
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private double global(String key) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key);
    }
}
