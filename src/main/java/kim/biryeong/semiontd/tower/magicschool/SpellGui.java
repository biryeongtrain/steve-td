package kim.biryeong.semiontd.tower.magicschool;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class SpellGui extends SimpleGui {
    private final ServerPlayer player;
    private final SemionGame game;
    private final MagicSchoolWizardTower tower;
    private RoundPhase displayedPhase;
    private kim.biryeong.semiontd.augment.AugmentSnapshot displayedAugments;

    public SpellGui(ServerPlayer player, SemionGame game, MagicSchoolWizardTower tower) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.game = game;
        this.tower = tower;
        setTitle(Component.literal("주문"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        displayedPhase = game.phase();
        displayedAugments = tower.augmentSnapshot();
        SpellGuiLayout.populate(this, (spell, index) -> {
            String unavailable = tower.spellUnavailableReason(spell);
            boolean unlocked = unavailable == null;
            boolean selected = tower.selectedSpell() == spell;
            var element = new GuiElementBuilder(spell == MagicSchoolSpell.MUGGLE_WAND ? Items.STICK : Items.ENCHANTED_BOOK)
                    .setName(Component.literal(spell.displayName()).withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal(!unlocked ? unavailable
                                    : selected ? "현재 지정된 주문" : "클릭: 주문 지정")
                            .withStyle(!unlocked ? ChatFormatting.RED : selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .glow(selected)
                    .setCallback((slot, type, action, clickedGui) -> {
                        if (!canManageTower()) {
                            close();
                            return;
                        }
                        var result = tower.changeSpell(game, player.getUUID(), spell);
                        if (result == MagicSchoolWizardTower.SpellChangeResult.INVALID_TOWER) {
                            close();
                            return;
                        }
                        String message = switch (result) {
                            case CHANGED -> spell.displayName() + " 주문을 지정했습니다.";
                            case ALREADY_SELECTED -> "이미 지정된 주문입니다.";
                            case INVALID_PHASE -> "전투 중에는 주문을 변경할 수 없습니다. 준비 시간에 변경하세요.";
                            case NOT_ENOUGH_EMERALDS -> "에메랄드가 부족합니다. 변경 비용: " + tower.spellChangeCost(spell) + " 에메랄드";
                            case UNAVAILABLE -> tower.spellUnavailableReason(spell);
                            default -> throw new IllegalStateException("Unexpected spell change result: " + result);
                        };
                        player.sendSystemMessage(Component.literal(message));
                        refresh();
                    });
            long cost = tower.spellChangeCost(spell);
            element.addLoreLine(Component.literal("변경 비용: " + (cost == 0 ? "무료" : cost + " 에메랄드"))
                    .withStyle(ChatFormatting.YELLOW));
            if (displayedPhase != RoundPhase.PREPARE_AND_SUMMON) {
                element.addLoreLine(Component.literal("준비 시간에만 주문 변경 가능").withStyle(ChatFormatting.RED));
            }
            if (unavailable != null) element.addLoreLine(Component.literal(unavailable).withStyle(ChatFormatting.RED));
            for (String line : spell.effectLines()) {
                element.addLoreLine(SemionText.mini(
                        "<gray>" + MagicSchoolSpell.highlightAttackCoefficients(line) + "</gray>"));
            }
            setSlot(index, element);
        });
    }

    private boolean canManageTower() {
        return tower.canManageSpells(game, player.getUUID());
    }

    @Override
    public void onTick() {
        if (!canManageTower()) {
            close();
        } else if (displayedPhase != game.phase() || !tower.augmentSnapshot().equals(displayedAugments)) refresh();
    }
}
