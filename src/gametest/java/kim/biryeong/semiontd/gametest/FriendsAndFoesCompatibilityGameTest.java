package kim.biryeong.semiontd.gametest;

import com.faboslav.friendsandfoes.common.init.FriendsAndFoesItems;
import com.faboslav.friendsandfoes.common.init.FriendsAndFoesPotions;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;

public final class FriendsAndFoesCompatibilityGameTest {
    @GameTest
    public void oldLightningRodBlockAndItemDataStillMigrate(GameTestHelper helper) {
        var fixer = net.minecraft.util.datafix.DataFixers.getDataFixer();
        for (String stage : List.of("exposed", "weathered", "oxidized", "waxed", "waxed_exposed", "waxed_weathered", "waxed_oxidized")) {
            String oldId = "friendsandfoes:" + stage + "_lightning_rod";
            String newId = "minecraft:" + stage + "_lightning_rod";
            var block = new com.google.gson.JsonObject();
            block.addProperty("Name", oldId);
            var properties = new com.google.gson.JsonObject();
            properties.addProperty("facing", "north");
            block.add("Properties", properties);
            var migratedBlock = fixer.update(net.minecraft.util.datafix.fixes.References.BLOCK_STATE,
                    new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, block), 4543, 4544);
            helper.assertTrue(migratedBlock.get("Name").asString("").equals(newId), Component.literal("Old lightning-rod block must migrate: " + oldId));
            helper.assertTrue(migratedBlock.get("Properties").get("facing").asString("").equals("north"), Component.literal("Lightning-rod state must survive migration"));
            var item = new com.google.gson.JsonObject();
            item.addProperty("id", oldId);
            item.addProperty("count", 1);
            var migratedItem = fixer.update(net.minecraft.util.datafix.fixes.References.ITEM_STACK,
                    new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE, item), 4543, 4544);
            helper.assertTrue(migratedItem.get("id").asString("").equals(newId), Component.literal("Old lightning-rod item must migrate: " + oldId));
        }
        helper.succeed();
    }
    @GameTest
    public void reachingPotionMixesStillWork(GameTestHelper helper) {
        for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
            assertBrew(helper, bottle, Potions.AWKWARD, FriendsAndFoesItems.CRAB_CLAW.get(), bottle, FriendsAndFoesPotions.REACHING.holder());
            assertBrew(helper, bottle, FriendsAndFoesPotions.REACHING.holder(), Items.REDSTONE, bottle, FriendsAndFoesPotions.LONG_REACHING.holder());
            assertBrew(helper, bottle, FriendsAndFoesPotions.REACHING.holder(), Items.GLOWSTONE_DUST, bottle, FriendsAndFoesPotions.STRONG_REACHING.holder());
        }
        helper.succeed();
    }

    private static void assertBrew(GameTestHelper helper, Item bottle, Holder<Potion> inputPotion, Item reagent, Item outputBottle, Holder<Potion> outputPotion) {
        ItemStack input = new ItemStack(bottle);
        input.set(DataComponents.POTION_CONTENTS, new PotionContents(inputPotion));
        var brewing = new BrewingInput(input, new ItemStack(reagent));
        var recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.BREWING, brewing, helper.getLevel()).orElseThrow();
        ItemStack output = recipe.value().assemble(brewing);
        helper.assertTrue(output.is(outputBottle), Component.literal("Brewing must preserve the requested bottle conversion"));
        helper.assertTrue(output.get(DataComponents.POTION_CONTENTS).potion().filter(outputPotion::equals).isPresent(), Component.literal("Brewing must retain reaching potion ID and strength"));
    }

    @GameTest
    public void beehiveDropsRetainSilkTouchAndHoney(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack silkTool = new ItemStack(Items.DIAMOND_AXE);
        silkTool.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        var player = helper.makeMockServerPlayerInLevel();
        for (String wood : List.of("acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle", "mangrove", "spruce", "warped")) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    net.minecraft.resources.Identifier.parse("friendsandfoes:" + wood + "_beehive"));
            var state = block.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5);
            BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
            level.setBlockAndUpdate(pos, state);
            var beehive = level.getBlockEntity(pos);
            var silkDrops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, beehive, player, silkTool);
            helper.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(block.asItem()), Component.literal("Silk touch must drop the original hive: " + wood));
            var honey = silkDrops.getFirst().get(DataComponents.BLOCK_STATE);
            helper.assertTrue(honey != null && "5".equals(honey.properties().get("honey_level")), Component.literal("Silk touch must retain honey level: " + wood));
            var plainDrops = net.minecraft.world.level.block.Block.getDrops(state, level, pos, beehive, player, new ItemStack(Items.DIAMOND_AXE));
            helper.assertTrue(plainDrops.size() == 1 && plainDrops.getFirst().is(block.asItem()), Component.literal("Plain tool must retain the original hive drop: " + wood));
            var plainHoney = plainDrops.getFirst().get(DataComponents.BLOCK_STATE);
            helper.assertTrue(plainHoney != null && "0".equals(plainHoney.properties().get("honey_level")), Component.literal("Plain tool must reset honey to zero: " + wood));
            helper.assertTrue(java.util.Objects.equals(plainHoney, new ItemStack(block.asItem()).get(DataComponents.BLOCK_STATE)), Component.literal("Plain tool must retain the registered item default without copying the filled hive state: " + wood));
        }
        helper.succeed();
    }

    @GameTest
    public void buttercupRemainsCompostable(GameTestHelper helper) {
        helper.assertTrue(FriendsAndFoesItems.BUTTERCUP.get().components().has(DataComponents.COMPOSTABLE), Component.literal("Buttercup must remain compostable"));
        helper.succeed();
    }
}