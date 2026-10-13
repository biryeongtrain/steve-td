package kim.biryeong.semiontd.summon.invasion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class InvasionAreaAttackTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 60)
    public void ogreAndPriestSlamsHitAtMostFiveTowersStartingWithTheTarget(GameTestHelper context) {
        for (String unit : List.of("ogre_champion", "dark_priest")) {
            Arena arena = new Arena(context);
            try {
                List<Tower> grid = arena.grid(5, 5);
                Tower center = grid.get(12);
                SemionMonsterEntity attacker = arena.spawn(unit, 1, arena.position(center).add(-1.5, 0, 0));
                double[] before = arena.healths(grid);
                attacker.attackStyle().hit(attacker, arena.entity(center));
                double[] lost = arena.lost(grid, before);
                long hit = java.util.Arrays.stream(lost).filter(value -> value > 1.0e-6).count();
                context.assertTrue(hit == 5, unit + " must hit exactly five of the 25 packed towers, hit " + hit);
                context.assertTrue(lost[12] > 0.0, unit + " must always hit the tower it aimed at");
                for (int i = 0; i < grid.size(); i++) {
                    int dx = i / 5 - 2;
                    int dz = i % 5 - 2;
                    if (lost[i] > 0.0) {
                        context.assertTrue(dx * dx + dz * dz <= 1, unit + " must hit the towers nearest the impact first");
                    }
                }
            } finally {
                arena.close();
            }
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 60)
    public void ordinaryDwarfShotPreservesFullDamageThroughEveryTower(GameTestHelper context) {
        Arena arena = new Arena(context);
        try {
            List<Tower> row = arena.grid(4, 1);
            SemionMonsterEntity dwarf = arena.spawn("dwarf_gunner", 1, arena.position(row.getFirst()).add(-2.5, 0, 0));
            dwarf.setYRot(-90.0F);
            double[] before = arena.healths(row);
            dwarf.attackStyle().hit(dwarf, arena.entity(row.getFirst()));
            double[] lost = arena.lost(row, before);
            context.assertTrue(lost[0] > 0.0, "The first tower takes the full shot");
            for (int i = 1; i < lost.length; i++) {
                double expected = lost[0];
                context.assertTrue(Math.abs(lost[i] - expected) < 0.05,
                        "Pierced tower " + i + " must take " + expected + ", took " + lost[i]);
            }
        } finally {
            arena.close();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena", maxTicks = 60)
    public void priestHealGrowsWithTheRoundAndAddsPartOfTheTargetMaxHealth(GameTestHelper context) {
        int round = 20;
        Arena arena = new Arena(context);
        try {
            Vec3 at = arena.start.add(4, 0, 4);
            SemionMonsterEntity priest = arena.spawn("dark_priest", round, at);
            SemionMonsterEntity orc = arena.spawn("orc_warrior", round, at.add(1.0, 0, 0));
            Monster ally = orc.runtimeMonster();
            ally.syncHealth(ally.maxHealth() * 0.2);
            orc.setHealth((float) ally.health());
            double before = ally.health();
            Goal heal = new InvasionSummon(kim.biryeong.semiontd.config.SummonConfig.defaultConfig().summons().get("dark_priest"))
                    .createAbilityGoals(priest).getFirst();
            heal.tick();
            double expected = 16.0 * SummonBalancePolicy.summonHealthMultiplier(round) + ally.maxHealth() * 0.02;
            double healed = ally.health() - before;
            context.assertTrue(Math.abs(healed - expected) < 0.5,
                    "Round " + round + " priest heal must be " + expected + ", healed " + healed);
        } finally {
            arena.close();
        }
        context.succeed();
    }

    private static final class Arena {
        final GameTestHelper context;
        final BlockPos origin;
        final Vec3 start;
        final PlayerLane lane;
        final UUID owner = UUID.randomUUID();
        final SemionPlayer sender = new SemionPlayer(UUID.randomUUID(), "sender", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        final List<SemionMonsterEntity> monsters = new ArrayList<>();

        Arena(GameTestHelper context) {
            this.context = context;
            this.origin = context.absolutePos(new BlockPos(6, 3, 6));
            this.start = Vec3.atCenterOf(origin);
            var layout = new LaneRegionLayout(1, start, List.of(start.add(0, 0, 14)), start.add(0, 0, 20),
                    BlockBounds.of(origin.offset(-6, -1, -6), origin.offset(20, 5, 22)), List.of());
            this.lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
        }

        List<Tower> grid(int columns, int rows) {
            List<Tower> towers = new ArrayList<>();
            for (int x = 0; x < columns; x++) {
                for (int z = 0; z < rows; z++) {
                    Tower tower = new ProductionTower(TowerType.builder("area_attack_" + x + "_" + z, "Target")
                            .maxHealth(1_000_000).damage(1).range(0).aggroPriority(10).build(), owner,
                            TeamId.RED, 1, GridPosition.from(origin.offset(4 + x, 0, 4 + z)));
                    lane.addTower(tower);
                    towers.add(tower);
                }
            }
            return towers;
        }

        SemionMonsterEntity spawn(String unit, int round, Vec3 at) {
            Monster monster = SummonRegistry.find(unit).orElseThrow()
                    .createMonster(new SummonContext(null, sender), TeamId.RED, 1, round);
            SemionMonsterEntity entity = lane.spawnMonsterAt(monster, at).orElseThrow();
            entity.setNoAi(true);
            entity.setNoGravity(true);
            monsters.add(entity);
            return entity;
        }

        SemionTowerEntity entity(Tower tower) {
            return (SemionTowerEntity) context.getLevel().getEntity(((ProductionTower) tower).entityId().orElseThrow());
        }

        Vec3 position(Tower tower) {
            return entity(tower).position();
        }

        double[] healths(List<Tower> towers) {
            return towers.stream().mapToDouble(Tower::health).toArray();
        }

        double[] lost(List<Tower> towers, double[] before) {
            double[] lost = new double[towers.size()];
            for (int i = 0; i < lost.length; i++) {
                lost[i] = before[i] - towers.get(i).health();
            }
            return lost;
        }

        void close() {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
        }
    }
}
