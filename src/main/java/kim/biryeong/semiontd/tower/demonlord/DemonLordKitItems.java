package kim.biryeong.semiontd.tower.demonlord;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DemonLordKitItems {
    private static final String KIT_KEY = "semion_td_demon_lord_kit";

    private DemonLordKitItems() {
    }

    public static ItemStack mark(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(KIT_KEY, true));
        stack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    public static boolean isKitItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().getBooleanOr(KIT_KEY, false);
    }

    public static void clear(Container inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isKitItem(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
