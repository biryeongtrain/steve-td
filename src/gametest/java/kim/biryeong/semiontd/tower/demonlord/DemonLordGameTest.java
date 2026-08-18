package kim.biryeong.semiontd.tower.demonlord;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class DemonLordGameTest {
    @GameTest
    public void cleanupOnlyRestoresFlightForAnExistingDemonLordState(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        try {
            player.getAbilities().mayfly = false;
            DemonLordStates.clear(player.getUUID());
            DemonLordService.cleanupPlayer(player);
            require(!player.getAbilities().mayfly, "Cleanup must not grant flight to an unrelated player.");

            DemonLordStates.getOrCreate(player.getUUID());
            player.getInventory().setItem(0, DemonLordKitItems.mark(new ItemStack(Items.NETHERITE_SWORD)));
            DemonLordService.cleanupPlayer(player);
            require(player.getAbilities().mayfly, "Demon lord cleanup must restore flight.");
            require(DemonLordStates.get(player.getUUID()) == null, "Demon lord state must be cleared.");
            require(player.getInventory().getItem(0).isEmpty(), "Marked combat kit items must be removed.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord cleanup GameTest failed: " + failure.getMessage()));
        } finally {
            DemonLordStates.clear(player.getUUID());
            player.discard();
        }
    }

    @GameTest
    public void altarDamageUsesSharedDefenseStatisticsAndKillAttribution(GameTestHelper context) {
        UUID owner = stableUuid("demon-lord-damage-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        DemonLordSkillTower altar = altar(context, owner, DemonLordSkill.WAVE_OF_MALICE, 1, 3, 3);
        ArrayList<SpawnedTarget> targets = new ArrayList<>();
        try {
            lane.addTower(altar);
            SpawnedTarget armored = spawnTarget(context, lane, new BlockPos(5, 2, 5), 100.0, 100.0);
            SpawnedTarget magic = spawnTarget(context, lane, new BlockPos(6, 2, 5), 100.0, 100.0);
            targets.add(armored);
            targets.add(magic);

            Tower.DamageResult physical = DemonLordService.dealDamage(
                    null, lane, altar, armored.entity(), 50.0, DamageType.PHYSICAL);
            Tower.DamageResult magical = DemonLordService.dealDamage(
                    null, lane, altar, magic.entity(), 50.0, DamageType.MAGIC);

            requireClose(25.0, physical.dealtDamage(), "Physical damage must respect armor.");
            requireClose(50.0, magical.dealtDamage(), "Magic damage must ignore armor when resistance is zero.");
            requireClose(25.0, altar.roundPhysicalDamageDealt(), "Physical damage statistics must be recorded once.");
            requireClose(50.0, altar.roundMagicDamageDealt(), "Magic damage statistics must be recorded once.");

            DemonLordService.dealDamage(null, lane, altar, magic.entity(), 1_000.0, DamageType.TRUE);
            require(!magic.runtime().isAlive(), "True damage must finish the target.");
            require(owner.equals(magic.runtime().lastHitPlayerId().orElse(null))
                            && magic.runtime().lastHitSourceKind() == KillSourceKind.TOWER,
                    "Demon lord skill kills must stay attributed to the altar owner.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord damage GameTest failed: " + failure.getMessage()));
        } finally {
            targets.forEach(target -> target.entity().discard());
            lane.clearTowers();
        }
    }

    @GameTest
    public void upgradesKeepBuildOrderAndEverySkillUsesTheSharedVfxPath(GameTestHelper context) {
        UUID owner = stableUuid("demon-lord-vfx-owner");
        PlayerLane lane = testLane(context, owner);
        prepareFloor(context);
        List<DemonLordSkillTower> altars = new ArrayList<>();
        try {
            int index = 0;
            for (DemonLordSkill skill : DemonLordSkill.values()) {
                DemonLordSkillTower altar = altar(
                        context, owner, skill, 1, 2 + index % 5 * 2, 2 + index / 5 * 3);
                lane.addTower(altar);
                altars.add(altar);
                index++;
            }
            DemonLordSkillTower upgraded = altar(context, owner, DemonLordSkill.DEMON_WINGS, 2, 4, 2);
            require(lane.replaceTower(altars.get(1), upgraded), "The second altar must upgrade in place.");
            List<DemonLordSkillTower> ordered = DemonLordService.orderedAltars(lane, owner);
            require(ordered.get(0).skill() == DemonLordSkill.WAVE_OF_MALICE
                            && ordered.get(1) == upgraded
                            && ordered.get(2).skill() == DemonLordSkill.SKY_BREAKER,
                    "Upgrading must not change build-order bindings.");

            TowerVfxService.resetStats();
            for (DemonLordSkillTower altar : ordered) {
                require(DemonLordVfx.showDebug(altar, lane, altar.entity(lane).position()),
                        altar.skill() + " must enter the shared combat VFX path.");
            }
            require(TowerVfxService.statsSummary().contains("queued=10"),
                    "All ten skills must be queued through the shared VFX budget: "
                            + TowerVfxService.statsSummary());
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord VFX GameTest failed: " + failure.getMessage()));
        } finally {
            lane.clearTowers();
            TowerVfxService.resetStats();
        }
    }

    private static DemonLordSkillTower altar(
            GameTestHelper context,
            UUID owner,
            DemonLordSkill skill,
            int tier,
            int x,
            int z
    ) {
        GridPosition position = grid(context, new BlockPos(x, 2, z));
        return new DemonLordSkillTower(
                DemonLordTowers.tower(skill, tier), owner, TeamId.RED, 1, position, position);
    }

    private static SpawnedTarget spawnTarget(
            GameTestHelper context,
            PlayerLane lane,
            BlockPos relative,
            double health,
            double armor
    ) {
        Monster runtime = new Monster(
                "demon-lord-target-" + relative.toShortString(),
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                health,
                armor,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(runtime, lane.laneLayout());
        Vec3 position = Vec3.atCenterOf(context.absolutePos(relative));
        entity.setPos(position.x, position.y, position.z);
        require(context.getLevel().addFreshEntity(entity), "Demon lord test target must spawn.");
        runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(runtime);
        return new SpawnedTarget(runtime, entity);
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(16, 6, 16));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))),
                BlockBounds.of(min, min),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(13, 2, 13))),
                BlockBounds.of(min, max),
                List.of(grid(context, new BlockPos(10, 2, 10))),
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
