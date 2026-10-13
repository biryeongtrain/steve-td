package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.summon.invasion.InvasionUnits;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 침공군 유닛 능력: 선딜 타격, 고블린 즉시 처치, 엘프 은신, 골렘 어그로 무시, 강령술사 소환, 트롤 재생. */
public final class InvasionUnitGameTest {
    private static final UUID OWNER = stableUuid("invasion-unit-owner");
    private static final UUID ENEMY = stableUuid("invasion-unit-enemy");

    @GameTest(maxTicks = 60)
    public void goblinBreaksNonCombatTowersButOnlyScratchesCombatTowers(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            try {
                SemionMonsterEntity goblin = fixture.spawn("goblin_scout", fixture.workbenchEntity.position());
                goblin.attackStyle().hit(goblin, fixture.workbenchEntity);
                require(fixture.workbench.health() <= 0.0, "The goblin must break a non-combat tower in one hit.");

                double before = fixture.combat.health();
                goblin.attackStyle().hit(goblin, fixture.combatEntity);
                require(fixture.combat.health() > 0.0 && fixture.combat.health() < before,
                        "A combat tower only takes the goblin's normal damage.");
            } finally {
                fixture.close();
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void ogreDamageLandsWhenTheClubHitsNotWhenTheSwingStarts(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            SemionMonsterEntity ogre = fixture.spawn("ogre_champion", fixture.combatEntity.position());
            double before = fixture.combat.health();
            double workbenchBefore = fixture.workbench.health();
            int hitDelay = InvasionUnits.profile("ogre_champion").hitDelayTicks();
            ogre.startAttack(fixture.combatEntity);
            require(fixture.combat.health() == before && ogre.hasPendingHit(), "The swing must not deal damage at once.");
            context.runAfterDelay(hitDelay + 2, () -> guard(context, () -> {
                try {
                    require(fixture.combat.health() < before, "The club must land at its animation hit frame.");
                    require(fixture.workbench.health() < workbenchBefore,
                            "The ogre's slam also hits towers standing next to the target.");
                } finally {
                    fixture.close();
                }
                context.succeed();
            }));
        });
    }

