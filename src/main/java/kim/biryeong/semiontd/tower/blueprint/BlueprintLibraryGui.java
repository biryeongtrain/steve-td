package kim.biryeong.semiontd.tower.blueprint;

import static kim.biryeong.semiontd.tower.blueprint.BlueprintEditorGui.text;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * 계정에 저장된 설계도 목록(9×6). 새 설계를 시작하거나 쉬프트+우클릭으로 지웁니다.
 * 지금 밸런스로 가격을 다시 셈해 보여 주고, 한도에 안 맞게 된 설계는 쓸 수 없다고 표시합니다.
 */
public final class BlueprintLibraryGui extends SimpleGui {
    private final ServerPlayer player;
    private final SemionGameManager gameManager;

    public BlueprintLibraryGui(ServerPlayer player, SemionGameManager gameManager) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.gameManager = gameManager;
        setTitle(Component.literal("내 설계도"));
        setLockPlayerInventory(true);
        BlueprintService.ensureLoaded(player, gameManager);
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            clearSlot(slot);
        }
        List<BlueprintDesign> designs = BlueprintLibrary.designs(player.getUUID());
        for (int i = 0; i < Math.min(45, designs.size()); i++) {
            int index = i;
            BlueprintDesign design = designs.get(i);
            BlueprintStats stats = design.stats();
            long price = BlueprintPricing.price(stats);
            Optional<String> invalid = BlueprintPricing.validate(stats)
                    .or(() -> BlueprintVisuals.find(design.visualSourceId()).isEmpty()
                            ? Optional.of("고를 수 없는 겉모습") : Optional.empty());
            GuiElementBuilder builder = GuiElementBuilder.from(BlueprintVisuals.find(design.visualSourceId())
                            .map(option -> BlueprintVisuals.icon(option.visual()))
                            .orElse(new net.minecraft.world.item.ItemStack(Items.PAPER)))
                    .setName(text((i + 1) + ". " + design.name(), invalid.isEmpty() ? ChatFormatting.AQUA : ChatFormatting.RED))
                    .addLoreLineRaw(text("가격 " + price + " · 타워 수 " + BlueprintPricing.slotCost(price), ChatFormatting.GREEN))
                    .addLoreLineRaw(text(BlueprintTexts.basicDps(stats) + " · 체력 "
                            + BlueprintTexts.num(stats.maxHealth()) + " · 사거리 " + BlueprintTexts.num(stats.range()), ChatFormatting.WHITE));
            if (stats.targetPriority() != BlueprintTargetPriority.FIRST) {
                builder.addLoreLineRaw(text("대상 우선도: " + stats.targetPriority().displayName(), ChatFormatting.YELLOW));
            }
            stats.modules().forEach((module, level) ->
                    builder.addLoreLineRaw(text(module.displayName() + " " + level + "단계: " + BlueprintTexts.effect(module, level), ChatFormatting.GRAY)));
            invalid.ifPresent(reason -> builder.addLoreLineRaw(text("지금은 못 씀: " + reason, ChatFormatting.RED)));
            builder.addLoreLineRaw(text("쉬프트+우클릭: 지우기", ChatFormatting.DARK_GRAY));
            builder.setCallback((slot, type, action, clickedGui) -> {
                if (type.shift && type.isRight) {
                    BlueprintService.Outcome outcome = BlueprintService.delete(player, gameManager, index);
                    outcome.messages().forEach(message -> player.sendSystemMessage(
                            Component.literal(message).withStyle(outcome.success() ? ChatFormatting.YELLOW : ChatFormatting.RED)));
                    refresh();
                }
            });
            setSlot(i, builder);
        }
        boolean full = designs.size() >= BlueprintPricing.maxBlueprints();
        setSlot(49, new GuiElementBuilder(full ? Items.BARRIER : Items.WRITABLE_BOOK)
                .setName(text(full ? "설계도가 가득 찼습니다" : "새로 설계하기", full ? ChatFormatting.RED : ChatFormatting.GREEN))
                .addLoreLineRaw(text(designs.size() + "/" + BlueprintPricing.maxBlueprints() + "장", ChatFormatting.GRAY))
                .setCallback((slot, type, action, clickedGui) -> {
                    if (!full) {
                        new BlueprintEditorGui(player, gameManager).open();
                    }
                }));
        setSlot(45, new GuiElementBuilder(Items.BARRIER)
                .setName(text("닫기", ChatFormatting.RED))
                .setCallback((slot, type, action, clickedGui) -> close()));
    }
}
