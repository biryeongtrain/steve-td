package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public abstract class AugmentCombatFixture {
    protected static ProductionTower add(GameTestHelper context, PlayerLane lane, String id, double damage) {
        TowerType type = new TowerType("augment_gametest_" + id, id, TowerCategory.DIRECT, 10, 100, 8, damage, 20, 0);
        ProductionTowerCatalog.registerStarter(type);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        ProductionTower tower = new ProductionTower(type, lane.ownerPlayer(), TeamId.RED, 1, position);
        lane.addTower(tower);
        entity(context, tower).setNoGravity(true);
        return tower;
    }

    protected static SemionTowerEntity entity(GameTestHelper context, EntityBackedTower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
    }

    protected static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health) {
        return monster(context, lane, position, health, false);
    }

    protected static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health, boolean lowPressure) {
        return monster(context, lane, position, health, lowPressure, DamageType.PHYSICAL);
    }

    protected static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health,
                                               boolean lowPressure, DamageType damageType) {
        return monster(context, lane, position, health, lowPressure, damageType, 0);
    }

    protected static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position, double health,
                                               boolean lowPressure, DamageType damageType, double armor) {
        Monster monster = new Monster("augment_target_" + UUID.randomUUID(), TeamId.RED, 1, Optional.empty(), Optional.empty(),
                health, armor, 0, AttackKind.MELEE, "minecraft:zombie", null, damageType, 0,
                SummonTier.T1, List.of(SummonRole.RUSH), 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        if (lowPressure) {
            monster.setOrigin(MonsterOrigin.NORMAL_PAID);
            SemionPlayer buyer = new SemionPlayer(UUID.randomUUID(), "low-pressure-fixture", TeamId.BLUE, 1,
                    new PlayerEconomy(EconomyConfig.defaultConfig()));
            AugmentEconomyService.beginPrepare(buyer, 5);
            AugmentEconomyService.onSelected(buyer, "low_pressure_high_yield", 5, Map.of());
            var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 5,
                    true, true, false, true, true, 10, 5);
            AugmentEconomyService.applyPurchaseBody(monster, plan);
            if (!AugmentEconomyService.commitPurchase(buyer, plan, monster)) throw new AssertionError("Low-pressure fixture contract must commit.");
        }
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    protected static AugmentSnapshot snapshot(Object... values) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (int index = 0; index < values.length; index += 2) {
            String id = (String) values[index];
            selections.add(new PlayerAugmentState.Selection(5 + index * 5, AugmentCatalog.find(id).orElseThrow().rarity(),
                    id, PlayerAugmentState.Outcome.SELECTED, null, (AugmentChoice) values[index + 1]));
        }
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }

    protected static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(spawn.add(8, 0, 0)), spawn.add(8, 0, 10),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 10)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        lane.assignAugmentTelemetry(new AugmentTelemetry());
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    protected static void cleanup(PlayerLane lane) {
        for (Monster monster : lane.activeMonsters()) {
            if (monster.hasMinecraftEntity() && lane.arenaWorld().getEntity(monster.minecraftEntityId()) != null) {
                lane.arenaWorld().getEntity(monster.minecraftEntityId()).discard();
            }
        }
        lane.activeMonsters().clear();
        for (Tower tower : List.copyOf(lane.towers())) lane.removeTower(tower);
        AreaEffectLaneIndex.unregister(lane);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    protected static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
    }
}
