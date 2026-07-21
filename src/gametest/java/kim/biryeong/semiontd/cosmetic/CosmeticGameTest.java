package kim.biryeong.semiontd.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.sgui.api.ClickType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.progression.SemionProgressionStore;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerSwapWithOffhandEvent;

public final class CosmeticGameTest {
    @GameTest
    public void builtInHatItemsArePolymerHeadwear(GameTestHelper context) {
        try {
            List<net.minecraft.world.item.Item> items = SemionCosmeticItems.items();
            assertEquals(120, items.size());
            for (int index = 0; index < items.size(); index++) {
                net.minecraft.world.item.Item item = items.get(index);
                ItemStack stack = new ItemStack(item);
                assertTrue(item instanceof PolymerItem, "Cosmetic item should expose a Polymer representation.");
                assertEquals(1, stack.getMaxStackSize());
                assertTrue(stack.get(DataComponents.EQUIPPABLE) != null, "Cosmetic item should be equippable.");
                assertEquals(index == 119 ? EquipmentSlot.OFFHAND : EquipmentSlot.HEAD,
                        stack.get(DataComponents.EQUIPPABLE).slot());
                assertEquals(BuiltInRegistries.ITEM.getKey(item), stack.get(DataComponents.ITEM_MODEL));
            }
            assertEquals("해적선장 모자", new ItemStack(items.getFirst()).getHoverName().getString());
            assertEquals("토끼 슬링백", new ItemStack(items.getLast()).getHoverName().getString());
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal("Built-in cosmetic hats failed: " + throwable.getMessage()));
        }
    }

    @GameTest
    public void catalogRoundTripPreservesAllItemComponentsAndOrder(GameTestHelper context) {
        try {
            Path path = Files.createTempDirectory("semion-cosmetic-catalog").resolve("cosmetics.json");
            CosmeticCatalog catalog = new CosmeticCatalog(path);
            catalog.load(context.getLevel().registryAccess());
            ItemStack original = componentRichHelmet(context);

            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    catalog.add(context.getLevel().registryAccess(), "crown", 25, original));
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    catalog.add(context.getLevel().registryAccess(), "halo", 40, new ItemStack(Items.GOLDEN_HELMET)));
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    catalog.add(context.getLevel().registryAccess(), "rabbit", 60, EquipmentSlot.OFFHAND,
                            new ItemStack(SemionCosmeticItems.items().getLast())));

            CosmeticCatalog reloaded = new CosmeticCatalog(path);
            assertTrue(reloaded.load(context.getLevel().registryAccess()), "Catalog should reload from disk.");
            assertEquals(List.of("crown", "halo", "rabbit"),
                    reloaded.entries().stream().map(CosmeticCatalog.Entry::id).toList());
            assertEquals(EquipmentSlot.HEAD, reloaded.find("crown").orElseThrow().slot());
            assertEquals(EquipmentSlot.OFFHAND, reloaded.find("rabbit").orElseThrow().slot());
            ItemStack restored = reloaded.find("crown").orElseThrow().item();
            assertEquals(1, restored.getCount());
            assertTrue(ItemStack.isSameItemSameComponents(original.copyWithCount(1), restored),
                    "Item codec should preserve custom name, lore, model, enchantment and custom data.");

            JsonObject legacyRoot = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            legacyRoot.getAsJsonArray("entries").get(0).getAsJsonObject().remove("slot");
            Files.writeString(path, legacyRoot.toString());
            assertTrue(reloaded.load(context.getLevel().registryAccess()), "Legacy head entries should remain loadable.");
            assertEquals(EquipmentSlot.HEAD, reloaded.find("crown").orElseThrow().slot());

            Files.writeString(path, "{ invalid");
            assertFalse(reloaded.load(context.getLevel().registryAccess()), "Invalid reload should fail.");
            assertEquals(List.of("crown", "halo", "rabbit"),
                    reloaded.entries().stream().map(CosmeticCatalog.Entry::id).toList(),
                    "Failed reload should preserve the active catalog.");
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal("Cosmetic catalog round trip failed: " + throwable.getMessage()));
        }
    }

    @GameTest
    public void shopClickFlowPagesAndEquipmentLifecycleWork(GameTestHelper context) {
        try {
            ServerPlayer player = context.makeMockServerPlayerInLevel();
            Path directory = Files.createTempDirectory("semion-cosmetic-shop");
            Path profilesPath = directory.resolve("profiles.json");
            SemionProgressionStore seed = new SemionProgressionStore(profilesPath);
            seed.putProfile(
                    player.getUUID(),
                    SemionPlayerProfile.fresh(player.getGameProfile().getName()).recordMatch(
                            player.getGameProfile().getName(), true, 1_000
                    )
            );

            SemionGameManager manager = new SemionGameManager();
            manager.configure(
                    EconomyConfig.defaultConfig(),
                    WaveConfig.defaultConfig(),
                    MapConfig.defaultConfig(),
                    ProgressionConfig.defaultConfig(),
                    profilesPath
            );
            CosmeticService service = new CosmeticService(manager, directory.resolve("cosmetics.json"));
            service.load(context.getLevel().getServer());
            ItemStack crown = new ItemStack(Items.DIAMOND_HELMET);
            crown.set(DataComponents.CUSTOM_NAME, Component.literal("Crown"));
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    service.add(context.getLevel().getServer(), "crown", 25, crown));

            CosmeticShopGui gui = new CosmeticShopGui(player, service);
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            SemionPlayerProfile purchased = service.profile(player);
            assertTrue(purchased.ownsCosmetic("crown"), "First click should purchase the item.");
            assertEquals(975L, purchased.cosmeticCurrency());
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Purchase should not auto-equip.");

            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertEquals("crown", service.profile(player).selectedCosmeticId());
            assertEquals(List.of("crown"), service.profile(player).selectedCosmeticIds());
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).hasNonDefault(DataComponents.CREATIVE_SLOT_LOCK),
                    "Equipped cosmetic should be locked in creative inventory.");
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).hasNonDefault(DataComponents.UNBREAKABLE),
                    "Equipped cosmetic should be unbreakable.");

            SemionHotbarService.grantMatchTools(player);
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            assertFalse(cosmeticArmorSlot(player).mayPickup(player), "Inventory armor slot must reject manual removal.");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
            assertEquals(
                    InteractionResult.FAIL,
                    UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND),
                    "Right-click head equipment replacement should be blocked."
            );

            invokeDropEquipment(player);
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Cosmetic should be removed before death drops.");
            assertFalse(context.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    player.getBoundingBox().inflate(4),
                    entity -> CosmeticItemSupport.isCosmetic(entity.getItem())
            ).iterator().hasNext(), "Death should not create a cosmetic item entity.");
            service.syncPlayer(player);
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)),
                    "Selected cosmetic should be restored after respawn or reconnect sync.");

            ItemStack updated = new ItemStack(Items.GOLDEN_HELMET);
            updated.set(DataComponents.CUSTOM_NAME, Component.literal("Updated Crown"));
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    service.update(context.getLevel().getServer(), "crown", 30, updated));
            service.syncPlayer(player);
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET),
                    "Catalog update should refresh the equipped copy.");

            CosmeticCatalog externalCatalog = new CosmeticCatalog(directory.resolve("cosmetics.json"));
            assertTrue(externalCatalog.load(context.getLevel().registryAccess()));
            ItemStack reloaded = new ItemStack(Items.CHAINMAIL_HELMET);
            reloaded.set(DataComponents.CUSTOM_NAME, Component.literal("Reloaded Crown"));
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    externalCatalog.update(context.getLevel().registryAccess(), "crown", 35, reloaded));
            assertTrue(service.reload(context.getLevel().getServer()), "Service should reload the catalog from disk.");
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET),
                    "Catalog reload should refresh equipped cosmetics for online players.");

            ItemStack rabbit = new ItemStack(SemionCosmeticItems.items().getLast());
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                    service.add(context.getLevel().getServer(), "rabbit", 150, EquipmentSlot.OFFHAND, rabbit));
            service.handleCatalogClick(player, "rabbit");
            service.handleCatalogClick(player, "rabbit");
            assertEquals(List.of("crown", "rabbit"), service.profile(player).selectedCosmeticIds(),
                    "Cosmetics in different equipment slots should stay selected together.");
            assertEquals("rabbit", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.OFFHAND)));
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            SemionHotbarService.grantMatchTools(player);
            assertEquals("rabbit", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.OFFHAND)),
                    "Match inventory reset should preserve the offhand cosmetic.");
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)),
                    "Match inventory reset should preserve the head cosmetic at the same time.");
            int cosmeticSlot = cosmeticMenuSlot(player);
            player.inventoryMenu.clicked(
                    cosmeticSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player
            );
            try (var invokers = Stimuli.select().forEntity(player)) {
                assertEquals(EventResult.DENY,
                        invokers.get(PlayerSwapWithOffhandEvent.EVENT).onSwapWithOffhand(player));
            }
            assertEquals("rabbit", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.OFFHAND)),
                    "Inventory clicks and the swap-hands key must be denied.");
            assertTrue(player.inventoryMenu.getCarried().isEmpty());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SemionCosmeticItems.items().getLast()));
            assertEquals(InteractionResult.FAIL,
                    UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND),
                    "Right-click offhand equipment replacement should be blocked.");

            invokeDropEquipment(player);
            assertTrue(player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty());
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
            service.syncPlayer(player);
            assertEquals("rabbit", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.OFFHAND)));
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            service.handleCatalogClick(player, "rabbit");
            assertTrue(player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty());
            assertEquals("crown", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            assertEquals(List.of("crown"), service.profile(player).selectedCosmeticIds());

            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertTrue(service.profile(player).selectedCosmeticIds().isEmpty(), "Occupied head slot should reject equip.");
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET));
            SemionHotbarService.grantMatchTools(player);
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                    "Match inventory reset should preserve the complete head slot.");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);

            for (int index = 1; index <= 45; index++) {
                ItemStack item = new ItemStack(Items.LEATHER_HELMET);
                item.set(DataComponents.CUSTOM_NAME, Component.literal("Cosmetic " + index));
                assertEquals(CosmeticCatalog.MutationResult.SUCCESS,
                        service.add(context.getLevel().getServer(), "extra_" + index, index, item));
            }
            gui.refresh();
            assertTrue(gui.getSlot(53) != null, "A catalog over 45 items should show the next-page button.");
            gui.click(53, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertEquals("Cosmetic 45", gui.getSlot(0).getItemStack().getHoverName().getString());

            CosmeticService.RemoveResult removed = service.remove(context.getLevel().getServer(), "crown");
            assertEquals(CosmeticCatalog.MutationResult.SUCCESS, removed.catalogResult());
            assertTrue(removed.profilesSaved());
            assertTrue(service.profile(player).ownsCosmetic("crown"), "Removing a listing must retain ownership history.");
            assertTrue(service.profile(player).selectedCosmeticIds().isEmpty());
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal("Cosmetic shop lifecycle failed: " + throwable.getMessage()));
        }
    }

    @GameTest
    public void cosmeticCommandTreeHasShopAliasAndOpOnlyManagement(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var semiontd = dispatcher.getRoot().getChild("semiontd");
        var cosmetic = semiontd == null ? null : semiontd.getChild("cosmetic");
        assertTrue(cosmetic != null, "Expected /semiontd cosmetic.");
        assertTrue(dispatcher.getRoot().getChild("치장") != null, "Expected /치장 alias.");
        CommandSourceStack nonOp = context.getLevel().getServer().createCommandSourceStack().withPermission(0);
        CommandSourceStack op = context.getLevel().getServer().createCommandSourceStack().withPermission(2);
        assertTrue(cosmetic.canUse(nonOp), "Players should be able to open the shop.");
        for (String childName : List.of("points", "add", "update", "remove", "list", "reload")) {
            var child = cosmetic.getChild(childName);
            assertTrue(child != null, "Missing cosmetic subcommand " + childName);
            assertFalse(child.canUse(nonOp), childName + " should require permission level 2.");
            assertTrue(child.canUse(op), childName + " should allow permission level 2.");
        }
        assertTrue(cosmetic.getChild("points").getChild("give").getChild("player").getChild("amount") != null,
                "Points command should accept one target player and a positive amount.");
        assertTrue(cosmetic.getChild("add").getChild("id").getChild("price").getChild("slot") != null,
                "Add command should accept an explicit slot.");
        assertTrue(cosmetic.getChild("update").getChild("id").getChild("price").getChild("slot") != null,
                "Update command should accept an explicit slot.");
        context.succeed();
    }

    private static ItemStack componentRichHelmet(GameTestHelper context) {
        ItemStack stack = new ItemStack(Items.DIAMOND_HELMET, 16);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Component Crown"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Original lore"))));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(2.5F), List.of(true), List.of("crown"), List.of(0x55AAFF)
        ));
        stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath("semion-td", "component_crown"));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("original", "kept"));
        stack.enchant(
                context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),
                3
        );
        return stack;
    }

    private static net.minecraft.world.inventory.Slot cosmeticArmorSlot(ServerPlayer player) {
        return player.inventoryMenu.slots.stream()
                .filter(slot -> CosmeticItemSupport.isCosmetic(slot.getItem()))
                .findFirst()
                .orElseThrow();
    }

    private static int cosmeticMenuSlot(ServerPlayer player) {
        for (int slot = 0; slot < player.inventoryMenu.slots.size(); slot++) {
            if (CosmeticItemSupport.isLockedOffhandCosmetic(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        throw new AssertionError("Locked offhand cosmetic slot was not found.");
    }

    private static void invokeDropEquipment(ServerPlayer player) throws Exception {
        Method method = Player.class.getDeclaredMethod("dropEquipment", ServerLevel.class);
        method.setAccessible(true);
        method.invoke(player, player.level());
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition) {
        assertTrue(condition, "Expected condition to be true.");
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition) {
        assertFalse(condition, "Expected condition to be false.");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual + ".");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + " Expected " + expected + " but got " + actual + ".");
        }
    }
}
