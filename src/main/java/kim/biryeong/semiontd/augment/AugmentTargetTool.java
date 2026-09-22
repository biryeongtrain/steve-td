package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** One reusable item; ownership and targets live in PlayerAugmentState, never in client item data. */
public final class AugmentTargetTool {
    private static final String MARKER = "semiontd_augment_target_tool";

    private AugmentTargetTool() {}

    public static boolean isTool(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.STICK) && data != null && data.getUnsafe().getBooleanOr(MARKER, false);
    }

    public static boolean grant(ServerPlayer online, PlayerAugmentState state, PlayerLane lane) {
        var selection = state.targetToolSelection().orElse(null);
        if (selection == null) {return false;}
        int slot = -1;
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            ItemStack existing = online.getInventory().getItem(index);
            if (!isTool(existing)) {continue;}
            if (slot < 0) {slot = index;} else {online.getInventory().setItem(index, ItemStack.EMPTY);}
        }
        if (slot < 0) {slot = online.getInventory().getFreeSlot();}
        if (slot < 0) {return false;}
        ItemStack item = new ItemStack(Items.STICK);
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putBoolean(MARKER, true));
        String name = AugmentCatalog.find(selection.augmentId()).orElseThrow().displayName();
        item.set(DataComponents.CUSTOM_NAME, Component.literal("증강 지정 · " + name).withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(targetLabel(selection, lane)));
        lore.add(Component.literal(AugmentService.targetCount(selection.augmentId()) == 2
                ? "타워 좌클릭: 선봉 · 우클릭: 포대" : "타워 좌클릭·우클릭: 지정"));
        lore.add(Component.literal("웅크리고 우클릭: 다음 증강 · 공중 우클릭: 해제"));
        lore.add(Component.literal("준비 시간에 변경 가능 · 들고 있으면 지정 타워 발광"));
        item.set(DataComponents.LORE, new ItemLore(lore));
        if (!ItemStack.matches(online.getInventory().getItem(slot), item)) {
            online.getInventory().setItem(slot, item);
            online.containerMenu.broadcastChanges();
        }
        return true;
    }

    public static String targetLabel(PlayerAugmentState.Selection selection, PlayerLane lane) {
        String primary = name(lane, selection.choice().primaryTargetId());
        return AugmentService.targetCount(selection.augmentId()) == 2
                ? "선봉: " + primary + " · 포대: " + name(lane, selection.choice().secondaryTargetId())
                : "대상: " + primary;
    }

    private static String name(PlayerLane lane, UUID id) {
        return lane == null || id == null ? "미지정" : lane.towers().stream().filter(tower -> tower.logicalId().equals(id))
                .map(tower -> tower.type().displayName()).findFirst().orElse("미지정");
    }

    public static void clear(ServerPlayer online) {
        for (int index = 0; index < online.getInventory().getContainerSize(); index++) {
            if (isTool(online.getInventory().getItem(index))) {online.getInventory().setItem(index, ItemStack.EMPTY);}
        }
    }
}
