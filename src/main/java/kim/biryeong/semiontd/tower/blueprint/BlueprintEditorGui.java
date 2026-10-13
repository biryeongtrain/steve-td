package kim.biryeong.semiontd.tower.blueprint;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.SemionGameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 설계도 편집 창(9×6).
 *
 * <pre>
 *  윗줄   겉모습 · · · 가격 요약 · · · 이름
 *  2~4줄  능력치마다 [+] / 아이콘 / [-] (쉬프트는 크게), 피해 유형은 클릭으로 바꿈
 *  5줄    붙인 모듈 4칸(좌클릭 단계↑, 우클릭 단계↓·떼기) · 모듈 추가 · 대상 우선도
 *  아랫줄 목록으로 · 초기화 · 저장
 * </pre>
 * 값을 바꿀 때마다 가격을 다시 계산해 윗줄에 바로 보여 줍니다.
 */
public final class BlueprintEditorGui extends SimpleGui {
    private static final int VISUAL_SLOT = 0;
    private static final int SUMMARY_SLOT = 4;
    private static final int NAME_SLOT = 8;
    private static final int FIRST_MODULE_SLOT = 36;
    private static final int ADD_MODULE_SLOT = 41;
    private static final int PRIORITY_SLOT = 43;
    private static final int DAMAGE_TYPE_SLOT = 25;

    private final ServerPlayer player;
    private final SemionGameManager gameManager;
    private final BlueprintDraft draft;
    private String status;

