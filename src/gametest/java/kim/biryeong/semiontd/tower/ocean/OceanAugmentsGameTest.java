package kim.biryeong.semiontd.tower.ocean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class OceanAugmentsGameTest {
    @GameTest
    public void recyclingUsesConnectedTargetsAndAppliesTwoSourceDecayOnce(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "s", "g1");
        OceanWaterTower first = new OceanWaterTower(OceanTowers.T1_WATER, owner, TeamId.RED, 1, position(context, 2, 3));
        OceanWaterTower second = new OceanWaterTower(OceanTowers.T1_WATER, owner, TeamId.RED, 1, position(context, 2, 4));
        OceanTower full = new OceanTower(OceanTowers.T1_COD, owner, TeamId.RED, 1, position(context, 3, 3));
        OceanTower low = new OceanTower(OceanTowers.T1_COD, owner, TeamId.RED, 1, position(context, 3, 4));
        OceanTower outside = new OceanTower(OceanTowers.T1_COD, owner, TeamId.RED, 1, position(context, 7, 7));
        try {
            lane.addTower(first);
            lane.addTower(second);
            lane.addTower(full);
            lane.addTower(low);
            lane.addTower(outside);
            full.addWater(full.waterSoftCap() - full.water());
            double before = low.water();
            double amount = TowerBalanceRuntime.ability(first.type().id(), "waveStartWater") * 1.5;
            double decay = OceanWaterTower.stackedSupplyMultiplier(2,
                    TowerBalanceRuntime.ability(OceanTower.CONFIG_ID, "waterSupplyStackDecay"));
            first.onWaveStarted(lane, 1);
            second.onWaveStarted(lane, 1);
            require(close(low.water(), before + 4 * amount * decay), "Recycling must move both shares and attenuate each source once.");
            require(close(full.water(), 1500), "A soft-capped tower must receive no supply.");
            require(close(outside.water(), 100), "Recycling must not reach an unlinked low-water tower.");
            low.addWater(2500 - low.water());
            first.execute(lane);
            require(close(low.water(), 2500), "High tide must preserve the original supply stop threshold.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    @GameTest
    public void currentHitsTwelveEnemiesAsMagicAndSpendsOnlyOneStoredCharge(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        PlayerLane lane = lane(context, owner, "g2");
        OceanTower tower = new OceanTower(OceanTowers.T1_COD, owner, TeamId.RED, 1, position(context, 3, 3));
        List<SemionMonsterEntity> targets = new ArrayList<>();
        try {
            lane.addTower(tower);
            tower.onWaveStarted(lane, 1);
            tower.spendWater(60);
            SemionTowerEntity source = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
            source.setNoAi(true);
            for (int index = 0; index < 14; index++) {
                targets.add(target(context, lane, source.position().add(.3 + index * .04, 0, 0), 1));
            }
            SemionMonsterEntity foreign = target(context, lane, source.position().add(.2, 0, 0), 2);
            targets.add(foreign);
            tower.onAttackResolved(source, targets.getFirst(), 1, 0, 0, false);
            require(tower.currentCharges() == 2, "A zero-damage basic attack must not consume current charges.");
            tower.onAttackResolved(source, targets.getFirst(), 1, 1, 1, false);
            require(tower.currentCharges() == 1, "One valid basic attack must consume exactly one charge.");
            require(targets.stream().filter(target -> target.runtimeMonster().health() < 2000).count() == 12,
                    "Current must damage at most twelve enemies including its primary target.");
            require(close(foreign.runtimeMonster().health(), 2000), "Current must preserve lane isolation.");
            require(tower.roundMagicDamageDealt() > 0, "Current must be attributed as magic damage.");
            AugmentCombat.runWithoutTriggers(() -> tower.onAttackResolved(source, targets.getFirst(), 1, 1, 1, false));
            require(tower.currentCharges() == 1, "Augment additional attacks must not spend current charges.");
            tower.resetForRound(lane);
            require(tower.currentCharges() == 0, "Round reset must discard unspent charges.");
            context.succeed();
        } finally {
            targets.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static PlayerLane lane(GameTestHelper context, UUID owner, String... cards) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 6, 7));
        LaneRegionLayout layout = new LaneRegionLayout(1, Vec3.atCenterOf(min), List.of(Vec3.atCenterOf(max)),
                Vec3.atCenterOf(max), BlockBounds.of(min, max), List.of(GridPosition.from(min)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(suffix ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, "semiontd:job_ocean_" + suffix,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static GridPosition position(GameTestHelper context, int x, int z) {
        BlockPos block = context.absolutePos(new BlockPos(x, 2, z));
        context.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
        context.getLevel().setBlock(block.above(), Blocks.AIR.defaultBlockState(), 3);
        context.getLevel().setBlock(block.above(2), Blocks.AIR.defaultBlockState(), 3);
        return GridPosition.from(block);
    }

    private static SemionMonsterEntity target(GameTestHelper context, PlayerLane lane, Vec3 position, int laneId) {
        Monster runtime = new Monster("ocean-augment-target", TeamId.RED, laneId, Optional.empty(), Optional.empty(),
                2000, 1, 0, AttackKind.MELEE, "minecraft:zombie", null, DamageType.PHYSICAL, 0,
                null, List.of(), 1L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        require(context.getLevel().addFreshEntity(entity), "Monster must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return entity;
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) < .0001;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
