package kim.biryeong.semiontd.tower.blueprint;

import static kim.biryeong.semiontd.tower.blueprint.BlueprintEditorGui.text;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/** 겉모습 고르기(9×6, 쪽마다 45개). 지금 게임에 있는 빌더 타워의 겉모습을 빌려 옵니다. */
public final class BlueprintVisualGui extends SimpleGui {
    private static final int PER_PAGE = 45;

    private final ServerPlayer player;
    private final SemionGameManager gameManager;

    public BlueprintVisualGui(ServerPlayer player, SemionGameManager gameManager, int page) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.gameManager = gameManager;
        List<BlueprintVisuals.Option> options = BlueprintVisuals.options();
        int pages = Math.max(1, (options.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        setTitle(Component.literal("겉모습 고르기 (" + (current + 1) + "/" + pages + ")"));
        setLockPlayerInventory(true);
        String selected = BlueprintDraft.of(player.getUUID()).visualSourceId;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= options.size()) {
                break;
            }
            BlueprintVisuals.Option option = options.get(index);
            GuiElementBuilder builder = GuiElementBuilder.from(BlueprintVisuals.icon(option.visual()))
                    .setName(text(option.sourceName(), option.sourceTowerId().equals(selected) ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .addLoreLineRaw(text("클릭: 이 겉모습으로", ChatFormatting.GRAY))
                    .setCallback((slot, type, action, clickedGui) -> {
                        BlueprintDraft.of(player.getUUID()).visualSourceId = option.sourceTowerId();
                        new BlueprintEditorGui(player, gameManager).open();
                    });
            if (option.sourceTowerId().equals(selected)) {
                builder.glow();
            }
            setSlot(i, builder);
        }
        if (current > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(text("이전 쪽", ChatFormatting.YELLOW))
                    .setCallback((slot, type, action, clickedGui) -> new BlueprintVisualGui(player, gameManager, current - 1).open()));
        }
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(text("편집 창으로", ChatFormatting.RED))
                .setCallback((slot, type, action, clickedGui) -> new BlueprintEditorGui(player, gameManager).open()));
        if (current < pages - 1) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(text("다음 쪽", ChatFormatting.YELLOW))
                    .setCallback((slot, type, action, clickedGui) -> new BlueprintVisualGui(player, gameManager, current + 1).open()));
        }
    }
}
