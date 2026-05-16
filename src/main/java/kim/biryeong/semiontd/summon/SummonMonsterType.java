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
    private final List<String> description;

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
                List.of(),
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
                List.of(),
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
                List.of(),
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
                dimensions,
                damageType,
                resistance,
                tier,
                roles,
                abilityActivations,
                List.of(),
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
            List<String> description,
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
        this.description = description == null || description.isEmpty()
                ? defaultDescription(this.roles, this.abilityActivations, this.attackKind, this.damageType)
                : List.copyOf(description);
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

    public final List<String> description() {
        return description;
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

    private static List<String> defaultDescription(
            List<SummonRole> roles,
            List<SummonAbilityActivation> abilityActivations,
            AttackKind attackKind,
            DamageType damageType
    ) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (roles.contains(SummonRole.DISRUPTOR)) {
            lines.add("상대 타워 성능을 깎거나 전선을 흔드는 교란형 소환수입니다.");
        } else if (roles.contains(SummonRole.SUPPORT)) {
            lines.add("아군 소환수를 회복하거나 강화해 라인 유지력을 높입니다.");
        } else if (roles.contains(SummonRole.SIEGE)) {
            lines.add("후반 진행도와 보스 압박에 강한 공성형 소환수입니다.");
        } else if (roles.contains(SummonRole.TANK)) {
            lines.add("높은 생존력으로 타워 화력을 오래 받아내는 탱커입니다.");
        } else if (roles.contains(SummonRole.SWARM)) {
            lines.add("낮은 비용으로 수를 늘려 타워 공격을 분산시키는 물량형 소환수입니다.");
        } else {
            lines.add("빠르게 전선을 압박하는 기본 공격형 소환수입니다.");
        }
        if (abilityActivations.contains(SummonAbilityActivation.COOLDOWN)) {
            lines.add("주기적으로 특수 능력을 사용합니다.");
        } else if (abilityActivations.contains(SummonAbilityActivation.PASSIVE)) {
            lines.add("소환되어 있는 동안 지속형 특성을 활용합니다.");
        }
        if (attackKind == AttackKind.RANGED) {
            lines.add("원거리 공격으로 타워 접근 전부터 피해를 누적합니다.");
        }
        if (damageType == DamageType.MAGIC) {
            lines.add("마법 피해 기반이라 물리 방어가 높은 대상에게도 압박을 줍니다.");
        }
        return List.copyOf(lines);
    }
}
