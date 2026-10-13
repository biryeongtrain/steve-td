package kim.biryeong.semiontd.tower.plant;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * 정원사 스킬 강화 창. 스킬 세 개를 한 줄에 놓고, 누르면 다음 단계로 강화합니다(준비 시간에만).
 */
public final class GardenerSkillGui extends SimpleGui {
    private static final int[] SKILL_SLOTS = {11, 13, 15};

    private final ServerPlayer player;
    private final SemionGame game;
    private final GardenerTower tower;

    public GardenerSkillGui(ServerPlayer player, SemionGame game, GardenerTower tower) {
        super(MenuType.GENERIC_9x3, player, false);
        this.player = player;
        this.game = game;
        this.tower = tower;
        setTitle(Component.literal("정원사 스킬 강화"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < 27; slot++) {
            clearSlot(slot);
        }
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long diamond = semionPlayer == null ? 0 : semionPlayer.economy().diamond();
        boolean editable = game.phase() == RoundPhase.PREPARE_AND_SUMMON;
        setSlot(4, new GuiElementBuilder(Items.DIAMOND)
                .setName(Component.literal("다이아 " + diamond).withStyle(ChatFormatting.AQUA))
                .addLoreLineRaw(Component.literal(editable ? "준비 시간에 강화할 수 있습니다." : "전투 중에는 강화할 수 없습니다.")
                        .withStyle(editable ? ChatFormatting.GRAY : ChatFormatting.RED)));
        GardenerTower.Skill[] skills = GardenerTower.Skill.values();
        for (int index = 0; index < skills.length; index++) {
            GardenerTower.Skill skill = skills[index];
            int level = tower.level(skill);
            long cost = tower.upgradeCost(skill);
            boolean max = level >= GardenerTower.MAX_SKILL_LEVEL;
            GuiElementBuilder builder = new GuiElementBuilder(skill.icon())
                    .setName(Component.literal(skill.displayName() + " " + level + "/" + GardenerTower.MAX_SKILL_LEVEL + "단계")
                            .withStyle(ChatFormatting.GOLD))
                    .setCount(level)
                    .hideDefaultTooltip();
            for (String line : tower.runtimeDetailLines()) {
                if (line.startsWith(skill.displayName())) {
                    builder.addLoreLineRaw(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
            }
            builder.addLoreLineRaw(max
                    ? Component.literal("최고 단계입니다.").withStyle(ChatFormatting.GREEN)
                    : Component.literal("강화 " + cost + " 다이아").withStyle(diamond >= cost ? ChatFormatting.AQUA : ChatFormatting.RED));
            if (!max && editable) {
                builder.setCallback((slot, type, action, clickedGui) -> upgrade(skill));
            }
            setSlot(SKILL_SLOTS[index], builder);
        }
    }

    private void upgrade(GardenerTower.Skill skill) {
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        if (semionPlayer == null || game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return;
        }
        GardenerTower.UpgradeResult result = tower.upgrade(skill, semionPlayer.economy());
        boolean success = result == GardenerTower.UpgradeResult.SUCCESS;
        player.sendSystemMessage(Component.literal(result.message())
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        kim.biryeong.semiontd.util.SemionPlayerPackets.playSound(player, success ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7f, 1.4f);
        refresh();
    }
}
