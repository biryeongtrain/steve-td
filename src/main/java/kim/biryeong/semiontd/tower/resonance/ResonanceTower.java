package kim.biryeong.semiontd.tower.resonance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.damagesource.DamageSource;

public final class ResonanceTower extends EntityBackedTower {
    private int resonanceLevel;
    private int resonanceLinks;
    private int pulseCharge;
    private double auraAttackSpeedBonus;
    private double auraDamageVsSlowedBonus;

    public ResonanceTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public ResonanceAspect aspect() {
        return ResonanceTowers.aspectOf(type());
    }

    public int resonanceLevel() {
        return resonanceLevel;
    }

    public int resonanceLinks() {
        return resonanceLinks;
    }

    public double auraAttackSpeedBonus() {
        return auraAttackSpeedBonus;
    }

    public double auraDamageVsSlowedBonus() {
        return auraDamageVsSlowedBonus;
    }

    void updateResonanceState(int level, int links, boolean allowDecrease) {
        int normalizedLevel = Math.max(0, level);
        int normalizedLinks = Math.max(0, links);
        resonanceLevel = allowDecrease ? normalizedLevel : Math.max(resonanceLevel, normalizedLevel);
        resonanceLinks = allowDecrease ? normalizedLinks : Math.max(resonanceLinks, normalizedLinks);
    }

    void updateAuraAttackSpeedBonus(double bonus, boolean allowDecrease) {
        double normalizedBonus = Math.max(0.0, bonus);
        auraAttackSpeedBonus = allowDecrease ? normalizedBonus : Math.max(auraAttackSpeedBonus, normalizedBonus);
    }

    void updateAuraDamageVsSlowedBonus(double bonus, boolean allowDecrease) {
        double normalizedBonus = Math.max(0.0, bonus);
        auraDamageVsSlowedBonus = allowDecrease ? normalizedBonus : Math.max(auraDamageVsSlowedBonus, normalizedBonus);
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of("무블룸 공명 Lv " + resonanceLevel
                + " (링크 " + resonanceLinks + ") / 받는 오라 공속 +" + percent(auraAttackSpeedBonus)
                + " / 둔화 대상 피해 +" + percent(auraDamageVsSlowedBonus));
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof ResonanceTower previousResonanceTower) {
            resonanceLevel = previousResonanceTower.resonanceLevel;
            resonanceLinks = previousResonanceTower.resonanceLinks;
            pulseCharge = previousResonanceTower.pulseCharge;
            auraAttackSpeedBonus = previousResonanceTower.auraAttackSpeedBonus;
            auraDamageVsSlowedBonus = previousResonanceTower.auraDamageVsSlowedBonus;
        }
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        ResonanceService.refreshLane(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        ResonanceService.refreshLane(lane);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        double speedBonus = auraAttackSpeedBonus + selfAttackSpeedBonus();
        return Math.max(1, (int) Math.ceil(baseIntervalTicks / Math.max(0.01, 1.0 + speedBonus)));
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double damageBonus = (aspect() == ResonanceAspect.FOCUS ? focusDamageBonus() : 0.0)
                + auraDamageVsSlowedBonus(target);
        return damageAmount * Math.max(0.0, 1.0 + damageBonus);
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (aspect() != ResonanceAspect.AMPLIFY || resonanceLevel <= 0) {
            return damageAmount;
        }
        double reduction = switch (resonanceLevel) {
            case 1 -> ability("bloomLevel1DamageReduction");
            case 2 -> ability("bloomLevel2DamageReduction");
            default -> ability("bloomLevel3DamageReduction");
        };
        return damageAmount * Math.max(0.0, 1.0 - reduction);
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (resonanceLevel <= 0 || towerEntity == null || target == null) {
            return;
        }
        switch (aspect()) {
            case FOCUS -> focusStrike(towerEntity, target, damageAmount);
            case WAVE -> waveAttack(towerEntity, target, damageAmount);
            case FROST -> frostAttack(towerEntity, target, damageAmount);
            case AMPLIFY -> bloomProtect(towerEntity);
        }
    }

