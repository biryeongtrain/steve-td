package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class EndAugments {
    static final String MINE = "job_end_towers_s";
    static final String GROWTH = "job_end_towers_g1";
    static final String BREATH = "job_end_towers_g2";
    static final String TWIN = "job_end_towers_p";
    private final List<Mine> mines = new ArrayList<>();
    private int completedTransfers;
    private boolean charged;
    private int activeTicks;
    private int remainingBreaths;
    private int nextBreathTick;
    private SemionMonsterEntity breathTarget;
    private SemionTowerEntity mineSource;

    void reset() {
        mines.clear();
        completedTransfers = 0;
        charged = false;
        activeTicks = 0;
        cancelBurst();
        mineSource = null;
    }

    void cancelBurst() {
        remainingBreaths = 0;
        breathTarget = null;
    }

    void onTransferCompleted(EndTower tower, Tower transferred, double maxHealth) {
        if (!AugmentCombat.allowsTriggers()) {return;}
        if (tower.augmentSnapshot().has(MINE)) {
            mines.add(new Mine(new Vec3(transferred.position().x() + .5,
                    transferred.position().y() + 1, transferred.position().z() + .5), maxHealth));
        }
        if (tower.augmentSnapshot().has(GROWTH)) {
            completedTransfers++;
            if (completedTransfers % Math.max(1, (int) tower.augmentSnapshot().parameter(GROWTH, "transfersPerCharge", 3)) == 0) {
                charged = true;
            }
        }
    }

    void onAttack(EndTower tower, SemionTowerEntity source, SemionMonsterEntity target,
            List<SemionMonsterEntity> secondaries, double attackDamage) {
        if (!AugmentCombat.allowsTriggers() || !charged || !tower.augmentSnapshot().has(GROWTH)) {return;}
        charged = false;
        double damage = attackDamage * tower.augmentSnapshot().parameter(GROWTH, "damageRatio", 1.5);
        magicHit(tower, source, target, damage);
        for (SemionMonsterEntity secondary : secondaries) {magicHit(tower, source, secondary, damage);}
    }

    void tick(EndTower tower, PlayerLane lane) {
        if (!AugmentCombat.allowsTriggers() || lane == null || lane.arenaWorld() == null) {return;}
        SemionTowerEntity source = tower.runtimeEntity(lane).orElse(null);
        if (source == null || !source.isAlive() || SuccubusDreams.isAsleep(source)) {return;}
        activeTicks++;
        if (tower.state() != EndTowerState.DRAGON || !tower.augmentSnapshot().has(BREATH)) {
            cancelBurst();
            return;
        }
        int interval = Math.max(1, (int) tower.augmentSnapshot().parameter(BREATH, "intervalTicks", 120));
        if (activeTicks % interval == 0) {
            remainingBreaths = (int) tower.augmentSnapshot().parameter(BREATH, "shots", 2);
            nextBreathTick = activeTicks;
            breathTarget = source.currentAttackTarget();
        }
        if (remainingBreaths > 0 && activeTicks >= nextBreathTick) {
            remainingBreaths--;
            nextBreathTick = activeTicks + (int) tower.augmentSnapshot().parameter(BREATH, "burstIntervalTicks", 4);
            breathTarget = selectTarget(tower, source, breathTarget);
            if (breathTarget != null) {fireBreath(tower, source, breathTarget);}
        }
    }

    void tickMines(EndTower tower, PlayerLane lane) {
        if (!AugmentCombat.allowsTriggers() || lane == null || lane.arenaWorld() == null) {return;}
        tower.runtimeEntity(lane).ifPresent(entity -> mineSource = entity);
        if (mineSource == null || mines.isEmpty()) {return;}
        double radius = tower.augmentSnapshot().parameter(MINE, "radius", 3);
        for (Iterator<Mine> iterator = mines.iterator(); iterator.hasNext();) {
            Mine mine = iterator.next();
            if (lane.arenaWorld().getGameTime() % 10 == 0) {
                lane.arenaWorld().sendParticles(ParticleTypes.REVERSE_PORTAL, mine.center().x, mine.center().y,
                        mine.center().z, 2, .25, .05, .25, .01);
            }
            if (lane.arenaWorld().getEntitiesOfClass(SemionMonsterEntity.class,
                    new AABB(mine.center(), mine.center()).inflate(radius),
                    target -> mineSource.isValidAttackTarget(target)
                            && target.position().distanceToSqr(mine.center()) <= radius * radius).isEmpty()) {continue;}
            iterator.remove();
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    AreaEffectIds.tower(tower, "augment_void_mine"), mineSource, mine.center(), radius,
                    Set.of(), null, AreaVfxSpec.onTrigger(AreaVfxStyles.DRAGON_BREATH))
                    .nearestTargets((int) tower.augmentSnapshot().parameter(MINE, "maxTargets", 8));
            TowerAreaDamage.apply(tower, mineSource, request,
                    ignored -> mine.maxHealth() * tower.augmentSnapshot().parameter(MINE, "healthRatio", 1),
                    true, (target, damage, killed) -> {}, DamageType.MAGIC);
        }
    }

    private static SemionMonsterEntity selectTarget(EndTower tower, SemionTowerEntity source, SemionMonsterEntity current) {
        double rangeSquared = source.attackRange() * source.attackRange();
        if (source.isValidAttackTarget(current) && source.distanceToSqr(current) <= rangeSquared
                && tower.canAttackTarget(source, current)) {return current;}
        return source.level().getEntitiesOfClass(SemionMonsterEntity.class, source.targetSearchBox(),
                target -> source.isValidAttackTarget(target) && source.distanceToSqr(target) <= rangeSquared
                        && tower.canAttackTarget(source, target)).stream()
                .min(Comparator.comparingDouble(source::distanceToSqr)).orElse(null);
    }

    private static void fireBreath(EndTower tower, SemionTowerEntity source, SemionMonsterEntity target) {
        double length = tower.augmentSnapshot().parameter(BREATH, "length", 12);
        double width = tower.augmentSnapshot().parameter(BREATH, "width", 3);
        Vec3 direction = target.position().subtract(source.position()).multiply(1, 0, 1).normalize();
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(tower, "augment_breath"), source, source.position(), Math.hypot(length, width),
                Set.of(), candidate -> inBreath(source.position(), direction, candidate.position(), length, width),
                AreaVfxSpec.onTrigger(AreaVfxStyles.DRAGON_BREATH))
                .nearestTargets((int) tower.augmentSnapshot().parameter(BREATH, "maxTargets", 12));
        double damage = source.attackDamageAmount(target) * tower.augmentSnapshot().parameter(BREATH, "damageRatio", .75);
        TowerAreaDamage.applyResolved(tower, source, request,
                candidate -> tower.resolveBasicAttackOutgoingDamage(source, candidate, damage),
                true, (candidate, amount, killed) -> {}, DamageType.MAGIC);
    }

    static boolean inBreath(Vec3 origin, Vec3 direction, Vec3 target, double length, double width) {
        Vec3 offset = target.subtract(origin).multiply(1, 0, 1);
        double forward = offset.dot(direction);
        double sideways = offset.x * direction.z - offset.z * direction.x;
        return direction.lengthSqr() > 0 && forward >= 0 && forward <= length && Math.abs(sideways) <= width / 2;
    }

    private static void magicHit(EndTower tower, SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        if (target == null || !target.isAlive()) {return;}
        Tower.DamageResult result = tower.damageResolvedTargetResult(source, target,
                tower.resolveBasicAttackOutgoingDamage(source, target, damage), DamageType.MAGIC);
        if (result.killed()) {tower.onKill(source, target, damage);}
    }

    List<String> details(EndTower tower) {
        List<String> lines = new ArrayList<>();
        if (tower.augmentSnapshot().has(MINE)) {lines.add("공허 지뢰: " + mines.size() + "개");}
        if (tower.augmentSnapshot().has(GROWTH)) {
            lines.add("폭풍 성장: 이번 전투 전달 " + completedTransfers + "기 / 추가 피해 " + (charged ? "충전" : "대기"));
        }
        return lines;
    }

    int mineCount() {return mines.size();}
    boolean charged() {return charged;}
    record Mine(Vec3 center, double maxHealth) {}
}