    public BlueprintEditorGui(ServerPlayer player, SemionGameManager gameManager) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.gameManager = gameManager;
        this.draft = BlueprintDraft.of(player.getUUID());
        setTitle(Component.literal("설계도 편집"));
        setLockPlayerInventory(true);
        refresh();
    }

    private record StatRow(int column, String label, Item icon, double step, double bigStep) {
    }

    private static final List<StatRow> ROWS = List.of(
            new StatRow(1, "체력", Items.GOLDEN_APPLE, 10, 100),
            new StatRow(2, "공격력", Items.IRON_SWORD, 1, 10),
            new StatRow(3, "공격 속도", Items.CLOCK, 1, 5),
            new StatRow(4, "사거리", Items.SPYGLASS, 0.5, 2),
            new StatRow(5, "어그로", Items.TARGET, 5, 20)
    );

    private void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            clearSlot(slot);
        }
        draft.clamp();
        BlueprintStats stats = draft.stats();

        BlueprintVisuals.Option visual = BlueprintVisuals.find(draft.visualSourceId).orElse(null);
        setSlot(VISUAL_SLOT, GuiElementBuilder.from(BlueprintVisuals.icon(visual == null ? null : visual.visual()))
                .setName(text("겉모습: " + (visual == null ? "없음" : visual.sourceName()), ChatFormatting.AQUA))
                .addLoreLineRaw(text("클릭: 겉모습 고르기", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> new BlueprintVisualGui(player, gameManager, 0).open()));
        setSlot(SUMMARY_SLOT, summary(stats));
        setSlot(NAME_SLOT, new GuiElementBuilder(Items.NAME_TAG)
                .setName(text("이름: " + (draft.name.isBlank() ? "(없음)" : draft.name), ChatFormatting.YELLOW))
                .addLoreLineRaw(text("클릭: 이름 짓기", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> new BlueprintNameGui(player, gameManager).open()));

        for (StatRow row : ROWS) {
            setSlot(9 + row.column(), stepButton(row, true));
            setSlot(18 + row.column(), new GuiElementBuilder(row.icon())
                    .setName(text(row.label() + ": " + statValue(row), ChatFormatting.WHITE))
                    .addLoreLineRaw(text(statHint(row), ChatFormatting.GRAY)));
            setSlot(27 + row.column(), stepButton(row, false));
        }
        setSlot(DAMAGE_TYPE_SLOT, new GuiElementBuilder(Items.BLAZE_POWDER)
                .setName(text("피해 유형: " + damageTypeName(draft.damageType), ChatFormatting.GOLD))
                .addLoreLineRaw(text("마법은 마법 저항을, 고정은 방어를 무시합니다.", ChatFormatting.GRAY))
                .addLoreLineRaw(text("클릭: 바꾸기 (고정 피해는 값이 더 비쌈)", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> {
                    DamageType[] types = DamageType.values();
                    draft.damageType = types[(draft.damageType.ordinal() + 1) % types.length];
                    refresh();
                }));

        int slot = FIRST_MODULE_SLOT;
        for (var entry : draft.modules.entrySet()) {
            BlueprintModule module = entry.getKey();
            int level = entry.getValue();
            setSlot(slot++, new GuiElementBuilder(BlueprintIcons.module(module))
                    .setName(text(module.displayName() + " " + level + "단계", ChatFormatting.AQUA))
                    .addLoreLineRaw(text(BlueprintTexts.effect(module, level), ChatFormatting.WHITE))
                    .addLoreLineRaw(text("좌클릭: 단계 올리기 · 우클릭: 내리기(1단계면 떼기)", ChatFormatting.GRAY))
                    .setCount(level)
                    .setCallback((index, type, action, clickedGui) -> changeModule(module, type)));
        }
        while (slot < FIRST_MODULE_SLOT + BlueprintPricing.maxModules() && slot < ADD_MODULE_SLOT) {
            setSlot(slot++, new GuiElementBuilder(Items.STAINED_GLASS_PANE.lightGray())
                    .setName(text("빈 모듈 칸", ChatFormatting.DARK_GRAY)));
        }
        boolean canAdd = draft.modules.size() < BlueprintPricing.maxModules();
        setSlot(ADD_MODULE_SLOT, new GuiElementBuilder(canAdd ? Items.ANVIL : Items.BARRIER)
                .setName(text(canAdd ? "모듈 추가" : "모듈 칸이 가득 찼습니다", canAdd ? ChatFormatting.GREEN : ChatFormatting.RED))
                .addLoreLineRaw(text("모듈은 " + BlueprintPricing.maxModules() + "개까지, 각 " + BlueprintModule.MAX_LEVEL + "단계까지", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> {
                    if (canAdd) {
                        new BlueprintModuleGui(player, gameManager).open();
                    }
                }));
        setSlot(PRIORITY_SLOT, new GuiElementBuilder(Items.COMPASS)
                .setName(text("대상 우선도: " + draft.targetPriority.displayName(), ChatFormatting.YELLOW))
                .addLoreLineRaw(text("누구를 먼저 노릴지 고릅니다. 가격에는 들지 않습니다.", ChatFormatting.GRAY))
                .addLoreLineRaw(text("클릭: 바꾸기", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> {
                    BlueprintTargetPriority[] values = BlueprintTargetPriority.values();
                    int step = type.isRight ? values.length - 1 : 1;
                    draft.targetPriority = values[(draft.targetPriority.ordinal() + step) % values.length];
                    refresh();
                }));

        setSlot(45, new GuiElementBuilder(Items.ARROW)
                .setName(text("설계도 목록으로", ChatFormatting.YELLOW))
                .setCallback((index, type, action, clickedGui) -> new BlueprintLibraryGui(player, gameManager).open()));
        setSlot(47, new GuiElementBuilder(Items.WATER_BUCKET)
                .setName(text("처음부터 다시", ChatFormatting.RED))
                .addLoreLineRaw(text("쉬프트+클릭: 모든 값을 기본으로 되돌립니다.", ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> {
                    if (type.shift) {
                        BlueprintDraft.reset(player.getUUID());
                        new BlueprintEditorGui(player, gameManager).open();
                    }
                }));
        Optional<String> problem = problem(stats);
        GuiElementBuilder save = new GuiElementBuilder(problem.isEmpty() ? Items.EMERALD : Items.BARRIER)
                .setName(text(problem.isEmpty() ? "저장" : "아직 저장할 수 없습니다", problem.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED))
                .addLoreLineRaw(text(problem.orElse("설계도는 저장하면 바꿀 수 없습니다. 계정에 남아 다음 경기에도 쓰입니다."),
                        problem.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.RED));
        if (status != null) {
            save.addLoreLineRaw(text(status, ChatFormatting.YELLOW));
        }
        if (problem.isEmpty()) {
            save.glow().setCallback((index, type, action, clickedGui) -> save());
        }
        setSlot(49, save);
    }

    private GuiElementBuilder summary(BlueprintStats stats) {
        long price = BlueprintPricing.price(stats);
        return new GuiElementBuilder(Items.MAP)
                .setName(text("가격 " + price + " · 타워 수 " + BlueprintPricing.slotCost(price), ChatFormatting.GREEN))
                .addLoreLineRaw(text(BlueprintTexts.basicDps(stats)
                        + " (" + damageTypeName(stats.damageType()) + ")", ChatFormatting.WHITE))
                .addLoreLineRaw(text("체력 " + BlueprintTexts.num(stats.maxHealth()) + " · 사거리 " + BlueprintTexts.num(stats.range()), ChatFormatting.WHITE))
                .addLoreLineRaw(text("위력 " + BlueprintTexts.num(BlueprintPricing.power(stats)) + " (세게 만들수록 값이 가파르게 오릅니다)", ChatFormatting.GRAY));
    }

    private GuiElementBuilder stepButton(StatRow row, boolean increase) {
        return new GuiElementBuilder(increase ? Items.STAINED_GLASS_PANE.lime() : Items.STAINED_GLASS_PANE.red())
                .setName(text(row.label() + (increase ? " 올리기" : " 내리기"), increase ? ChatFormatting.GREEN : ChatFormatting.RED))
                .addLoreLineRaw(text("클릭 " + BlueprintTexts.num(row.step()) + " · 쉬프트 " + BlueprintTexts.num(row.bigStep()), ChatFormatting.GRAY))
                .setCallback((index, type, action, clickedGui) -> {
                    double amount = (type.shift ? row.bigStep() : row.step()) * (increase ? 1 : -1);
                    adjust(row, amount);
                    refresh();
                });
    }

    private void adjust(StatRow row, double amount) {
        switch (row.column()) {
            case 1 -> draft.maxHealth += amount;
            case 2 -> draft.damage += amount;
            // 공격 속도를 올리면 공격 간격이 줄어듭니다.
            case 3 -> draft.attackIntervalTicks -= (int) Math.round(amount);
            case 4 -> draft.range += amount;
            case 5 -> draft.aggroPriority += (int) Math.round(amount);
            default -> {
            }
        }
    }

    private String statValue(StatRow row) {
        return switch (row.column()) {
            case 1 -> BlueprintTexts.num(draft.maxHealth);
            case 2 -> BlueprintTexts.num(draft.damage);
            case 3 -> BlueprintTexts.num(20.0 / draft.attackIntervalTicks) + "회/초 (" + draft.attackIntervalTicks + "틱)";
            case 4 -> BlueprintTexts.num(draft.range) + "칸";
            case 5 -> Integer.toString(draft.aggroPriority);
            default -> "";
        };
    }

    private static String statHint(StatRow row) {
        return switch (row.column()) {
            case 1 -> "몹의 공격을 버티는 체력입니다.";
            case 2 -> "한 번 공격할 때의 피해입니다.";
            case 3 -> "올리면 공격 간격이 짧아집니다.";
            case 4 -> "사거리 6칸보다 길면 값이 오릅니다.";
            case 5 -> "높을수록 몹이 이 타워를 먼저 노립니다. 가격에는 들지 않습니다.";
            default -> "";
        };
    }

    private void changeModule(BlueprintModule module, ClickType type) {
        int level = draft.modules.getOrDefault(module, 0);
        if (type.isRight) {
            if (level <= 1) {
                draft.modules.remove(module);
            } else {
                draft.modules.put(module, level - 1);
            }
        } else if (level < BlueprintModule.MAX_LEVEL) {
            draft.modules.put(module, level + 1);
        }
        refresh();
    }

    private Optional<String> problem(BlueprintStats stats) {
        if (BlueprintStates.sanitizeName(draft.name).isEmpty()) {
            return Optional.of("이름을 지어 주세요(1~16자).");
        }
        return BlueprintLibrary.check(player.getUUID(), draft.design());
    }

    private void save() {
        BlueprintService.Outcome outcome = BlueprintService.save(player, gameManager, draft.design());
        if (!outcome.success()) {
            status = String.join(" ", outcome.messages());
            refresh();
            return;
        }
        outcome.messages().forEach(message -> player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN)));
        BlueprintDraft.reset(player.getUUID());
        new BlueprintLibraryGui(player, gameManager).open();
    }

    static String damageTypeName(DamageType type) {
        return switch (type) {
            case PHYSICAL -> "물리";
            case MAGIC -> "마법";
            case TRUE -> "고정";
        };
    }

    static Component text(String value, ChatFormatting color) {
        return Component.literal(value).withStyle(style -> style.withColor(color).withItalic(false));
    }
}
