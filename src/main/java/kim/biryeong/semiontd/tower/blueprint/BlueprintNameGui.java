package kim.biryeong.semiontd.tower.blueprint;

import static kim.biryeong.semiontd.tower.blueprint.BlueprintEditorGui.text;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** 설계도 이름 짓기(모루). 결과 칸을 누르면 이름을 정하고 편집 창으로 돌아갑니다. */
public final class BlueprintNameGui extends AnvilInputGui {
    private final ServerPlayer player;
    private final SemionGameManager gameManager;

    public BlueprintNameGui(ServerPlayer player, SemionGameManager gameManager) {
        super(player, false);
        this.player = player;
        this.gameManager = gameManager;
        setTitle(Component.literal("설계도 이름"));
        setLockPlayerInventory(true);
        setDefaultInputValue(BlueprintDraft.of(player.getUUID()).name);
        setSlot(1, new GuiElementBuilder(Items.ARROW)
                .setName(text("편집 창으로", ChatFormatting.YELLOW))
                .setCallback((slot, type, action, clickedGui) -> new BlueprintEditorGui(player, gameManager).open()));
        refreshConfirm();
    }

    @Override
    public void onInput(String input) {
        refreshConfirm();
    }

    private void refreshConfirm() {
        String name = BlueprintStates.sanitizeName(getInput());
        boolean valid = !name.isEmpty();
        GuiElementBuilder confirm = new GuiElementBuilder(valid ? Items.DYE.lime() : Items.BARRIER)
                .setName(text(valid ? "'" + name + "'(으)로 정하기" : "1~16자로 입력하세요", valid ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (valid) {
            confirm.setCallback((slot, type, action, clickedGui) -> {
                BlueprintDraft.of(player.getUUID()).name = name;
                new BlueprintEditorGui(player, gameManager).open();
            });
        }
        setSlot(2, confirm);
    }
}
