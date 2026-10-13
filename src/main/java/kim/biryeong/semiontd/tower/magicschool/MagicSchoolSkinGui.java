package kim.biryeong.semiontd.tower.magicschool;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.ui.PlayerSkinInputGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class MagicSchoolSkinGui extends SimpleGui {
    private final ServerPlayer player;
    private final SemionGameManager gameManager;
    private final SemionGame game;
    private final HogwartsTower school;

    public MagicSchoolSkinGui(ServerPlayer player, SemionGameManager gameManager, SemionGame game, HogwartsTower school) {
        super(MenuType.GENERIC_9x4, player, false);
        this.player = player;
        this.gameManager = gameManager;
        this.game = game;
        this.school = school;
        setTitle(Component.literal("마법사 스킨"));
        setLockPlayerInventory(true);
        refresh();
    }

    private boolean canManage() { return MagicSchoolCurriculum.canManageSchool(game, player.getUUID(), school); }

    private void refresh() {
        int index = 0;
        for (var kind : MagicSchoolSkins.Kind.values()) {
            var skin = MagicSchoolSkins.preference(player.getUUID(), kind).orElse(null);
            setSlot(11 + index, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setProfile(MagicSchoolSkins.profile(player.getUUID(), kind, player.getGameProfile()))
                    .setName(Component.literal(kind.displayName() + " 스킨").withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal(skin == null ? "기본값: 내 플레이어 스킨" : "적용 중: " + skin.sourceName()))
                    .addLoreLine(Component.literal(kind == MagicSchoolSkins.Kind.FRESHMAN
                            ? "신입생에게 적용됩니다." : "T2 마법사와 T3 대마법사에게 함께 적용됩니다.").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("클릭: 플레이어 이름 검색").withStyle(ChatFormatting.YELLOW))
                    .glow(skin != null).setCallback((slot, type, action, clickedGui) -> {
                        if (!canManage()) { close(); return; }
                        new PlayerSkinInputGui(player, kind.displayName(), skin == null ? "" : skin.sourceName(),
                                value -> gameManager.saveMagicSchoolSkin(player.getUUID(), player.getGameProfile().name(), kind, value),
                                () -> new MagicSchoolSkinGui(player, gameManager, game, school).open(), this::canManage).open();
                    }));
            setSlot(20 + index, new GuiElementBuilder(skin == null ? Items.DYE.gray() : Items.BARRIER)
                    .setName(Component.literal(kind.displayName() + " 기본 스킨 복원").withStyle(ChatFormatting.RED))
                    .addLoreLine(Component.literal("내 플레이어의 스킨으로 되돌립니다."))
                    .setCallback((slot, type, action, clickedGui) -> {
                        if (!canManage()) { close(); return; }
                        if (skin == null) return;
                        boolean saved = gameManager.saveMagicSchoolSkin(player.getUUID(), player.getGameProfile().name(), kind, null);
                        player.sendSystemMessage(Component.literal(saved ? "기본 스킨으로 복원했습니다." : "스킨 설정을 저장하지 못했습니다."));
                        if (saved) refresh();
                    }));
            index++;
        }
        setSlot(31, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("닫기"))
                .setCallback((slot, type, action, clickedGui) -> close()));
    }

    @Override
    public void onTick() { if (!canManage()) close(); }
}
