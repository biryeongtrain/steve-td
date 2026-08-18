package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectAction;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ten demon lord skills.
 *
 * <p>Every skill reads its numbers from the altar's tower id, so a live config can retune any single
 * tier. Damage is always multiplied by {@link DemonLordState#damageMultiplier()} - levels are the
 * builder's only scaling, since none of its towers ever deal damage.
 */
public final class DemonLordSkills {
    /** 벽에 부딪혔을 때 벽면에서 떨어뜨려 놓을 거리. 블록 안에 끼는 걸 막습니다. */
    private static final double WALL_STANDOFF = 0.8;

    private DemonLordSkills() {
    }

    /**
     * @return cooldown ticks to refund, or 0. 파멸의 손아귀만 처치 시 일부를 돌려줍니다.
     */
    public static int cast(
            ServerPlayer player,
            PlayerLane lane,
            DemonLordState state,
            DemonLordSkill skill,
            DemonLordSkillTower altar,
            long gameTime
    ) {
        switch (skill) {
            case WAVE_OF_MALICE -> castWaveOfMalice(player, lane, state, altar);
            case DEMON_WINGS -> castDemonWings(player, lane, state, altar);
            case SKY_BREAKER -> castSkyBreaker(player, lane, state, altar);
            case ARCANE_BOMBARDMENT -> castArcaneBombardment(player, lane, state, altar, gameTime);
            case DEMON_BARRIER -> castDemonBarrier(player, lane, state, altar);
            case HELLFIRE_BRAND -> castHellfireBrand(player, lane, state, altar, gameTime);
            case SOUL_DRAIN -> castSoulDrain(player, lane, state, altar);
            case ROAR_OF_DREAD -> castRoarOfDread(player, lane, state, altar);
            case GRIP_OF_DOOM -> {
                return castGripOfDoom(player, lane, state, altar);
            }
            case HELL_GUILLOTINE -> castHellGuillotine(player, lane, state, altar);
            default -> {
            }
        }
        return 0;
    }

    /**
     * 유일한 단일 대상 기술. 정면에서 가장 가까운 적 하나만 잡습니다.
     *
     * <p>잃은 체력 비례 추가 피해가 붙어 이미 두들겨 맞은 대상을 끊어 내는 데 강하고, 처치에
     * 성공하면 쿨타임 일부를 돌려받아 연쇄로 이어 갈 수 있습니다.
     *
     * @return 처치 시 돌려줄 쿨타임 틱
     */
    private static int castGripOfDoom(ServerPlayer player, PlayerLane lane, DemonLordState state, DemonLordSkillTower altar) {
        double range = reach(state, altar, "range", 9.0);
        Vec3 origin = player.position();
        Vec3 look = horizontal(player.getLookAngle());

        SemionMonsterEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (SemionMonsterEntity candidate : monstersNear(lane, origin, range)) {
            Vec3 toTarget = horizontal(candidate.position().subtract(origin));
            // 정면 90도 안쪽만 후보로 봅니다. 뒤에 있는 적이 잡히면 조준이 안 됩니다.
            if (look.dot(toTarget) < Math.cos(Math.toRadians(45.0))) {
                continue;
            }
            double distance = candidate.position().distanceTo(origin);
            if (distance < bestScore) {
                bestScore = distance;
                target = candidate;
            }
        }
        if (target == null) {
            sound(player, SoundEvents.WITHER_SHOOT, 0.6f, 0.5f);
            return 0;
        }

        Monster runtime = target.runtimeMonster();
        if (runtime == null) {
            return 0;
        }
        Vec3 victimPosition = target.position();
        double threshold = runtime.maxHealth() * ability(altar, "executeHealthRatio", 0.50);
        if (runtime.health() > threshold) {
            // 처형 조건 미달. 일반 피해만 넣고, 잃은 체력이 많을수록 아프게 해 임계값까지 밀어 줍니다.
            double missing = Math.max(0.0, runtime.maxHealth() - runtime.health());
            double damage = (ability(altar, "damage", 98.0) + missing * ability(altar, "missingHealthRatio", 0.10))
                    * state.damageMultiplier();
            DemonLordService.dealDamage(player, lane, altar, target, damage, DamageType.MAGIC);

            Vec3 pull = horizontal(origin.subtract(victimPosition)).scale(ability(altar, "pullStrength", 0.5));
            target.setDeltaMovement(pull.x, 0.2, pull.z);
            target.hurtMarked = true;
            sound(player, SoundEvents.WITHER_HURT, 1.0f, 0.6f);
            return 0;
        }

        // 처형. 남은 체력을 고정 피해로 그대로 날려 방어·저항과 무관하게 확실히 끊습니다.
        double victimHealth = runtime.health();
        DemonLordService.dealDamage(player, lane, altar, target, victimHealth, DamageType.TRUE);

        // 시체가 터집니다. 폭발 피해는 처형 시점 체력에 비례하므로 단단한 적일수록 크게 터집니다.
        double blast = victimHealth * ability(altar, "explosionHealthRatio", 1.0)
                + ability(altar, "areaDamage", 30.0) * state.damageMultiplier();
        double blastRadius = reach(state, altar, "explosionRadius", 4.0);
        SemionMonsterEntity executedTarget = target;
        applyArea(altar, lane, victimPosition, blastRadius, nearby -> nearby != executedTarget,
                AreaVfxStyles.CORPSE_EXPLOSION,
                nearby -> damageOutcome(DemonLordService.dealDamage(
                        player, lane, altar, nearby, blast, DamageType.MAGIC)));
        sound(player, SoundEvents.WITHER_DEATH, 1.0f, 0.7f);
        return (int) ability(altar, "killRefundTicks", 60.0);
    }

    /**
     * 바라보는 지점에 지속 장판을 깝니다.
     *
     * <p>발밑 고정이 아니라 시선으로 놓기 때문에, 몰려오는 길목에 미리 깔아 두거나 이미 뭉친
     * 무리 한가운데를 노릴 수 있습니다. 한 번에 하나만 유지되며 재시전하면 이전 장판을 덮어씁니다.
     */
    private static void castHellfireBrand(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar, long gameTime) {
        int interval = (int) Math.max(1.0, ability(altar, "tickIntervalTicks", 20.0));
        int duration = (int) Math.max(1.0, ability(altar, "zoneDurationTicks", 100.0));
        Vec3 centre = lookTarget(player, reach(state, altar, "placementRange", 10.0));

        state.placeZone(new DemonLordState.HellfireZone(
                altar.type(),
                centre,
                reach(state, altar, "zoneRadius", 3.5),
                ability(altar, "damage", 14.0) * state.damageMultiplier(),
                ability(altar, "damageTakenBonus", 0.10),
                interval,
                gameTime + duration,
                gameTime + interval
        ));
        DemonLordVfx.show(altar, lane, centre, reach(state, altar, "zoneRadius", 3.5), AreaVfxStyles.DEBUFF);
        sound(player, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
    }

    /**
     * 시선 지점으로 순간이동해 내리찍습니다.
     *
     * <p>피해는 <b>마왕 자신이</b> 잃은 체력에 비례해 커집니다. 대상이 아니라 시전자 기준인 것은
     * 의도적입니다 — 대상 기준 증폭은 이미 파멸의 손아귀가 맡고 있고, 이쪽은 몰렸을 때 판을
     * 뒤집는 역할이라 위험을 감수할수록 보상이 커야 합니다.
     *
     * <p>순간이동은 돌진과 같은 이유로 두 번 막습니다 — 레이캐스트로 벽을 넘지 않게 하고,
     * 착지 지점이 벽 속이나 허공이 아닌지 확인합니다. 거리 자체는 제한하지 않습니다.
     */
    private static void castHellGuillotine(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double radius = reach(state, altar, "radius", 4.0);
        Vec3 landing = DemonLordService.safeLanding(
                player, player.position(), lookTarget(player, reach(state, altar, "range", 10.0)));

        // 잃은 체력 비율 0(만피)~1(빈사). 빈사에서 missingHealthDamageBonus 만큼 증가합니다.
        double missingRatio = Math.max(0.0, Math.min(1.0, 1.0 - state.healthRatio()));
        double amplifier = 1.0 + missingRatio * ability(altar, "missingHealthDamageBonus", 1.0);
        double damage = ability(altar, "damage", 45.0) * state.damageMultiplier() * amplifier;

        player.teleportTo(landing.x, landing.y, landing.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();

        applyArea(altar, lane, landing, radius, ignored -> true, AreaVfxStyles.PULSE, monster -> {
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, altar, monster, damage, DamageType.MAGIC);
            push(monster, horizontal(monster.position().subtract(landing)), 0.5, 0.3);
            return damageOutcome(result);
        });
        sound(player, SoundEvents.ANVIL_LAND, 1.0f, 0.8f);
    }

    /** 시선이 닿는 지점. 블록에 막히면 그 자리, 아니면 최대 사거리 끝입니다. */
    private static Vec3 lookTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(range));
        HitResult clip = player.level().clip(new ClipContext(
                eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return clip.getType() == HitResult.Type.MISS ? far : clip.getLocation();
    }

    /**
     * 전방 직선을 꿰뚫어 피해를 주고, 준 피해에 비례해 회복하며, 맞은 적을 그 자리에 묶습니다.
     *
     * <p>구속은 이동 속도를 100% 깎는 것이라 공격은 그대로 합니다. 붙어 있는 적을 떼어내는 것이
     * 아니라, 도망치거나 지나쳐 가려는 적을 붙잡아 두는 기술입니다.
     */
    private static void castSoulDrain(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double range = reach(state, altar, "range", 7.0);
        double width = ability(altar, "width", 1.6);
        double damage = ability(altar, "damage", 26.0) * state.damageMultiplier();
        int rootTicks = (int) Math.max(1.0, ability(altar, "rootDurationTicks", 40.0));

        Vec3 start = player.position();
        Vec3 look = horizontal(player.getLookAngle());
        Vec3 end = start.add(look.scale(range));

        double[] dealtDamage = {0.0};
        applyArea(
                altar, lane, start.lerp(end, 0.5), range,
                monster -> distanceToSegment(monster.position(), start, end) <= width,
                AreaVfxStyles.SPLASH,
                monster -> {
                    Tower.DamageResult damageResult = DemonLordService.dealDamage(
                            player, lane, altar, monster, damage, DamageType.MAGIC);
                    dealtDamage[0] += damageResult.dealtDamage();
                    monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 1.0, rootTicks);
                    return damageOutcome(damageResult);
                });
        if (dealtDamage[0] > 0.0) {
            state.heal(soulDrainHealing(
                    dealtDamage[0],
                    state.maxHealth(),
                    ability(altar, "lifeStealRatio", 0.25),
                    ability(altar, "lifeStealCap", 0.12)
            ));
        }
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.6f);
    }

    /** 주위를 밀어내고 이동을 늦추며 공격을 막습니다. 포위를 푸는 용도입니다. */
    private static void castRoarOfDread(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double radius = reach(state, altar, "radius", 5.0);
        double damage = ability(altar, "damage", 19.0) * state.damageMultiplier();
        double knockback = ability(altar, "knockback", 1.0);
        double slow = ability(altar, "moveSpeedReduction", 0.50);
        int duration = (int) Math.max(1.0, ability(altar, "dreadDurationTicks", 50.0));

        Vec3 origin = player.position();
        applyArea(altar, lane, origin, radius, ignored -> true, AreaVfxStyles.DEBUFF, monster -> {
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, altar, monster, damage, DamageType.MAGIC);
            push(monster, horizontal(monster.position().subtract(origin)), knockback, 0.4);
            monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, duration);
            // 공격 자체를 막습니다. 속도만 깎으면 붙어 있는 적은 계속 때립니다.
            monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, 1.0, duration);
            return damageOutcome(result);
        });
        sound(player, SoundEvents.WARDEN_ROAR, 1.2f, 0.8f);
    }

    /** 점과 선분 사이의 수평 거리. 영혼 흡수의 직선 판정에 씁니다. */
    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 flatPoint = new Vec3(point.x, 0.0, point.z);
        Vec3 flatFrom = new Vec3(from.x, 0.0, from.z);
        Vec3 segment = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0e-6) {
            return flatPoint.distanceTo(flatFrom);
        }
        double projection = Math.max(0.0, Math.min(1.0,
                flatPoint.subtract(flatFrom).dot(segment) / lengthSqr));
        return flatPoint.distanceTo(flatFrom.add(segment.scale(projection)));
    }

    /** 전방 부채꼴을 쓸어 피해를 주고 뒤로 밀어냅니다. */
    private static void castWaveOfMalice(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double range = reach(state, altar, "range", 6.0);
        double halfAngleCos = Math.cos(Math.toRadians(ability(altar, "coneDegrees", 60.0) / 2.0));
        double damage = ability(altar, "damage", 34.0) * state.damageMultiplier();
        double knockback = ability(altar, "knockback", 0.8);

        Vec3 origin = player.position();
        Vec3 look = horizontal(player.getLookAngle());
        applyArea(altar, lane, origin, range, monster -> {
            Vec3 toMonster = horizontal(monster.position().subtract(origin));
            return toMonster.lengthSqr() <= 1.0e-4 || look.dot(toMonster.normalize()) >= halfAngleCos;
        }, AreaVfxStyles.SPLASH, monster -> {
            Vec3 toMonster = horizontal(monster.position().subtract(origin));
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, altar, monster, damage, DamageType.MAGIC);
            push(monster, toMonster, knockback, 0.35);
            return damageOutcome(result);
        });
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.4f);
    }

    /** 도약하며 주위를 밀어내고 체력을 회복합니다. */
    private static void castDemonWings(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double radius = reach(state, altar, "radius", 4.0);
        double damage = ability(altar, "damage", 23.0) * state.damageMultiplier();
        double knockback = ability(altar, "knockback", 0.7);
        double leapPower = ability(altar, "leapPower", 1.0);

        Vec3 origin = player.position();
        applyArea(altar, lane, origin, radius, ignored -> true, AreaVfxStyles.PULSE, monster -> {
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, altar, monster, damage, DamageType.MAGIC);
            push(monster, horizontal(monster.position().subtract(origin)), knockback, 0.4);
            return damageOutcome(result);
        });
        state.heal(state.maxHealth() * ability(altar, "healRatio", 0.10));

        Vec3 look = horizontal(player.getLookAngle());
        player.setDeltaMovement(look.x * leapPower, 0.62, look.z * leapPower);
        player.hurtMarked = true;
        player.resetFallDistance();

        sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 0.8f);
    }

    /**
     * 전방으로 돌진해 부딪힌 적을 띄우고 기절시킵니다.
     *
     * <p>돌진은 텔레포트로 처리합니다. 속도로 밀면 서버 틱 동안 충돌 판정이 새기 때문에, 경로를
     * 샘플링해 맞은 적을 모두 잡아낸 뒤 끝점으로 옮기는 쪽이 결과가 일정합니다.
     */
    private static void castSkyBreaker(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double distance = reach(state, altar, "dashDistance", 8.0);
        double hitRadius = reach(state, altar, "hitRadius", 2.0);
        double damage = ability(altar, "damage", 68.0) * state.damageMultiplier();
        double lift = ability(altar, "liftPower", 0.8);
        int stunTicks = (int) Math.max(1.0, ability(altar, "stunTicks", 40.0));

        Vec3 start = player.position();
        Vec3 look = horizontal(player.getLookAngle());
        Vec3 end = resolveDashEnd(player, lane, start, look, distance);
        double travelled = start.distanceTo(end);

        applyArea(altar, lane, start.lerp(end, 0.5), travelled / 2.0 + hitRadius,
                monster -> distanceToSegment(monster.position(), start, end) <= hitRadius,
                AreaVfxStyles.SPLASH,
                monster -> {
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, altar, monster, damage, DamageType.PHYSICAL);
            monster.setDeltaMovement(monster.getDeltaMovement().x, lift, monster.getDeltaMovement().z);
            monster.hurtMarked = true;
            // 기절: 이동·공격 속도·공격력을 모두 100% 깎아 아무것도 못 하게 만듭니다.
            monster.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 1.0, stunTicks);
            monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, 1.0, stunTicks);
            monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, 1.0, stunTicks);
            return damageOutcome(result);
        });
        player.teleportTo(end.x, end.y, end.z);
        player.resetFallDistance();
        sound(player, SoundEvents.RAVAGER_ROAR, 1.0f, 0.9f);
    }

    /**
     * 돌진이 실제로 멈춰야 하는 지점을 구합니다.
     *
     * <p>텔레포트는 충돌을 무시하므로 그냥 목표 지점으로 옮기면 아레나 배리어를 뚫고 맵 밖으로
     * 나가 떨어집니다. 그래서 두 겹으로 막습니다 — 먼저 블록에 레이캐스트해 벽 앞에서 멈추고,
     * 그다음 실제로 설 수 있는 자리인지(벽 속이 아닌지, 발밑이 허공이 아닌지) 확인합니다.
     * 레인 경계는 보지 않습니다 — 마왕은 어디로든 돌진할 수 있습니다.
     */
    private static Vec3 resolveDashEnd(ServerPlayer player, PlayerLane lane, Vec3 start, Vec3 look, double distance) {
        Vec3 from = start.add(0.0, 0.6, 0.0);
        Vec3 to = from.add(look.scale(distance));
        HitResult clip = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = clip.getType() == HitResult.Type.MISS
                ? to
                : clip.getLocation().subtract(look.scale(WALL_STANDOFF));

        Vec3 end = new Vec3(stop.x, start.y, stop.z);
        // 벽이 코앞이면 뒤로 밀려 시작점보다 뒤에 설 수 있으므로 제자리로 되돌립니다.
        if (horizontal(end.subtract(start)).dot(look) <= 0.0) {
            end = start;
        }
        return DemonLordService.safeLanding(player, start, end);
    }

    /**
     * 먼저 공중으로 솟아오르고, 정점에 도달하면 그때 포격합니다.
     *
     * <p>발사는 {@code castDelayTicks} 만큼 미뤄 예약해 두고 {@link #tickPending} 이 처리합니다.
     * 조준은 시전 시점이 아니라 <b>발사 시점의 시선</b>을 씁니다. 솟아오른 뒤 아래를 내려다보며
     * 조준하는 게 이 스킬의 그림이기 때문입니다.
     */
    private static void castArcaneBombardment(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar, long gameTime) {
        player.setDeltaMovement(player.getDeltaMovement().x, ability(altar, "jumpPower", 0.9), player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.resetFallDistance();

        int delay = (int) Math.max(1.0, ability(altar, "castDelayTicks", 10.0));
        state.queueBombardment(altar.type(), gameTime + delay);

        DemonLordVfx.show(altar, lane, player.position(), 2.0, AreaVfxStyles.BUFF);
        sound(player, SoundEvents.ENDER_DRAGON_FLAP, 0.9f, 1.4f);
    }

    /** Runs the delayed and lasting parts of skills. Called once per lane tick. */
    public static void tickPending(ServerPlayer player, PlayerLane lane, DemonLordState state, long gameTime) {
        tickHellfireZone(player, lane, state, gameTime);
        if (!state.bombardmentReady(gameTime)) {
            return;
        }
        TowerType altar = state.consumeBombardment();
        if (altar == null) {
            return;
        }
        double blastRadius = reach(state, altar, "blastRadius", 4.0);
        double damage = ability(altar, "damage", 53.0) * state.damageMultiplier();
        double range = reach(state, altar, "projectileRange", 18.0);

        // 실제 투사체 엔티티 대신 레이캐스트로 착탄 지점을 구합니다. 결과는 같고, 라운드마다
        // 수십 발이 날아다니는 엔티티를 만들지 않아도 됩니다.
        Vec3 eye = player.getEyePosition();
        Vec3 target = eye.add(player.getLookAngle().scale(range));
        HitResult clip = player.level().clip(new ClipContext(
                eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 impact = clip.getType() == HitResult.Type.MISS ? target : clip.getLocation();

        DemonLordSkillTower sourceAltar = DemonLordService.altarFor(lane, player.getUUID(), altar);
        applyArea(sourceAltar, lane, impact, blastRadius, ignored -> true, AreaVfxStyles.PULSE,
                monster -> damageOutcome(DemonLordService.dealDamage(
                        player, lane, sourceAltar, monster, damage, DamageType.MAGIC)));
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.1f);
    }

    /**
     * Pulses the 지옥불 낙인 zone and draws its outline.
     *
     * <p>The zone lives on the player's state rather than as an entity, so it costs one bounds check
     * per monster per pulse and disappears on its own when the round ends.
     */
    private static void tickHellfireZone(ServerPlayer player, PlayerLane lane, DemonLordState state, long gameTime) {
        DemonLordState.HellfireZone zone = state.zone();
        if (zone == null) {
            return;
        }
        if (gameTime >= zone.expiryTick()) {
            state.clearZone();
            return;
        }
        if (gameTime < zone.nextPulseTick()) {
            return;
        }
        DemonLordSkillTower sourceAltar = DemonLordService.altarFor(
                lane, player.getUUID(), zone.altarType());
        applyArea(sourceAltar, lane, zone.centre(), zone.radius(), ignored -> true,
                AreaVfxStyles.DEBUFF, monster -> {
            Tower.DamageResult result = DemonLordService.dealDamage(
                    player, lane, sourceAltar, monster, zone.damage(), DamageType.MAGIC);
            monster.applyTimedEffect(
                    TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                    zone.damageTakenBonus(),
                    zone.tickIntervalTicks() * 2
            );
            return damageOutcome(result);
        });
        state.placeZone(new DemonLordState.HellfireZone(
                zone.altarType(),
                zone.centre(),
                zone.radius(),
                zone.damage(),
                zone.damageTakenBonus(),
                zone.tickIntervalTicks(),
                zone.expiryTick(),
                gameTime + zone.tickIntervalTicks()
        ));
    }

    /** 최대 체력 비례 방어막을 두릅니다. */
    private static void castDemonBarrier(ServerPlayer player, PlayerLane lane, DemonLordState state,
            DemonLordSkillTower altar) {
        double shield = state.maxHealth() * ability(altar, "shieldRatio", 0.25);
        int duration = (int) Math.max(1.0, ability(altar, "shieldDurationTicks", 160.0));
        state.grantShield(shield, player.level().getGameTime() + duration);
        DemonLordVfx.show(altar, lane, player.position(), 2.0, AreaVfxStyles.BUFF);
        sound(player, SoundEvents.TOTEM_USE, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------- internals

    private static double ability(DemonLordSkillTower altar, String key, double fallback) {
        return ability(altar.type(), key, fallback);
    }

    /**
     * 거리 계열 수치입니다. 스킬 범위 스탯이 여기에만 곱해집니다.
     *
     * <p>사거리·반경·돌진 거리처럼 "얼마나 멀리 닿는가"만 늘려야 하므로, 피해나 지속 시간이
     * 딸려 올라가지 않도록 통과 지점을 하나로 모아 두었습니다.
     */
    private static double reach(DemonLordState state, DemonLordSkillTower altar, String key, double fallback) {
        return reach(state, altar.type(), key, fallback);
    }

    private static double reach(DemonLordState state, TowerType altar, String key, double fallback) {
        double base = ability(altar, key, fallback);
        return state == null ? base : base * state.skillRangeMultiplier();
    }

    private static double ability(TowerType altar, String key, double fallback) {
        return TowerBalanceRuntime.ability(altar.id(), key, fallback);
    }

    private static AreaEffectResult<SemionMonsterEntity> applyArea(
            DemonLordSkillTower altar,
            PlayerLane lane,
            Vec3 center,
            double radius,
            Predicate<SemionMonsterEntity> filter,
            ResourceLocation style,
            AreaEffectAction<SemionMonsterEntity> action
    ) {
        var source = altar == null ? null : altar.entity(lane);
        if (source == null) {
            return AreaEffectResult.empty();
        }
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "demon_lord/" + altar.skill().key()),
                source,
                center,
                radius,
                Set.of(),
                filter,
                AreaVfxSpec.onTrigger(style)
        );
        return SemionTdApi.areaEffects().applyToMonsters(request, action);
    }

    private static AreaEffectOutcome damageOutcome(Tower.DamageResult result) {
        if (result.dealtDamage() <= 0.0) {
            return AreaEffectOutcome.UNCHANGED;
        }
        return result.killed() ? AreaEffectOutcome.KILLED : AreaEffectOutcome.APPLIED;
    }

    static double soulDrainHealing(double dealtDamage, double maxHealth, double ratio, double capRatio) {
        return Math.min(Math.max(0.0, dealtDamage) * ratio, Math.max(0.0, maxHealth) * capRatio);
    }

    /** Live monsters of this lane whose entity sits within {@code radius} of {@code center}. */
    private static List<SemionMonsterEntity> monstersNear(PlayerLane lane, Vec3 center, double radius) {
        List<SemionMonsterEntity> found = new ArrayList<>();
        double radiusSqr = radius * radius;
        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            if (entity.position().distanceToSqr(center) <= radiusSqr) {
                found.add(entity);
            }
        }
        return found;
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x, 0.0, vector.z);
        return flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private static void push(SemionMonsterEntity monster, Vec3 direction, double strength, double lift) {
        Vec3 away = horizontal(direction).scale(strength);
        monster.setDeltaMovement(away.x, lift, away.z);
        monster.hurtMarked = true;
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, volume, pitch);
        }
    }
}
