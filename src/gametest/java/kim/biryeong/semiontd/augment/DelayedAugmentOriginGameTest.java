package kim.biryeong.semiontd.augment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class DelayedAugmentOriginGameTest {
    @GameTest
    public void delayedIgniteAndPoisonKeepApplicationOriginAndNativeCallbacks(GameTestHelper context) {
        PlayerLane lane = lane(context);
        CapturingTower source = tower(context, lane, 2);
        try {
            for (boolean poison : new boolean[]{false, true}) {
                for (boolean suppressed : new boolean[]{false, true}) {
                    SemionMonsterEntity target = monster(context, lane, 4, 3);
                    Runnable apply = () -> applyDot(target, source, poison);
                    if (suppressed) AugmentCombat.runWithoutTriggers(apply);
                    else apply.run();
                    require(AugmentCombat.allowsTriggers(), "Application guard must unwind before the delayed tick");
                    target.aiStep();
                    require(!target.isAlive(), "A native delayed damage tick must still kill");
                    require(triggerAllowedAtDeath(target) != suppressed, "Delayed death must retain the application-time trigger origin");
                    if (!poison) require(source.igniteCallbackAllowed != suppressed,
                            "Native ignite-kill callback must run under the same captured guard");
                }
            }
            require(source.igniteCallbacks == 2, "Suppression must preserve native ignite-kill callbacks");
            require(AugmentCombat.allowsTriggers(), "Delayed damage and death guards must unwind");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void poisonRefreshCannotEraseAnExtraAttackContribution(GameTestHelper context) {
        PlayerLane lane = lane(context);
        CapturingTower source = tower(context, lane, 2);
        try {
            SemionMonsterEntity target = monster(context, lane, 4, 3);
            AugmentCombat.runWithoutTriggers(() -> applyDot(target, source, true));
            applyDot(target, source, true);
            target.aiStep();
            require(!target.isAlive() && !triggerAllowedAtDeath(target),
                    "A normal refresh must not relabel the still-present extra-attack poison stack");
            CapturingTower copy = new CapturingTower(lane, position(context, 3, 2, 3));
            copy.markTemporaryCopy(source.logicalId());
            lane.addTower(copy);
            SemionMonsterEntity copyTarget = monster(context, lane, 5, 3);
            applyDot(copyTarget, copy, false);
            copyTarget.aiStep();
            require(!copyTarget.isAlive() && !triggerAllowedAtDeath(copyTarget),
                    "Temporary-copy DoT captures suppression even outside an extra-attack callback");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            lane.clearTowers();
        }
    }

    @GameTest
    public void copyDamageCannotStartContagionBeforeAttackCallbacks(GameTestHelper context) {
        PlayerLane lane = lane(context);
        lane.assignAugmentSnapshot(contagion());
        AreaEffectLaneIndex.register(lane);
        CapturingTower original = tower(context, lane, 2);
        CapturingTower copy = new CapturingTower(lane, position(context, 3, 2, 3));
        copy.markTemporaryCopy(original.logicalId());
        lane.addTower(copy);
        SemionMonsterEntity sleeper = monster(context, lane, 4, 3);
        SemionMonsterEntity nearby = monster(context, lane, 5, 3);
        try {
            SuccubusDreams.add(sleeper, lane, original, 10);
            copy.damageTargetResult(copy.runtimeEntity(lane).orElseThrow(), sleeper, 100);
            require(!sleeper.isAlive(), "Temporary copy still deals native damage");
            require(!SuccubusDreams.isAsleep(nearby), "Copy damage must suppress contagion before recordAttack is reached");
            require(!triggerAllowedAtDeath(sleeper), "Copy death origin must also survive the delayed lane notification");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
            lane.clearTowers();
        }
    }

    @GameTest
    public void delayedExtraDotCannotStartContagionDuringDreamTick(GameTestHelper context) {
        PlayerLane lane = lane(context);
        lane.assignAugmentSnapshot(contagion());
        AreaEffectLaneIndex.register(lane);
        CapturingTower source = tower(context, lane, 2);
        try {
            SemionMonsterEntity sleeper = monster(context, lane, 4, 3);
            SemionMonsterEntity nearby = monster(context, lane, 5, 3);
            SuccubusDreams.add(sleeper, lane, source, 10);
            AugmentCombat.runWithoutTriggers(() -> applyDot(sleeper, source, false));
            sleeper.aiStep();
            lane.activeMonsters().remove(sleeper.runtimeMonster());
            SuccubusDreams.tick(lane);
            require(!SuccubusDreams.isAsleep(nearby),
                    "Retained-corpse dream processing must replay suppressed DoT provenance");
            require(AugmentCombat.allowsTriggers(), "Dream death guard must unwind");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal(failure.toString()));
        } finally {
            SuccubusDreams.clearLane(lane);
            AreaEffectLaneIndex.unregister(lane);
            lane.clearTowers();
        }
    }

    private static void applyDot(SemionMonsterEntity target, CapturingTower source, boolean poison) {
        if (poison) target.applyBeePoison(source.ownerPlayer(), source, 10, 3, 100, 1);
        else target.applyIgnite(source.ownerPlayer(), source, TraitLoadout.none(), 10, 0, 1, 100, 1);
    }

    private static boolean triggerAllowedAtDeath(SemionMonsterEntity target) {
        boolean[] allowed = {false};
        AugmentCombat.withKillOrigin(target.runtimeMonster(), () -> allowed[0] = AugmentCombat.allowsTriggers());
        return allowed[0];
    }

    private static AugmentSnapshot contagion() {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, SuccubusDreams.CONTAGION, PlayerAugmentState.Outcome.SELECTED,
                null, AugmentChoice.none())));
    }

    private static PlayerLane lane(GameTestHelper context) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 5, 7));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(min),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4)))), Vec3.atCenterOf(max),
                BlockBounds.of(min, max), List.of(position(context, 6, 2, 6)));
        return new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
    }

    private static CapturingTower tower(GameTestHelper context, PlayerLane lane, int x) {
        CapturingTower tower = new CapturingTower(lane, position(context, x, 2, 3));
        lane.addTower(tower);
        tower.runtimeEntity(lane).orElseThrow().setNoAi(true);
        return tower;
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, int x, int z) {
        Monster monster = new Monster("delayed-origin", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                5, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        GridPosition position = position(context, x, 2, z);
        entity.setPos(position.x() + .5, position.y() + 1, position.z() + .5);
        require(context.getLevel().addFreshEntity(entity), "Fixture monster must spawn");
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CapturingTower extends ProductionTower {
        int igniteCallbacks;
        boolean igniteCallbackAllowed;

        CapturingTower(PlayerLane lane, GridPosition position) {
            super(new TowerType("delayed_origin_fixture", "Origin fixture", TowerCategory.DIRECT,
                    0, 100, 5, 10, 20, 1), lane.ownerPlayer(), TeamId.RED, 1, position);
        }

        @Override
        public void onIgniteKill(SemionMonsterEntity target) {
            igniteCallbacks++;
            igniteCallbackAllowed = AugmentCombat.allowsTriggers();
        }
    }
}
