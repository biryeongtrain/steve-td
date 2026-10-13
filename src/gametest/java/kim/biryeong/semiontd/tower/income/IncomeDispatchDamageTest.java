package kim.biryeong.semiontd.tower.income;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.invasion.InvasionUnits;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;

public final class IncomeDispatchDamageTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void dispatchAreaUsesHalfSecondaryDamageWhileDwarfRemainsSingleTarget(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        for (String id : List.of("dark_priest", "dwarf_gunner", "ogre_champion")) {
            for (boolean dispatch : List.of(true, false)) {
                try (Fixture fixture = Fixture.start(context, id, dispatch)) {
                    double primary = fixture.primary.health();
                    double neighbor = fixture.neighbor.health();
                    require(fixture.attacker.attackStyle().hitDelayTicks() == InvasionUnits.profile(id).hitDelayTicks(),
                            id + " retains the original impact timing.");
                    fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                    require(fixture.primary.health() < primary, id + " still deals primary damage.");
                    double primaryLost = primary - fixture.primary.health();
                    double secondaryLost = neighbor - fixture.neighbor.health();
                    double expected = dispatch ? (id.equals("dwarf_gunner") ? 0.0 : primaryLost * 0.5) : primaryLost;
                    require(Math.abs(secondaryLost - expected) < 0.001,
                            id + " secondary damage must follow the income dispatch boundary: " + dispatch);
                    if (dispatch && id.equals("dwarf_gunner")) {
                        var neighborEntity = context.getLevel().getEntity(((EntityBackedTower) fixture.neighbor).entityId().orElseThrow());
                        MonsterAttackStyle.strike(fixture.attacker, (SemionTowerEntity) neighborEntity,
                                fixture.attacker.attackDamageAmount());
                        require(Math.abs((primary - fixture.primary.health()) - (neighbor - fixture.neighbor.health())) < 0.001,
                                id + " primary damage must remain exactly one normal strike.");
                    }
                }
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void dispatchWithDeadPrimaryCannotDamageNearbyDefenses(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        for (String id : List.of("dark_priest", "dwarf_gunner", "ogre_champion")) {
            try (Fixture fixture = Fixture.start(context, id, true)) {
                double neighbor = fixture.neighbor.health();
                require(fixture.lane.killTower(fixture.primary), "Primary target must die before impact.");
                fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                require(fixture.neighbor.health() == neighbor, id + " cannot transfer a missed hit to nearby defenses.");
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void dispatchPriestHealingUsesGrowthAndTwoPercentForMultipleAllies(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        try (Fixture fixture = Fixture.start(context, "dark_priest", true)) {
            var owner = fixture.game.players().values().stream().filter(player -> player.teamId() == TeamId.RED).findFirst().orElseThrow();
            var unit = fixture.game.summonShop().find("dark_priest").orElseThrow();
            var ally = fixture.lane.spawnMonsterAt(unit.createMonster(new SummonContext(fixture.game, owner), TeamId.BLUE, 1, 1),
                    fixture.attacker.position().add(0, 0, 1)).orElseThrow();
            ally.setNoAi(true);
            for (var entity : List.of(fixture.attacker, ally)) {
                entity.runtimeMonster().syncHealth(entity.runtimeMonster().maxHealth() * 0.5);
                entity.setHealth((float) entity.runtimeMonster().health());
            }
            double selfBefore = fixture.attacker.runtimeMonster().health();
            double allyBefore = ally.runtimeMonster().health();
            var goals = unit.createAbilityGoals(fixture.attacker);
            require(goals.size() == 1, "The priest keeps its independent area-healing ability.");
            goals.getFirst().tick();
            double growth = Math.max(1.0, fixture.attacker.runtimeMonster().maxHealth() / unit.maxHealth());
            double expectedSelf = 16.0 * growth + fixture.attacker.runtimeMonster().maxHealth() * 0.02;
            double expectedAlly = 16.0 * growth + ally.runtimeMonster().maxHealth() * 0.02;
            require(Math.abs(fixture.attacker.runtimeMonster().health() - selfBefore - expectedSelf) < 0.001,
                    "The dispatch priest must heal itself with the two-percent formula.");
            require(Math.abs(ally.runtimeMonster().health() - allyBefore - expectedAlly) < 0.001,
                    "The dispatch priest must use each ally's own maximum health.");
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void dispatchRechecksDefenseEligibilityAndPiercingReachAtImpact(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        for (String id : List.of("dark_priest", "dwarf_gunner", "ogre_champion")) {
            try (Fixture fixture = Fixture.start(context, id, true)) {
                var otherLaneTower = new ProductionTower(fixture.primary.type(), fixture.primary.ownerPlayer(),
                        fixture.primary.teamId(), 2, fixture.primary.position());
                fixture.primaryEntity.configure(otherLaneTower, fixture.lane.laneLayout());
                double otherLaneHealth = otherLaneTower.health();
                require(!fixture.attacker.canDamageDefense(fixture.primaryEntity), "Another lane must reject the impact.");
                fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                require(otherLaneTower.health() == otherLaneHealth, id + " cannot hit a defense that changed lanes.");
                fixture.primaryEntity.configure(fixture.primary, fixture.lane.laneLayout());
                double primaryHealth = fixture.primary.health();
                fixture.attacker.setIgnoresDefenses(true);
                fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                require(fixture.primary.health() == primaryHealth, id + " obeys disabled defense damage.");
                fixture.attacker.setIgnoresDefenses(false);
                var animal = context.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(6, 3, 6));
                try {
                    animal.setPos(fixture.primaryEntity.position());
                    float health = animal.getHealth();
                    require(!fixture.attacker.canDamageDefense(animal), "Ordinary living entities are not lane defenders.");
                    fixture.attacker.attackStyle().hit(fixture.attacker, animal);
                    require(animal.getHealth() == health, id + " cannot damage an ordinary living entity.");
                } finally {
                    animal.discard();
                }
                if (id.equals("dwarf_gunner")) {
                    fixture.primaryEntity.setPos(fixture.primaryEntity.position().add(100, 0, 0));
                    fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                    require(fixture.primary.health() == primaryHealth, "A target beyond the original bullet length is not hit.");
                }
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void dispatchAreaCapsAtFiveIncludingPrimaryAndOnlyHitsNearestNeighbors(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        for (String id : List.of("dark_priest", "ogre_champion")) {
            try (Fixture fixture = Fixture.start(context, id, true)) {
                var towers = fixture.lane.towers();
                double[] before = towers.stream().mapToDouble(Tower::health).toArray();
                double primaryBefore = fixture.primary.health();
                fixture.attacker.attackStyle().hit(fixture.attacker, fixture.primaryEntity);
                double primaryLost = primaryBefore - fixture.primary.health();
                int hitCount = 0;
                for (int i = 0; i < towers.size(); i++) {
                    var tower = towers.get(i);
                    double lost = before[i] - tower.health();
                    if (lost > 0.0) {
                        hitCount++;
                        require(Math.abs(lost - primaryLost * (tower == fixture.primary ? 1.0 : 0.5)) < 0.001,
                                id + " every extra target takes exactly half damage.");
                        var entity = context.getLevel().getEntity(((EntityBackedTower) tower).entityId().orElseThrow());
                        require(entity.position().distanceToSqr(fixture.primaryEntity.position()) <= 1.01,
                                id + " selects the primary and nearest four towers.");
                    }
                }
                require(hitCount == 5, id + " hits exactly five of nine adjacent defenses, got " + hitCount);
            }
        }
        context.succeed();
    }

    private record Fixture(SemionGame game, PlayerLane lane, Tower primary, Tower neighbor,
            SemionTowerEntity primaryEntity, SemionMonsterEntity attacker) implements AutoCloseable {
        static Fixture start(GameTestHelper context, String id, boolean dispatch) {
            UUID owner = UUID.randomUUID();
            UUID enemy = UUID.randomUUID();
            var defaults = EconomyConfig.defaultConfig();
            var economy = new EconomyConfig(defaults.startingDiamond(), defaults.startingEmerald(), defaults.startingIncome(),
                    defaults.emeraldCap(), defaults.emeraldProduction(), new EconomyConfig.TowerLimitConfig(25, 5, 5, 0, 25),
                    defaults.killReward(), defaults.teamTransfer(), defaults.emeraldIncomeBoost());
            SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
            try {
                require(game.selectJob(owner, DemonLordTowerJob.ID), "The sender must be a Demon Lord.");
                require(game.selectJob(enemy, DeveloperTowerJob.ID), "The defender must be a developer.");
                require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                        List.of(new AssignedParticipant(owner, "income-sender", TeamId.RED, 1),
                                new AssignedParticipant(enemy, "income-defender", TeamId.BLUE, 1)), Set.of(), 2)),
                        "The match must start.");
                PlayerLane lane = game.playerLane(enemy).orElseThrow();
                BlockPos at = adjacent(lane);
                game.players().get(enemy).economy().addMineral(100000);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        require(ProductionTowerService.placeTower(game, enemy, at.offset(dx, 0, dz), DeveloperTowers.ALPHA.id())
                                == TowerPlacementResult.SUCCESS, "Nine adjacent defenses must be placed.");
                    }
                }
                Tower primary = lane.towerAt(GridPosition.from(at));
                Tower neighbor = lane.towerAt(GridPosition.from(at.east()));
                SemionTowerEntity primaryEntity = context.getLevel().getEntitiesOfClass(SemionTowerEntity.class,
                                new AABB(at).inflate(4), entity -> entity.runtimeTower() == primary).getFirst();
                var unit = game.summonShop().find(id).orElseThrow();
                Monster monster;
                if (dispatch) {
                    var tower = new IncomeTower(IncomeTowerService.towerType(unit), owner, TeamId.RED, 1,
                            GridPosition.from(at), id);
                    monster = IncomeTowerService.createDispatch(game, game.players().get(owner), tower,
                            TeamId.BLUE, 1).orElseThrow();
                } else {
                    monster = unit.createMonster(new SummonContext(game, game.players().get(owner)), TeamId.BLUE, 1, 1);
                }
                require(IncomeTowerService.isDispatch(monster) == dispatch, "Only tower dispatches carry the marker.");
                var attacker = lane.spawnMonsterAt(monster, primaryEntity.position().add(-2, 0, 0)).orElseThrow();
                attacker.setNoAi(true);
                return new Fixture(game, lane, primary, neighbor, primaryEntity, attacker);
            } catch (Throwable failure) {
                game.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            game.close();
        }
    }

    private static BlockPos adjacent(PlayerLane lane) {
        var bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x < bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                var position = new BlockPos(x, bounds.min().getY(), z);
                boolean fits = true;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        fits &= lane.canPlaceTowerAt(position.offset(dx, 0, dz));
                    }
                }
                if (fits) return position;
            }
        }
        throw new AssertionError("The fixture needs adjacent tower plots.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
