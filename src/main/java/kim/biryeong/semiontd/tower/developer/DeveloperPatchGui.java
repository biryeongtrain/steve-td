package kim.biryeong.semiontd.tower.developer;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * The 개발자 builder's preparation-phase console for one tower.
 *
 * <p>Every operation this family has lives here, and every one of them is refused outside
 * {@link RoundPhase#PREPARE_AND_SUMMON}. That is the builder's defining constraint: decisions are
 * made before the wave and the wave is where the player finds out whether they were right.
 *
 * <p>Reached from a 패치 콘솔 button on that tower's own details dialog, the same way the 용사 builder
 * hangs its shop off a hero tower. Scoping the console to one tower is deliberate: a lane-wide list
 * would show five entries all named 알파 with no way to tell which block on the lane each one is,
 * so the player has to arrive here by clicking the tower they mean.
 */
public final class DeveloperPatchGui extends SimpleGui {
    private static final int STATUS_SLOT = 4;
    private static final int PATCH_ROW = 9;
    private static final int HOTFIX_ROW = 18;
    private static final int OPTIMIZATION_ROW = 27;
    private static final int BUG_ROW = 36;
    private static final int MAINTENANCE_SLOT = 45;
    private static final int PIN_SLOT = 46;
    private static final int CLOSE_SLOT = 53;

    private final ServerPlayer player;
    private final SemionGame game;
    private final DeveloperTower tower;

    public DeveloperPatchGui(ServerPlayer player, SemionGame game, DeveloperTower tower) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.game = game;
        this.tower = tower;
        setTitle(Component.literal("개발자 콘솔 · " + tower.type().displayName()));
        setLockPlayerInventory(true);
        refresh();
    }

    /** True when this tower can be opened in the console at all. */
    public static boolean supports(SemionGame game, ServerPlayer player, Object candidate) {
        return candidate instanceof DeveloperTower developerTower
                && DeveloperTowers.isGrowthTower(developerTower.type())
                && player.getUUID().equals(developerTower.ownerPlayer())
                && game != null;
    }

    private PlayerLane lane() {
        return game.playerLane(player.getUUID()).orElse(null);
    }

    private boolean editable() {
        return game.phase() == RoundPhase.PREPARE_AND_SUMMON;
    }

    private void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            clearSlot(slot);
        }
        PlayerLane lane = lane();
        DeveloperPatchService.refreshCapacity(lane, player.getUUID());
        DeveloperStates.PlayerState state = DeveloperStates.of(player.getUUID());

        drawStatus(state);
        drawPatches(lane, state, false, PATCH_ROW);
        drawPatches(lane, state, true, HOTFIX_ROW);
        drawOptimizations(lane, state);
        drawBugs(lane, state);
        drawMaintenance(lane, state);
        drawPin(lane, state);

        setSlot(CLOSE_SLOT, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((slot, type, action) -> close()));
    }

    private void drawStatus(DeveloperStates.PlayerState state) {
        GuiElementBuilder status = new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(Component.literal(tower.type().displayName()).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("정식 패치 " + state.patchesRemaining() + "/"
                        + state.capacity().patchSlots()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("핫픽스 " + state.hotfixesRemaining() + "/"
                        + state.capacity().hotfixes()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("긴급 점검 " + state.maintenancesRemaining()
                        + " · 디버그 " + state.debugRemovalsRemaining()
                        + " · 재현 " + state.reproductionsRemaining()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("최적화 " + state.optimizationsRemaining() + " (매치 전체)")
                        .withStyle(ChatFormatting.GRAY));
        for (String line : DeveloperTowerLines.describe(tower)) {
            status.addLoreLine(Component.literal(stripTags(line)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!editable()) {
            status.addLoreLine(Component.literal("준비 단계에만 조작할 수 있습니다.").withStyle(ChatFormatting.RED));
        }
        setSlot(STATUS_SLOT, status);
    }

    private void drawPatches(PlayerLane lane, DeveloperStates.PlayerState state, boolean hotfix, int row) {
        int index = 0;
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            int existing = DeveloperTowerData.effectiveCount(tower, patch);
            double step = patch.stepAmount(existing) * tower.patchEfficiency(lane)
                    * (hotfix ? DeveloperBalance.hotfixScale(tower.type()) : 1.0);
            int remaining = hotfix ? state.hotfixesRemaining() : state.patchesRemaining();
            boolean usable = editable() && remaining > 0 && !DeveloperTowerData.isPinned(tower)
                    && (hotfix || !tower.hasBug(DeveloperBug.ROLLBACK_FAILURE));

            GuiElementBuilder button = new GuiElementBuilder(patch.item())
                    .setName(Component.literal((hotfix ? "핫픽스 · " : "정식 패치 · ") + patch.displayName())
                            .withStyle(usable ? (hotfix ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA)
                                    : ChatFormatting.DARK_GRAY))
                    .addLoreLine(Component.literal("적용량 " + amount(patch, step))
                            .withStyle(ChatFormatting.WHITE))
                    .addLoreLine(Component.literal("누적 " + existing + "회 · 반복할수록 효과가 줄어듭니다")
                            .withStyle(ChatFormatting.GRAY));
            if (hotfix) {
                button.addLoreLine(Component.literal("이번 라운드부터 적용")
                        .withStyle(ChatFormatting.GREEN));
                button.addLoreLine(Component.literal("불안정 +1 · 버그 확정 발생")
                        .withStyle(ChatFormatting.RED));
                if (DeveloperTowers.isLts(tower.type())) {
                    button.addLoreLine(Component.literal("LTS는 불안정이 쌓이지 않습니다")
                            .withStyle(ChatFormatting.GREEN));
                }
            } else {
                button.addLoreLine(Component.literal("다음 라운드부터 적용")
                        .withStyle(ChatFormatting.YELLOW));
            }
            button.setCallback((slot, type, action) -> {
                if (!editable()) {
                    notify("준비 단계에만 패치할 수 있습니다.");
                    return;
                }
                notify(DeveloperPatchService.applyPatch(lane(), tower, patch, hotfix));
                refresh();
            });
            setSlot(row + index, button);
            index++;
        }
    }

    private void drawOptimizations(PlayerLane lane, DeveloperStates.PlayerState state) {
        int index = 0;
        for (DeveloperOptimization optimization : DeveloperOptimization.values()) {
            boolean owned = tower.hasOptimization(optimization);
            boolean conflict = DeveloperPatchService.wouldConflict(tower, optimization);
            boolean usable = editable() && !owned && state.optimizationsRemaining() > 0
                    && !tower.hasBug(DeveloperBug.READ_ONLY);

            GuiElementBuilder button = new GuiElementBuilder(optimization.item())
                    .setName(Component.literal(optimization.displayName())
                            .withStyle(owned ? ChatFormatting.GREEN
                                    : usable ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))
                    .addLoreLine(Component.literal(describeCost(optimization)).withStyle(ChatFormatting.RED))
                    .addLoreLine(Component.literal(describeGain(optimization)).withStyle(ChatFormatting.GREEN))
                    .addLoreLine(Component.literal("영구적입니다. 되돌릴 수 없습니다.")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .glow(owned);
            if (conflict) {
                button.addLoreLine(Component.literal("이미 적용된 최적화와 상쇄되어 이득이 거의 없습니다")
                        .withStyle(ChatFormatting.RED));
            }
            if (tower.hasBug(DeveloperBug.READ_ONLY)) {
                button.addLoreLine(Component.literal("읽기 전용 버그로 잠겨 있습니다")
                        .withStyle(ChatFormatting.RED));
            }
            button.setCallback((slot, type, action) -> {
                if (!editable()) {
                    notify("준비 단계에만 최적화할 수 있습니다.");
                    return;
                }
                notify(DeveloperPatchService.applyOptimization(lane(), tower, optimization));
                refresh();
            });
            setSlot(OPTIMIZATION_ROW + index, button);
            index++;
        }
    }

    private void drawBugs(PlayerLane lane, DeveloperStates.PlayerState state) {
        Set<DeveloperBug> bugs = DeveloperTowerData.bugs(tower);
        boolean visible = state.bugsVisible();
        int index = 0;
        for (DeveloperBug bug : bugs) {
            GuiElementBuilder button = new GuiElementBuilder(visible ? bug.item() : Items.GRAY_DYE)
                    .setName(Component.literal(visible ? bug.displayName() : "정체불명의 버그")
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (visible) {
                for (String line : bug.description()) {
                    button.addLoreLine(Component.literal(stripTags(line)).withStyle(ChatFormatting.GRAY));
                }
                if (bug.dangerousToSpread()) {
                    button.addLoreLine(Component.literal("재현으로 퍼뜨리면 라인 전체가 위험해집니다")
                            .withStyle(ChatFormatting.RED));
                }
            } else {
                button.addLoreLine(Component.literal("테스터를 지으면 내용을 볼 수 있습니다")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            boolean canDebug = editable() && state.debugRemovalsRemaining() > 0;
            button.addLoreLine(Component.literal(canDebug ? "클릭: 버그 제거" : "디버거가 필요합니다")
                    .withStyle(canDebug ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            button.setCallback((slot, type, action) -> {
                if (!editable()) {
                    notify("준비 단계에만 제거할 수 있습니다.");
                    return;
                }
                notify(DeveloperPatchService.removeBug(lane(), tower, bug));
                refresh();
            });
            setSlot(BUG_ROW + index * 2, button);
            GuiElementBuilder reproduce = new GuiElementBuilder(Items.REPEATER)
                    .setName(Component.literal("재현 · " + (visible ? bug.displayName() : "정체불명"))
                            .withStyle(ChatFormatting.YELLOW))
                    .addLoreLine(Component.literal("클릭 후 같은 라인의 다른 자기 성장 타워를 선택")
                            .withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("웅크린 채 타워 클릭: 취소").withStyle(ChatFormatting.DARK_GRAY));
            reproduce.setCallback((slot, type, action) -> {
                if (!editable()) {
                    notify("준비 단계에만 재현할 수 있습니다.");
                    return;
                }
                DeveloperPatchService.Result result = DeveloperPatchService.armReproduction(lane(), tower, bug);
                notify(result);
                if (result.success()) {
                    close();
                } else {
                    refresh();
                }
            });
            setSlot(BUG_ROW + index * 2 + 1, reproduce);
            index++;
        }
        if (bugs.isEmpty()) {
            setSlot(BUG_ROW, new GuiElementBuilder(Items.LIME_DYE)
                    .setName(Component.literal("버그 없음").withStyle(ChatFormatting.GREEN))
                    .addLoreLine(Component.literal("이 타워는 깨끗합니다.").withStyle(ChatFormatting.GRAY)));
        }
    }

    private void drawMaintenance(PlayerLane lane, DeveloperStates.PlayerState state) {
        boolean usable = editable() && state.maintenancesRemaining() > 0;
        setSlot(MAINTENANCE_SLOT, new GuiElementBuilder(Items.ANVIL)
                .setName(Component.literal("긴급 점검")
                        .withStyle(usable ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))
                .addLoreLine(Component.literal("이번 라운드는 작동하지 않습니다").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("다음 라운드에 체력 완전 회복 · 불안정 0 · 공격력 +"
                        + Math.round(DeveloperBalance.maintenanceDamageBonus() * 100.0) + "%")
                        .withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("메모리 누수도 함께 초기화됩니다").withStyle(ChatFormatting.GRAY))
                .setCallback((slot, type, action) -> {
                    if (!editable()) {
                        notify("준비 단계에만 점검할 수 있습니다.");
                        return;
                    }
                    notify(DeveloperPatchService.applyMaintenance(lane(), tower, game.currentRound()));
                    refresh();
                }));
    }

    private void drawPin(PlayerLane lane, DeveloperStates.PlayerState state) {
        boolean pinned = DeveloperTowerData.isPinned(tower);
        int used = DeveloperPatchService.pinnedCount(lane, player.getUUID());
        setSlot(PIN_SLOT, new GuiElementBuilder(pinned ? Items.LIME_DYE : Items.IRON_INGOT)
                .setName(Component.literal(pinned ? "버전 고정 해제" : "버전 고정")
                        .withStyle(pinned ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                .addLoreLine(Component.literal("고정된 타워는 패치도 버그도 재현도 걸리지 않습니다")
                        .withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("고정 슬롯 " + used + "/" + state.versionPinSlots())
                        .withStyle(ChatFormatting.DARK_GRAY))
                .glow(pinned)
                .setCallback((slot, type, action) -> {
                    if (!editable()) {
                        notify("준비 단계에만 고정할 수 있습니다.");
                        return;
                    }
                    notify(DeveloperPatchService.setPinned(lane(), tower, !pinned));
                    refresh();
                }));
    }

    private String describeCost(DeveloperOptimization optimization) {
        return switch (optimization) {
            case RANGE -> "사거리 " + percent(-optimization.cost());
            case DURABILITY -> "체력 " + percent(-optimization.cost());
            case JUDGEMENT -> "항상 가장 가까운 적만 공격";
            case FIRE_RATE -> "공격 간격 " + percent(optimization.cost());
            case ACCURACY -> "빗나갈 확률 " + percent(optimization.cost());
            case ATTACK -> "공격력 " + percent(-optimization.cost());
            case SLOT -> "타워 슬롯 2칸 사용";
        };
    }

    private String describeGain(DeveloperOptimization optimization) {
        return switch (optimization) {
            case RANGE -> "연사 " + percent(optimization.gain());
            case DURABILITY, FIRE_RATE -> "공격력 " + percent(optimization.gain());
            case JUDGEMENT -> "사거리 " + percent(optimization.gain());
            case ACCURACY -> "명중 시 피해 " + percent(optimization.gain());
            case ATTACK -> "체력 " + percent(optimization.gain()) + " · 어그로 +40";
            case SLOT -> "공격력·사거리·연사·체력 " + percent(optimization.gain());
        };
    }

    private static String amount(DeveloperPatch patch, double value) {
        if (patch.isFlat()) {
            return "+" + Math.round(value);
        }
        return "+" + String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private static String percent(double value) {
        return (value >= 0.0 ? "+" : "") + String.format(Locale.ROOT, "%.0f%%", value * 100.0);
    }

    /** The dialog lines carry MiniMessage tags; item lore is plain text. */
    private static String stripTags(String line) {
        return line == null ? "" : line.replaceAll("<[^>]*>", "").trim();
    }

    private void notify(DeveloperPatchService.Result result) {
        if (result == null) {
            return;
        }
        player.sendSystemMessage(Component.literal(result.message())
                .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result.spawnedBug() != null) {
            boolean visible = DeveloperStates.of(player.getUUID()).bugsVisible();
            List<String> lore = visible ? result.spawnedBug().description() : List.of();
            player.sendSystemMessage(Component.literal(
                            "버그: " + (visible ? result.spawnedBug().displayName() : "정체불명"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lore.forEach(line -> player.sendSystemMessage(
                    Component.literal(stripTags(line)).withStyle(ChatFormatting.GRAY)));
        }
    }

    private void notify(String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
