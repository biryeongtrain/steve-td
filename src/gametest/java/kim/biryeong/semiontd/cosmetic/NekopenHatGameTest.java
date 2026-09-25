package kim.biryeong.semiontd.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.sgui.api.ClickType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.config.BundledBalanceDefaults;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.progression.SemionProgressionStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

public final class NekopenHatGameTest {
    @GameTest
    public void nekopenHatCosts100PointsAndEquipsFromShop(GameTestHelper context) {
        try {
            Path directory = Files.createTempDirectory("semion-nekopen-shop");
            JsonObject bundled = BundledBalanceDefaults.load("cosmetics.json", JsonObject.class, null);
            JsonArray entries = new JsonArray();
            for (var element : bundled.getAsJsonArray("entries")) {
                if (element.getAsJsonObject().get("id").getAsString().equals("nekopen_hat")) {
                    entries.add(element);
                }
            }
            assertEquals(1, entries.size());
            JsonObject catalog = new JsonObject();
            catalog.add("entries", entries);
            Path catalogPath = directory.resolve("cosmetics.json");
            Files.writeString(catalogPath, catalog.toString());

            ServerPlayer player = context.makeMockServerPlayerInLevel();
            String playerName = player.getGameProfile().getName();
            Path profilesPath = directory.resolve("profiles.json");
            SemionGameManager manager = new SemionGameManager();
            manager.configure(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    MapConfig.defaultConfig(), ProgressionConfig.defaultConfig(), profilesPath);
            manager.grantCosmeticCurrency(player.getUUID(), playerName, 99);
            CosmeticService service = new CosmeticService(manager, catalogPath);
            service.load(context.getLevel().getServer());
            CosmeticCatalog.Entry entry = service.entries().getFirst();
            ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("semion-td", "nekopen_hat");
            assertEquals(100L, entry.price());
            assertEquals(EquipmentSlot.HEAD, entry.slot());
            assertEquals(modelId, BuiltInRegistries.ITEM.getKey(entry.item().getItem()));
            assertTrue(entry.item().getItem() instanceof PolymerItem);
            assertEquals(modelId, entry.item().get(DataComponents.ITEM_MODEL));

            CosmeticShopGui gui = new CosmeticShopGui(player, service);
            assertEquals("네코펭 모자", gui.getSlot(0).getItemStack().getHoverName().getString());
            assertTrue(gui.getSlot(0).getItemStack().get(DataComponents.LORE).lines().stream()
                    .anyMatch(line -> line.getString().equals("가격: 100 치장 포인트")));
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertFalse(service.profile(player).ownsCosmetic("nekopen_hat"));
            assertEquals(99L, service.profile(player).cosmeticCurrency());
            manager.grantCosmeticCurrency(player.getUUID(), playerName, 1);
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertTrue(service.profile(player).ownsCosmetic("nekopen_hat"));
            assertEquals(0L, service.profile(player).cosmeticCurrency());
            assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
            gui.click(0, ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ClickType.PICKUP);
            assertEquals("nekopen_hat", CosmeticItemSupport.cosmeticId(player.getItemBySlot(EquipmentSlot.HEAD)));
            assertEquals(modelId, player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.ITEM_MODEL));
            assertEquals(0L, service.profile(player).cosmeticCurrency());
            SemionPlayerProfile persisted = new SemionProgressionStore(profilesPath)
                    .getOrCreateProfile(player.getUUID(), playerName);
            assertTrue(persisted.ownsCosmetic("nekopen_hat"));
            assertEquals(List.of("nekopen_hat"), persisted.selectedCosmeticIds());
            context.succeed();
        } catch (Throwable throwable) {
            context.fail(Component.literal("Nekopen shop purchase/equip failed: " + throwable.getMessage()));
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
