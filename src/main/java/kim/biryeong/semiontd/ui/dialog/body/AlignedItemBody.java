package kim.biryeong.semiontd.ui.dialog.body;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record AlignedItemBody(
        ItemStack item,
        Component description,
        boolean showTooltip,
        int width,
        int height
) {
}
