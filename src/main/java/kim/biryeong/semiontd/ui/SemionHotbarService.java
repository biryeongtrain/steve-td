package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class SemionHotbarService {
    private static final int TOWER_TOOL_SLOT = 0;
    private static final int SUMMON_TOOL_SLOT = 1;
    private static final int LEADER_TOOL_SLOT = 2;
    private static final Component TOWER_TOOL_NAME = Component.literal("타워 관리").withStyle(ChatFormatting.AQUA);
    private static final Component SUMMON_TOOL_NAME = Component.literal("견제 소환").withStyle(ChatFormatting.LIGHT_PURPLE);
    private static final Component LEADER_TOOL_NAME = Component.literal("팀장 타깃").withStyle(ChatFormatting.GOLD);

    private SemionHotbarService() {
    }

    public static void register(SemionGameManager gameManager) {
        UseItemCallback.EVENT.register((player, world, hand) -> handleUse(gameManager, player, world, hand));
    }

    public static void grantMatchTools(ServerPlayer player) {
        player.getInventory().clearContent();
        setTool(player, TOWER_TOOL_SLOT, towerTool());
        setTool(player, SUMMON_TOOL_SLOT, summonTool());
    }

    public static void grantLeaderTool(ServerPlayer player) {
        setTool(player, LEADER_TOOL_SLOT, leaderTool());
    }

    public static void clearMatchTools(ServerPlayer player) {
        clearTool(player, TOWER_TOOL_SLOT, Items.COMPASS);
        clearTool(player, SUMMON_TOOL_SLOT, Items.ECHO_SHARD);
        clearTool(player, LEADER_TOOL_SLOT, Items.BLAZE_ROD);
    }

    private static InteractionResult handleUse(
            SemionGameManager gameManager,
            Player player,
            Level world,
            InteractionHand hand
    ) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.isActiveParticipant(serverPlayer.getUUID())) {
            return InteractionResult.PASS;
        }

        ItemStack stack = serverPlayer.getItemInHand(hand);
        if (isTowerTool(stack)) {
            gameManager.dialogService().showTowerControl(serverPlayer, game, gameManager.buildGuideService());
            return InteractionResult.SUCCESS;
        }
        if (isSummonTool(stack)) {
            gameManager.dialogService().showSummonShop(serverPlayer, game);
            return InteractionResult.SUCCESS;
        }
        if (isLeaderTool(stack)) {
            if (game.players().get(serverPlayer.getUUID()) != null
                    && game.teams().get(game.players().get(serverPlayer.getUUID()).teamId()).hasLeader(serverPlayer.getUUID())) {
                gameManager.dialogService().showLeaderTargetControl(serverPlayer, game);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    private static ItemStack towerTool() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(DataComponents.CUSTOM_NAME, TOWER_TOOL_NAME);
        return stack;
    }

    private static ItemStack summonTool() {
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        stack.set(DataComponents.CUSTOM_NAME, SUMMON_TOOL_NAME);
        return stack;
    }

    private static ItemStack leaderTool() {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        stack.set(DataComponents.CUSTOM_NAME, LEADER_TOOL_NAME);
        return stack;
    }

    private static void setTool(ServerPlayer player, int slot, ItemStack tool) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing.isEmpty() || isSemionTool(existing)) {
            player.getInventory().setItem(slot, tool);
        }
    }

    private static void clearTool(ServerPlayer player, int slot, net.minecraft.world.item.Item item) {
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing.is(item) && isSemionTool(existing)) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean isTowerTool(ItemStack stack) {
        return stack.is(Items.COMPASS) && isNamed(stack, TOWER_TOOL_NAME);
    }

    private static boolean isSummonTool(ItemStack stack) {
        return stack.is(Items.ECHO_SHARD) && isNamed(stack, SUMMON_TOOL_NAME);
    }

    private static boolean isLeaderTool(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) && isNamed(stack, LEADER_TOOL_NAME);
    }

    private static boolean isSemionTool(ItemStack stack) {
        return isTowerTool(stack) || isSummonTool(stack) || isLeaderTool(stack);
    }

    private static boolean isNamed(ItemStack stack, Component expectedName) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && customName.getString().equals(expectedName.getString());
    }
}
