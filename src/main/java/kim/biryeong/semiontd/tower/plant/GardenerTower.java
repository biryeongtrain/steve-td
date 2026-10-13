package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 정원사. 식물 빌더의 단일 엘리트 타워입니다.
 *
 * <p>한 명에 하나만 세울 수 있고 티어가 오르지 않습니다. 대신 스킬 세 개를 각각 3단계까지 다이아로 강화합니다.
 * 지형이 필요 없고, 서 있는 곳과 상관없이 잔디(최대 체력 성장)·사암(가시 반사)·회백토(사거리·공속·피해 성장) 효과를
 * 모두 받습니다. 균사는 적에게 거는 약화라 받지 않습니다. 개화 피해는 잔디·사암·회백토 칸을 모두 셉니다.
 *
 * <ul>
 *   <li>평타(러커식): 대상까지, 그리고 그 너머 사거리 끝까지 일직선으로 가시가 솟아 줄의 적 모두에게 피해를 줍니다.</li>
 *   <li>꽃밭 치유: 가장 다친 아군 타워 자리에 회복 장판을 깔아, 그 안의 아군 타워를 지속해서 치유합니다.</li>
 *   <li>지배: 사거리 안에서 체력이 가장 높은 적을 지배합니다. 지배당한 적은 멈춰 서서 제 편을 공격합니다.</li>
 *   <li>생기 흡수: 주변 적의 체력을 빨아들여 다친 아군 타워에게 나눠 줍니다.</li>
 * </ul>
 * 수치는 {@code tower_balance.json}의 {@code plant_gardener} 능력치에서 {@code <키>_<단계>} 이름으로 읽습니다.
 */
public class GardenerTower extends PlantCombatTower {
    public static final int MAX_SKILL_LEVEL = 3;

    public enum Skill {
        HEAL("heal", "꽃밭 치유", Items.PINK_PETALS),
        DOMINATE("dominate", "지배", Items.WITHER_ROSE),
        DRAIN("drain", "생기 흡수", Items.SUNFLOWER);

        private final String key;
        private final String displayName;
        private final Item icon;

        Skill(String key, String displayName, Item icon) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }

