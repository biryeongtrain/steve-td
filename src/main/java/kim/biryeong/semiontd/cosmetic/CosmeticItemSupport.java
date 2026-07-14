package kim.biryeong.semiontd.cosmetic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class CosmeticItemSupport {
    private static final String COSMETIC_ID_KEY = "semion_td_cosmetic_id";

    private CosmeticItemSupport() {
    }

    public static ItemStack equippedCopy(CosmeticCatalog.Entry entry) {
        ItemStack equipped = entry.item().copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, equipped, tag -> tag.putString(COSMETIC_ID_KEY, entry.id()));
        equipped.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        equipped.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return equipped;
    }

    public static boolean isCosmetic(ItemStack stack) {
        return !cosmeticId(stack).isBlank();
    }

    public static String cosmeticId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? "" : customData.getUnsafe().getStringOr(COSMETIC_ID_KEY, "");
    }
}
