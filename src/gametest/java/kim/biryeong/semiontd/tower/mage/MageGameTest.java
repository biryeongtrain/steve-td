package kim.biryeong.semiontd.tower.mage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class MageGameTest {
    @GameTest(maxTicks = 120)
    public void prophetSurvivesAndExecutesOnlyTheFirstMatchingIncome(GameTestHelper context) {
        UUID owner = stableUuid("mage-prophecy-owner");
        MageStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        MageCoreTower core = core(context, owner, new BlockPos(3, 2, 3));
        String expected = MageTowers.predictionTypes().keySet().iterator().next();
        MageProphetTower prophet = new MageProphetTower(
                MageTowers.predictionTypes().get(expected), owner, TeamId.RED, 1,
                grid(context, new BlockPos(4, 2, 3))
        );
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            lane.addTower(core);
            lane.addTower(prophet);
            lane.markWaveStarted(1);
            require(!prophet.isDestroyed(lane), "A selected prophet must remain alive when the wave starts.");
            SemionTowerEntity prophetEntity = towerEntity(context, prophet);
            require(prophetEntity.isAlive(), "The prophet visual entity must remain alive.");

            Vec3 center = prophetEntity.position().add(0.0, 0.0, 2.0);
            SpawnedTarget natural = spawnTarget(context, lane, center, expected, Optional.empty());
            SpawnedTarget income = spawnTarget(context, lane, center.add(0.3, 0.0, 0.0), expected,
                    Optional.of(stableUuid("mage-prophecy-sender")));
            targets.add(natural);
            targets.add(income);
            int before = MageStates.state(owner).mana();
            prophet.tick(lane);

            require(natural.runtime().isAlive(), "A natural-wave monster with the same id must be ignored.");
            require(!income.runtime().isAlive(), "The first matching income must be executed.");
            int reward = TowerBalanceRuntime.abilityInt(
                    MageBalance.GLOBAL_ID, "prophecyReward", MageBalance.PROPHECY_REWARD
            );
            require(MageStates.state(owner).mana() - before == reward,
                    "A successful prophecy must grant the configured mana reward once.");
            require(owner.equals(income.runtime().lastHitPlayerId().orElse(null))
                            && income.runtime().lastHitSourceKind() == KillSourceKind.TOWER,
                    "Prophecy TRUE damage must keep tower owner and kill attribution.");
            require(prophet.roundPhysicalDamageDealt() > 0.0,
                    "Prophecy TRUE damage must be included in tower damage statistics.");

            SpawnedTarget second = spawnTarget(context, lane, center.add(0.6, 0.0, 0.0), expected,
                    Optional.of(stableUuid("mage-prophecy-sender-two")));
            targets.add(second);
            prophet.tick(lane);
            require(second.runtime().isAlive(), "Each prophet may only execute one matching income per round.");

            require(lane.killTower(core), "The magic core must enter the normal destroyed lifecycle.");
            prophet.onWaveStarted(lane, 2);
            SpawnedTarget withoutCore = spawnTarget(context, lane, center.add(0.9, 0.0, 0.0), expected,
                    Optional.of(stableUuid("mage-prophecy-sender-three")));
            targets.add(withoutCore);
            prophet.tick(lane);
            require(withoutCore.runtime().isAlive(), "A prophecy must not trigger without a living magic core.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Mage prophecy GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            MageStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 120)
    public void deadUnitsProduceNoManaAndFinalDefenseDoesNotRechargeSupport(GameTestHelper context) {
        UUID owner = stableUuid("mage-mana-lifecycle");
        MageStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        MageCoreTower core = core(context, owner, new BlockPos(3, 2, 3));
        MageWizardTower deadWizard = new MageWizardTower(
                MageTowers.WIZARD, owner, TeamId.RED, 1, grid(context, new BlockPos(4, 2, 3))
        );
        MageProphetTower deadProphet = new MageProphetTower(
                MageTowers.PROPHET, owner, TeamId.RED, 1, grid(context, new BlockPos(5, 2, 3))
        );
        MageWizardTower support = new MageWizardTower(
                MageTowers.spellType(MageSpell.MAGIC_AMPLIFICATION), owner, TeamId.RED, 1,
                grid(context, new BlockPos(4, 2, 4))
        );
        try {
            lane.addTower(core);
            lane.addTower(deadWizard);
            lane.addTower(deadProphet);
            lane.addTower(support);
            require(lane.killTower(deadWizard) && lane.killTower(deadProphet),
                    "The idle mage units must enter the normal destroyed state.");
            int before = MageStates.state(owner).mana();
            MageTowerLifecycle.finishRound(lane, owner);
            int produced = MageStates.state(owner).mana() - before;
            require(produced == MageBalance.coreMana() + support.naturalManaProduction(),
                    "Only the living core and living idle support wizard may produce mana, produced=" + produced + '.');

            lane.markWaveStarted(2);
            MageStates.state(owner).addMana(100);
            support.tick(lane);
            require(support.spellUsed(), "The support spell must activate when the wave begins.");
            int afterSupport = MageStates.state(owner).mana();
            lane.moveTowersToFinalDefense();
            support.tick(lane);
            require(MageStates.state(owner).mana() == afterSupport,
                    "Final-defense movement must not recast or recharge a support spell.");

            require(lane.killTower(core), "The magic core must enter the normal destroyed lifecycle.");
            before = MageStates.state(owner).mana();
            MageTowerLifecycle.finishRound(lane, owner);
            int after = MageStates.state(owner).mana();
            require(after == before,
                    "Natural mana production must stop while the magic core is destroyed, before="
                            + before + ", after=" + after + ", coreHealth=" + core.health() + '.');
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Mage mana lifecycle GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            lane.clearTowers();
            MageStates.clear(owner);
        }
    }

    @GameTest(maxTicks = 120)
    public void windCutterUsesMagicDamageAndStopsAtTenTargets(GameTestHelper context) {
        UUID owner = stableUuid("mage-wind-cutter");
        MageStates.clear(owner);
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        MageCoreTower core = core(context, owner, new BlockPos(3, 2, 3));
        MageWizardTower wind = new MageWizardTower(
                MageTowers.spellType(MageSpell.WIND_CUTTER), owner, TeamId.RED, 1,
                grid(context, new BlockPos(4, 2, 3))
        );
        MageWizardTower amplification = new MageWizardTower(
                MageTowers.spellType(MageSpell.MAGIC_AMPLIFICATION), owner, TeamId.RED, 1,
                grid(context, new BlockPos(5, 2, 3))
        );
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            AreaEffectLaneIndex.register(lane);
            lane.addTower(core);
            lane.addTower(wind);
            lane.addTower(amplification);
            lane.markWaveStarted(1);
            MageStates.state(owner).addMana(1_000);
            for (int cast = 0; cast < MageBalance.ARCHMAGE_CASTS; cast++) {
                require(wind.tryBeginCast(MageSpell.WIND_CUTTER), "Archmage cast setup must spend mana.");
            }
            require(amplification.tryBeginCast(MageSpell.MAGIC_AMPLIFICATION),
                    "The nearby amplification spell must activate.");
            MageStates.state(owner).addMana(1_000);

            Vec3 source = towerEntity(context, wind).position();
            for (int index = 0; index < 12; index++) {
                targets.add(spawnTarget(
                        context,
                        lane,
                        source.add(0.0, 0.0, 2.0 + index * 0.3),
                        "mage-wind-target-" + index,
                        Optional.empty()
                ));
            }
            wind.tick(lane);

            long damaged = targets.stream().filter(target -> target.runtime().health() < 200.0).count();
            require(damaged == MageBalance.WIND_CUTTER_MAX_TARGETS,
                    "Wind Cutter must stop at its ten-target cap, damaged=" + damaged + '.');
            requireClose(900.0, wind.roundMagicDamageDealt(),
                    "Wind Cutter must use the common magic damage path at the capped multiplier.");
            require(targets.stream().noneMatch(target -> !target.runtime().isAlive()),
                    "The target-cap check should damage, not kill, its test targets.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Mage Wind Cutter GameTest failed: "
                    + failure.getClass().getName() + ": " + failure.getMessage()));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
            MageStates.clear(owner);
        }
    }

    private static MageCoreTower core(GameTestHelper context, UUID owner, BlockPos position) {
        return new MageCoreTower(MageTowers.MAGIC_CORE, owner, TeamId.RED, 1, grid(context, position));
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position,
            String id,
            Optional<UUID> sender
    ) {
        Monster runtime = new Monster(id, TeamId.RED, 1, sender, Optional.empty(),
                200.0, 0.0, 20.0, AttackKind.MELEE, "minecraft:zombie", 5L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Mage test target must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static SemionTowerEntity towerEntity(GameTestHelper context, kim.biryeong.semiontd.tower.Tower tower) {
        return (SemionTowerEntity) context.getLevel().getEntity(
                ((kim.biryeong.semiontd.tower.EntityBackedTower) tower).entityId().orElseThrow()
        );
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(16, 6, 16));
        List<GridPosition> finalSlots = List.of(
                grid(context, new BlockPos(10, 2, 10)),
                grid(context, new BlockPos(11, 2, 10)),
                grid(context, new BlockPos(10, 2, 11)),
                grid(context, new BlockPos(11, 2, 11))
        );
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                BlockBounds.of(context.absolutePos(new BlockPos(2, 2, 2)), context.absolutePos(new BlockPos(2, 2, 2))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 13))),
                BlockBounds.of(min, max),
                finalSlots,
                1
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static GridPosition grid(GameTestHelper context, BlockPos relative) {
        return GridPosition.from(context.absolutePos(relative));
    }

    private static void prepareFloor(GameTestHelper context) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                BlockPos floor = context.absolutePos(new BlockPos(x, 1, z));
                context.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                context.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.001) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private record SpawnedTarget(Monster runtime, SemionMonsterEntity entity) {
    }
}