    @GameTest(maxTicks = 60)
    public void elfHidesUnlessAttackingAndGolemTargetsDefenders(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            try {
                SemionMonsterEntity elf = fixture.spawn("elf_assassin", fixture.combatEntity.position());
                require(elf.isStealthed(), "An idle elf assassin is hidden and untargetable.");
                elf.startAttack(fixture.combatEntity);
                require(!elf.isStealthed(), "The elf is revealed while attacking.");

                SemionMonsterEntity golem = fixture.spawn("siege_golem", fixture.combatEntity.position());
                require(!golem.ignoresDefenses() && golem.canTargetDefense(fixture.combatEntity),
                        "The siege golem can acquire and attack lane defenders.");
                SemionMonsterEntity orc = fixture.spawn("orc_warrior", fixture.combatEntity.position());
                require(orc.canTargetDefense(fixture.combatEntity), "Ordinary units still fight towers.");
            } finally {
                fixture.close();
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 220)
    public void necromancerRaisesSkeletonsOneByOneWithoutRewards(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            fixture.spawn("necromancer", fixture.combatEntity.position().add(0, 0, 3));
            context.runAfterDelay(180, () -> guard(context, () -> {
                try {
                    List<Monster> skeletons = fixture.enemyLane.activeMonsters().stream()
                            .filter(monster -> monster.id().equals(InvasionUnits.NECROMANCER_MINION))
                            .toList();
                    require(!skeletons.isEmpty() && skeletons.size() <= 6,
                            "The necromancer raises up to six skeletons, got " + skeletons.size());
                    require(skeletons.stream().allMatch(monster -> monster.origin() == MonsterOrigin.FREE_AUGMENT
                                    && monster.mineralReward() == 0),
                            "Raised skeletons give no kill reward.");
                } finally {
                    fixture.close();
                }
                context.succeed();
            }));
        });
    }

    @GameTest(maxTicks = 80)
    public void trollRegeneratesEvenWhileFighting(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            // 재생만 보도록 전투 타워를 먼저 부숴 두고, 레인 입구 쪽에 세웁니다.
            fixture.enemyLane.killTower(fixture.combat);
            SemionMonsterEntity troll = fixture.enemyLane.spawnMonsterAt(fixture.create("troll_javelineer"),
                    fixture.enemyLane.laneLayout().spawn()).orElseThrow();
            Monster runtime = troll.runtimeMonster();
            runtime.syncHealth(runtime.maxHealth() * 0.3);
            troll.setHealth((float) runtime.health());
            double[] baseline = new double[1];
            // 첫 틱에 레인이 체력을 한 번 맞춰 두므로, 그 뒤 값을 기준으로 재생을 봅니다.
            context.runAfterDelay(2, () -> baseline[0] = runtime.health());
            context.runAfterDelay(42, () -> guard(context, () -> {
                try {
                    require(runtime.health() > baseline[0] + 5.0,
                            "The troll keeps regenerating in combat (" + baseline[0] + " -> " + runtime.health() + ").");
                } finally {
                    fixture.close();
                }
                context.succeed();
            }));
        });
    }

    /** 테스트 본문의 예외를 서버를 멈추지 않고 테스트 실패로 알립니다. */
    private static void guard(GameTestHelper context, Runnable body) {
        try {
            body.run();
        } catch (Throwable failure) {
            StringBuilder trace = new StringBuilder(failure.toString());
            StackTraceElement[] frames = failure.getStackTrace();
            for (int i = 0; i < Math.min(6, frames.length); i++) {
                trace.append(" @ ").append(frames[i]);
            }
            context.fail(net.minecraft.network.chat.Component.literal(trace.toString()));
        }
    }

    // ------------------------------------------------------------------ 준비

    @GameTest(maxTicks = 40)
    public void creakingNeverLosesMoreThanItsHitCapAtOnce(GameTestHelper context) {
        guard(context, () -> {
            Fixture fixture = Fixture.start(context);
            try {
                SemionMonsterEntity creaking = fixture.spawn("creaking", fixture.combatEntity.position());
                Monster monster = creaking.runtimeMonster();
                double max = monster.maxHealth();
                monster.damage(max * 10.0, kim.biryeong.semiontd.entity.monster.DamageType.TRUE);
                require(Math.abs(monster.health() - max * 0.95) < 1.0e-6,
                        "A huge hit must only take 5% of the creaking's max health, left " + monster.health() + "/" + max);
                require(creaking.isAlive(), "The creaking must survive the huge hit.");
            } finally {
                fixture.close();
            }
            context.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper context;
        final SemionGame game;
        final PlayerLane enemyLane;
        final Tower workbench;
        final Tower combat;
        final SemionTowerEntity workbenchEntity;
        final SemionTowerEntity combatEntity;

        private Fixture(GameTestHelper context, SemionGame game, PlayerLane enemyLane, Tower workbench, Tower combat,
                SemionTowerEntity workbenchEntity, SemionTowerEntity combatEntity) {
            this.context = context;
            this.game = game;
            this.enemyLane = enemyLane;
            this.workbench = workbench;
            this.combat = combat;
            this.workbenchEntity = workbenchEntity;
            this.combatEntity = combatEntity;
        }

        static Fixture start(GameTestHelper context) {
            SemionGame game = new SemionGame(
                    EconomyConfig.defaultConfig(),
                    WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            require(game.selectJob(OWNER, DemonLordTowerJob.ID), "The sender is a demon lord.");
            require(game.selectJob(ENEMY, DeveloperTowerJob.ID), "The defender is a developer builder.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                    new AssignedParticipant(OWNER, "invasion-red", TeamId.RED, 1),
                    new AssignedParticipant(ENEMY, "invasion-blue", TeamId.BLUE, 1)), Set.of(), 2)), "The game must start.");
            game.players().get(ENEMY).economy().addMineral(100_000);
            PlayerLane lane = game.playerLane(ENEMY).orElseThrow();
            BlockPos workbenchAt = emptyPosition(lane, 0);
            require(ProductionTowerService.placeTower(game, ENEMY, workbenchAt, DeveloperTowers.WORKBENCH.id())
                    == TowerPlacementResult.SUCCESS, "The workbench must be placed.");
            BlockPos combatAt = workbenchAt.east();
            if (!lane.canPlaceTowerAt(combatAt) || lane.hasTowerAt(GridPosition.from(combatAt))) {
                combatAt = emptyPosition(lane, 1);
            }
            require(ProductionTowerService.placeTower(game, ENEMY, combatAt, DeveloperTowers.ALPHA.id())
                    == TowerPlacementResult.SUCCESS, "The combat tower must be placed.");
            Tower workbench = lane.towerAt(GridPosition.from(workbenchAt));
            Tower combat = lane.towerAt(GridPosition.from(combatAt));
            return new Fixture(context, game, lane, workbench, combat, entity(context, workbench), entity(context, combat));
        }

        Monster create(String unit) {
            return SummonRegistry.find(unit).orElseThrow()
                    .createMonster(new SummonContext(game, game.players().get(OWNER)), TeamId.BLUE, enemyLane.laneId(), 1);
        }

        SemionMonsterEntity spawn(String unit, Vec3 near) {
            return enemyLane.spawnMonsterAt(create(unit), near.add(1.0, 0.0, 0.0)).orElseThrow();
        }

        void close() {
            game.close();
        }

        private static SemionTowerEntity entity(GameTestHelper context, Tower tower) {
            return context.getLevel().getEntitiesOfClass(SemionTowerEntity.class,
                            new AABB(context.absolutePos(BlockPos.ZERO)).inflate(256),
                            entity -> entity.runtimeTower() == tower)
                    .stream().findFirst().orElseThrow(() -> new AssertionError("The tower entity must exist."));
        }
    }

    private static BlockPos emptyPosition(PlayerLane lane, int skip) {
        var bounds = lane.laneLayout().laneArea();
        int found = 0;
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.min().getY(), z);
                if (lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(GridPosition.from(candidate)) && found++ == skip) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("No empty tower position was found.");
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
