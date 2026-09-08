package kim.biryeong.semiontd.ui;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.tower.gamble.GamblePoker;
import kim.biryeong.semiontd.tower.gamble.PokerTableTower;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.level.ServerPlayer;

public final class PokerTableDialog {
    private PokerTableDialog() {
    }

    public static String command(PokerTableTower tower) {
        var position = tower.managementPosition();
        return "/semiontd poker " + position.x() + " " + position.y() + " " + position.z()
                + " " + tower.betToken();
    }

    public static MultiActionDialog create(PokerTableTower tower, long diamonds) {
        Input bet = new Input("bet", new NumberRangeInput(300, Component.literal("베팅 다이아"),
                "options.generic_value", new NumberRangeInput.RangeInfo(
                        GamblePoker.MIN_BET, GamblePoker.MAX_BET, Optional.of((float) GamblePoker.MIN_BET),
                        Optional.of(1.0F))));
        ActionButton confirm = new ActionButton(
                new CommonButtonData(Component.literal("선택 금액으로 카드 3장 뽑기"),
                        Optional.of(Component.literal("단 한 번만 베팅하며, 8 하이 이하는 타워가 영구 파괴됩니다.")), 300),
                Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                        new JsonPrimitive(command(tower) + " $(bet)")).getOrThrow())));
        ActionButton cancel = new ActionButton(
                new CommonButtonData(Component.literal("취소"), Optional.empty(), 150), Optional.empty());
        String body = "보유 다이아: " + diamonds + "\n200~1000 다이아 · 한 번만 강화\n"
                + "패 점수 × 베팅액에 비례해 체력을 얻습니다.\n"
                + "8 하이 이하: 영구 파괴 / 9~J 하이: 대실패\n"
                + "상위 패라도 특수능력 점수가 부족하면 체력만 얻습니다.\n"
                + "베팅 비용은 판매 시 돌려받지 못합니다.";
        return new MultiActionDialog(new CommonDialogData(Component.literal("포커 테이블 베팅"),
                Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal(body), 360)), List.of(bet)),
                List.of(confirm), Optional.of(cancel), 1);
    }

    public static void show(ServerPlayer player, PokerTableTower tower, long diamonds) {
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(create(tower, diamonds))));
    }
}
