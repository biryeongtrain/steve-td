package kim.biryeong.semiontd.tower.villager;

import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

public class VillagerThornTower extends EntityBackedTower {
    private int thornCooldownTicks = 0;
    private int survivalBonus = 0;
    private int giantTicks = -1;
    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public VillagerThornTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    @Override
    public void onDamaged(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount, double previousHealth, double currentHealth) {
        if (this.thornCooldownTicks > 0) {
            return;
        }
        float range = (float) value("thornRadius");
        double damage = value("thornDamage");
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "thorns"), towerEntity, range,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        TowerAreaDamage.apply(this, towerEntity, request, monster -> damage, false);

        this.thornCooldownTicks = ticks("thornCooldownTicks");
    }

    @Override
    protected double builderCurrentMaxHealth() {
        double giant = VillagerAugments.isGiant(this)
                ? augmentSnapshot().parameter(VillagerAugments.GIANT, "maxHealthBonus", 2) : 0;
        return applyTraitMaxHealth(maxHealth() * (1.0 + survivalHealthBonus())) * (1 + giant);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        double bonus = survivalHealthBonus();
        return java.util.List.of("생존 스택 " + survivalBonus + "/" + maxSurvivalStacks()
                + " (체력 +" + percent(bonus) + ")", VillagerAugments.detail(this));
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        VillagerAugments.roundEnded(this);
        giantTicks = -1;
        if (!deployedAtFinalDefense()) {
            increaseSurvivalBonus();
        }
        super.resetForRound(lane);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        increaseSurvivalBonus();
        super.moveToFinalDefense(lane, position);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (this.thornCooldownTicks > 0) {
            this.thornCooldownTicks--;
        }
        if (giantTicks < 0 || health() <= 0 || !VillagerAugments.isGiant(this)) {return;}
        if (++giantTicks < augmentSnapshot().parameter(VillagerAugments.GIANT, "intervalTicks", 60)) {return;}
        giantTicks = 0;
        if (entityId().isEmpty() || !(lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity source)
                || kim.biryeong.semiontd.tower.succubus.SuccubusDreams.isAsleep(source)) {return;}
        var request = MonsterAreaEffectRequest.aroundTower(AreaEffectIds.tower(this, "village_giant"), source,
                augmentSnapshot().parameter(VillagerAugments.GIANT, "radius", 4),
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE))
                .nearestTargets((int) augmentSnapshot().parameter(VillagerAugments.GIANT, "maxTargets", 12));
        kim.biryeong.semiontd.augment.AugmentCombat.runWithoutTriggers(() ->
                TowerAreaDamage.apply(this, source, request,
                        target -> currentMaxHealth() * augmentSnapshot().parameter(VillagerAugments.GIANT, "healthDamageRatio", .12), true));
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        super.onWaveStarted(lane, round);
        VillagerAugments.waveStarted(this);
        giantTicks = 0;
    }

    @Override
    public void onDeath(PlayerLane lane) {
        super.onDeath(lane);
        VillagerAugments.onDeath(this, lane);
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        super.onAttackResolved(source, target, attempted, outgoing, dealt, killed);
        VillagerAugments.attackResolved(this, source, target, dealt);
    }

    int survivalStacks() {return survivalBonus;}

    void addSurvivalStacks(int count) {
        for (int i = 0; i < Math.min(count, maxSurvivalStacks()); i++) {increaseSurvivalBonus();}
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof VillagerThornTower thornTower) {
            survivalBonus = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), thornTower.survivalBonus);
            syncHealth(currentMaxHealth());
        }
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private int maxSurvivalStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks");
    }

    private double survivalHealthBonus() {
        return value("healthBonusPerSurvivedRound") * VillagerAugments.totalStacks(this) * VillagerAdvStates.survivalBonusMultiplier(this);
    }

    private void increaseSurvivalBonus() {
        double previousMaxHealth = currentMaxHealth();
        int previousBonus = survivalBonus;
        survivalBonus = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), survivalBonus + 1);
        if (survivalBonus > previousBonus) {
            syncHealth(health() + Math.max(0.0, currentMaxHealth() - previousMaxHealth));
        }
    }
}
