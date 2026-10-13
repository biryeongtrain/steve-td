package kim.biryeong.semiontd.tower.magicschool;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.function.ObjIntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

final class SpellGuiLayout {
    private static final List<String> ROW_LABELS = List.of(
            "1단계 주문", "2단계 주문", "3단계 주문", "4단계 주문", "5단계 주문", "용서받지 못할 저주");

    private SpellGuiLayout() {
    }

    static void populate(SimpleGui gui, ObjIntConsumer<MagicSchoolSpell> spellSlot) {
        for (int slot = 0; slot < 54; slot++) gui.clearSlot(slot);
        for (int row = 0; row < ROW_LABELS.size(); row++) {
            gui.setSlot(row * 9, new GuiElementBuilder(row == 5 ? Items.WITHER_SKELETON_SKULL : Items.BOOK)
                    .setName(Component.literal(ROW_LABELS.get(row))
                            .withStyle(row == 5 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD)));
        }
        int[] columns = new int[ROW_LABELS.size()];
        for (MagicSchoolSpell spell : MagicSchoolSpell.values()) {
            int row = spell.tier() - 1;
            if (row < 0 || row >= ROW_LABELS.size() || columns[row] >= 8) {
                throw new IllegalStateException("Spell does not fit the six-row layout: " + spell.id());
            }
            spellSlot.accept(spell, row * 9 + 1 + columns[row]++);
        }
    }
}
