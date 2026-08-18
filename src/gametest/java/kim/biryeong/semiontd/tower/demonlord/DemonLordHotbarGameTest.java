package kim.biryeong.semiontd.tower.demonlord;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 마왕이 전투에 들어가며 치운 핫바가 전투 뒤에 돌아오는지 확인합니다.
 *
 * <p>{@link DemonLordGameTest} 와 나눠 둔 것은 그쪽이 아직 gametest 엔트리포인트에 등록되어
 * 있지 않기 때문입니다.
 */
public final class DemonLordHotbarGameTest {
    /**
     * 전에는 전투가 끝날 때 일반 매치의 타워·소환 도구만 다시 쥐여 줬습니다. 샌드박스의 라운드
     * 이동 도구는 전투 진입에 지워진 뒤 영영 돌아오지 않아, 2라운드부터 라운드를 넘길 수단이
     * 사라졌습니다. 무엇을 들고 있었는지 기억해 두면 도구 종류를 하나도 몰라도 됩니다.
     */
    @GameTest
    public void leavingCombatRestoresWhateverTheKitDisplaced(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        try {
            player.getInventory().setItem(0, new ItemStack(Items.COMPASS));
            player.getInventory().setItem(2, new ItemStack(Items.CLOCK));
            DemonLordService.rememberHotbar(player);

            // 전투 진입: 스킬이 두 칸을 모두 가져갑니다.
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(2, ItemStack.EMPTY);
            // 라운드가 시작되며 게임이 0번만 새로 채운 상황.
            player.getInventory().setItem(0, new ItemStack(Items.ECHO_SHARD));

            DemonLordService.restoreHotbar(player);
            require(player.getInventory().getItem(2).is(Items.CLOCK),
                    "샌드박스 라운드 이동 도구가 전투 후 돌아와야 합니다.");
            require(player.getInventory().getItem(0).is(Items.ECHO_SHARD),
                    "이미 채워진 칸은 덮어쓰지 않아야 합니다.");

            // 되돌린 기록은 한 번 쓰고 버립니다. 남겨 두면 다음 라운드에 낡은 핫바가 되살아납니다.
            player.getInventory().setItem(2, ItemStack.EMPTY);
            DemonLordService.restoreHotbar(player);
            require(player.getInventory().getItem(2).isEmpty(),
                    "되돌린 기록은 한 번 쓰고 버려야 합니다.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord hotbar GameTest failed: " + failure.getMessage()));
        } finally {
            player.discard();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