    private double selfAttackSpeedBonus() {
        return switch (aspect()) {
            case FOCUS -> switch (resonanceLevel) {
                case 1 -> ability("focusLevel1AttackSpeedBonus");
                case 2 -> ability("focusLevel2AttackSpeedBonus");
                default -> resonanceLevel >= 3 ? ability("focusLevel3AttackSpeedBonus") : 0.0;
            };
            case WAVE -> resonanceLevel >= 1 ? ability("waveLevel1AttackSpeedBonus") : 0.0;
            default -> 0.0;
        };
    }

    private double focusDamageBonus() {
        return switch (resonanceLevel) {
            case 2 -> ability("focusLevel2DamageBonus");
            default -> resonanceLevel >= 3 ? ability("focusLevel3DamageBonus") : 0.0;
        };
    }

    private double auraDamageVsSlowedBonus(SemionMonsterEntity target) {
        if (target == null || auraDamageVsSlowedBonus <= 0.0 || !hasFrostDebuff(target)) {
            return 0.0;
        }
        return auraDamageVsSlowedBonus;
    }

    private boolean hasFrostDebuff(SemionMonsterEntity target) {
        return target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 0.0
                || target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION) > 0.0;
    }

    private void focusStrike(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (resonanceLevel < 3 || !chargeReady("focusStrikeEveryAttacks")) {
            return;
        }
        double strikeDamage = damageAmount * ability("focusStrikeDamageRatio");
        if (strikeDamage > 0.0) {
            boolean killed = damageTarget(towerEntity, target, strikeDamage);
            TowerVfxService.showSecondaryAttack(towerEntity, target);
            if (killed) {
                onKill(towerEntity, target, strikeDamage);
            }
        }
    }

    private void waveAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (resonanceLevel >= 2) {
            double splashRadius = resonanceLevel >= 3 ? ability("waveLevel3SplashRadius") : ability("waveLevel2SplashRadius");
            double splashRatio = resonanceLevel >= 3 ? ability("waveLevel3SplashDamageRatio") : ability("waveLevel2SplashDamageRatio");
            areaDamage(towerEntity, target, splashRadius, damageAmount * splashRatio, false, 0, 0.0, 0.0);
        }
        if (resonanceLevel >= 3 && chargeReady("wavePulseEveryAttacks")) {
            areaDamage(
                    towerEntity,
                    target,
                    ability("wavePulseRadius"),
                    damageAmount * ability("wavePulseDamageRatio"),
                    true,
                    0,
                    0.0,
                    0.0
            );
        }
    }

    private void frostAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        applyFrostDebuff(target);
        if (resonanceLevel >= 3 && chargeReady("frostPulseEveryAttacks")) {
            areaDamage(
                    towerEntity,
                    target,
                    ability("frostPulseRadius"),
                    damageAmount * ability("frostPulseDamageRatio"),
                    true,
                    abilityTicks("frostPulseSlowTicks"),
                    ability("frostPulseSlowMagnitude"),
                    ability("frostPulseAttackSpeedReductionMagnitude")
            );
        }
    }

    private void bloomProtect(SemionTowerEntity towerEntity) {
        if (resonanceLevel < 3 || !chargeReady("bloomProtectEveryAttacks")) {
            return;
        }
        double radius = ability("bloomProtectRadius");
        double healAmount = type().damage() * ability("bloomProtectHealRatio");
        double reduction = ability("bloomProtectDamageReduction");
        int ticks = abilityTicks("bloomProtectTicks");
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                AreaEffectIds.tower(this, "bloom_protect"),
                towerEntity,
                towerEntity.position(),
                radius,
                TowerAreaTargetMode.ENTITIES,
                true,
                candidate -> candidate.tower() instanceof ResonanceTower,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
        SemionTdApi.areaEffects().applyToTowers(request, candidate ->
                protectTower(candidate.entity().orElseThrow(), healAmount, reduction, ticks)
                        ? AreaEffectOutcome.APPLIED
                        : AreaEffectOutcome.UNCHANGED);
    }

    private boolean protectTower(SemionTowerEntity towerEntity, double healAmount, double reduction, int ticks) {
        boolean changed = false;
        if (healAmount > 0.0 && towerEntity.receiveHealing(healAmount)) {
            towerEntity.playHealingAnimation();
            changed = true;
        }
        if (reduction > 0.0 && ticks > 0) {
            double previous = towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION);
            towerEntity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, ticks);
            changed |= Double.compare(previous,
                    towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION)) != 0;
        }
        return changed;
    }

    private void applyFrostDebuff(SemionMonsterEntity target) {
        int ticks = switch (resonanceLevel) {
            case 1 -> abilityTicks("frostLevel1SlowTicks");
            case 2 -> abilityTicks("frostLevel2SlowTicks");
            default -> abilityTicks("frostLevel3SlowTicks");
        };
        double moveSpeedReduction = switch (resonanceLevel) {
            case 1 -> ability("frostLevel1SlowMagnitude");
            case 2 -> ability("frostLevel2SlowMagnitude");
            default -> ability("frostLevel3SlowMagnitude");
        };
        double attackSpeedReduction = switch (resonanceLevel) {
            case 1 -> ability("frostLevel1AttackSpeedReductionMagnitude");
            case 2 -> ability("frostLevel2AttackSpeedReductionMagnitude");
            default -> ability("frostLevel3AttackSpeedReductionMagnitude");
        };
        if (ticks > 0 && moveSpeedReduction > 0.0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, moveSpeedReduction, ticks);
        }
        if (ticks > 0 && attackSpeedReduction > 0.0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, ticks);
        }
    }

    private boolean chargeReady(String abilityKey) {
        int every = Math.max(1, abilityInt(abilityKey));
        pulseCharge++;
        if (pulseCharge < every) {
            return false;
        }
        pulseCharge = 0;
        return true;
    }

    private void areaDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double radius,
            double damageAmount,
            boolean includePrimaryTarget,
            int debuffTicks,
            double moveSpeedReduction,
            double attackSpeedReduction
    ) {
        if (radius <= 0.0 || damageAmount <= 0.0 && (debuffTicks <= 0 || moveSpeedReduction <= 0.0 && attackSpeedReduction <= 0.0)) {
            return;
        }
        AreaVfxSpec vfx = damageAmount > 0.0
                ? AreaVfxSpec.onTrigger(includePrimaryTarget ? AreaVfxStyles.PULSE : AreaVfxStyles.SPLASH)
                : AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, damageAmount > 0.0 ? "area_damage" : "area_debuff"),
                towerEntity,
                target.position(),
                radius,
                includePrimaryTarget ? Set.of() : Set.of(target.getUUID()),
                null,
                vfx
        );
        SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
            boolean killed = false;
            boolean changed = damageAmount > 0.0;
            if (damageAmount > 0.0) {
                killed = damageTarget(towerEntity, monster, damageAmount);
                if (killed) {
                    onKill(towerEntity, monster, damageAmount);
                }
            }
            if (debuffTicks > 0 && moveSpeedReduction > 0.0) {
                double previous = monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION);
                monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, moveSpeedReduction, debuffTicks);
                changed |= Double.compare(previous,
                        monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION)) != 0;
            }
            if (debuffTicks > 0 && attackSpeedReduction > 0.0) {
                double previous = monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION);
                monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, debuffTicks);
                changed |= Double.compare(previous,
                        monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION)) != 0;
            }
            if (killed) {
                return AreaEffectOutcome.KILLED;
            }
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
    }

    private double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int abilityInt(String key) {
        return TowerBalanceRuntime.abilityInt(type().id(), key);
    }

    private int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
