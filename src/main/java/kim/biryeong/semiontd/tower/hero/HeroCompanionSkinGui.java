package kim.biryeong.semiontd.tower.hero;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class HeroCompanionSkinGui extends SimpleGui {
    private static final int[] ROLE_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int[] RESET_SLOTS = {19, 20, 21, 22, 23, 24};

    private final ServerPlayer player;
    private final SemionGameManager gameManager;

    public HeroCompanionSkinGui(ServerPlayer player, SemionGameManager gameManager) {
        super(MenuType.GENERIC_9x4, player, false);
        this.player = player;
        this.gameManager = gameManager;
        setTitle(Component.literal("동료 스킨 설정"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < getSize(); slot++) {
            clearSlot(slot);
        }
        int index = 0;
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            HeroCompanionSkinPreference skin = HeroCompanionSkins.preference(player.getUUID(), role).orElse(null);
            GameProfile profile = HeroCompanionSkins.profile(player.getUUID(), role, skin);
            GuiElementBuilder roleButton = new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setSkullOwner(profile, player.getServer())
                    .setName(Component.literal(role.displayName() + " 스킨")
                            .withStyle(skin == null ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                    .addLoreLine(Component.literal(skin == null
                            ? "역할 기본 스킨"
                            : "적용 중: " + skin.sourceName()).withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("클릭: 플레이어 이름 검색").withStyle(ChatFormatting.AQUA))
                    .glow(skin != null)
                    .setCallback((slot, type, action) ->
                            new HeroCompanionSkinInputGui(player, gameManager, role).open());
            setSlot(ROLE_SLOTS[index], roleButton);

            GuiElementBuilder resetButton = new GuiElementBuilder(skin == null ? Items.GRAY_DYE : Items.BARRIER)
                    .setName(Component.literal(role.displayName() + " 기본 스킨 복원")
                            .withStyle(skin == null ? ChatFormatting.DARK_GRAY : ChatFormatting.RED))
                    .addLoreLine(Component.literal(skin == null ? "이미 기본 스킨입니다." : "저장된 스킨을 삭제합니다."));
            if (skin != null) {
                resetButton.setCallback((slot, type, action) -> {
                    if (gameManager.saveHeroCompanionSkin(
                            player.getServer(),
                            player.getUUID(),
                            player.getGameProfile().getName(),
                            role,
                            null
                    )) {
                        player.sendSystemMessage(Component.literal(role.displayName() + " 스킨을 기본값으로 복원했습니다.")
                                .withStyle(ChatFormatting.GREEN));
                        refresh();
                    } else {
                        player.sendSystemMessage(Component.literal("스킨 설정을 저장하지 못했습니다.")
                                .withStyle(ChatFormatting.RED));
                    }
                });
            }
            setSlot(RESET_SLOTS[index], resetButton);
            index++;
        }
        setSlot(31, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((slot, type, action) -> close()));
    }
}
