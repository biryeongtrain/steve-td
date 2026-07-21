package kim.biryeong.semiontd.entity.monster;

import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Monster {
    private static final double DEFAULT_PROGRESS_PER_TICK = 0.004;
    public static final double FINAL_DEFENSE_ATTACK_RANGE = 2.0;
    public static final double FINAL_DEFENSE_PROGRESS = 0.90;

    private final String id;
    private final TeamId targetTeam;
    private final int targetLaneId;
    private final Optional<UUID> ownerPlayer;
    private final Optional<TeamId> senderTeam;
    private String senderName;
    private double maxHealth;
    private final double armor;
    private final double resistance;
    private double attackDamage;
    private final AttackKind attackKind;
    private final DamageType damageType;
    private final String entityTypeId;
    private final String blockbenchModelId;
    private final MonsterDimensions dimensions;
    private final long mineralReward;
    private final SummonTier summonTier;
    private final List<SummonRole> summonRoles;
    private final double targetPriority;
    private final double movementSpeedMultiplier;
    private final double attackRange;
    private final int attackIntervalTicks;
    private double attackDamageMultiplier = 1.0;
    private double attackSpeedMultiplier = 1.0;
    private double health;
    private double laneProgress;
    private int minecraftEntityId = -1;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private MonsterState state = MonsterState.SPAWNING;
    private Optional<UUID> lastHitPlayerId = Optional.empty();
    private KillSourceKind lastHitSourceKind = KillSourceKind.UNKNOWN;
    private boolean rewardGranted;
    private boolean laneLeakRecorded;
    private int activeTicks;
    private int laneBreachTicks;
    private int survivalScalingIntervalTicks;
    private int survivalScalingStacks;
    private boolean finalDefenseCombat;
    private final Map<MonsterDataKey<?>, Object> data = new HashMap<>();

    public Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            long mineralReward
    ) {
        this(
                id,
                targetTeam,
                targetLaneId,
                ownerPlayer,
                senderTeam,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                null,
                DamageType.PHYSICAL,
                0,
                MonsterDimensions.DEFAULT,
                null,
                List.of(),
                mineralReward
        );
    }

    public Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            MonsterDimensions dimensions,
            long mineralReward
    ) {
        this(
                id,
                targetTeam,
                targetLaneId,
                ownerPlayer,
                senderTeam,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                DamageType.PHYSICAL,
                0,
                dimensions,
                null,
                List.of(),
                mineralReward
        );
    }

    public Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            long mineralReward
    ) {
        this(
                id,
                targetTeam,
                targetLaneId,
                ownerPlayer,
                senderTeam,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                DamageType.PHYSICAL,
                0,
                MonsterDimensions.DEFAULT,
                null,
                List.of(),
                mineralReward
        );
    }

    public Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            DamageType damageType,
            double resistance,
            SummonTier summonTier,
            List<SummonRole> summonRoles,
            long mineralReward
    ) {
        this(
                id,
                targetTeam,
                targetLaneId,
                ownerPlayer,
                senderTeam,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                damageType,
                resistance,
                MonsterDimensions.DEFAULT,
                summonTier,
                summonRoles,
                mineralReward
        );
    }

    public Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            DamageType damageType,
            double resistance,
            MonsterDimensions dimensions,
            SummonTier summonTier,
            List<SummonRole> summonRoles,
            long mineralReward
    ) {
        this(
                id,
                targetTeam,
                targetLaneId,
                ownerPlayer,
                senderTeam,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                damageType,
                resistance,
                dimensions,
                summonTier,
                summonRoles,
                mineralReward,
                0.0,
                WaveMonsterEntry.DEFAULT_MOVEMENT_SPEED_MULTIPLIER,
                WaveMonsterEntry.defaultAttackRange(attackKind),
                WaveMonsterEntry.DEFAULT_ATTACK_INTERVAL_TICKS
        );
    }

    private Monster(
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Optional<UUID> ownerPlayer,
            Optional<TeamId> senderTeam,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            DamageType damageType,
            double resistance,
            MonsterDimensions dimensions,
            SummonTier summonTier,
            List<SummonRole> summonRoles,
            long mineralReward,
            double targetPriority,
            double movementSpeedMultiplier,
            double attackRange,
            int attackIntervalTicks
    ) {
        this.id = id;
        this.targetTeam = targetTeam;
        this.targetLaneId = targetLaneId;
        this.ownerPlayer = ownerPlayer;
        this.senderTeam = senderTeam;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.resistance = Math.max(0, resistance);
        this.attackDamage = attackDamage;
        this.attackKind = attackKind;
        this.damageType = damageType == null ? DamageType.PHYSICAL : damageType;
        String normalizedEntityTypeId = SemionBilModelCache.normalize(entityTypeId);
        this.blockbenchModelId = SemionBilModelCache.normalize(blockbenchModelId);
        this.entityTypeId = normalizedEntityTypeId == null && this.blockbenchModelId == null ? "minecraft:zombie" : normalizedEntityTypeId;
        this.dimensions = MonsterDimensions.orDefault(dimensions);
        this.mineralReward = mineralReward;
        this.summonTier = summonTier;
        this.summonRoles = summonRoles == null ? List.of() : List.copyOf(summonRoles);
        this.targetPriority = targetPriority;
        this.movementSpeedMultiplier = movementSpeedMultiplier;
        this.attackRange = attackRange;
        this.attackIntervalTicks = attackIntervalTicks;
        this.health = maxHealth;
    }

    public static Monster fromWaveEntry(WaveMonsterEntry entry, TeamId targetTeam, int targetLaneId) {
        return new Monster(
                entry.id(),
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                entry.health(),
                entry.armor(),
                entry.attackDamage(),
                entry.attackKind(),
                entry.entityType(),
                entry.blockbenchModelId(),
                DamageType.PHYSICAL,
                0.0,
                entry.dimensions(),
                null,
                List.of(),
                entry.mineralReward(),
                entry.targetPriority(),
                entry.movementSpeedMultiplier(),
                entry.attackRange(),
                entry.attackIntervalTicks()
        );
    }

    public String id() {
        return id;
    }

    public TeamId targetTeam() {
        return targetTeam;
    }

    public int targetLaneId() {
        return targetLaneId;
    }

    public Optional<UUID> ownerPlayer() {
        return ownerPlayer;
    }

    public Optional<TeamId> senderTeam() {
        return senderTeam;
    }

    public Optional<String> senderName() {
        return Optional.ofNullable(senderName);
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public double laneProgress() {
        return laneProgress;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double armor() {
        return armor;
    }

    public double resistance() {
        return resistance;
    }

    public double attackDamage() {
        return attackDamage * attackDamageMultiplier;
    }

    public AttackKind attackKind() {
        return attackKind;
    }

    public DamageType damageType() {
        return damageType;
    }

    public Optional<SummonTier> summonTier() {
        return Optional.ofNullable(summonTier);
    }

    public List<SummonRole> summonRoles() {
        return summonRoles;
    }

    public double targetPriority() {
        return targetPriority;
    }

    public double movementSpeedMultiplier() {
        return movementSpeedMultiplier;
    }

    public double attackRange() {
        return inFinalDefenseCombat() ? FINAL_DEFENSE_ATTACK_RANGE : attackRange;
    }

    public int attackIntervalTicks() {
        return Math.max(1, (int) Math.ceil(attackIntervalTicks / attackSpeedMultiplier));
    }

    public void applyAttackModifiers(double damageMultiplier, double speedMultiplier) {
        attackDamageMultiplier = Math.max(0.0, damageMultiplier);
        attackSpeedMultiplier = Math.max(0.01, speedMultiplier);
    }

    public void enterFinalDefenseCombat() {
        finalDefenseCombat = true;
    }

    public boolean inFinalDefenseCombat() {
        return finalDefenseCombat;
    }

    public double targetPriorityScore() {
        double rolePriority = summonRoles.stream()
                .mapToInt(SummonRole::targetPriority)
                .max()
                .orElse(0);
        double threatBonus = summonRoles.contains(SummonRole.SIEGE)
                && laneProgress >= SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS
                ? SummonBalancePolicy.SIEGE_NEAR_BOSS_TARGET_BONUS
                : 0;
        return (laneProgress * 100.0) + targetPriority + rolePriority + threatBonus;
    }

    public long mineralReward() {
        return mineralReward;
    }

    public String entityTypeId() {
        return entityTypeId == null ? "minecraft:zombie" : entityTypeId;
    }

    public Optional<String> blockbenchModelId() {
        return Optional.ofNullable(blockbenchModelId);
    }

    public MonsterDimensions dimensions() {
        return dimensions;
    }

    public int minecraftEntityId() {
        return minecraftEntityId;
    }

    public boolean hasMinecraftEntity() {
        return minecraftEntityId >= 0;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    public double spawnZ() {
        return spawnZ;
    }

    public MonsterState state() {
        return state;
    }

    public Optional<UUID> lastHitPlayerId() {
        return lastHitPlayerId;
    }

    public KillSourceKind lastHitSourceKind() {
        return lastHitSourceKind;
    }

    public boolean rewardGranted() {
        return rewardGranted;
    }

    public boolean laneLeakRecorded() {
        return laneLeakRecorded;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public int laneBreachTicks() {
        return laneBreachTicks;
    }

    public int survivalScalingStacks() {
        return survivalScalingStacks;
    }

    public double attributionThreat() {
        return Math.max(1.0, maxHealth + Math.max(0.0, attackDamage));
    }

    public <T> void setData(MonsterDataKey<T> key, T value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            removeData(key);
            return;
        }
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException("Monster data " + key.id() + " requires " + key.type().getName());
        }
        data.put(key, value);
    }

    public <T> Optional<T> getData(MonsterDataKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = data.get(key);
        return value == null ? Optional.empty() : Optional.of(key.cast(value));
    }

    public void removeData(MonsterDataKey<?> key) {
        if (key != null) {
            data.remove(key);
        }
    }

    public boolean isAlive() {
        return state == MonsterState.ALIVE || state == MonsterState.SPAWNING;
    }

    public boolean isRemoved() {
        return state == MonsterState.DEAD || state == MonsterState.REMOVED;
    }

    public void tickLaneMovement() {
        if (state == MonsterState.SPAWNING) {
            state = MonsterState.ALIVE;
        }
        if (state != MonsterState.ALIVE) {
            return;
        }

        laneProgress = Math.min(1.0, laneProgress + DEFAULT_PROGRESS_PER_TICK);
        if (laneProgress >= 1.0) {
            state = MonsterState.REACHED_BOSS;
        }
    }

    public void syncLaneProgress(double laneProgress) {
        this.laneProgress = Math.max(0.0, Math.min(1.0, laneProgress));
        if (state == MonsterState.SPAWNING) {
            state = MonsterState.ALIVE;
        }
        if (this.laneProgress >= FINAL_DEFENSE_PROGRESS) {
            enterFinalDefenseCombat();
        }
        if (state == MonsterState.ALIVE && this.laneProgress >= 1.0) {
            state = MonsterState.REACHED_BOSS;
        }
    }

    public void syncHealth(double health) {
        this.health = Math.max(0.0, Math.min(maxHealth, health));
        if (state == MonsterState.SPAWNING && this.health > 0) {
            state = MonsterState.ALIVE;
        }
        if (this.health <= 0) {
            state = MonsterState.DEAD;
        }
    }

    public void damage(double amount) {
        damage(amount, DamageType.PHYSICAL);
    }

    public void damage(double amount, DamageType incomingDamageType) {
        if (amount <= 0 || (!isAlive() && state != MonsterState.REACHED_BOSS)) {
            return;
        }
        DamageType damageType = incomingDamageType == null ? DamageType.PHYSICAL : incomingDamageType;
        double defense = switch (damageType) {
            case PHYSICAL -> armor;
            case MAGIC -> resistance;
            case TRUE -> 0;
        };
        double effectiveDamage = damageType == DamageType.TRUE
                ? amount
                : amount * 100.0 / (100.0 + Math.max(0.0, defense));
        health = Math.max(0, health - effectiveDamage);
        if (health <= 0) {
            state = MonsterState.DEAD;
        }
    }

    public void heal(double amount) {
        if (amount <= 0 || !isAlive()) {
            return;
        }
        health = Math.min(maxHealth, health + amount);
    }

    public boolean tickSurvivalScaling(MonsterScalingConfig config, int roundElapsedTicks) {
        if (state != MonsterState.ALIVE && state != MonsterState.SPAWNING && state != MonsterState.REACHED_BOSS) {
            return false;
        }

        activeTicks++;
        if (state == MonsterState.REACHED_BOSS) {
            laneBreachTicks++;
        } else {
            laneBreachTicks = 0;
        }

        MonsterScalingConfig safeConfig = config == null ? MonsterScalingConfig.defaultConfig() : config;
        if (!safeConfig.enabled() || !scalingAppliesToSource(safeConfig)) {
            survivalScalingIntervalTicks = 0;
            return false;
        }

        boolean survivedLongEnough = activeTicks >= safeConfig.survivalDelayTicks();
        boolean roundDelayReached = roundElapsedTicks >= safeConfig.survivalDelayTicks();
        boolean breachDelayReached = laneBreachTicks >= safeConfig.laneBreachDelayTicks();
        if (!survivedLongEnough || (!roundDelayReached && !breachDelayReached)) {
            survivalScalingIntervalTicks = 0;
            return false;
        }

        survivalScalingIntervalTicks++;
        if (survivalScalingIntervalTicks < safeConfig.intervalTicks()) {
            return false;
        }

        survivalScalingIntervalTicks = 0;
        applySurvivalScaling(safeConfig);
        return true;
    }

    private boolean scalingAppliesToSource(MonsterScalingConfig config) {
        return ownerPlayer.isPresent() ? config.scaleIncomeMonsters() : config.scaleWaveMonsters();
    }

    private void applySurvivalScaling(MonsterScalingConfig config) {
        double healthMultiplier = 1.0 + config.healthGrowthPercentPerInterval() / 100.0;
        double attackDamageMultiplier = 1.0 + config.attackDamageGrowthPercentPerInterval() / 100.0;
        maxHealth *= healthMultiplier;
        health = Math.min(maxHealth, health * healthMultiplier);
        attackDamage *= attackDamageMultiplier;
        survivalScalingStacks++;
    }

    public void recordLastHit(UUID playerId, KillSourceKind sourceKind) {
        lastHitPlayerId = Optional.ofNullable(playerId);
        lastHitSourceKind = sourceKind == null ? KillSourceKind.UNKNOWN : sourceKind;
    }

    public void recordBossHit() {
        lastHitPlayerId = Optional.empty();
        lastHitSourceKind = KillSourceKind.BOSS;
    }

    public void markRewardGranted() {
        rewardGranted = true;
    }

    public void markLaneLeakRecorded() {
        laneLeakRecorded = true;
    }

    public void markRemoved() {
        state = MonsterState.REMOVED;
    }

    public void clearMinecraftEntityReference() {
        minecraftEntityId = -1;
    }

    public void markMinecraftEntitySpawned(int minecraftEntityId, double spawnX, double spawnY, double spawnZ) {
        this.minecraftEntityId = minecraftEntityId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
    }
}
