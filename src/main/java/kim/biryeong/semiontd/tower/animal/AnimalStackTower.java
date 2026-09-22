package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import java.util.List;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

abstract class AnimalStackTower extends EntityBackedTower {
    static final String FRIENDSHIP = "job_animal_towers_s";
    static final String LEADER = "job_animal_towers_g1";
    static final String PACK = "job_animal_towers_g2";
    static final String UNION = "job_animal_towers_p";
    private static final TowerDataKey<Integer> PACK_HITS = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "animal_pack_hits"), Integer.class);
    private int currentStacks;
    private int realStacks;
    private boolean leaderAuraActive;
    private boolean livingLeaderExists;
    private List<String> unionLeaders = List.of();

    protected AnimalStackTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    protected AnimalStackTower(
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
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        refreshAnimalStacks(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        refreshAnimalStacks(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        refreshAnimalStacks(lane);
        super.tick(lane);
    }

    protected final int currentStacks() {
        return currentStacks;
    }

    protected final boolean atMaxStacks() {
        return currentStacks >= maxStacks();
    }

    protected final boolean hasLeaderAura() {
        return leaderAuraActive;
    }

    protected final boolean isLeader() {
        return type().id().equals(leaderType().id());
    }

    protected final double leaderValue(String key) {
        return TowerBalanceRuntime.ability(leaderType().id(), key);
    }

    protected final double auraValue(TowerType leader, String key) {
        if (unionLeaders.contains(leader.id())) {
            return TowerBalanceRuntime.ability(leader.id(), key)
                    * augmentSnapshot().parameter(UNION, "auraMultiplier", 2.0);
        }
        return unionLeaders.isEmpty() && hasLeaderAura() && leaderType().id().equals(leader.id())
                ? TowerBalanceRuntime.ability(leader.id(), key) : 0.0;
    }

    protected final double animalHealthMultiplier() {
        double bonus = auraValue(AnimalTowers.T4_PIG_LEADER_TOWER, "leaderMaxHealthBonus");
        if (isLeader() && augmentSnapshot().has(LEADER)) {
            bonus += augmentSnapshot().parameter(LEADER, "maxHealthBonus", 1.0);
        }
        return 1.0 + bonus;
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return super.builderCurrentMaxHealth() * animalHealthMultiplier();
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity entity, SemionMonsterEntity target, double damage) {
        double bonus = auraValue(AnimalTowers.T4_RABBIT_LEADER_TOWER, "leaderDamageBonus");
        if (isLeader() && augmentSnapshot().has(LEADER)) {
            bonus += augmentSnapshot().parameter(LEADER, "damageBonus", .60);
        }
        if (!(this instanceof FoxTower) && target != null && target.getMaxHealth() > 0
                && target.getHealth() / target.getMaxHealth()
                <= auraValue(AnimalTowers.T4_FOX_LEADER_TOWER, "leaderExecuteThresholdBonus")) {
            bonus += auraValue(AnimalTowers.T4_FOX_LEADER_TOWER, "leaderExecuteDamageBonus");
        }
        return damage * (1.0 + bonus);
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity entity, DamageSource source, double damage) {
        return damage * (1.0 - Math.min(.95,
                auraValue(AnimalTowers.T4_PIG_LEADER_TOWER, "leaderDamageReductionBonus")));
    }

    @Override
    public int adjustAttackInterval(int interval) {
        return Math.max(1, interval - (int) Math.round(
                auraValue(AnimalTowers.T4_WOLF_LEADER_TOWER, "leaderAttackIntervalReductionTicks")));
    }

    @Override
    public double adjustAttackRange(double range) {
        return range + auraValue(AnimalTowers.T4_RABBIT_LEADER_TOWER, "leaderRangeBonus");
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int round) {
        super.onWaveStarted(lane, round);
        setData(PACK_HITS, 0);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        setData(PACK_HITS, 0);
        super.resetForRound(lane);
    }

    @Override
    public void onAttackResolved(SemionTowerEntity entity, SemionMonsterEntity target, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        super.onAttackResolved(entity, target, attempted, outgoing, dealt, killed);
        if (!AugmentCombat.allowsTriggers() || dealt <= 0.0 || entity == null || target == null) return;
        if (isLeader() && augmentSnapshot().has(LEADER)) {
            var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(this, "augment_leader_splash"),
                    entity, target.position(), augmentSnapshot().parameter(LEADER, "radius", 2.5),
                    java.util.Set.of(), null, AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH));
            AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.apply(this, entity, request,
                    other -> attempted * augmentSnapshot().parameter(LEADER, "damageRatio", .60), true));
        }
        PlayerLane lane = attachedLane();
        if (!augmentSnapshot().has(PACK) || lane == null) return;
        List<AnimalStackTower> family = lane.towers().stream()
                .filter(AnimalStackTower.class::isInstance).map(AnimalStackTower.class::cast)
                .filter(other -> ownerPlayer().equals(other.ownerPlayer()) && isStackFamily(other)).toList();
        if (family.isEmpty()) return;
        AnimalStackTower counter = family.getFirst();
        int hits = counter.getDataOrDefault(PACK_HITS, 0) + 1;
        int every = Math.max(1, (int) augmentSnapshot().parameter(PACK, "everyAttacks", 5));
        counter.setData(PACK_HITS, hits % every);
        if (hits < every || !target.isAlive()) return;
        int remaining = (int) augmentSnapshot().parameter(PACK, "maxAllies", 3);
        for (AnimalStackTower other : family) {
            if (remaining <= 0) break;
            if (other == this || other.health() <= 0 || other.entityId().isEmpty()) continue;
            if (!(lane.arenaWorld().getEntity(other.entityId().getAsInt()) instanceof SemionTowerEntity ally)
                    || !ally.isAlive() || !ally.isValidAttackTarget(target)
                    || ally.distanceToSqr(target) > ally.attackRange() * ally.attackRange()
                    || kim.biryeong.semiontd.tower.succubus.SuccubusDreams.isAsleep(ally)) continue;
            AugmentCombat.additionalAttack(ally, target, augmentSnapshot().parameter(PACK, "damageRatio", 1.0));
            remaining--;
        }
    }

    @Override
    public final boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        if (option == null || !option.targetType().id().equals(leaderType().id())) {
            return true;
        }
        return type().id().equals(leaderBaseType().id()) && realStacks >= maxStacks() && !hasOtherLivingLeader(lane);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("무리 스택 " + currentStacks + "/" + maxStacks());
        if (realStacks < currentStacks) lines.add("종을 뛰어넘는 우정 +1 (승급 조건 제외)");
        if (!unionLeaders.isEmpty()) lines.add("우두머리 연합: " + unionLeaders.size() + "종 오라 공유");
        if (augmentSnapshot().has(PACK) && attachedLane() != null) {
            Tower counter = attachedLane().towers().stream()
                    .filter(other -> ownerPlayer().equals(other.ownerPlayer()) && isStackFamily(other))
                    .findFirst().orElse(this);
            lines.add("무리 공격 충전 " + counter.getDataOrDefault(PACK_HITS, 0) + "/"
                    + (int) augmentSnapshot().parameter(PACK, "everyAttacks", 5));
        }
        if (type().id().equals(leaderBaseType().id())) {
            lines.add("우두머리 승급: 최대 무리 " + (realStacks >= maxStacks() ? "충족" : "미충족")
                    + ", 계열 우두머리 " + (livingLeaderExists ? "존재" : "없음"));
        }
        if (isLeader()) {
            lines.add("우두머리 오라 " + (health() > 0.0 && atMaxStacks() ? "활성" : "비활성"));
        } else if (leaderAuraActive) {
            lines.add("우두머리 오라 적용 중");
        }
        return lines;
    }

    protected final int refreshStacks(PlayerLane lane) {
        int previousStacks = currentStacks;
        currentStacks = countMatchingTowers(lane);
        onStacksChanged(lane, previousStacks, currentStacks);
        return currentStacks;
    }

    protected void onStacksChanged(PlayerLane lane, int previousStacks, int currentStacks) {
    }

    protected void onLeaderAuraChanged(PlayerLane lane, boolean previousActive, boolean currentActive) {
    }

    protected abstract boolean isStackFamily(Tower tower);

    protected abstract int maxStacks();

    protected abstract TowerType leaderBaseType();

    protected abstract TowerType leaderType();

    private int countMatchingTowers(PlayerLane lane) {
        if (lane == null) {
            return 0;
        }
        long count = lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(this::isStackFamily)
                .count();
        realStacks = Math.min(maxStacks(), (int) count);
        boolean friend = augmentSnapshot().has(FRIENDSHIP) && lane.towers().stream()
                .anyMatch(other -> other instanceof AnimalStackTower && ownerPlayer().equals(other.ownerPlayer())
                        && !isStackFamily(other));
        return Math.min(maxStacks(), realStacks + (friend ? 1 : 0));
    }

    static void refreshAnimalStacks(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof AnimalStackTower animalTower) {
                animalTower.refreshStacks(lane);
            }
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof AnimalStackTower animalTower) {
                animalTower.refreshLeaderState(lane);
            }
        }
    }

    private void refreshLeaderState(PlayerLane lane) {
        double previousMaxHealth = currentMaxHealth();
        List<String> previousUnion = unionLeaders;
        List<String> livingLeaders = lane.towers().stream()
                .filter(AnimalStackTower.class::isInstance).map(AnimalStackTower.class::cast)
                .filter(other -> ownerPlayer().equals(other.ownerPlayer()) && other.health() > 0 && other.isLeader())
                .map(other -> other.type().id()).distinct().sorted().toList();
        unionLeaders = augmentSnapshot().has(UNION)
                && livingLeaders.size() >= (int) augmentSnapshot().parameter(UNION, "requiredLeaderKinds", 3)
                ? livingLeaders : List.of();
        boolean previousLeaderExists = livingLeaderExists;
        boolean previousAuraActive = leaderAuraActive;
        livingLeaderExists = hasOtherLivingLeader(lane);
        leaderAuraActive = !isLeader() && health() > 0.0 && findActiveLeader(lane) != null;
        if (previousAuraActive != leaderAuraActive && previousUnion.isEmpty() && unionLeaders.isEmpty()) {
            onLeaderAuraChanged(lane, previousAuraActive, leaderAuraActive);
        }
        if (!previousUnion.equals(unionLeaders)) {
            syncHealth(health() * currentMaxHealth() / Math.max(1.0, previousMaxHealth));
        }
        if (previousLeaderExists != livingLeaderExists || previousAuraActive != leaderAuraActive
                || !previousUnion.equals(unionLeaders)) {
            onStateChanged(lane);
        }
    }

    private boolean hasOtherLivingLeader(PlayerLane lane) {
        return lane != null && lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .anyMatch(tower -> tower.health() > 0.0 && tower.type().id().equals(leaderType().id()));
    }

    private AnimalStackTower findActiveLeader(PlayerLane lane) {
        if (lane == null) {
            return null;
        }
        return lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> tower instanceof AnimalStackTower)
                .map(tower -> (AnimalStackTower) tower)
                .filter(tower -> tower.health() > 0.0)
                .filter(tower -> tower.type().id().equals(leaderType().id()))
                .filter(AnimalStackTower::atMaxStacks)
                .filter(tower -> withinLeaderAura(lane, tower))
                .findFirst()
                .orElse(null);
    }

    private boolean withinLeaderAura(PlayerLane lane, AnimalStackTower leader) {
        double radius = Math.max(0.0, leaderValue("leaderAuraRadius"));
        return center(lane).distanceToSqr(leader.center(lane)) <= radius * radius;
    }

    private Vec3 center(PlayerLane lane) {
        if (lane != null && lane.arenaWorld() != null) {
            var currentEntity = entityId().isPresent() ? lane.arenaWorld().getEntity(entityId().getAsInt()) : null;
            if (currentEntity instanceof SemionTowerEntity towerEntity) {
                return towerEntity.position().add(0.0, towerEntity.getBbHeight() * 0.5, 0.0);
            }
        }
        return new Vec3(position().x() + 0.5, position().y() + 1.0, position().z() + 0.5);
    }
}
