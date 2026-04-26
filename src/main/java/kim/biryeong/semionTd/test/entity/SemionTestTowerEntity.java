package kim.biryeong.semionTd.test.entity;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import java.util.UUID;
import kim.biryeong.semionTd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semionTd.map.LaneRegionLayout;
import kim.biryeong.semionTd.test.entity.goal.TestTowerAttackMonsterGoal;
import kim.biryeong.semionTd.test.tower.TestTower;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import xyz.nucleoid.packettweaker.PacketContext;

public final class SemionTestTowerEntity extends PathfinderMob implements PolymerEntity, LaneDefenseEntity {
    private static final double DEFAULT_MOVE_SPEED = 0.23;
    private static final double DEFAULT_TARGET_ACQUIRE_RANGE = 24.0;
    private static final double TARGET_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double TARGET_SEARCH_VERTICAL_PADDING = 3.0;

    private int laneId;
    private UUID ownerPlayer;
    private double attackRange;
    private double attackDamage;
    private int attackIntervalTicks;
    private int aggroPriority;
    private boolean finalDefense;
    private double targetAcquireRange;
    private double moveSpeed;
    private LaneRegionLayout laneLayout;

    public SemionTestTowerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TestTowerAttackMonsterGoal(this));
    }

    public void configure(TestTower tower, LaneRegionLayout laneLayout) {
        this.laneLayout = laneLayout;
        laneId = tower.laneId();
        ownerPlayer = tower.ownerPlayer();
        attackRange = tower.type().range();
        attackDamage = tower.type().damage();
        attackIntervalTicks = tower.type().attackIntervalTicks();
        aggroPriority = tower.aggroPriority();
        finalDefense = tower.deployedAtFinalDefense();
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        moveSpeed = DEFAULT_MOVE_SPEED;
        setCustomName(Component.literal(tower.type().displayName()));
        setCustomNameVisible(true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tower.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(moveSpeed);
        setHealth((float) tower.health());
    }

    public int laneId() {
        return laneId;
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    @Override
    public boolean defendsLane(int targetLaneId) {
        return finalDefense || laneId == targetLaneId;
    }

    @Override
    public int aggroPriority() {
        return aggroPriority;
    }

    public double attackRange() {
        return attackRange;
    }

    public double targetAcquireRange() {
        return targetAcquireRange;
    }

    public AABB targetSearchBox() {
        if (laneLayout == null) {
            return getBoundingBox().inflate(targetAcquireRange);
        }
        return laneLayout.defenseSearchBox(position(), TARGET_SEARCH_HORIZONTAL_PADDING, TARGET_SEARCH_VERTICAL_PADDING);
    }

    public double moveSpeedAmount() {
        return moveSpeed;
    }

    public double attackDamageAmount() {
        return attackDamage;
    }

    public int attackIntervalTicks() {
        return attackIntervalTicks;
    }

    public void syncTowerState(TestTower tower) {
        aggroPriority = tower.aggroPriority();
        finalDefense = tower.deployedAtFinalDefense();
        if (Math.abs(getHealth() - tower.health()) > 0.01F) {
            setHealth((float) tower.health());
        }
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityType.ARMOR_STAND;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}

