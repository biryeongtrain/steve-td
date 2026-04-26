package kim.biryeong.semionTd.entity.monster;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semionTd.config.AttackKind;
import kim.biryeong.semionTd.config.WaveMonsterEntry;
import kim.biryeong.semionTd.game.TeamId;
import kim.biryeong.semionTd.summon.SummonMonsterType;

public final class Monster {
    private static final double DEFAULT_PROGRESS_PER_TICK = 0.004;

    private final String id;
    private final TeamId targetTeam;
    private final int targetLaneId;
    private final Optional<UUID> ownerPlayer;
    private final Optional<TeamId> senderTeam;
    private final double maxHealth;
    private final double armor;
    private final double attackDamage;
    private final AttackKind attackKind;
    private final String entityTypeId;
    private final long mineralReward;
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
        this.id = id;
        this.targetTeam = targetTeam;
        this.targetLaneId = targetLaneId;
        this.ownerPlayer = ownerPlayer;
        this.senderTeam = senderTeam;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.attackDamage = attackDamage;
        this.attackKind = attackKind;
        this.entityTypeId = entityTypeId;
        this.mineralReward = mineralReward;
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
                entry.mineralReward()
        );
    }

    public static Monster fromSummonType(
            SummonMonsterType type,
            TeamId targetTeam,
            int targetLaneId,
            UUID ownerPlayer,
            TeamId senderTeam
    ) {
        return new Monster(
                type.id(),
                targetTeam,
                targetLaneId,
                Optional.of(ownerPlayer),
                Optional.of(senderTeam),
                type.maxHealth(),
                type.armor(),
                type.attackDamage(),
                type.attackKind(),
                type.entityTypeId(),
                type.mineralReward()
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

    public double laneProgress() {
        return laneProgress;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double attackDamage() {
        return attackDamage;
    }

    public AttackKind attackKind() {
        return attackKind;
    }

    public long mineralReward() {
        return mineralReward;
    }

    public String entityTypeId() {
        return entityTypeId;
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
        if (!isAlive() && state != MonsterState.REACHED_BOSS) {
            return;
        }
        double effectiveDamage = Math.max(1.0, amount - armor);
        health = Math.max(0, health - effectiveDamage);
        if (health <= 0) {
            state = MonsterState.DEAD;
        }
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