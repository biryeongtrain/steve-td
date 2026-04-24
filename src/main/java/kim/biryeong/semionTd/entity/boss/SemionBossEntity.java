package kim.biryeong.semionTd.entity.boss;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import kim.biryeong.semionTd.entity.boss.goal.BossAttackLaneMonsterGoal;
import kim.biryeong.semionTd.game.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

public class SemionBossEntity extends PathfinderMob implements PolymerEntity {
    private TeamId teamId = TeamId.RED;
    private EntityType<?> polymerEntityType = EntityType.IRON_GOLEM;

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
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return polymerEntityType;
    }

    public void configure(TeamId teamId, BossMonster bossMonster) {
        this.teamId = teamId;
        setCustomName(Component.literal(teamId.name() + " Boss"));
        setCustomNameVisible(true);
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(bossMonster.maxHealth());
        getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        setHealth((float) bossMonster.health());
    }

    public TeamId teamId() {
        return teamId;
    }
}
