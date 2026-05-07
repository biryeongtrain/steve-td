package kim.biryeong.semiontd.summon;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.world.entity.ai.goal.Goal;

public abstract class SummonMonsterType {
    private final String id;
    private final String displayName;
    private final long gasCost;
    private final long incomeGain;
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
    private final SummonTier tier;
    private final List<SummonRole> roles;
    private final List<SummonAbilityActivation> abilityActivations;

    protected SummonMonsterType(
            String id,
            String displayName,
            long gasCost,
            long incomeGain,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            long mineralReward
    ) {
        this(
                id,
                displayName,
                gasCost,
                incomeGain,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                null,
                MonsterDimensions.DEFAULT,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                mineralReward
        );
    }

    protected SummonMonsterType(
            String id,
            String displayName,
            long gasCost,
            long incomeGain,
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
                displayName,
                gasCost,
                incomeGain,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                MonsterDimensions.DEFAULT,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                mineralReward
        );
    }

    protected SummonMonsterType(
            String id,
            String displayName,
            long gasCost,
            long incomeGain,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            DamageType damageType,
            double resistance,
            SummonTier tier,
            List<SummonRole> roles,
            List<SummonAbilityActivation> abilityActivations,
            long mineralReward
    ) {
        this(
                id,
                displayName,
                gasCost,
                incomeGain,
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                MonsterDimensions.DEFAULT,
                damageType,
                resistance,
                tier,
                roles,
                abilityActivations,
                mineralReward
        );
    }

    protected SummonMonsterType(
            String id,
            String displayName,
            long gasCost,
            long incomeGain,
            double maxHealth,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityTypeId,
            String blockbenchModelId,
            MonsterDimensions dimensions,
            DamageType damageType,
            double resistance,
            SummonTier tier,
            List<SummonRole> roles,
            List<SummonAbilityActivation> abilityActivations,
            long mineralReward
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Summon monster id cannot be blank.");
        }
        if (gasCost < 0 || incomeGain < 0 || maxHealth <= 0 || armor < 0 || resistance < 0 || attackDamage < 0 || mineralReward < 0) {
            throw new IllegalArgumentException("Summon monster numeric values are invalid.");
        }

        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.gasCost = gasCost;
        this.incomeGain = incomeGain;
        this.maxHealth = maxHealth;
        this.armor = armor;
        this.resistance = resistance;
        this.attackDamage = attackDamage;
        this.attackKind = attackKind == null ? AttackKind.MELEE : attackKind;
        this.damageType = damageType == null ? DamageType.PHYSICAL : damageType;
        String normalizedEntityTypeId = SemionBilModelCache.normalize(entityTypeId);
        this.blockbenchModelId = SemionBilModelCache.normalize(blockbenchModelId);
        this.entityTypeId = normalizedEntityTypeId == null && this.blockbenchModelId == null ? "minecraft:zombie" : normalizedEntityTypeId;
        this.dimensions = MonsterDimensions.orDefault(dimensions);
        this.mineralReward = mineralReward;
        this.tier = tier == null ? SummonTier.T1 : tier;
        this.roles = roles == null || roles.isEmpty() ? List.of(SummonRole.RUSH) : List.copyOf(roles);
        this.abilityActivations = abilityActivations == null ? List.of() : List.copyOf(abilityActivations);
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final long gasCost() {
        return gasCost;
    }

    public final long incomeGain() {
        return incomeGain;
    }

    public final double maxHealth() {
        return maxHealth;
    }

    public final double armor() {
        return armor;
    }

    public final double resistance() {
        return resistance;
    }

    public final double attackDamage() {
        return attackDamage;
    }

    public final AttackKind attackKind() {
        return attackKind;
    }

    public final DamageType damageType() {
        return damageType;
    }

    public final String entityTypeId() {
        return entityTypeId == null ? "minecraft:zombie" : entityTypeId;
    }

    public final Optional<String> blockbenchModelId() {
        return Optional.ofNullable(blockbenchModelId);
    }

    public final MonsterDimensions dimensions() {
        return dimensions;
    }

    public final long mineralReward() {
        return mineralReward;
    }

    public final SummonTier tier() {
        return tier;
    }

    public final List<SummonRole> roles() {
        return roles;
    }

    public final List<SummonAbilityActivation> abilityActivations() {
        return abilityActivations;
    }

    public final double incomeRatio() {
        return gasCost == 0 ? 0.0 : (double) incomeGain / gasCost;
    }

    public final double targetRolePriority() {
        return roles.stream().mapToInt(SummonRole::targetPriority).max().orElse(0);
    }

    public Monster createMonster(SummonContext context, TeamId targetTeam, int targetLaneId) {
        Objects.requireNonNull(context, "context");
        UUID ownerPlayer = context.player().uuid();
        TeamId senderTeam = context.player().teamId();
        return new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.of(ownerPlayer),
                Optional.of(senderTeam),
                maxHealth,
                armor,
                attackDamage,
                attackKind,
                entityTypeId,
                blockbenchModelId,
                damageType,
                resistance,
                dimensions,
                tier,
                roles,
                mineralReward
        );
    }

    public void onSummoned(SummonContext context, Monster monster) {
    }

    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of();
    }
}
