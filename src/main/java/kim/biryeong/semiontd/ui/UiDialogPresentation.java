package kim.biryeong.semiontd.ui;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.core.Holder;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

import static kim.biryeong.semiontd.ui.UiDialogBodyRenderer.*;

final class UiDialogPresentation {
    private static final int BODY_WIDTH = 256;
    private static final int BUTTON_WIDTH = 180;

    private UiDialogPresentation() {
    }

    static void show(ServerPlayer player, String title, String body) {
        show(player, title, miniMessage(body));
    }

    static void show(ServerPlayer player, String title, Component body) {
        Dialog dialog = new NoticeDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        List.<DialogBody>of(new PlainMessage(body, BODY_WIDTH)),
                        List.of()
                ),
                NoticeDialog.DEFAULT_ACTION
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    static void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions) {
        showActions(player, title, body, actions, 2);
    }

    static void showActions(ServerPlayer player, String title, String body, List<ActionButton> actions, int columns) {
        showActions(player, title, body, actions, actionButton("닫기", "", "창을 닫습니다."), columns);
    }

    static void showActions(
            ServerPlayer player,
            String title,
            String body,
            List<ActionButton> actions,
            ActionButton exitAction,
            int columns
    ) {
        if (actions.isEmpty()) {
            showActions(player, title, actionDialogBodies(body, BODY_WIDTH), actions, columns);
            return;
        }
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        Component.literal(title),
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        actionDialogBodies(body, BODY_WIDTH),
                        List.of()
                ),
                actions,
                Optional.of(exitAction),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    static void showActions(ServerPlayer player, String title, List<DialogBody> bodies, List<ActionButton> actions, int columns) {
        showActions(player, Component.literal(title), bodies, actions, columns);
    }

    static void showActions(ServerPlayer player, Component title, List<DialogBody> bodies, List<ActionButton> actions, int columns) {
        if (actions.isEmpty()) {
            Dialog dialog = new NoticeDialog(
                    new CommonDialogData(
                            title,
                            Optional.empty(),
                            true,
                            false,
                            DialogAction.CLOSE,
                            bodies,
                            List.of()
                    ),
                    NoticeDialog.DEFAULT_ACTION
            );
            player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
            return;
        }
        Dialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        title,
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        bodies,
                        List.of()
                ),
                actions,
                Optional.of(actionButton("닫기", "", "창을 닫습니다.")),
                columns
        );
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(dialog)));
    }

    static ActionButton actionButton(String label, String command, String tooltip) {
        return actionButton(label, command, Component.literal(tooltip), BUTTON_WIDTH);
    }

    static ActionButton actionButton(String label, String command, Component tooltip, int width) {
        return actionButton(Component.literal(label), command, tooltip, width);
    }

    static ActionButton actionButton(Component label, String command, Component tooltip, int width) {
        Optional<net.minecraft.server.dialog.action.Action> action = command == null || command.isBlank()
                ? Optional.empty()
                : Optional.of(new StaticAction(new ClickEvent.RunCommand(command)));
        return new ActionButton(
                new CommonButtonData(label, Optional.of(tooltip), width),
                action
        );
    }

    static ActionButton actionSpacer() {
        return new ActionButton(new CommonButtonData(Component.empty(), Optional.empty(), 1), Optional.empty());
    }
}
