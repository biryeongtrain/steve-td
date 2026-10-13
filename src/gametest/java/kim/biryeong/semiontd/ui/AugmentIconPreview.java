package kim.biryeong.semiontd.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentDescriptions;
import kim.biryeong.semiontd.augment.AugmentIconCategory;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.ui.augment.AugmentCardDialog;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class AugmentIconPreview {
    private AugmentIconPreview() { }

    public static void show(ServerPlayer player, int page) {
        if (!Boolean.getBoolean("semiontd.capture") || !player.getGameProfile().name().equals("SemionCapture")) {
            throw new IllegalStateException("The icon preview belongs to the isolated capture fixture");
        }
        var categories = AugmentIconCategory.values();
        var cards = new ArrayList<AugmentService.CardLine>();
        var buttons = new ArrayList<AugmentService.Button>();
        for (int slot = 0; slot < 3; slot++) {
            var category = categories[(page * 3 + slot) % categories.length];
            var card = AugmentCatalog.definitions().stream().filter(value -> AugmentIconCategory.of(value) == category)
                    .findFirst().orElseThrow();
            String description = AugmentDescriptions.describe(card, AugmentConfig.defaults());
            cards.add(new AugmentService.CardLine(category.label(), card.displayName(), card, description));
            buttons.add(new AugmentService.Button(card.displayName(), "", description));
        }
        while (buttons.size() < 7) buttons.add(new AugmentService.Button("", "", "아이콘 렌더링 미리보기"));
        var source = new AugmentService.Screen("아이콘 분류 미리보기", "", cards, buttons, 3,
                0, false);
        var preview = Component.empty();
        AugmentCardDialog.body(source).visit((style, text) -> {
            preview.append(Component.literal(text).setStyle(style.withClickEvent(null)));
            return Optional.empty();
        }, Style.EMPTY);
        var common = new CommonDialogData(Component.literal("효과별 아이콘 확인 " + (page + 1) + "/5 · 선택 불가"),
                Optional.empty(), true, false, DialogAction.NONE,
                List.of(new PlainMessage(preview, AugmentCardDialog.CONTENT_WIDTH + 8)), List.of());
        var close = new ActionButton(new CommonButtonData(Component.literal("닫기"), Optional.empty(), 100), Optional.empty());
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(
                new MultiActionDialog(common, List.of(close), Optional.empty(), 1))));
        player.setExperienceLevels(2060 + page);
    }
}
