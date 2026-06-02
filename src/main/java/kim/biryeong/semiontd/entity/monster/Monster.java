package kim.biryeong.semiontd.entity.monster;

import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Monster {
    private static final double DEFAULT_PROGRESS_PER_TICK = 0.004;

    private final String id;
    private final TeamId targetTeam;
    private final int targetLaneId;
    private final Optional<UUID> ownerPlayer;
    private final Optional<TeamId> senderTeam;
    private final double maxHealth;
    private final double armor;
    private final double resistance;
    private final double attackDamage;
    private final AttackKind attackKind;
    private final DamageType damageType;
    private final String entityTypeId;
    private final String blockbenchModelId;
    private final MonsterDimensions dimensions;
    private final long mineralReward;
    private final SummonTier summonTier;
    private final List<SummonRole> summonRoles;
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
                entry.dimensions(),
                entry.mineralReward()
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
        return attackDamage;
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

    public double targetPriorityScore() {
        double rolePriority = summonRoles.stream()
                .mapToInt(SummonRole::targetPriority)
                .max()
                .orElse(0);
        double threatBonus = summonRoles.contains(SummonRole.SIEGE)
                && laneProgress >= SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS
                ? SummonBalancePolicy.SIEGE_NEAR_BOSS_TARGET_BONUS
                : 0;
        return (laneProgress * 100.0) + rolePriority + threatBonus;
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

    public double attributionThreat() {
        return Math.max(1.0, maxHealth + Math.max(0.0, attackDamage));
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
        if (!isAlive() && state != MonsterState.REACHED_BOSS) {
            return;
        }
        double reduction = switch (incomingDamageType == null ? DamageType.PHYSICAL : incomingDamageType) {
            case PHYSICAL -> armor;
            case MAGIC -> resistance;
            case TRUE -> 0;
        };
        double effectiveDamage = Math.max(1.0, amount - reduction);
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

    public void markMinecraftEntitySpawned(int minecraftEntityId, double spawnX, double spawnY, double spawnZ) {
        this.minecraftEntityId = minecraftEntityId;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
    }
}
