package kim.biryeong.semiontd.cosmetic;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;

public final class CosmeticItemSupport {
    private static final String COSMETIC_ID_KEY = "semion_td_cosmetic_id";
    private static final List<EquipmentSlot> SUPPORTED_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.OFFHAND);

    private CosmeticItemSupport() {
    }

    public static ItemStack equippedCopy(CosmeticCatalog.Entry entry) {
        ItemStack equipped = entry.item().copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, equipped, tag -> tag.putString(COSMETIC_ID_KEY, entry.id()));
        equipped.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        equipped.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return equipped;
    }

    public static List<EquipmentSlot> supportedSlots() {
        return SUPPORTED_SLOTS;
    }

    public static boolean supports(EquipmentSlot slot) {
        return SUPPORTED_SLOTS.contains(slot);
    }

    public static String slotName(EquipmentSlot slot) {
        return slot == EquipmentSlot.OFFHAND ? "왼손" : "머리";
    }

    public static boolean isCosmetic(ItemStack stack) {
        return !cosmeticId(stack).isBlank();
    }

    public static boolean isLockedOffhandCosmetic(ItemStack stack) {
        if (!isCosmetic(stack)) {
            return false;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.OFFHAND;
    }

    public static String cosmeticId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? "" : customData.copyTag().getStringOr(COSMETIC_ID_KEY, "");
    }
}
