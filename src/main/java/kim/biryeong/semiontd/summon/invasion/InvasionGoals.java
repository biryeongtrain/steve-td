package kim.biryeong.semiontd.summon.invasion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 침공군 유닛이 공격과 따로 계속 돌리는 능력. 모두 매 틱 돌고(바닐라 목표는 기본 2틱마다) 엔티티 틱 수로
 * 시간을 재므로, 게임 배속이 바뀌어도 애니메이션·공격과 같은 빠르기로 움직입니다.
 */
final class InvasionGoals {
    private InvasionGoals() {
    }

    /** 매 틱 도는 능력의 틀. */
    abstract static class Ability extends Goal {
        protected final SemionMonsterEntity caster;

        Ability(SemionMonsterEntity caster) {
            this.caster = caster;
        }

        @Override
        public boolean canUse() {
            return caster.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        /** 같은 레인으로 쳐들어간 살아 있는 아군(자기 포함). */
        protected List<SemionMonsterEntity> alliesWithin(double radius) {
            Monster self = caster.runtimeMonster();
            if (self == null) {
                return List.of();
            }
            double radiusSqr = radius * radius;
            return caster.level().getEntitiesOfClass(SemionMonsterEntity.class, caster.getBoundingBox().inflate(radius, 3.0, radius),
                    other -> other.isAlive() && other.runtimeMonster() != null
                            && other.runtimeMonster().targetTeam() == self.targetTeam()
                            && other.runtimeMonster().targetLaneId() == self.targetLaneId()
                            && caster.distanceToSqr(other) <= radiusSqr);
        }

        protected ServerLevel serverLevel() {
            return caster.level() instanceof ServerLevel level ? level : null;
        }

        protected long seed() {
            return caster.level().getGameTime() + caster.getId();
        }
    }

    // ------------------------------------------------------------------ 트롤: 재생

    /** 전투 중에도 계속 체력이 찹니다. 초당 최대 체력의 {@code ratioPerSecond}. 연출은 없습니다. */
    static final class Regeneration extends Ability {
        private static final int PERIOD = 10;
        private final double ratioPerSecond;

        Regeneration(SemionMonsterEntity caster, double ratioPerSecond) {
            super(caster);
            this.ratioPerSecond = ratioPerSecond;
        }

        @Override
        public void tick() {
            Monster monster = caster.runtimeMonster();
            if (monster == null || caster.tickCount % PERIOD != 0 || monster.health() >= monster.maxHealth()) {
                return;
            }
            caster.receiveHealing(monster.maxHealth() * ratioPerSecond * PERIOD / 20.0);
        }
    }

    // ------------------------------------------------------------------ 오크: 광폭화

    /** 체력이 {@code threshold} 아래로 내려가면 공격력·공격 속도가 오릅니다(죽을 때까지 유지). */
    static final class Berserk extends Ability {
        private static final int REFRESH = 10;
        private final double threshold;
        private final double damageBonus;
        private final double attackSpeedBonus;
        private boolean triggered;

        Berserk(SemionMonsterEntity caster, double threshold, double damageBonus, double attackSpeedBonus) {
            super(caster);
            this.threshold = threshold;
            this.damageBonus = damageBonus;
            this.attackSpeedBonus = attackSpeedBonus;
        }

        @Override
        public void tick() {
            Monster monster = caster.runtimeMonster();
            if (monster == null || caster.tickCount % REFRESH != 0) {
                return;
            }
            if (!triggered && monster.health() > monster.maxHealth() * threshold) {
                return;
            }
            if (!triggered) {
                triggered = true;
                InvasionVfx.playAt(serverLevel(), InvasionVfx.orcBerserk(seed()), caster.position());
            }
            caster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS, damageBonus, REFRESH + 5);
            caster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_BONUS, attackSpeedBonus, REFRESH + 5);
        }

