package kim.biryeong.semiontd.tower.nether;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;

public class NetherTower extends EntityBackedTower {
    public static final String CONFIG_ID = "nether_global";
    private static final TowerDataKey<NetherTowerState> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether_tower_state"),
            NetherTowerState.class
    );
    private static final ResourceLocation LOW_HEALTH_DAMAGE_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/low_health_damage");
    private static final ResourceLocation LOW_HEALTH_DAMAGE_REDUCTION_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/low_health_damage_reduction");
    private static final ResourceLocation LOW_HEALTH_ATTACK_SPEED_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/low_health_attack_speed");
    private static final ResourceLocation ZOMBIE_ATTACK_SPEED_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/zombie_attack_speed");
    private static final ResourceLocation PIGLIN_KILL_DAMAGE_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/piglin_kill_damage");
    private static final ResourceLocation ZOMBIE_TRANSITION_DAMAGE_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether/zombie_transition_damage");

    private int attackCounter;
    private int decayReductionTicks;
    private int pulseCooldownTicks;
    private int markCounter;
    private boolean lastAttackWasCritical;

    public NetherTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        setData(STATE, NetherTowerState.NETHER);
    }

    public NetherTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        setData(STATE, NetherTowerState.NETHER);
    }

    public NetherTowerState state() {
        return getDataOrDefault(STATE, NetherTowerState.NETHER);
    }

    @Override
    public void tick(PlayerLane lane) {
        SemionTowerEntity entity = towerEntity(lane).orElse(null);
        if (entity != null) {
            refreshDynamicTimedEffects(entity);
        }
        if (decayReductionTicks > 0) {
            decayReductionTicks--;
        }
        if (pulseCooldownTicks > 0) {
            pulseCooldownTicks--;
        }
        if (entity != null && shouldDecay(lane)) {
            applyDecay(lane, entity);
        }
        super.tick(lane);
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        if (entityWasUnloaded()) {
            return super.isDestroyed(lane);
        }
        if (state() != NetherTowerState.NETHER || entityId().isEmpty()) {
            return super.isDestroyed(lane);
        }
        Optional<SemionTowerEntity> entity = towerEntity(lane);
        if (entity.isPresent() && entity.get().isAlive() && !entity.get().isRemoved()) {
            return super.isDestroyed(lane);
        }
        reviveAsZombie(lane, entity.orElse(null));
        return false;
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (state() == NetherTowerState.NETHER && currentHealth <= 0.0) {
            reviveAsZombie(null, towerEntity);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        setData(STATE, NetherTowerState.NETHER);
        attackCounter = 0;
        decayReductionTicks = 0;
        pulseCooldownTicks = 0;
        markCounter = 0;
        lastAttackWasCritical = false;
        super.resetForRound(lane);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        Monster monster = target == null ? null : target.runtimeMonster();
        double adjusted = damageAmount;
        if (NetherTowers.isStriderLine(type()) && monster != null && monster.senderTeam().isPresent()) {
            adjusted *= 1.0 + value("incomeDamageBonus");
        }
        if (is(NetherTowers.T3_PIGLIN_BRUTE) && isCritical() && isTankOrHighHealthTarget(monster)) {
            adjusted *= 1.0 + value("tankDamageBonus");
        }
        if (NetherTowers.isSkeletonLine(type()) && healthRatio(target) <= value("lowTargetHealthThreshold")) {
            adjusted *= 1.0 + value("lowTargetDamageBonus");
        }
        if (is(NetherTowers.T3_WITHER) && monster != null && monster.maxHealth() >= value("highHealthThreshold")) {
            adjusted *= 1.0 + value("highHealthDamageBonus");
        }
        if (is(NetherTowers.T3_WITHER)
                && state() == NetherTowerState.ZOMBIE
                && healthRatio(target) <= value("zombieExecuteThreshold")) {
            adjusted *= 1.0 + value("zombieExecuteDamageBonus");
        }
        return adjusted;
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (!NetherTowers.isSkeletonLine(type())) {
            return Optional.empty();
        }
        if (is(NetherTowers.T3_WITHER)) {
            Optional<SemionMonsterEntity> highHealthTarget = candidates.stream()
                    .filter(target -> {
                        Monster monster = target.runtimeMonster();
                        return monster != null && monster.maxHealth() >= value("highHealthThreshold");
                    })
                    .max(Comparator.comparingDouble(target -> {
                Monster monster = target.runtimeMonster();
                return monster == null ? target.getMaxHealth() : monster.maxHealth();
                    }));
            if (highHealthTarget.isPresent()) {
                return highHealthTarget;
            }
        }
        return candidates.stream().min(Comparator.comparingDouble(this::healthRatio));
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        lastAttackWasCritical = isCritical();
        heal(towerEntity, damageAmount * lifeStealRatio(target));
        attackCounter++;

        if (NetherTowers.isStriderLine(type()) && lastAttackWasCritical) {
            decayReductionTicks = Math.max(decayReductionTicks, ticks("decayReductionTicks"));
        }
        if (NetherTowers.isHoglinLine(type()) || NetherTowers.isBlazeLine(type())) {
            splashAroundTarget(towerEntity, target, damageAmount, splashRadius(), value("splashDamageRatio"));
        }
        if (NetherTowers.isBlazeLine(type()) && lastAttackWasCritical && pulseCooldownTicks <= 0) {
            damageAroundTarget(towerEntity, target, value("pulseRadius"), scaledPulseDamage("pulseDamageRatio"));
            pulseCooldownTicks = ticks("pulseIntervalTicks");
        }
        int extraAttackEvery = TowerBalanceRuntime.abilityInt(type().id(), "extraAttackEvery", 0);
        if (NetherTowers.isBlazeLine(type())
                && lastAttackWasCritical
                && extraAttackEvery > 0
                && attackCounter % extraAttackEvery == 0) {
            extraAttack(towerEntity, target, damageAmount * value("extraAttackDamageRatio"));
        }
        if (is(NetherTowers.T3_GHAST) && lastAttackWasCritical) {
            applyMonsterDamageTakenMark(target, sourceId("ghast_mark"), value("criticalMarkDamageTakenBonus"), ticks("markDurationTicks"));
        }
        if (is(NetherTowers.T2_WITHER_SKELETON) || is(NetherTowers.T3_WITHER)) {
            int maxStacks = Math.max(1, TowerBalanceRuntime.abilityInt(type().id(), "maxMarkStacks", 1));
            double bonus = value("markDamageTakenBonus") + (state() == NetherTowerState.ZOMBIE ? value("zombieMarkDamageTakenBonus") : 0.0);
            applyMonsterDamageTakenMark(target, sourceId("wither_skeleton_mark_" + (markCounter++ % maxStacks)), bonus, ticks("markDurationTicks"));
        }
        if (is(NetherTowers.T3_WITHER) && lastAttackWasCritical) {
            splashAroundTarget(towerEntity, target, damageAmount, value("criticalSplashRadius"), value("criticalSplashDamageRatio"));
            applyMonsterDamageTakenMark(target, sourceId("wither_mark"), value("criticalMarkDamageTakenBonus"), ticks("markDurationTicks"));
        }
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity != null && NetherTowers.isStriderLine(type()) && value("killDamageBonus") > 0.0) {
            towerEntity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, PIGLIN_KILL_DAMAGE_SOURCE, value("killDamageBonus"), ticks("killDamageBonusTicks"));
        }
        if (NetherTowers.isSkeletonLine(type()) && lastAttackWasCritical) {
            heal(towerEntity, damageAmount * value("criticalKillLifeStealRatio"));
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("상태 " + (state() == NetherTowerState.NETHER ? "네더" : "좀비"));
        lines.add(deployedAtFinalDefense()
                ? "체력 감소 최종 방어선에서 중단"
                : "체력 감소 초당 " + percent(decayRatioPerSecond()));
        double damageBonus = lowHealthDamageBonus();
        if (damageBonus > 0.0) {
            lines.add("저체력 피해 +" + percent(damageBonus));
        }
        lines.add("흡혈 " + percent(lifeStealRatio(null)));
        if (decayReductionTicks > 0) {
            lines.add("체력 감소 완화 " + oneDecimal(decayReductionTicks / 20.0) + "초");
        }
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof NetherTower netherTower)) {
            return;
        }
        attackCounter = netherTower.attackCounter;
        decayReductionTicks = netherTower.decayReductionTicks;
        pulseCooldownTicks = netherTower.pulseCooldownTicks;
        markCounter = netherTower.markCounter;
        lastAttackWasCritical = netherTower.lastAttackWasCritical;
        double previousMax = Math.max(1.0, netherTower.currentMaxHealth());
        syncHealth(currentMaxHealth() * Math.max(0.0, netherTower.health() / previousMax));
    }

    private void applyDecay(PlayerLane lane, SemionTowerEntity entity) {
        double damage = currentMaxHealth() * decayRatioPerSecond() / 20.0;
        if (decayReductionTicks > 0) {
            damage *= Math.max(0.0, 1.0 - value("decayReductionRatio"));
        }
        double nextHealth = health() - damage;
        if (nextHealth <= 0.0 && state() == NetherTowerState.NETHER) {
            reviveAsZombie(lane, entity);
            return;
        }
        syncHealth(nextHealth);
        entity.setHealth((float) health());
        if (health() <= 0.0 && state() == NetherTowerState.ZOMBIE) {
            entity.discard();
        }
    }

    private void reviveAsZombie(PlayerLane lane, SemionTowerEntity entity) {
        setData(STATE, NetherTowerState.ZOMBIE);
        syncHealth(currentMaxHealth() * Math.max(0.01, global("zombieReviveHealthRatio")));
        if (entity == null || entity.isRemoved()) {
            if (lane != null) {
                onRemoved(lane);
                onPlaced(lane);
                entity = towerEntity(lane).orElse(null);
            }
        } else {
            entity.setHealth((float) health());
        }
        if (entity != null && is(NetherTowers.T3_ZOMBIFIED_PIGLIN)) {
            entity.forceAttackReady();
        }
        if (entity != null && is(NetherTowers.T3_PIGLIN_BRUTE)) {
            entity.applyTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_BONUS,
                    ZOMBIE_TRANSITION_DAMAGE_SOURCE,
                    value("zombieTransitionDamageBonus"),
                    ticks("zombieTransitionDamageBonusTicks")
            );
        }
        if (entity != null && NetherTowers.isBlazeLine(type())) {
            damageAroundTower(
                    entity,
                    value("zombieTransitionPulseRadius"),
                    scaledPulseDamage("zombieTransitionPulseDamageRatio")
            );
        }
        TowerVfxService.showNetherTransition(entity);
        if (lane != null) {
            onStateChanged(lane);
        }
    }

    private boolean shouldDecay(PlayerLane lane) {
        return lane != null
                && !deployedAtFinalDefense()
                && !lane.activeMonsters().isEmpty()
                && health() > 0.0;
    }

    private void refreshDynamicTimedEffects(SemionTowerEntity entity) {
        int refreshTicks = Math.max(2, (int) Math.round(global("effectRefreshTicks")));
        entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, LOW_HEALTH_DAMAGE_SOURCE, lowHealthDamageBonus(), refreshTicks);
        entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, LOW_HEALTH_DAMAGE_REDUCTION_SOURCE, criticalDamageReduction(), refreshTicks);
        entity.refreshTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, LOW_HEALTH_ATTACK_SPEED_SOURCE, missingHealthAttackSpeedBonus(), refreshTicks);
        entity.refreshTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, ZOMBIE_ATTACK_SPEED_SOURCE, zombieAttackSpeedBonus(), refreshTicks);
    }

    private double lowHealthDamageBonus() {
        if (healthRatio() > global("lowHealthThreshold")) {
            return 0.0;
        }
        return Math.min(global("lowHealthDamageBonusCap"), missingHealthRatio() * global("damagePerMissingHealth"));
    }

    private double lowHealthLifeStealBonus() {
        if (healthRatio() > global("lowHealthThreshold")) {
            return 0.0;
        }
        return Math.min(global("lifeStealBonusCap"), missingHealthRatio() * global("lifeStealPerMissingHealth"));
    }

    private double missingHealthAttackSpeedBonus() {
        double cap = value("missingHealthAttackSpeedBonusCap");
        if (state() == NetherTowerState.ZOMBIE && value("zombieMissingHealthAttackSpeedBonusCap") > 0.0) {
            cap = value("zombieMissingHealthAttackSpeedBonusCap");
        }
        return Math.max(0.0, missingHealthRatio() * cap);
    }

    private double criticalDamageReduction() {
        if (!isCritical()) {
            return 0.0;
        }
        return value("criticalDamageReduction");
    }

    private double zombieAttackSpeedBonus() {
        return state() == NetherTowerState.ZOMBIE ? value("zombieAttackSpeedBonus") : 0.0;
    }

    private double decayRatioPerSecond() {
        return state() == NetherTowerState.ZOMBIE
                ? global("zombieDecayMaxHealthRatioPerSecond")
                : global("netherDecayMaxHealthRatioPerSecond");
    }

    private double lifeStealRatio(SemionMonsterEntity target) {
        double base = state() == NetherTowerState.ZOMBIE
                ? valueOrGlobal("zombieLifeStealRatio")
                : valueOrGlobal("netherLifeStealRatio");
        double ratio = base + lowHealthLifeStealBonus() + value("lifeStealBonus");
        Monster monster = target == null ? null : target.runtimeMonster();
        if (is(NetherTowers.T3_PIGLIN_BRUTE)
                && isCritical()
                && isTankOrHighHealthTarget(monster)) {
            ratio += value("tankLifeStealBonus");
        }
        return Math.max(0.0, ratio);
    }

    private boolean isTankOrHighHealthTarget(Monster monster) {
        return monster != null
                && (monster.summonRoles().contains(SummonRole.TANK)
                || monster.maxHealth() >= value("highHealthThreshold"));
    }

    private double scaledPulseDamage(String ratioKey) {
        return Math.max(0.0, type().damage() * value(ratioKey));
    }

    private double valueOrGlobal(String key) {
        double value = TowerBalanceRuntime.ability(type().id(), key, Double.NaN);
        return Double.isNaN(value) ? global(key) : value;
    }

    private double splashRadius() {
        double radius = value("splashRadius");
        if (NetherTowers.isHoglinLine(type()) && state() == NetherTowerState.ZOMBIE) {
            radius += value("zombieSplashRadiusBonus");
        }
        if (is(NetherTowers.T3_GHAST) && healthRatio() <= global("lowHealthThreshold")) {
            radius += value("lowHealthSplashRadiusBonus");
        }
        return radius;
    }

    private void splashAroundTarget(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount,
            double radius,
            double damageRatio
    ) {
        if (towerEntity == null || target == null || radius <= 0.0 || damageRatio <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "splash"), towerEntity, target, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyBasicAttackSplash(
                this,
                towerEntity,
                request,
                monster -> damageAmount * damageRatio,
                true,
                (monster, splashDamage, killed) -> heal(towerEntity, splashDamage * lifeStealRatio(monster))
        );
    }

    private void damageAroundTower(SemionTowerEntity towerEntity, double radius, double damageAmount) {
        if (towerEntity == null || radius <= 0.0 || damageAmount <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "pulse"), towerEntity, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        TowerAreaDamage.apply(
                this,
                towerEntity,
                request,
                monster -> damageAmount,
                true,
                (monster, appliedDamage, killed) -> heal(towerEntity, appliedDamage * lifeStealRatio(monster)),
                DamageType.MAGIC
        );
    }

    private void damageAroundTarget(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double radius,
            double damageAmount
    ) {
        if (towerEntity == null || target == null || radius <= 0.0 || damageAmount <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "pulse"), towerEntity, target, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        ).including(target.getUUID());
        TowerAreaDamage.apply(
                this,
                towerEntity,
                request,
                monster -> damageAmount,
                true,
                (monster, appliedDamage, killed) -> heal(towerEntity, appliedDamage * lifeStealRatio(monster)),
                DamageType.MAGIC
        );
    }

    private void extraAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        SemionMonsterEntity extraTarget = findSecondaryTarget(towerEntity, target).orElse(target);
        if (extraTarget == null || !extraTarget.isAlive()) {
            return;
        }
        var result = damageBasicAttackTargetResult(
                towerEntity, extraTarget, damageAmount, DamageType.MAGIC
        );
        TowerVfxService.showSecondaryAttack(towerEntity, extraTarget);
        heal(towerEntity, damageAmount * lifeStealRatio(extraTarget));
        if (result.killed()) {
            onKill(towerEntity, extraTarget, damageAmount);
        }
    }

    private Optional<SemionMonsterEntity> findSecondaryTarget(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        if (towerEntity == null || target == null) {
            return Optional.empty();
        }
        double range = Math.max(0.0, value("secondaryRange"));
        if (range <= 0.0) {
            return Optional.empty();
        }
        double rangeSqr = range * range;
        AABB box = towerEntity.getBoundingBox().inflate(range);
        return towerEntity.level().getEntities(towerEntity, box, entity ->
                        entity instanceof SemionMonsterEntity monster
                                && monster != target
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(towerEntity) <= rangeSqr
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .min(Comparator.comparingDouble(towerEntity::distanceToSqr));
    }

    private void applyMonsterDamageTakenMark(
            SemionMonsterEntity target,
            ResourceLocation sourceId,
            double magnitude,
            int durationTicks
    ) {
        if (target == null || sourceId == null || magnitude <= 0.0 || durationTicks <= 0) {
            return;
        }
        target.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, sourceId, magnitude, durationTicks);
    }

    private void heal(SemionTowerEntity towerEntity, double amount) {
        if (towerEntity != null && amount > 0.0) {
            towerEntity.healTarget(towerEntity, amount);
        }
    }

    private Optional<SemionTowerEntity> towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private boolean isCritical() {
        return healthRatio() <= global("criticalHealthThreshold");
    }

    private double healthRatio() {
        return currentMaxHealth() <= 0.0 ? 0.0 : health() / currentMaxHealth();
    }

    private double missingHealthRatio() {
        return 1.0 - healthRatio();
    }

    private double healthRatio(SemionMonsterEntity target) {
        if (target == null || target.getMaxHealth() <= 0.0F) {
            return 1.0;
        }
        return target.getHealth() / target.getMaxHealth();
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private double global(String key) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private boolean is(TowerType towerType) {
        return towerType != null && type().id().equals(towerType.id());
    }

    private ResourceLocation sourceId(String suffix) {
        String path = "nether/" + type().id() + "/" + ownerPlayer() + "/" + laneId()
                + "/" + position().x() + "_" + position().y() + "_" + position().z() + "/" + suffix;
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
