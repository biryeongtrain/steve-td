package kim.biryeong.semiontd.tower.body;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * 신체 타워의 공용 실행부입니다.
 *
 * <p>일반 타워 AI의 자동 공격 범위는 0으로 막고, 오직 심장의 박동이 왔을 때만
 * 각 기관의 행동을 한 번 실행합니다.</p>
 */
public final class BodyTower extends EntityBackedTower {
    private static final List<ResourceLocation> SKIN_STACK_SOURCES = stackSources("skin");

    private final Map<UUID, Integer> genitalHitCounts = new HashMap<>();
    private int heartDeathStacks;
    private boolean waveActive;
    private int normalHeartbeats;
    private boolean adrenalineUsed;
    private int fastIntervals;
    private double skinHealthLost;
    private long lastSkinHeartbeat = Long.MIN_VALUE;

    public BodyTower(
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
    protected boolean execute(PlayerLane lane) {
        if (!heartbeat(lane)) {
            return false;
        }
        if (AugmentCombat.allowsTriggers() && augmentSnapshot().has("job_body_g1")
                && ++normalHeartbeats >= augmentSnapshot().parameter("job_body_g1", "normalBeats", 2)) {
            normalHeartbeats = 0;
            AugmentCombat.runWithoutTriggers(() -> heartbeat(lane));
        }
        return true;
    }

    private boolean heartbeat(PlayerLane lane) {
        if (lane == null || !BodyTowers.isHeart(type()) || !waveActive || health() <= 0.0
                || SuccubusDreams.isAsleep(this)) {return false;}
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof BodyTower bodyTower
                    && bodyTower != this
                    && bodyTower.health() > 0.0) {
                bodyTower.actOnHeartbeat(lane);
            }
        }
        entity(lane).ifPresent(source -> {
            source.playAnimation(SemionAnimationState.ATTACK);
            TowerVfxService.showBodyHeartbeat(source);
        });
        return true;
    }

    void actOnHeartbeat(PlayerLane lane) {
        Optional<SemionTowerEntity> source = entity(lane);
        if (source.isEmpty()) {
            return;
        }
        SemionTowerEntity entity = source.get();
        if (!entity.isAlive() || SuccubusDreams.isAsleep(entity)) {return;}
        entity.playAnimation(SemionAnimationState.ATTACK);
        switch (BodyTowers.roleOf(type())) {
            case BRAIN -> brainAction(entity);
            case SKIN -> skinAction(entity);
            case EYE -> eyeAction(lane, entity);
            case GENITAL -> genitalAction(entity);
            case HEART -> {
            }
        }
    }

    private void brainAction(SemionTowerEntity source) {
        SemionMonsterEntity primary = selectPrimary(monstersInRange(source, type().range()));
        if (primary == null) {
            return;
        }
        double radius = BodyBalance.brainSplashRadius(type());
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "brain_wave"),
                source,
                primary,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF)
        ).including(primary.getUUID());

        TowerAreaDamage.applyResolved(
                this,
                source,
                request,
                source::attackDamageAmount,
                true,
                (target, damage, killed) -> {
                    if (!killed && target.isAlive()) {
                        applyBrainStack(target);
                    }
                }
        );
    }

    private void applyBrainStack(SemionMonsterEntity target) {
        double taken = BodyBalance.brainDamageTaken(type());
        double weakened = BodyBalance.brainAttackReduction(type());
        int ticks = BodyBalance.brainDebuffTicks(type());
        target.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, taken, ticks);
        target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, weakened, ticks);
    }

    private void skinAction(SemionTowerEntity source) {
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "skin_burst"),
                source,
                type().range(),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyResolved(this, source, request, source::attackDamageAmount, true, (target, damage, killed) -> {
        });

        if (BodyTowers.tier(type()) >= 2) {
            applySkinStack(source);
        }
    }

    private void applySkinStack(SemionTowerEntity source) {
        double reduction = BodyBalance.skinReductionPerStack(type());
        int ticks = BodyBalance.skinReductionTicks(type());
        ResourceLocation openSource = null;
        for (ResourceLocation stackSource : SKIN_STACK_SOURCES) {
            if (source.hasTimedEffectSource(TimedEffectType.TOWER_DAMAGE_REDUCTION, stackSource)) {
                source.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, stackSource, reduction, ticks);
            } else if (openSource == null) {
                openSource = stackSource;
            }
        }
        if (openSource != null) {
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, openSource, reduction, ticks);
        }
    }

    private void eyeAction(PlayerLane lane, SemionTowerEntity source) {
        Vec3 direction = eyeDirection(lane.laneLayout(), deployedAtFinalDefense());
        if (augmentSnapshot().has("job_body_s")) {
            SemionMonsterEntity nearest = monstersInRange(source, type().range()).stream()
                    .min(Comparator.comparingDouble(source::distanceToSqr)).orElse(null);
            if (nearest == null) {return;}
            Vec3 delta = nearest.position().subtract(source.position());
            direction = new Vec3(delta.x, 0.0, delta.z).normalize();
        }
        if (direction.lengthSqr() <= 0.0) {
            return;
        }
        lockEyeDirection(source, direction);
        double range = type().range();
        double width = BodyBalance.eyeWidth(type());
        Vec3 rayDirection = direction;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "eye_ray"),
                source,
                range,
                AreaVfxSpec.none()
        ).withFilter(target -> insideEyeRay(source.position(), target.position(), rayDirection, range, width));
        TowerAreaDamage.applyResolved(this, source, request, source::attackDamageAmount, true, (target, damage, killed) -> {
        });
        TowerVfxService.showBodyEyeLaser(source, direction, range);
    }

    private void genitalAction(SemionTowerEntity source) {
        SemionMonsterEntity primary = selectPrimary(monstersInRange(source, type().range()));
        if (primary == null) {
            return;
        }
        hitGenitalTarget(source, primary);

        int extraTargets = BodyBalance.genitalExtraTargets(type());
        if (extraTargets <= 0) {
            return;
        }
        List<SemionMonsterEntity> nearby = monstersAround(
                source,
                primary.position(),
                BodyBalance.genitalExtraTargetRadius(type()),
                Set.of(primary.getUUID())
        );
        nearby.stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(primary)))
                .limit(extraTargets)
                .forEach(target -> hitGenitalTarget(source, target));
    }

    private void hitGenitalTarget(SemionTowerEntity source, SemionMonsterEntity target) {
        double outgoing = source.attackDamageAmount(target);
        Tower.DamageResult result = damageResolvedTargetResult(source, target, outgoing);
        source.recordAttack(target, outgoing, result.outgoingDamage(), result.dealtDamage(), result.killed());
        TowerVfxService.showAttack(source, target, result.killed(), false);
        if (result.killed() || !target.isAlive()) {
            genitalHitCounts.remove(target.getUUID());
            return;
        }

        int hits = genitalHitCounts.getOrDefault(target.getUUID(), 0) + 1;
        if (hits < 2) {
            genitalHitCounts.put(target.getUUID(), hits);
            return;
        }
        genitalHitCounts.remove(target.getUUID());
        damageTargetResult(source, target, BodyBalance.genitalMagicDamage(type()), DamageType.MAGIC);
        target.applyTimedEffect(
                TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION,
                BodyBalance.genitalSlow(type()),
                BodyBalance.genitalSlowTicks(type())
        );
    }

    private List<SemionMonsterEntity> monstersInRange(SemionTowerEntity source, double radius) {
        return monstersAround(source, source.position(), radius, Set.of());
    }

    private List<SemionMonsterEntity> monstersAround(
            SemionTowerEntity source,
            Vec3 center,
            double radius,
            Set<UUID> excluded
    ) {
        List<SemionMonsterEntity> targets = new ArrayList<>();
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "target_scan"),
                source,
                center,
                Math.max(0.1, radius),
                excluded,
                null,
                AreaVfxSpec.none()
        );
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            targets.add(target);
            return AreaEffectOutcome.UNCHANGED;
        });
        return targets;
    }

    private static SemionMonsterEntity selectPrimary(List<SemionMonsterEntity> targets) {
        return targets.stream()
                .max(Comparator.comparingDouble(target -> target.runtimeMonster().targetPriorityScore()))
                .orElse(null);
    }

    private Optional<SemionTowerEntity> entity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    static Vec3 eyeDirection(LaneRegionLayout layout) {
        return eyeDirection(layout, false);
    }

    static Vec3 eyeDirection(LaneRegionLayout layout, boolean finalDefense) {
        List<Vec3> pathPoints = layout.pathPoints();
        int start = finalDefense ? pathPoints.size() - 2 : 0;
        int end = finalDefense ? -1 : pathPoints.size() - 1;
        int step = finalDefense ? -1 : 1;
        for (int index = start; index != end; index += step) {
            Vec3 from = pathPoints.get(index);
            Vec3 to = pathPoints.get(index + 1);
            Vec3 againstTravel = new Vec3(from.x - to.x, 0.0, from.z - to.z);
            if (againstTravel.lengthSqr() > 0.0) {
                return againstTravel.normalize();
            }
        }
        return Vec3.ZERO;
    }

    static boolean insideEyeRay(
            Vec3 origin,
            Vec3 target,
            Vec3 direction,
            double range,
            double width
    ) {
        Vec3 delta = target.subtract(origin);
        double projection = delta.x * direction.x + delta.z * direction.z;
        if (projection < 0.0 || projection > range) {
            return false;
        }
        double horizontalDistanceSqr = delta.x * delta.x + delta.z * delta.z;
        double perpendicularSqr = Math.max(0.0, horizontalDistanceSqr - projection * projection);
        return perpendicularSqr <= width * width;
    }

    private static void lockEyeDirection(SemionTowerEntity source, Vec3 direction) {
        if (direction.lengthSqr() <= 0.0) {
            return;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        source.setYRot(yaw);
        source.setYHeadRot(yaw);
        source.setYBodyRot(yaw);
    }

    private static List<ResourceLocation> stackSources(String name) {
        return List.of(
                ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "body/" + name + "/stack_1"),
                ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "body/" + name + "/stack_2"),
                ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "body/" + name + "/stack_3")
        );
    }

    @Override
    public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
        if (!BodyTowers.isHeart(type())
                || BodyTowers.tier(type()) < 2
                || health() <= 0.0
                || !(destroyedTower instanceof BodyTower)
                || !ownerPlayer().equals(destroyedTower.ownerPlayer())) {
            return;
        }
        heartDeathStacks = Math.min(BodyBalance.heartMaxDeathStacks(type()), heartDeathStacks + 1);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (!BodyTowers.isHeart(type())) {
            return baseIntervalTicks;
        }
        int interval = Math.max(1, baseIntervalTicks - heartAttackIntervalReduction());
        return fastIntervals > 0 ? Math.max(1, (int) Math.round(interval
                * augmentSnapshot().parameter("job_body_g2", "intervalRatio", 0.5))) : interval;
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return super.builderCurrentMaxHealth() * (BodyTowers.isHeart(type()) && augmentSnapshot().has("job_body_g2")
                ? 1.0 + augmentSnapshot().parameter("job_body_g2", "healthBonus", 0.8) : 1.0);
    }

    @Override
    public void onDamaged(SemionTowerEntity source, DamageSource damageSource, double damageAmount,
            double previousHealth, double currentHealth) {
        super.onDamaged(source, damageSource, damageAmount, previousHealth, currentHealth);
        if (!waveActive || !AugmentCombat.allowsTriggers() || source == null) {return;}
        double lost = Math.max(0.0, previousHealth - currentHealth);
        if (lost <= 0.0) {return;}
        if (BodyTowers.isHeart(type()) && !adrenalineUsed && augmentSnapshot().has("job_body_g2")
                && currentHealth > 0.0 && currentHealth <= currentMaxHealth()
                * augmentSnapshot().parameter("job_body_g2", "healthThreshold", 0.5)) {
            adrenalineUsed = true;
            source.receiveHealing(currentMaxHealth() * augmentSnapshot().parameter("job_body_g2", "healRatio", 0.3));
            fastIntervals = (int) augmentSnapshot().parameter("job_body_g2", "fastBeats", 8);
            if (cooldownTicksRemaining() > 0 && fastIntervals > 0) {
                double ratio = augmentSnapshot().parameter("job_body_g2", "intervalRatio", 0.5);
                reduceCooldownTicks((int) Math.floor(cooldownTicksRemaining() * (1.0 - ratio)));
                fastIntervals--;
            }
        }
        if (BodyTowers.roleOf(type()) != BodyTowers.Role.SKIN || !augmentSnapshot().has("job_body_p")
                || damageSource == null || !(damageSource.getEntity() instanceof SemionMonsterEntity)) {return;}
        if (!accumulateSkinLoss(lost)) {return;}
        PlayerLane lane = attachedLane();
        if (lane == null) {return;}
        long now = source.level().getGameTime();
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof BodyTower heart && BodyTowers.isHeart(heart.type())
                    && ownerPlayer().equals(heart.ownerPlayer()) && heart.acceptSkinHeartbeat(now)) {
                AugmentCombat.runWithoutTriggers(() -> heart.heartbeat(lane));
            }
        }
    }

    boolean accumulateSkinLoss(double loss) {
        double threshold = currentMaxHealth() * augmentSnapshot().parameter("job_body_p", "healthLossRatio", 0.15);
        if (threshold <= 0.0 || loss <= 0.0) {return false;}
        skinHealthLost += loss;
        if (skinHealthLost + 1.0e-9 < threshold) {return false;}
        skinHealthLost = Math.max(0.0, skinHealthLost - Math.floor((skinHealthLost + 1.0e-9) / threshold) * threshold);
        return true;
    }

    boolean acceptSkinHeartbeat(long now) {
        if (!waveActive || health() <= 0.0 || SuccubusDreams.isAsleep(this)
                || (lastSkinHeartbeat != Long.MIN_VALUE && now - lastSkinHeartbeat
                < augmentSnapshot().parameter("job_body_p", "cooldownTicks", 20))) {return false;}
        lastSkinHeartbeat = now;
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        entity(lane).ifPresent(SemionTowerEntity::refreshCombatStats);
        int interval = entity(lane).map(SemionTowerEntity::attackIntervalTicks)
                .orElseGet(() -> adjustAttackInterval(type().attackIntervalTicks()));
        if (fastIntervals > 0) {fastIntervals--;}
        return interval;
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>(heartDeathDetailLines());
        if (BodyTowers.isHeart(type()) && augmentSnapshot().has("job_body_g2")) {
            lines.add("아드레날린 " + (adrenalineUsed ? "사용됨" : "대기") + " · 가속 간격 " + fastIntervals + "회");
        }
        if (BodyTowers.roleOf(type()) == BodyTowers.Role.SKIN && augmentSnapshot().has("job_body_p")) {
            lines.add("예민한 피부 누적 손실 " + oneDecimal(skinHealthLost) + "/"
                    + oneDecimal(currentMaxHealth() * augmentSnapshot().parameter("job_body_p", "healthLossRatio", 0.15)));
        }
        return List.copyOf(lines);
    }

    private List<String> heartDeathDetailLines() {
        if (!BodyTowers.isHeart(type()) || BodyTowers.tier(type()) < 2) {
            return List.of();
        }
        int stacks = heartDeathStacks();
        int maxStacks = BodyBalance.heartMaxDeathStacks(type());
        int interval = adjustAttackInterval(type().attackIntervalTicks());
        if (stacks >= maxStacks) {
            return List.of("사망 중첩 " + stacks + "/" + maxStacks + " · 현재 공격 주기 " + interval + "틱");
        }
        int perReduction = Math.max(1, BodyBalance.heartStacksPerIntervalReduction(type()));
        int untilNextReduction = perReduction - stacks % perReduction;
        return List.of("사망 중첩 " + stacks + "/" + maxStacks
                + " · 현재 공격 주기 " + interval + "틱 · 다음 감소까지 " + untilNextReduction + "중첩");
    }

    int heartDeathStacks() {
        return Math.min(heartDeathStacks, BodyBalance.heartMaxDeathStacks(type()));
    }

    private int heartAttackIntervalReduction() {
        int stacksPerReduction = Math.max(1, BodyBalance.heartStacksPerIntervalReduction(type()));
        return heartDeathStacks() / stacksPerReduction;
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        return 0.0;
    }

    @Override
    public boolean canChaseTargets() {
        return false;
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        if (lane != null && BodyTowers.roleOf(type()) == BodyTowers.Role.EYE) {
            entity(lane).ifPresent(source -> lockEyeDirection(
                    source,
                    eyeDirection(lane.laneLayout(), deployedAtFinalDefense())
            ));
        }
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        resetAugmentWave();
        genitalHitCounts.clear();
        if (BodyTowers.roleOf(type()) == BodyTowers.Role.EYE) {
            entity(lane).ifPresent(source -> lockEyeDirection(
                    source,
                    eyeDirection(lane.laneLayout(), deployedAtFinalDefense())
            ));
        }
    }

    @Override
    public void onLaneCleared(PlayerLane lane) {
        genitalHitCounts.clear();
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        resetAugmentWave();
        super.resetForRound(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof BodyTower previous) {
            waveActive = previous.waveActive;
            heartDeathStacks = Math.min(BodyBalance.heartMaxDeathStacks(type()), previous.heartDeathStacks);
            genitalHitCounts.clear();
            genitalHitCounts.putAll(previous.genitalHitCounts);
            normalHeartbeats = previous.normalHeartbeats;
            adrenalineUsed = previous.adrenalineUsed;
            fastIntervals = previous.fastIntervals;
            skinHealthLost = previous.skinHealthLost;
            lastSkinHeartbeat = previous.lastSkinHeartbeat;
        }
    }

    private void resetAugmentWave() {
        normalHeartbeats = 0;
        adrenalineUsed = false;
        fastIntervals = 0;
        skinHealthLost = 0.0;
        lastSkinHeartbeat = Long.MIN_VALUE;
    }
}
