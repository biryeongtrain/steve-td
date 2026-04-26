package kim.biryeong.semionTd.entity.monster;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semionTd.config.AttackKind;
import kim.biryeong.semionTd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semionTd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semionTd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semionTd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

public class SemionMonsterEntity extends PathfinderMob implements PolymerEntity {
    private static final double DEFAULT_MELEE_RANGE = 2.5;
    private static final double DEFAULT_RANGED_RANGE = 8.0;
    private static final double DEFAULT_FOLLOW_RANGE = 12.0;
    private static final double DEFENSE_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double DEFENSE_SEARCH_VERTICAL_PADDING = 3.0;
    private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;

    private EntityType<?> polymerEntityType = EntityType.ZOMBIE;
    private Monster runtimeMonster;
    private LaneRegionLayout laneLayout;

    public SemionMonsterEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MonsterAttackTargetGoal(this, 1.1));
        goalSelector.addGoal(2, new LaneFollowGoal(this, 1.0));
        targetSelector.addGoal(0, new AcquireLaneDefenseTargetGoal(this));
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return polymerEntityType;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (runtimeMonster != null) {
            runtimeMonster.syncHealth(0.0);
        }
    }

    public void configureFrom(Monster monster, LaneRegionLayout laneLayout) {
        this.runtimeMonster = monster;
        this.laneLayout = laneLayout;
        setCustomName(Component.literal(monster.id()));
        setCustomNameVisible(true);
        setPolymerEntityType(monster.entityTypeId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(monster.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(monster.attackDamage());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRangeFor(monster));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
        setHealth((float) monster.health());
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (getTarget() instanceof LaneDefenseEntity defenseEntity && runtimeMonster != null) {
            if (!getTarget().isAlive() || !defenseEntity.defendsLane(runtimeMonster.targetLaneId())) {
                setTarget(null);
            }
        } else if (getTarget() != null && !getTarget().isAlive()) {
            setTarget(null);
        }
    }

    public boolean hasLanePath() {
        return laneLayout != null;
    }

    public List<Vec3> pathPoints() {
        if (laneLayout == null) {
            return List.of();
        }

        List<Vec3> points = new ArrayList<>(laneLayout.waypoints().size() + 1);
        points.addAll(laneLayout.waypoints());
        points.add(laneLayout.bossPosition());
        return points;
    }

    public AABB defenseSearchBox() {
        if (laneLayout == null) {
            return getBoundingBox().inflate(DEFAULT_FOLLOW_RANGE);
        }
        return laneLayout.defenseSearchBox(position(), DEFENSE_SEARCH_HORIZONTAL_PADDING, DEFENSE_SEARCH_VERTICAL_PADDING);
    }

    public Monster runtimeMonster() {
        return runtimeMonster;
    }

    public double attackRange() {
        if (runtimeMonster == null) {
            return DEFAULT_MELEE_RANGE;
        }
        return runtimeMonster.attackKind() == AttackKind.RANGED ? DEFAULT_RANGED_RANGE : DEFAULT_MELEE_RANGE;
    }

    public int attackIntervalTicks() {
        return DEFAULT_ATTACK_INTERVAL_TICKS;
    }

    private double followRangeFor(Monster monster) {
        double baseAttackRange = monster.attackKind() == AttackKind.RANGED ? DEFAULT_RANGED_RANGE : DEFAULT_MELEE_RANGE;
        return Math.max(DEFAULT_FOLLOW_RANGE, baseAttackRange + 2.0);
    }

    private void setPolymerEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            polymerEntityType = EntityType.ZOMBIE;
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            polymerEntityType = EntityType.ZOMBIE;
            return;
        }

        polymerEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.ZOMBIE);
    }
}
