package kim.biryeong.semiontd.cosmetic;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class OffhandCosmeticClickTest {
    @GameTest
    public void outsideClicksKeepCosmeticProtectionWithoutIndexingSentinels(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        var menu = player.inventoryMenu;
        menu.clicked(-999, 0, ContainerInput.THROW, player);
        menu.clicked(-999, 0, ContainerInput.PICKUP, player);
        menu.clicked(-1, 0, ContainerInput.QUICK_CRAFT, player);
        ItemStack locked = CosmeticItemSupport.equippedCopy(new CosmeticCatalog.Entry(
                "offhand_click_regression", 0, EquipmentSlot.OFFHAND,
                new ItemStack(SemionCosmeticItems.items().getLast())));
        require(CosmeticItemSupport.isLockedOffhandCosmetic(locked), "Fixture must use a real locked offhand cosmetic");
        menu.setCarried(locked);
        menu.clicked(-999, 0, ContainerInput.PICKUP, player);
        require(menu.getCarried() == locked && locked.getCount() == 1, "Outside pickup must preserve locked carried cosmetic");
        menu.setCarried(ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, locked);
        int offhandSlot = -1;
        for (int index = 0; index < menu.slots.size(); index++) {
            if (menu.getSlot(index).getItem() == locked) {
                offhandSlot = index;
                break;
            }
        }
        require(offhandSlot >= 0, "Equipped cosmetic must occupy a real menu slot");
        menu.clicked(offhandSlot, 0, ContainerInput.PICKUP, player);
        require(menu.getCarried().isEmpty(), "Protected offhand slot cannot be picked up");
        require(player.getItemBySlot(EquipmentSlot.OFFHAND) == locked, "Protected offhand cosmetic remains equipped");
        context.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