        boolean triggered() {
            return triggered;
        }
    }

    // ------------------------------------------------------------------ 군단장: 오라

    /** 둘레의 아군(자기 포함)이 받는 피해가 줄고 공격력이 오릅니다. 항상 켜진 패시브라 연출은 없습니다. */
    static final class CommandAura extends Ability {
        private static final int REFRESH = 10;
        private final double radius;
        private final double damageReduction;
        private final double attackBonus;

        CommandAura(SemionMonsterEntity caster, double radius, double damageReduction, double attackBonus) {
            super(caster);
            this.radius = radius;
            this.damageReduction = damageReduction;
            this.attackBonus = attackBonus;
        }

        @Override
        public void tick() {
            if (caster.tickCount % REFRESH != 0) {
                return;
            }
            for (SemionMonsterEntity ally : alliesWithin(radius)) {
                ally.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, damageReduction, REFRESH + 5);
                ally.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS, attackBonus, REFRESH + 5);
            }
        }
    }

    // ------------------------------------------------------------------ 암흑 신관: 광역 치유

    /**
     * 공격과 따로 도는 광역 치유. 둘레의 다친 아군을 한꺼번에 치유하고 발밑에 연출만 띄웁니다(모션 없음).
     * 다친 아군이 없으면 {@code retry}틱 뒤 다시 봅니다.
     */
    static final class AreaHeal extends Ability {
        private final double radius;
        private final double healAmount;
        private final double maxHealthRatio;
        private final int cooldown;
        private final int retry;
        private int readyTick;

        AreaHeal(SemionMonsterEntity caster, double radius, double healAmount, double maxHealthRatio, int cooldown, int retry) {
            super(caster);
            this.radius = radius;
            this.healAmount = healAmount;
            this.maxHealthRatio = maxHealthRatio;
            this.cooldown = cooldown;
            this.retry = retry;
        }

        @Override
        public void tick() {
            if (caster.tickCount < readyTick || caster.isStunned()) {
                return;
            }
            boolean healed = false;
            for (SemionMonsterEntity ally : alliesWithin(radius)) {
                Monster monster = ally.runtimeMonster();
                if (monster.health() >= monster.maxHealth()) {
                    continue;
                }
                double before = monster.health();
                ally.receiveHealing(healAmount + monster.maxHealth() * maxHealthRatio);
                if (monster.health() > before) {
                    healed = true;
                    InvasionVfx.playAt(serverLevel(), InvasionVfx.priestHeal(seed() + ally.getId()), ally.position());
                }
            }
            readyTick = caster.tickCount + (healed ? cooldown : retry);
        }
    }

    // ------------------------------------------------------------------ 강령술사: 해골 소환

    /**
     * 지팡이를 들어(공격 모션) 발밑에 마법진을 펴고, {@code count}마리의 해골을 {@code interval}틱 간격으로 한 마리씩
     * 불러냅니다. 해골은 보상이 없고, 한 강령술사가 동시에 거느리는 수는 {@code maxAlive}마리까지입니다.
     */
    static final class RaiseSkeletons extends Ability {
        private final int count;
        private final int interval;
        private final int cooldown;
        private final int castDelay;
        private final int maxAlive;
        private final double healthRatio;
        private final double damageRatio;
        private final List<UUID> minions = new ArrayList<>();
        private int readyTick;
        private int nextSpawnTick = -1;
        private int remaining;

        RaiseSkeletons(SemionMonsterEntity caster, int count, int interval, int cooldown, int castDelay, int maxAlive,
                double healthRatio, double damageRatio) {
            super(caster);
            this.count = count;
            this.interval = Math.max(1, interval);
            this.cooldown = cooldown;
            this.castDelay = castDelay;
            this.maxAlive = maxAlive;
            this.healthRatio = healthRatio;
            this.damageRatio = damageRatio;
            this.readyTick = castDelay * 2;
        }

        @Override
        public void tick() {
            if (remaining > 0) {
                if (caster.tickCount >= nextSpawnTick) {
                    raiseOne();
                    remaining--;
                    nextSpawnTick = caster.tickCount + interval;
                }
                return;
            }
            if (caster.tickCount < readyTick || caster.isStunned() || caster.hasPendingHit()) {
                return;
            }
            int room = maxAlive - aliveMinions();
            if (room <= 0) {
                readyTick = caster.tickCount + 20;
                return;
            }
            remaining = Math.min(count, room);
            nextSpawnTick = caster.tickCount + castDelay;
            readyTick = caster.tickCount + cooldown;
            caster.playAnimation(SemionAnimationState.ATTACK);
            InvasionVfx.playAt(serverLevel(), InvasionVfx.necroCast(castDelay + remaining * interval, seed()), caster.position());
        }

        private int aliveMinions() {
            ServerLevel level = serverLevel();
            if (level == null) {
                return 0;
            }
            minions.removeIf(id -> !(level.getEntity(id) instanceof SemionMonsterEntity minion) || !minion.isAlive());
            return minions.size();
        }

        private void raiseOne() {
            Monster necromancer = caster.runtimeMonster();
            Optional<PlayerLane> lane = AreaEffectLaneIndex.findForMonster(caster);
            if (necromancer == null || lane.isEmpty()) {
                return;
            }
            double angle = caster.getRandom().nextDouble() * Math.PI * 2.0;
            double reach = 0.8 + caster.getRandom().nextDouble() * 1.2;
            Vec3 at = caster.position().add(Math.sin(angle) * reach, 0.0, Math.cos(angle) * reach);
            if (!caster.level().noCollision(skeletonBox(at))) {
                at = caster.position();
            }
            Monster skeleton = new Monster(
                    InvasionUnits.NECROMANCER_MINION,
                    necromancer.targetTeam(),
                    necromancer.targetLaneId(),
                    necromancer.ownerPlayer(),
                    necromancer.senderTeam(),
                    Math.max(1.0, necromancer.maxHealth() * healthRatio),
                    0.0,
                    Math.max(1.0, necromancer.attackDamage() * damageRatio),
                    kim.biryeong.semiontd.config.AttackKind.RANGED,
                    "minecraft:skeleton",
                    null,
                    kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL,
                    0.0,
                    kim.biryeong.semiontd.entity.monster.MonsterDimensions.DEFAULT,
                    kim.biryeong.semiontd.summon.SummonTier.T1,
                    List.of(),
                    0L
            );
            // 소환물은 처치 보상이 없습니다(보상 규칙이 이 출처를 건너뜁니다).
            skeleton.setOrigin(MonsterOrigin.FREE_AUGMENT);
            necromancer.senderName().ifPresent(skeleton::setSenderName);
            Vec3 spot = at;
            lane.get().spawnMonsterAt(skeleton, spot).ifPresent(minion -> {
                minions.add(minion.getUUID());
                InvasionVfx.playAt(serverLevel(), InvasionVfx.necroRise(seed() + minion.getId()), spot);
            });
        }

        private static AABB skeletonBox(Vec3 at) {
            return new AABB(at.x - 0.3, at.y + 0.05, at.z - 0.3, at.x + 0.3, at.y + 1.9, at.z + 0.3);
        }
    }

    static Comparator<SemionMonsterEntity> byMissingHealth() {
        return Comparator.comparingDouble(entity -> entity.runtimeMonster().health() - entity.runtimeMonster().maxHealth());
    }
}
