package kim.biryeong.semiontd.tower.illager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.phys.AABB;

public class IllagerTower extends EntityBackedTower {
    private static final ResourceLocation RAID_DAMAGE_SOURCE = raidSource("damage");
    private static final ResourceLocation RAID_ATTACK_SPEED_SOURCE = raidSource("attack_speed");
    private static final ResourceLocation RAID_DAMAGE_REDUCTION_SOURCE = raidSource("damage_reduction");

    private final IllagerTargetPolicy targetPolicy;

    public IllagerTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        this(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition, IllagerTargetPolicy.DEFAULT);
    }

    public IllagerTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition,
            IllagerTargetPolicy targetPolicy
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.targetPolicy = targetPolicy == null ? IllagerTargetPolicy.DEFAULT : targetPolicy;
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        if (!IllagerTowers.isCaptainTower(type())) {
            return;
        }
        var patterns = entity.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        entity.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(patterns));
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        Optional<SemionMonsterEntity> forced = forcedMarkedTarget(candidates);
        if (forced.isPresent()) {
            return forced;
        }
        return switch (targetPolicy) {
            case LOW_HEALTH -> candidates.stream()
                    .filter(this::validRuntimeMonster)
                    .min(Comparator.comparingDouble(monster -> monster.runtimeMonster().health()));
            case HIGH_HEALTH -> candidates.stream()
                    .filter(this::validRuntimeMonster)
                    .max(Comparator.comparingDouble(monster -> monster.runtimeMonster().health()));
            case INCOME -> candidates.stream()
                    .filter(this::validRuntimeMonster)
                    .filter(monster -> monster.runtimeMonster().ownerPlayer().isPresent())
                    .max(Comparator.comparingDouble(monster -> monster.runtimeMonster().targetPriorityScore()));
            case DEFAULT -> Optional.empty();
        };
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double multiplier = 1.0;
        Monster monster = target == null ? null : target.runtimeMonster();
        boolean raidActive = IllagerRaidStates.active(ownerPlayer());
        Optional<IllagerMark> mark = IllagerMarks.activeMark(monster, ownerPlayer());
        if (mark.isPresent()) {
            multiplier += mark.get().damageTakenBonus();
            if (raidActive) {
                multiplier += ability("raidMarkedDamageBonus");
            }
        }
        if (monster != null && monster.ownerPlayer().isPresent()) {
            multiplier += ability("incomeDamageBonus");
            if (raidActive) {
                multiplier += ability("raidIncomeDamageBonus");
            }
        }
        return damageAmount * Math.max(0.0, multiplier);
    }

    @Override
    public void tick(PlayerLane lane) {
        refreshRaidTimedEffects(lane);
        super.tick(lane);
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        applyMark(target);
        applySplash(towerEntity, target, damageAmount);
    }

    protected void applyMark(SemionMonsterEntity target) {
        Monster monster = target == null ? null : target.runtimeMonster();
        if (monster == null) {
            return;
        }
        int duration = abilityTicks("markDurationTicks");
        double damageBonus = ability("markDamageTakenBonus");
        double forceTargetRadius = ability("forceTargetRadius");
        if (IllagerRaidStates.active(ownerPlayer())) {
            duration += abilityTicks("raidMarkDurationBonusTicks");
            damageBonus += ability("raidMarkDamageTakenBonus");
            damageBonus += raidTargetPolicyMarkBonus();
            forceTargetRadius += ability("raidForceTargetRadiusBonus");
        }
        if (duration <= 0 || damageBonus <= 0) {
            return;
        }
        IllagerMarks.apply(
                monster,
                ownerPlayer(),
                damageBonus,
                duration,
                position(),
                forceTargetRadius
        );
    }

    protected void applySplash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double splashRadius = ability("splashRadius");
        double splashRatio = ability("splashDamageRatio");
        if (IllagerRaidStates.active(ownerPlayer())) {
            splashRadius += ability("raidSplashRadiusBonus");
            splashRatio += ability("raidSplashDamageRatioBonus");
        }
        if (splashRadius <= 0 || splashRatio <= 0) {
            return;
        }

        double finalSplashRatio = splashRatio;
        AABB splashBox = target.getBoundingBox().inflate(splashRadius);
        double splashRadiusSqr = splashRadius * splashRadius;
        towerEntity.level().getEntities(towerEntity, splashBox,
                        entity -> entity instanceof SemionMonsterEntity splashTarget
                                && splashTarget.isAlive()
                                && splashTarget != target
                                && splashTarget.runtimeMonster() != null
                                && towerEntity.defendsLane(splashTarget.runtimeMonster().targetLaneId())
                                && splashTarget.distanceToSqr(target) <= splashRadiusSqr)
                .stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(entity -> {
                    double splashDamage = damageAmount * finalSplashRatio;
                    if (damageTarget(towerEntity, entity, splashDamage)) {
                        onKill(towerEntity, entity, splashDamage);
                    }
                });
    }

    protected double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    protected int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private void refreshRaidTimedEffects(PlayerLane lane) {
        if (lane == null || health() <= 0.0 || !IllagerRaidStates.active(ownerPlayer()) || entityId().isEmpty()) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            return;
        }

        int ticks = Math.max(1, TowerBalanceRuntime.abilityTicks(IllagerRaidStates.RAID_CONFIG_ID, "timedEffectDurationTicks", 40));
        refreshTimedEffect(towerEntity, TimedEffectType.TOWER_DAMAGE_BONUS, RAID_DAMAGE_SOURCE, IllagerRaidStates.damageBonus(ownerPlayer()), ticks);
        refreshTimedEffect(towerEntity, TimedEffectType.TOWER_ATTACK_SPEED_BONUS, RAID_ATTACK_SPEED_SOURCE, IllagerRaidStates.attackSpeedBonus(ownerPlayer()), ticks);
        refreshTimedEffect(towerEntity, TimedEffectType.TOWER_DAMAGE_REDUCTION, RAID_DAMAGE_REDUCTION_SOURCE, ability("raidDamageReduction"), ticks);
    }

    private void refreshTimedEffect(
            SemionTowerEntity towerEntity,
            TimedEffectType type,
            ResourceLocation sourceId,
            double magnitude,
            int ticks
    ) {
        if (magnitude > 0.0) {
            towerEntity.refreshTimedEffect(type, sourceId, magnitude, ticks);
        }
    }

    private double raidTargetPolicyMarkBonus() {
        return switch (targetPolicy) {
            case LOW_HEALTH -> ability("raidLowHealthMarkDamageTakenBonus");
            case HIGH_HEALTH -> ability("raidHighHealthMarkDamageTakenBonus");
            case DEFAULT, INCOME -> 0.0;
        };
    }

    private Optional<SemionMonsterEntity> forcedMarkedTarget(List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(this::validRuntimeMonster)
                .filter(monster -> IllagerMarks.activeMark(monster.runtimeMonster(), ownerPlayer())
                        .map(mark -> mark.forcesTargetFor(position()))
                        .orElse(false))
                .max(Comparator.comparingDouble(monster -> monster.runtimeMonster().targetPriorityScore()));
    }

    private boolean validRuntimeMonster(SemionMonsterEntity monster) {
        return monster != null && monster.runtimeMonster() != null;
    }

    private static ResourceLocation raidSource(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_raid/" + path);
    }
}