        public Item icon() {
            return icon;
        }
    }

    public enum UpgradeResult {
        SUCCESS("스킬을 강화했습니다."),
        MAX_LEVEL("이미 최고 단계입니다."),
        NOT_ENOUGH_DIAMOND("다이아가 부족합니다.");

        private final String message;

        UpgradeResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** 꽃밭 치유 회복을 다른 회복과 따로 세는 출처 id. 같은 정원사의 장판은 겹치지 않습니다. */
    private static final Identifier HEAL_SOURCE = Identifier.fromNamespaceAndPath("semion-td", "gardener_heal_field");

    private final int[] levels = {1, 1, 1};
    private long nextHealTick = Long.MIN_VALUE;
    private long nextDominateTick = Long.MIN_VALUE;
    private long nextDrainTick = Long.MIN_VALUE;
    private long nextPulseTick = Long.MIN_VALUE;

    /** 깔려 있는 회복 장판. */
    private Vec3 healCentre;
    private long healUntil;

    /** 지배 중인 적과 끝나는 틱. */
    private SemionMonsterEntity dominated;
    private long dominateUntil;

    public GardenerTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public GardenerTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    // ------------------------------------------------------------------ 지형 효과

    /** 균사를 뺀 모든 지형 효과를 받습니다. 균사는 적에게 거는 약화라 정원사가 받으면 자해입니다. */
    @Override
    protected boolean standsOn(PlantSoil soil) {
        return soil != null && soil != PlantSoil.MYCELIUM;
    }

    /** 어디에 서 있든 잔디·회백토 성장 스택이 라운드마다 쌓입니다. */
    @Override
    protected boolean growsThisRound() {
        return true;
    }

    /** 개화: 잔디·사암·회백토 칸을 모두 세어 칸당 피해를 더합니다(상한은 다른 식물과 같습니다). */
    @Override
    public double bloomBonus() {
        int tiles = PlantSoilStates.count(ownerPlayer(), PlantSoil.MEADOW)
                + PlantSoilStates.count(ownerPlayer(), PlantSoil.DESERT)
                + PlantSoilStates.count(ownerPlayer(), PlantSoil.PODZOL);
        double bonus = tiles * global("bloomDamagePerTile");
        return Math.max(0.0, Math.min(global("bloomDamageCap"), bonus));
    }

    // ------------------------------------------------------------------ 스킬 단계

    public int level(Skill skill) {
        return levels[skill.ordinal()];
    }

    /** 다음 단계로 올리는 값(다이아). 최고 단계면 0입니다. */
    public long upgradeCost(Skill skill) {
        int level = level(skill);
        if (level >= MAX_SKILL_LEVEL) {
            return 0L;
        }
        return Math.max(0L, Math.round(value("upgradeCost", level + 1, level == 1 ? 150.0 : 250.0)));
    }

    public UpgradeResult upgrade(Skill skill, PlayerEconomy economy) {
        if (level(skill) >= MAX_SKILL_LEVEL) {
            return UpgradeResult.MAX_LEVEL;
        }
        long cost = upgradeCost(skill);
        if (economy == null || !economy.spendDiamond(cost)) {
            return UpgradeResult.NOT_ENOUGH_DIAMOND;
        }
        levels[skill.ordinal()]++;
        addPaidMineralCost(cost);
        return UpgradeResult.SUCCESS;
    }

    /** {@code <키>_<단계>} 능력치. 없으면 {@code fallback}. */
    private double value(String key, int level, double fallback) {
        return TowerBalanceRuntime.ability(type().id(), key + "_" + level, fallback);
    }

    private double value(Skill skill, String key, double fallback) {
        return value(key, level(skill), fallback);
    }

    private double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(type().id(), key, fallback);
    }

    private double skillRange() {
        return Math.max(type().range(), ability("skillRange", 10.0));
    }

    // ------------------------------------------------------------------ 스킬 틱

    @Override
    protected boolean execute(PlayerLane lane) {
        SemionTowerEntity source = towerEntity(lane).orElse(null);
        if (source == null || lane.arenaWorld() == null || health() <= 0.0) {
            return true;
        }
        long now = lane.arenaWorld().getGameTime();
        boolean pulse = now >= nextPulseTick;
        if (pulse) {
            nextPulseTick = now + 20;
            tickHealField(lane, now);
            tickDomination(lane, source, now);
        }
        if (now >= nextHealTick) {
            Tower hurt = mostHurtAlly(lane, source.position(), skillRange());
            if (hurt != null) {
                castHealField(lane, source, hurt, now);
                nextHealTick = now + Math.max(40, (int) value(Skill.HEAL, "healCooldownTicks", 300.0));
            }
        }
        if (now >= nextDominateTick && (dominated == null || now >= dominateUntil)) {
            SemionMonsterEntity strongest = strongestMonster(lane, source.position(), skillRange());
            if (strongest != null) {
                castDomination(source, strongest, now);
                nextDominateTick = now + Math.max(40, (int) value(Skill.DOMINATE, "dominateCooldownTicks", 400.0));
            }
        }
        if (now >= nextDrainTick) {
            double radius = value(Skill.DRAIN, "drainRadius", 4.0);
            List<SemionMonsterEntity> victims = monstersNear(lane, source.position(), radius);
            if (!victims.isEmpty()) {
                castDrain(lane, source, victims, radius);
                nextDrainTick = now + Math.max(40, (int) value(Skill.DRAIN, "drainCooldownTicks", 200.0));
            }
        }
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return 2;
    }

    // ---- 평타: 러커식 일직선 가시

    /**
     * 대상을 친 뒤, 정원사에서 대상 쪽으로 사거리 끝까지 일직선으로 가시가 솟아 그 줄의 다른 적에게 평타 피해의
     * {@code lineSplashRatio}만큼 줍니다(대상은 이미 맞았으니 뺍니다).
     */
    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        super.onAttackResolved(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (towerEntity == null || target == null) {
            return;
        }
        PlayerLane lane = attachedLane();
        Vec3 start = towerEntity.position();
        Vec3 flat = new Vec3(target.getX() - start.x, 0.0, target.getZ() - start.z);
        Vec3 direction = flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        double length = Math.max(flat.length(), towerEntity.attackRange());
        Vec3 end = start.add(direction.scale(length));
        double halfWidth = ability("lineWidth", 1.0) / 2.0 + 0.4;
        double ratio = ability("lineSplashRatio", 0.6);
        if (lane != null && ratio > 0.0 && resolvedOutgoingDamage > 0.0) {
            for (SemionMonsterEntity monster : monstersNear(lane, start.lerp(end, 0.5), length / 2.0 + halfWidth)) {
                if (monster == target || distanceToSegment(monster.position(), start, end) > halfWidth) {
                    continue;
                }
                double damage = resolvedOutgoingDamage * ratio;
                if (damageResolvedTargetResult(towerEntity, monster, damage, DamageType.MAGIC).killed()) {
                    onKill(towerEntity, monster, damage);
                }
            }
        }
        if (towerEntity.level() instanceof ServerLevel level) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.thornLine(
                    kim.biryeong.semiontd.vfx.DisplayShapes.yawOf(direction.x, direction.z), length,
                    PlantDisplayVfx.seed(level)), start);
        }
    }

    // ---- 꽃밭 치유

    private void castHealField(PlayerLane lane, SemionTowerEntity source, Tower hurt, long now) {
        healCentre = towerPosition(lane, hurt);
        if (healCentre == null) {
            return;
        }
        int duration = Math.max(20, (int) value(Skill.HEAL, "healDurationTicks", 100.0));
        healUntil = now + duration;
        source.playAnimation(SemionAnimationState.SKILL);
        tickHealField(lane, now);
        if (source.level() instanceof ServerLevel level) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.healField(value(Skill.HEAL, "healRadius", 3.0), duration,
                    PlantDisplayVfx.seed(level)), healCentre);
            level.playSound(null, healCentre.x, healCentre.y, healCentre.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 1.2f, 1.2f);
        }
    }

    /** 장판이 깔려 있는 동안 1초마다 그 안의 아군 타워에게 초당 회복을 2초씩 갱신합니다. */
    private void tickHealField(PlayerLane lane, long now) {
        if (healCentre == null || now >= healUntil) {
            healCentre = null;
            return;
        }
        double radius = value(Skill.HEAL, "healRadius", 3.0);
        double ratio = value(Skill.HEAL, "healPerSecond", 0.02);
        for (Tower tower : List.copyOf(lane.towers())) {
            SemionTowerEntity entity = towerEntityOf(lane, tower);
            if (entity == null || tower.health() <= 0.0
                    || entity.position().distanceToSqr(healCentre) > radius * radius) {
                continue;
            }
            entity.refreshTimedEffect(TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND, HEAL_SOURCE,
                    tower.currentMaxHealth() * ratio, 40);
        }
    }

    // ---- 지배

    private void castDomination(SemionTowerEntity source, SemionMonsterEntity target, long now) {
        int duration = Math.max(20, (int) value(Skill.DOMINATE, "dominateDurationTicks", 60.0));
        dominated = target;
        dominateUntil = now + duration;
        // 지배당한 적은 라인에 남은 적 수에서 빠집니다. 나머지가 모두 쓰러지면 이 적을 기다리지 않고 라인이 정리됩니다.
        if (target.runtimeMonster() != null) {
            target.runtimeMonster().setExcludedFromLaneCount(true);
        }
        // 편을 바꿉니다: 원래 편 몹이 공격 대상으로 고를 수 있고, 아군 타워는 더 이상 노리지 않습니다.
        target.setDominatedFor(laneId());
        // 지배당한 적은 제 발로 움직이지도, 원래 편을 위해 싸우지도 않습니다.
        target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0, duration);
        target.setTarget(null);
        source.playAnimation(SemionAnimationState.SKILL);
        if (source.level() instanceof ServerLevel level) {
            PlantDisplayVfx.follow(PlantDisplayVfx.dominationCrown(duration, PlantDisplayVfx.seed(level)), target);
            Vec3 from = source.position();
            Vector3f to = new Vector3f((float) (target.getX() - from.x), (float) (target.getY() - from.y),
                    (float) (target.getZ() - from.z));
            PlantDisplayVfx.play(level, PlantDisplayVfx.dominationLink(to, PlantDisplayVfx.seed(level)), from);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO,
                    SoundSource.HOSTILE, 1.0f, 1.3f);
        }
    }

    /** 지배당한 적은 1초마다 주변의 제 편에게 정원사 공격력 × 비율의 피해를 줍니다. */
    private void tickDomination(PlayerLane lane, SemionTowerEntity source, long now) {
        if (dominated == null) {
            return;
        }
        if (now >= dominateUntil || !dominated.isAlive() || dominated.isRemoved()) {
            releaseDomination();
            return;
        }
        // 정원사가 미드(최종 방어)로 옮겨 가면 지배당한 적도 곁으로 따라갑니다.
        if (dominated.position().distanceToSqr(source.position()) > 12.0 * 12.0 || source.deployedAtFinalDefense()
                && dominated.position().distanceToSqr(source.position()) > 4.0 * 4.0) {
            Vec3 beside = source.position().add(1.5, 0.0, 0.0);
            dominated.teleportTo(beside.x, beside.y, beside.z);
            dominated.setDeltaMovement(Vec3.ZERO);
        }
        double radius = value(Skill.DOMINATE, "dominatePulseRadius", 2.5);
        double ratio = value(Skill.DOMINATE, "dominatePulseRatio", 1.0);
        int hits = 0;
        for (SemionMonsterEntity monster : enemiesNear(source, dominated.position(), radius)) {
            if (monster == dominated) {
                continue;
            }
            strike(source, monster, ratio);
            hits++;
        }
        if (source.level() instanceof ServerLevel level) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.dominationPulse(radius, hits > 0, PlantDisplayVfx.seed(level)),
                    dominated.position());
        }
    }

    /**
     * 지배를 풉니다. 라인이 아직 싸우는 중이면 다시 라인의 적으로 돌아가고, 이미 라인이 정리됐거나(미드로 따라간 경우 등)
     * 라운드가 끝났다면 되돌아갈 곳이 없으므로 보상 없이 시들어 사라집니다.
     */
    private void releaseDomination() {
        releaseDomination(false);
    }

    private void releaseDomination(boolean roundOver) {
        SemionMonsterEntity released = dominated;
        dominated = null;
        if (released == null) {
            return;
        }
        released.setDominatedFor(-1);
        PlayerLane lane = attachedLane();
        boolean wither = roundOver || lane == null || lane.clearedThisRound() || deployedAtFinalDefenseNow(lane);
        if (released.runtimeMonster() != null) {
            released.runtimeMonster().setExcludedFromLaneCount(false);
            if (wither && released.isAlive()) {
                released.runtimeMonster().markRemoved();
                if (lane != null) {
                    lane.activeMonsters().remove(released.runtimeMonster());
                }
                released.discard();
            }
        }
    }

    private boolean deployedAtFinalDefenseNow(PlayerLane lane) {
        return towerEntity(lane).map(SemionTowerEntity::deployedAtFinalDefense).orElse(false);
    }

    /** 정원사가 팔리거나 쓰러지면 지배도 풀립니다. */
    @Override
    public void onRemoved(PlayerLane lane) {
        releaseDomination();
        super.onRemoved(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        releaseDomination(true);
        healCentre = null;
        super.resetForRound(lane);
    }

    // ---- 생기 흡수

    /** 주변 적에게 피해를 주고, 준 피해의 비율만큼을 다친 아군 타워(사거리 안)에게 고르게 나눠 치유합니다. */
    private void castDrain(PlayerLane lane, SemionTowerEntity source, List<SemionMonsterEntity> victims, double radius) {
        double ratio = value(Skill.DRAIN, "drainDamageRatio", 1.2);
        double dealt = 0.0;
        List<Vector3f> sources = new ArrayList<>();
        Vec3 at = source.position();
        for (SemionMonsterEntity monster : victims) {
            double before = monster.runtimeMonster() == null ? 0.0 : monster.runtimeMonster().health();
            strike(source, monster, ratio);
            double after = monster.runtimeMonster() == null ? 0.0 : Math.max(0.0, monster.runtimeMonster().health());
            dealt += Math.max(0.0, before - after);
            if (sources.size() < 6) {
                sources.add(new Vector3f((float) (monster.getX() - at.x), (float) (monster.getY() - at.y + 1.0),
                        (float) (monster.getZ() - at.z)));
            }
        }
        List<Tower> hurt = new ArrayList<>();
        for (Tower tower : List.copyOf(lane.towers())) {
            SemionTowerEntity entity = towerEntityOf(lane, tower);
            if (entity != null && tower.health() > 0.0 && tower.health() < tower.currentMaxHealth()
                    && entity.position().distanceToSqr(at) <= skillRange() * skillRange()) {
                hurt.add(tower);
            }
        }
        double pool = dealt * value(Skill.DRAIN, "drainHealRatio", 0.5);
        List<Vector3f> targets = new ArrayList<>();
        if (pool > 0.0 && !hurt.isEmpty()) {
            double share = pool / hurt.size();
            for (Tower tower : hurt) {
                SemionTowerEntity entity = towerEntityOf(lane, tower);
                entity.healTarget(entity, share);
                if (targets.size() < 6) {
                    targets.add(new Vector3f((float) (entity.getX() - at.x), (float) (entity.getY() - at.y + 1.0),
                            (float) (entity.getZ() - at.z)));
                }
            }
        }
        source.playAnimation(SemionAnimationState.SKILL);
        if (source.level() instanceof ServerLevel level) {
            PlantDisplayVfx.play(level, PlantDisplayVfx.lifeDrain(radius, sources, targets, PlantDisplayVfx.seed(level)), at);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.2f, 1.1f);
        }
    }

    // ---- 공용

    /** 스킬 한 방: 지금 공격력(버프 포함) × 비율을 마법 피해로 넣습니다. 처치하면 {@code true}. */
    private boolean strike(SemionTowerEntity source, SemionMonsterEntity monster, double ratio) {
        double damage = modifyOutgoingDamage(source, monster, source.attackDamageAmount(monster) * ratio);
        if (damage <= 0.0) {
            return false;
        }
        Tower.DamageResult result = damageResolvedTargetResult(source, monster, damage, DamageType.MAGIC);
        if (result.killed()) {
            onKill(source, monster, damage);
        }
        return result.killed();
    }

    private static SemionTowerEntity towerEntityOf(PlayerLane lane, Tower tower) {
        if (!(tower instanceof EntityBackedTower backed) || backed.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity
                && !entity.isRemoved() ? entity : null;
    }

    private static Vec3 towerPosition(PlayerLane lane, Tower tower) {
        SemionTowerEntity entity = towerEntityOf(lane, tower);
        return entity == null ? null : entity.position();
    }

    /** 사거리 안에서 체력 비율이 가장 낮은(가장 다친) 아군 타워. 다친 타워가 없으면 {@code null}. */
    private Tower mostHurtAlly(PlayerLane lane, Vec3 centre, double range) {
        Tower best = null;
        double bestRatio = 0.999;
        for (Tower tower : List.copyOf(lane.towers())) {
            SemionTowerEntity entity = towerEntityOf(lane, tower);
            if (entity == null || tower.health() <= 0.0 || entity.position().distanceToSqr(centre) > range * range) {
                continue;
            }
            double ratio = tower.health() / Math.max(1.0, tower.currentMaxHealth());
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = tower;
            }
        }
        return best;
    }

    private static List<SemionMonsterEntity> monstersNear(PlayerLane lane, Vec3 centre, double radius) {
        List<SemionMonsterEntity> found = new ArrayList<>();
        double radiusSqr = radius * radius;
        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity
                    && !entity.isRemoved() && !entity.isStealthed() && !entity.isDominated()
                    && entity.position().distanceToSqr(centre) <= radiusSqr) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * 이 정원사 편을 노리는 적(월드 전체에서). 미드(최종 방어)에서는 다른 레인에서 온 적도 섞이므로 레인 목록 대신
     * 엔티티를 찾고, 적이 노리는 팀으로 편을 가립니다. 은신·지배당한 몹은 뺍니다.
     */
    private List<SemionMonsterEntity> enemiesNear(SemionTowerEntity source, Vec3 centre, double radius) {
        double radiusSqr = radius * radius;
        return source.level().getEntitiesOfClass(SemionMonsterEntity.class,
                new net.minecraft.world.phys.AABB(centre, centre).inflate(radius, 3.0, radius),
                entity -> entity.isAlive() && !entity.isRemoved() && !entity.isStealthed() && !entity.isDominated()
                        && entity.runtimeMonster() != null && entity.runtimeMonster().isAlive()
                        && teamId().equals(entity.runtimeMonster().targetTeam())
                        && entity.position().distanceToSqr(centre) <= radiusSqr);
    }

    /** 사거리 안에서 지금 체력이 가장 높은 적. */
    private static SemionMonsterEntity strongestMonster(PlayerLane lane, Vec3 centre, double range) {
        SemionMonsterEntity best = null;
        double bestHealth = 0.0;
        for (SemionMonsterEntity entity : monstersNear(lane, centre, range)) {
            double health = entity.runtimeMonster() == null ? 0.0 : entity.runtimeMonster().health();
            if (health > bestHealth) {
                bestHealth = health;
                best = entity;
            }
        }
        return best;
    }

    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        return kim.biryeong.semiontd.tower.area.LineTargets.distanceToSegment(point, from, to);
    }

    // ------------------------------------------------------------------ 정보

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>(super.runtimeDetailLines());
        lines.add("일직선 가시 · 줄의 적에게 평타의 " + pct(ability("lineSplashRatio", 0.6)));
        lines.add("꽃밭 치유 " + level(Skill.HEAL) + "단계 · 반경 " + number(value(Skill.HEAL, "healRadius", 3.0))
                + " · 초당 " + pct(value(Skill.HEAL, "healPerSecond", 0.02))
                + " · " + seconds(value(Skill.HEAL, "healDurationTicks", 100.0)) + "초");
        lines.add("지배 " + level(Skill.DOMINATE) + "단계 · " + seconds(value(Skill.DOMINATE, "dominateDurationTicks", 60.0))
                + "초 · 주변 피해 x" + number(value(Skill.DOMINATE, "dominatePulseRatio", 1.0))
                + (dominated != null ? " · 지배 중" : ""));
        lines.add("생기 흡수 " + level(Skill.DRAIN) + "단계 · 반경 " + number(value(Skill.DRAIN, "drainRadius", 4.0))
                + " · 피해 x" + number(value(Skill.DRAIN, "drainDamageRatio", 1.2))
                + " · 흡수 " + pct(value(Skill.DRAIN, "drainHealRatio", 0.5)));
        return List.copyOf(lines);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String pct(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    private static String seconds(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
