package kim.biryeong.semiontd.tower.demonlord;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * Where a demon lord spends level-up points.
 *
 * <p>Opened from the 준비 단계 hotbar, because that is when the player is deciding things anyway.
 * Points are spent one click at a time and never refunded within a match - the screen says so, so
 * nobody dumps ten points into the wrong line and finds out later.
 */
public final class DemonLordStatGui extends SimpleGui {
    private static final int FIRST_STAT_SLOT = 10;

    private final ServerPlayer owner;

    public DemonLordStatGui(ServerPlayer owner) {
        super(MenuType.GENERIC_9x3, owner, false);
        this.owner = owner;
        setTitle(Component.literal("스탯 포인트 분배"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        DemonLordState state = DemonLordStates.get(owner.getUUID());
        for (int slot = 0; slot < 27; slot++) {
            clearSlot(slot);
        }
        if (state == null) {
            setSlot(13, new GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("마왕 상태가 없습니다.").withStyle(ChatFormatting.RED)));
            return;
        }

        setSlot(4, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .setName(Component.literal("Lv." + state.level() + "  ·  남은 포인트 " + state.unspentPoints())
                        .withStyle(ChatFormatting.AQUA))
                .addLoreLineRaw(Component.literal("몹을 잡거나 라운드를 넘기면 레벨이 오릅니다.")
                        .withStyle(ChatFormatting.GRAY))
                .addLoreLineRaw(Component.literal("분배한 포인트는 이 경기 동안 되돌릴 수 없습니다.")
                        .withStyle(ChatFormatting.DARK_GRAY)));

        int slot = FIRST_STAT_SLOT;
        for (DemonLordStat stat : DemonLordStat.values()) {
            boolean affordable = state.unspentPoints() > 0;
            GuiElementBuilder builder = new GuiElementBuilder(stat.icon())
                    .setName(Component.literal(stat.displayName() + "  " + state.points(stat) + "포인트")
                            .withStyle(ChatFormatting.YELLOW))
                    .addLoreLineRaw(Component.literal(stat.description()).withStyle(ChatFormatting.GRAY))
                    .addLoreLineRaw(Component.literal("현재 " + currentEffect(state, stat))
                            .withStyle(ChatFormatting.GREEN))
                    .addLoreLineRaw(Component.literal(affordable ? "클릭: 1포인트 투자" : "남은 포인트가 없습니다.")
                            .withStyle(affordable ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            if (affordable) {
                builder.setCallback((clicked, type, action) -> {
                    if (state.allocate(stat)) {
                        state.markLoadoutDirty();
                        refresh();
                    }
                });
            }
            setSlot(slot++, builder);
        }

        setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((clicked, type, action) -> close()));
    }

    /** 지금 이 스탯이 실제로 주고 있는 값입니다. 포인트 숫자만으로는 체감이 안 옵니다. */
    private static String currentEffect(DemonLordState state, DemonLordStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> "최대 체력 " + Math.round(state.maxHealth());
            case ATTACK -> "피해 배율 " + percent(state.damageMultiplier());
            case DEFENSE -> "피해 감소 " + percent(state.damageReduction());
            case COOLDOWN -> "쿨타임 " + percent(state.cooldownMultiplier()) + " (낮을수록 좋음)";
            case SKILL_RANGE -> "범위 배율 " + percent(state.skillRangeMultiplier());
            case MOVE_SPEED -> "이동 속도 +" + percent(state.moveSpeedBonus());
        };
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100.0) + "%";
    }
}
