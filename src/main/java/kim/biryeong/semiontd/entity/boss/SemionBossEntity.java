package kim.biryeong.semiontd.entity.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import kim.biryeong.semiontd.entity.boss.goal.BossAttackLaneMonsterGoal;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

public class SemionBossEntity extends PathfinderMob implements PolymerEntity {
    public static final double FINAL_DEFENSE_ENGAGEMENT_RANGE = Monster.FINAL_DEFENSE_ATTACK_RANGE;
    private static final double DAMAGE_SCALING_PER_ROUND = 0.10;
    private static final double SUMMON_DAMAGE_MULTIPLIER = 3.0;

    private TeamId teamId = TeamId.RED;
    private BossMonster runtimeBoss;
    private EntityType<?> polymerEntityType = EntityType.IRON_GOLEM;
    private Vec3 anchorPosition;
    private int currentRound = 1;

    public SemionBossEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new BossAttackLaneMonsterGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        holdAnchorPosition();
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return polymerEntityType;
    }

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof ServerPlayer) {
            return;
        }
        super.actuallyHurt(serverLevel, damageSource, amount);
        if (runtimeBoss != null && amount > 0.0F) {
            runtimeBoss.damage(amount);
        }
    }

    public void configure(TeamId teamId, BossMonster bossMonster) {
        this.teamId = teamId;
        this.runtimeBoss = bossMonster;
        setCustomName(Component.literal(teamId.name() + " Boss"));
        setCustomNameVisible(true);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(bossMonster.maxHealth());
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                .setBaseValue(bossMonster.attackDamage());
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(1.0);
        setHealth((float) bossMonster.health());
        setNoGravity(true);
    }

    public int attackIntervalTicks() {
        return runtimeBoss == null ? 13 : runtimeBoss.attackIntervalTicks();
    }

    public TeamId teamId() {
        return teamId;
    }

    public void setAnchorPosition(Vec3 anchorPosition) {
        this.anchorPosition = anchorPosition;
        holdAnchorPosition();
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = Math.max(1, currentRound);
    }

    public double attackDamageAgainst(Monster target) {
        double damage = getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        damage *= 1.0 + Math.max(0, currentRound - 1) * DAMAGE_SCALING_PER_ROUND;
        if (target != null && target.senderTeam().isPresent()) {
            damage *= SUMMON_DAMAGE_MULTIPLIER;
        }
        return damage;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    private void holdAnchorPosition() {
        if (anchorPosition == null) {
            return;
        }
        if (position().distanceToSqr(anchorPosition) > 0.0001) {
            teleportTo(anchorPosition.x, anchorPosition.y, anchorPosition.z);
        }
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
    }
}
