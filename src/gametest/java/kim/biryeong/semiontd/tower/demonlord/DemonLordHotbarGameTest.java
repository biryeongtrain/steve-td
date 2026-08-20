package kim.biryeong.semiontd.tower.demonlord;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

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

    /**
     * 스킬 카드를 들고 우클릭해도 바닐라 사용 동작이 돌면 안 됩니다.
     *
     * <p>스킬 아이콘 중에는 염소 뿔·방패·화염구·위더 해골처럼 진짜 사용 동작이 붙은 것들이
     * 있습니다. 그냥 흘려보내면 아레나 한복판에서 불을 놓거나 방패를 들거나 하다가 튕깁니다.
     * 시전은 슬롯을 잡는 동작이고 카드 자체는 표시용이라, 우클릭은 아무 일도 없어야 합니다.
     */
    @GameTest
    public void skillCardsSwallowTheirVanillaRightClick(GameTestHelper context) {
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        try {
            BlockHitResult blockHit = new BlockHitResult(
                    player.position(), Direction.UP, BlockPos.containing(player.position()), false);
            for (DemonLordSkill skill : DemonLordSkill.values()) {
                ItemStack card = DemonLordKitItems.mark(new ItemStack(skill.item()));
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, card);
                InteractionResult result = UseItemCallback.EVENT.invoker()
                        .interact(player, player.level(), InteractionHand.MAIN_HAND);
                require(result != InteractionResult.PASS,
                        skill.displayName() + " 카드가 바닐라 사용 동작으로 넘어갑니다: " + skill.item());
                require(UseBlockCallback.EVENT.invoker()
                                .interact(player, player.level(), InteractionHand.MAIN_HAND, blockHit)
                                == InteractionResult.FAIL,
                        skill.displayName() + " 카드가 블록 사용 동작으로 넘어갑니다: " + skill.item());
            }

            // 마검은 우클릭 스킬의 입력 장치입니다. 블록을 보고 눌러도 시전 경로를 막으면 안 됩니다.
            player.getInventory().setSelectedSlot(DemonLordSkill.BLADE_SLOT);
            player.getInventory().setItem(
                    DemonLordSkill.BLADE_SLOT,
                    DemonLordKitItems.mark(new ItemStack(Items.NETHERITE_SWORD)));
            require(UseBlockCallback.EVENT.invoker()
                            .interact(player, player.level(), InteractionHand.MAIN_HAND, blockHit)
                            != InteractionResult.FAIL,
                    "블록을 보고 마검을 우클릭해도 스킬 시전 경로가 열려 있어야 합니다.");

            // 마검이 아닌 일반 아이템은 건드리지 않습니다. 마왕 장비가 아닌 것까지 먹으면
            // 다른 빌더의 도구가 통째로 죽습니다.
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.COMPASS));
            require(UseItemCallback.EVENT.invoker()
                            .interact(player, player.level(), InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                    "마왕 장비가 아닌 아이템은 그대로 흘려보내야 합니다.");
            context.succeed();
        } catch (Throwable failure) {
            context.fail(Component.literal("Demon lord right-click GameTest failed: " + failure.getMessage()));
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
