package kim.biryeong.semiontd.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;
import kim.biryeong.semiontd.tower.end.EndTower;

public abstract class TowerCoreAugmentFixture {
    protected static final class Fixture implements AutoCloseable {
        private final GameTestHelper context;
        public final UUID owner = UUID.randomUUID();
        public final PlayerLane lane;
        private final List<SemionMonsterEntity> monsters = new ArrayList<>();

        public Fixture(GameTestHelper context, String... ids) {
            this.context = context;
            var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                    List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                    Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                    BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                    List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
            lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
            List<PlayerAugmentState.Selection> selections = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                selections.add(new PlayerAugmentState.Selection(5 + i * 10, AugmentRarity.GOLD,
                        ids[i], PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()));
            }
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), selections));
            AreaEffectLaneIndex.register(lane);
        }

        public GridPosition position(int offset) {return GridPosition.from(context.absolutePos(new BlockPos(3 + offset, 2, 3)));}
        public SemionTowerEntity entity(EntityBackedTower tower) {return tower.runtimeEntity(lane).orElseThrow();}
        public void add(EntityBackedTower tower) {lane.addTower(tower); entity(tower).setNoAi(true);}
        public WarlockTower warlock(int offset) {
            WarlockTower tower = new WarlockTower(WarlockTowers.RANGED_WARLOCK_TOWER, owner, TeamId.RED, 1, position(offset));
            add(tower);
            return tower;
        }
        public EndTower end(TowerType type) {
            EndTower tower = new EndTower(type, owner, TeamId.RED, 1, position(0));
            add(tower);
            return tower;
        }
        public SemionMonsterEntity target(Vec3 position, double health) {
            Monster monster = new Monster("augment_target", TeamId.RED, 1, Optional.empty(), Optional.empty(), health, 0, 1,
                    AttackKind.MELEE, "minecraft:zombie", 0);
            monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
            var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            entity.configureFrom(monster, lane.laneLayout());
            entity.setNoAi(true);
            entity.setPos(position);
            require(context.getLevel().addFreshEntity(entity), "The target spawns.");
            monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
            lane.activeMonsters().add(monster);
            monsters.add(entity);
            return entity;
        }
        @Override public void close() {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    protected static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}

    protected static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .001, message + " Expected " + expected + ", got " + actual);
    }
}
