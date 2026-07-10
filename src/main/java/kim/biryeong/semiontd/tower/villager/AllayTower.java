package kim.biryeong.semiontd.tower.villager;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;

public class AllayTower extends SupportTower {
    private static final ResourceLocation WEAPON_SMITH_SOURCE = supportId("weapon_smith");
    private static final ResourceLocation ARMORER_SOURCE = supportId("armorer");
    private static final TowerDataKey<Long> HEAL_BLOCKED_UNTIL = TowerDataKey.of(supportId("allay_heal_blocked_until"), Long.class);
    private static final TowerDataKey<Long> WEAPON_SMITH_BLOCKED_UNTIL = TowerDataKey.of(supportId("weapon_smith_blocked_until"), Long.class);
    private static final TowerDataKey<Long> ARMORER_BLOCKED_UNTIL = TowerDataKey.of(supportId("armorer_blocked_until"), Long.class);

    public AllayTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public AllayTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (is(VillagerTowers.T1_ALLAY_TOWER)) {
            return applyHeal(lane, radius(), healAmount(lane, value("healAmount")));
        }
        if (is(VillagerTowers.T2_ALLAY_TOWER)) {
            return applyHeal(lane, radius(), healAmount(lane, value("healAmount")));
        }
        if (is(VillagerTowers.T2_WEAPON_SMITH_TOWER)) {
            return applyWeaponSmithBuff(lane, radius(), value("weaponBuff"));
        }
        if (is(VillagerTowers.T3_ARMORER_TOWER)) {
            return applyArmorerSupport(lane);
        }
        if (is(VillagerTowers.T3_WEAPON_SMITH_TOWER)) {
            return applyWeaponSmithBuff(lane, radius(), value("weaponBuff"));
        }
        return Boolean.FALSE;
    }

    private boolean applyHeal(PlayerLane lane, double radius, double amount) {
        SemionTowerEntity source = towerEntity(this, lane).orElse(null);
        if (source == null) {
            return false;
        }
        TowerAreaEffectRequest request = supportRequest(source, radius, "heal")
                .withFilter(target -> canApply(target.tower(), HEAL_BLOCKED_UNTIL, lane.arenaWorld().getGameTime()));
        return SemionTdApi.areaEffects().applyToTowers(request, target -> {
            if (!heal(target.tower(), lane, amount)) {
                return AreaEffectOutcome.UNCHANGED;
            }
            block(target.tower(), HEAL_BLOCKED_UNTIL, lane, lane.arenaWorld().getGameTime());
            return AreaEffectOutcome.APPLIED;
        }).appliedCount() > 0;
    }

    private boolean applyWeaponSmithBuff(PlayerLane lane, double radius, double magnitude) {
        SemionTowerEntity source = towerEntity(this, lane).orElse(null);
        if (source == null) {
            return false;
        }
        TowerAreaEffectRequest request = supportRequest(source, radius, "weapon_smith")
                .withFilter(target -> canApply(target.tower(), WEAPON_SMITH_BLOCKED_UNTIL, lane.arenaWorld().getGameTime())
                        && target.entity().isPresent());
        return SemionTdApi.areaEffects().applyToTowers(request, target -> {
            SemionTowerEntity entity = target.entity().orElseThrow();
            boolean damageApplied = entity.applyTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_BONUS,
                    WEAPON_SMITH_SOURCE,
                    magnitude,
                    ticks("buffDurationTicks")
            );
            boolean speedApplied = entity.applyTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                    WEAPON_SMITH_SOURCE,
                    magnitude,
                    ticks("buffDurationTicks")
            );
            if (damageApplied || speedApplied) {
                block(target.tower(), WEAPON_SMITH_BLOCKED_UNTIL, lane, lane.arenaWorld().getGameTime());
                return AreaEffectOutcome.APPLIED;
            }
            return AreaEffectOutcome.UNCHANGED;
        }).appliedCount() > 0;
    }

    private boolean applyArmorerSupport(PlayerLane lane) {
        SemionTowerEntity source = towerEntity(this, lane).orElse(null);
        if (source == null) {
            return false;
        }
        TowerAreaEffectRequest request = supportRequest(source, radius(), "armorer")
                .withFilter(target -> canApply(target.tower(), ARMORER_BLOCKED_UNTIL, lane.arenaWorld().getGameTime()));
        return SemionTdApi.areaEffects().applyToTowers(request, target -> {
            boolean healed = heal(target.tower(), lane, healAmount(lane, value("healAmount")));
            boolean reducedDamage = target.entity()
                    .map(entity -> entity.applyTimedEffect(
                            TimedEffectType.TOWER_DAMAGE_REDUCTION,
                            ARMORER_SOURCE,
                            value("damageReduction"),
                            ticks("buffDurationTicks")
                    ))
                    .orElse(false);
            if (healed || reducedDamage) {
                block(target.tower(), ARMORER_BLOCKED_UNTIL, lane, lane.arenaWorld().getGameTime());
                return AreaEffectOutcome.APPLIED;
            }
            return AreaEffectOutcome.UNCHANGED;
        }).appliedCount() > 0;
    }

    private TowerAreaEffectRequest supportRequest(SemionTowerEntity source, double radius, String effect) {
        return TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, effect),
                source,
                radius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
    }

    private boolean heal(Tower target, PlayerLane lane, double amount) {
        Optional<SemionTowerEntity> targetEntity = towerEntity(target, lane);
        if (targetEntity.isPresent()) {
            boolean healed = targetEntity.get().receiveHealing(amount);
            if (healed) {
                targetEntity.get().playHealingAnimation();
            }
            return healed;
        }
        if (target.health() > 0.0 && target.health() < target.currentMaxHealth()) {
            double before = target.health();
            target.syncHealth(before + amount);
            return target.health() > before;
        }
        return Boolean.FALSE;
    }

    private Optional<SemionTowerEntity> towerEntity(Tower target, PlayerLane lane) {
        if (!(target instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private boolean canApply(Tower target, TowerDataKey<Long> key, long gameTime) {
        return target.getDataOrDefault(key, 0L) <= gameTime;
    }

    private void block(Tower target, TowerDataKey<Long> key, PlayerLane lane, long gameTime) {
        target.setData(key, gameTime + supportBlockTicks(lane));
    }

    private static ResourceLocation supportId(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "tower_support/" + path);
    }

    private double radius() {
        return value("radius");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return reducedTicks(super.cooldownTicksAfterExecute(lane), intervalReduction(lane));
    }

    private double healAmount(PlayerLane lane, double baseAmount) {
        return baseAmount * (1.0 + activeEffect(lane, TimedEffectType.TOWER_HEAL_AMOUNT_BONUS));
    }

    private int supportBlockTicks(PlayerLane lane) {
        return reducedTicks(ticks("supportBlockTicks"), intervalReduction(lane));
    }

    private int reducedTicks(int baseTicks, double reduction) {
        return Math.max(1, (int) Math.ceil(baseTicks * Math.max(0.01, 1.0 - reduction)));
    }

    private double intervalReduction(PlayerLane lane) {
        return activeEffect(lane, TimedEffectType.TOWER_ABILITY_INTERVAL_REDUCTION);
    }

    private double activeEffect(PlayerLane lane, TimedEffectType type) {
        return towerEntity(this, lane)
                .map(entity -> entity.activeTimedEffectMagnitude(type))
                .orElse(0.0);
    }

    private boolean is(TowerType towerType) {
        return VillagerTowers.matches(type(), towerType);
    }
}
