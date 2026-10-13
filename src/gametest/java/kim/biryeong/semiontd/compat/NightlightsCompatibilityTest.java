package kim.biryeong.semiontd.compat;

import com.flechazo.nightlights.block.AbstractLightBlock;
import com.flechazo.nightlights.block.AbstractNightLightBlock;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class NightlightsCompatibilityTest {
    @GameTest
    public void registeredLightsKeepIdsStateCodecsAndPolymerOverlays(GameTestHelper helper) {
        int count = 0;
        for (var id : BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals("nightlights")) continue;
            var block = BuiltInRegistries.BLOCK.getValue(id);
            var item = BuiltInRegistries.ITEM.getValue(id);
            helper.assertTrue(item == block.asItem(), Component.literal("Block item ID must match: " + id));
            helper.assertTrue(PolymerSyncedObject.getSyncedObject(BuiltInRegistries.BLOCK, block) != null,
                    Component.literal("Block must have Polymer overlay: " + id));
            helper.assertTrue(PolymerSyncedObject.getSyncedObject(BuiltInRegistries.ITEM, item) != null,
                    Component.literal("Item must have Polymer overlay: " + id));
            for (var state : List.of(block.defaultBlockState(), block.getStateDefinition().getPossibleStates().getLast())) {
                var encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
                helper.assertTrue(BlockState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow() == state,
                        Component.literal("Saved light state must round-trip: " + id));
            }
            count++;
        }
        helper.assertTrue(count == 82, Component.literal("Original 82 Nightlights blocks and items must remain registered"));
        helper.succeed();
    }

    @GameTest
    public void wearableLightsRetainBrightnessCycleAndHeadSlot(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        try {
            for (String name : List.of("frog_black", "mushroom_black", "octopus_light_gray")) {
                var block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nightlights", name));
                var stack = new ItemStack(block);
                var equippable = stack.get(DataComponents.EQUIPPABLE);
                helper.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.HEAD,
                        Component.literal("Wearable head slot must remain: " + name));
                level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(AbstractNightLightBlock.FACING, Direction.WEST));
                int[] brightness = {8, 13, 8, 8};
                boolean[] lit = {true, true, false, true};
                for (int step = 0; step < brightness.length; step++) {
                    var state = level.getBlockState(pos);
                    helper.assertTrue(state.getValue(AbstractLightBlock.BRIGHTNESS) == brightness[step]
                                    && state.getValue(AbstractLightBlock.LIT) == lit[step]
                                    && state.getValue(AbstractNightLightBlock.FACING) == Direction.WEST,
                            Component.literal("Original light cycle and orientation must remain: " + name + " step " + step));
                    if (step + 1 < brightness.length) {
                        state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    }
                }
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
