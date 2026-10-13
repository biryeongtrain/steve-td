package kim.biryeong.semiontd.ui.augment;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.augment.AugmentIconCategory;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.ui.rp.SemionUiFont;
import kim.biryeong.semiontd.util.TextUncenterer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class AugmentCardDialog {
    public static final int CARD_WIDTH = AugmentCardFrames.WIDTH;
    public static final int GAP = 10;
    public static final int CONTENT_WIDTH = CARD_WIDTH * 3 + GAP * 2;
    public static final int TOTAL_ROWS = AugmentCardFrames.CARD_ROWS + 1 + AugmentCardFrames.BUTTON_ROWS;
    private static final int TEXT_WIDTH = CARD_WIDTH - 18;

    private AugmentCardDialog() { }

    public static boolean show(ServerPlayer player, AugmentService.Screen screen) {
        if (!PolymerResourcePackUtils.hasMainPack(player)) return false;
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog(screen))));
        return true;
    }

    public static MultiActionDialog dialog(AugmentService.Screen screen) {
        if (screen.cards().size() != 3 || screen.buttons().size() != 7) {
            throw new IllegalArgumentException("Three cards and one shared reroll control required");
        }
        var common = new CommonDialogData(SemionText.mini(screen.title()), Optional.empty(), true,
                false, DialogAction.NONE, List.of(new PlainMessage(body(screen), CONTENT_WIDTH + 8)), List.of());
        List<ActionButton> navigation = screen.buttons().subList(4, screen.buttons().size()).stream()
                .map(button -> new ActionButton(new CommonButtonData(Component.literal(button.label()),
                        Optional.empty(), 100),
                        Optional.of(new StaticAction(new ClickEvent.RunCommand(button.command()))))).toList();
        return new MultiActionDialog(common, navigation, Optional.empty(), 3);
    }

    public static Component body(AugmentService.Screen screen) {
        List<List<Component>> titles = new ArrayList<>();
        List<List<Component>> descriptions = new ArrayList<>();
        for (var card : screen.cards()) {
            titles.add(lines(Component.literal(card.definition().displayName())
                    .withColor(AugmentCardFrames.color(card.definition().rarity())), 2));
            descriptions.add(lines(Component.literal(card.summary()).withColor(0xE3E9F5), 7));
        }
        var result = Component.empty();
        for (int row = 0; row < TOTAL_ROWS; row++) {
            if (row > 0) result.append("\n");
            result.append(hitRow(screen, row)).append(SemionUiFont.offset(-CONTENT_WIDTH));
            for (int slot = 0; slot < 3; slot++) {
                if (slot > 0) result.append(SemionUiFont.space(GAP));
                var card = screen.cards().get(slot);
                var rarity = card.definition().rarity();
                MutableComponent cell = Component.empty();
                if (row < AugmentCardFrames.CARD_ROWS) {
                    cell.append(AugmentCardFrames.card(rarity, AugmentIconCategory.of(card.definition()), row));
                    Component text = Component.empty();
                    if (row == 1) text = Component.literal(categoryLabel(card.definition())).withColor(AugmentCardFrames.color(rarity));
                    if (row >= 8 && row < 10) text = titles.get(slot).get(row - 8);
                    if (row >= 11 && row < 18) text = descriptions.get(slot).get(row - 11);
                    overlay(cell, text, CARD_WIDTH);
                    cell.withStyle(Style.EMPTY.withShadowColor(0));
                } else if (row == AugmentCardFrames.CARD_ROWS) {
                    cell.append(SemionUiFont.space(CARD_WIDTH));
                } else if (slot != 1) {
                    cell.append(SemionUiFont.space(CARD_WIDTH));
                } else {
                    boolean enabled = screen.canReroll();
                    int buttonRow = row - AugmentCardFrames.CARD_ROWS - 1;
                    cell.append(SemionUiFont.space(AugmentCardFrames.BUTTON_INSET));
                    var control = Component.empty()
                            .append(AugmentCardFrames.button(rarity, enabled, screen.rerollsRemaining(), buttonRow))
                            .append(SemionUiFont.offset(-1));
                    if (buttonRow == 1) {
                        var count = Component.literal(Integer.toString(screen.rerollsRemaining()))
                                .withColor(enabled ? AugmentCardFrames.color(rarity) : 0x65738A);
                        control.append(SemionUiFont.offset(-AugmentCardFrames.BUTTON_WIDTH))
                                .append(SemionUiFont.space(25)).append(count)
                                .append(fill(AugmentCardFrames.BUTTON_WIDTH - 25 - TextUncenterer.preciseWidth(count)));
                    }
                    control.withStyle(Style.EMPTY.withShadowColor(0));
                    cell.append(control);
                    cell.append(SemionUiFont.space(AugmentCardFrames.BUTTON_INSET));
                }
                result.append(cell);
            }
        }
        return result;
    }

    private static Component hitRow(AugmentService.Screen screen, int row) {
        var result = Component.empty();
        if (row < AugmentCardFrames.CARD_ROWS) {
            for (int slot = 0; slot < 3; slot++) {
                if (slot > 0) result.append(SemionUiFont.space(GAP));
                result.append(Component.empty().append(SemionUiFont.space(CARD_WIDTH))
                        .withStyle(actionStyle(screen.buttons().get(slot))));
            }
        } else if (row > AugmentCardFrames.CARD_ROWS && screen.canReroll()) {
            int inset = CARD_WIDTH + GAP + AugmentCardFrames.BUTTON_INSET;
            result.append(SemionUiFont.space(inset));
            result.append(Component.empty().append(SemionUiFont.space(AugmentCardFrames.BUTTON_WIDTH))
                    .withStyle(actionStyle(screen.buttons().get(3))));
            result.append(SemionUiFont.space(inset));
        } else {
            result.append(SemionUiFont.space(CONTENT_WIDTH));
        }
        return result;
    }

    private static Style actionStyle(AugmentService.Button button) {
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(button.command()))
                .withShadowColor(0);
    }

    private static void overlay(MutableComponent cell, Component text, int width) {
        if (text.getString().isEmpty()) return;
        double advance = TextUncenterer.preciseWidth(text);
        int left = (int) Math.floor((width - advance) / 2);
        cell.append(SemionUiFont.offset(-width)).append(SemionUiFont.space(left))
                .append(text).append(fill(width - left - advance));
    }

    private static Component fill(double width) {
        var result = SemionUiFont.space(Math.max(0, (int) Math.floor(width)));
        if (width - Math.floor(width) > .25) result.append(SemionUiFont.halfPixel());
        return result;
    }

    private static List<Component> lines(Component text, int count) {
        var wrapped = TextUncenterer.splitLines(text, TEXT_WIDTH, "ko_kr");
        List<Component> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            if (index >= wrapped.size()) result.add(Component.empty());
            else if (index == count - 1 && wrapped.size() > count) {
                String line = wrapped.get(index).getString();
                while (!line.isEmpty() && TextUncenterer.width(Component.literal(line + "…")) > TEXT_WIDTH) {
                    line = line.substring(0, line.offsetByCodePoints(line.length(), -1));
                }
                result.add(Component.literal(line + "…").setStyle(text.getStyle()));
            } else result.add(wrapped.get(index));
        }
        return List.copyOf(result);
    }

    public static String categoryLabel(kim.biryeong.semiontd.augment.AugmentDefinition card) {
        return kim.biryeong.semiontd.augment.AugmentScope.of(card)
                == kim.biryeong.semiontd.augment.AugmentScope.JOB_SPECIFIC ? "전용" : "";
    }
}
