package kim.biryeong.semiontd.tower.blueprint;

import static kim.biryeong.semiontd.tower.blueprint.BlueprintEditorGui.text;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/** 모듈 고르기(9×4). 클릭하면 1단계로 붙이고 편집 창으로 돌아갑니다. */
public final class BlueprintModuleGui extends SimpleGui {
    private final ServerPlayer player;
    private final SemionGameManager gameManager;

    public BlueprintModuleGui(ServerPlayer player, SemionGameManager gameManager) {
        super(MenuType.GENERIC_9x4, player, false);
        this.player = player;
        this.gameManager = gameManager;
        setTitle(Component.literal("모듈 고르기 (" + BlueprintModule.values().length + "종)"));
        setLockPlayerInventory(true);
        BlueprintDraft draft = BlueprintDraft.of(player.getUUID());
        int slot = 0;
        for (BlueprintModule module : BlueprintModule.values()) {
            boolean attached = draft.modules.containsKey(module);
            GuiElementBuilder builder = new GuiElementBuilder(BlueprintIcons.module(module))
                    .setName(text(module.displayName() + " (" + kindName(module.kind()) + ")",
                            attached ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA))
                    .addLoreLineRaw(text("1단계: " + BlueprintTexts.effect(module, 1), ChatFormatting.WHITE))
                    .addLoreLineRaw(text(BlueprintModule.MAX_LEVEL + "단계: " + BlueprintTexts.effect(module, BlueprintModule.MAX_LEVEL), ChatFormatting.WHITE))
                    .addLoreLineRaw(text(attached ? "이미 붙어 있습니다." : "클릭: 1단계로 붙이기", attached ? ChatFormatting.RED : ChatFormatting.GRAY));
            if (!attached) {
                builder.setCallback((index, type, action, clickedGui) -> {
                    BlueprintDraft current = BlueprintDraft.of(player.getUUID());
                    if (current.modules.size() < BlueprintPricing.maxModules()) {
                        current.modules.put(module, 1);
                    }
                    new BlueprintEditorGui(player, gameManager).open();
                });
            }
            setSlot(slot++, builder);
        }
        setSlot(31, new GuiElementBuilder(Items.ARROW)
                .setName(text("편집 창으로", ChatFormatting.YELLOW))
                .setCallback((index, type, action, clickedGui) -> new BlueprintEditorGui(player, gameManager).open()));
    }

    private static String kindName(BlueprintModule.Kind kind) {
        return switch (kind) {
            case OFFENSE -> "공격";
            case DEFENSE -> "생존";
            case UTILITY -> "유틸";
            case SUPPORT -> "지원";
            case SUMMON -> "소환";
        };
    }
}
