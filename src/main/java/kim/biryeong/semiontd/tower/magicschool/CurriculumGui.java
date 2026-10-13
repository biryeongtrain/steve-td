package kim.biryeong.semiontd.tower.magicschool;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.Arrays;
import java.util.List;
import java.math.BigDecimal;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.Upgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;

public final class CurriculumGui extends SimpleGui {
    private final ServerPlayer player;
    private final SemionGame game;
    private final HogwartsTower school;
    private int displayedRound;
    private boolean displayedAvailable;

    public CurriculumGui(ServerPlayer player, SemionGame game, HogwartsTower school) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.game = game;
        this.school = school;
        setTitle(Component.literal("커리큘럼"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        displayedRound = game.currentRound();
        displayedAvailable = MagicSchoolCurriculum.canUpgradeThisRound(player.getUUID(), displayedRound);
        for (Upgrade upgrade : Upgrade.values()) {
            int level = MagicSchoolCurriculum.level(player.getUUID(), upgrade);
            boolean maximum = level >= upgrade.maxLevel();
            var element = new GuiElementBuilder(item(upgrade))
                    .setName(Component.literal(upgrade.displayName()).withStyle(ChatFormatting.GOLD));
            description(upgrade).forEach(line -> element.addLoreLine(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            if (upgrade.maxLevel() > 1) {
                element.addLoreLine(Component.literal("수업: " + level + " / " + upgrade.maxLevel()).withStyle(ChatFormatting.AQUA));
            }
            String status = maximum ? "구매 완료 · 내 모든 학생에게 적용"
                    : "클릭: " + (upgrade.cost(level) == 0 ? "첫 수업 무료" : upgrade.cost(level) + " 다이아");
            boolean enabled = MagicSchoolCurriculum.enabled(player.getUUID(), upgrade);
            if (upgrade.toggleable() && maximum) {
                status = "현재 " + (enabled ? "ON" : "OFF") + " · 클릭하여 전환 (무료)";
            }
            element.addLoreLine(Component.literal(status).withStyle(maximum ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            setSlot(upgrade.slot(), element.glow(upgrade.toggleable() ? enabled : maximum)
                    .setCallback((slot, type, action, clickedGui) -> activate(upgrade)));
        }
        for (int tier = 2; tier <= 5; tier++) {
            int spellTier = tier;
            boolean unlocked = MagicSchoolCurriculum.isSpellTierUnlocked(player.getUUID(), tier);
            String name = MagicSchoolCurriculum.spellTierName(tier);
            var element = new GuiElementBuilder(Items.ENCHANTED_BOOK)
                    .setName(Component.literal(name + " 해금").withStyle(ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("사용 단계 조건을 만족하는 내 마법사가 해당 주문을 선택할 수 있습니다.").withStyle(ChatFormatting.GRAY));
            if (!unlocked && tier > 2) {
                element.addLoreLine(Component.literal("선행 필요: " + MagicSchoolCurriculum.spellTierName(tier - 1) + " 해금")
                        .withStyle(MagicSchoolCurriculum.isSpellTierUnlocked(player.getUUID(), tier - 1)
                                ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (Arrays.stream(MagicSchoolSpell.values()).noneMatch(spell -> spell.tier() == spellTier)) {
                element.addLoreLine(Component.literal("현재 등록된 주문 없음 · 해금 상태는 유지됩니다.").withStyle(ChatFormatting.GRAY));
            }
            setSlot(45 + tier - 2, element
                    .addLoreLine(Component.literal(unlocked ? "해금 완료"
                                    : "클릭: " + MagicSchoolCurriculum.spellTierCost(tier) + " 다이아")
                            .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .glow(unlocked).setCallback((slot, type, action, clickedGui) -> complete(name,
                            MagicSchoolCurriculum.unlockSpellTier(game, player.getUUID(), school, spellTier))));
        }

    }

    private void activate(Upgrade upgrade) {
        var result = upgrade.toggleable() ? MagicSchoolCurriculum.toggle(game, player.getUUID(), school, upgrade)
                : MagicSchoolCurriculum.purchase(game, player.getUUID(), school, upgrade);
        if (result == MagicSchoolCurriculum.PurchaseResult.TOGGLED) {
            player.sendSystemMessage(Component.literal(upgrade.displayName() + ": "
                    + (MagicSchoolCurriculum.enabled(player.getUUID(), upgrade) ? "ON" : "OFF") + " (다음 웨이브부터 적용)"));
            refresh();
        } else complete(upgrade.displayName(), result);
    }

    private void complete(String name, MagicSchoolCurriculum.PurchaseResult result) {
        if (result == MagicSchoolCurriculum.PurchaseResult.INVALID_SCHOOL) {
            close();
            return;
        }
        player.sendSystemMessage(Component.literal(switch (result) {
            case PURCHASED -> name + " 업그레이드를 완료했습니다.";
            case ALREADY_PURCHASED -> "이미 최대 단계 또는 해금 완료 상태입니다.";
            case NOT_ENOUGH_DIAMONDS -> "다이아가 부족합니다.";
            case ROUND_LIMIT_REACHED -> "커리큘럼은 한 라운드에 한 번만 업그레이드할 수 있습니다.";
            case PREREQUISITE_REQUIRED -> "변신술 수업을 먼저 구매해야 합니다.";
            case PREVIOUS_SPELL_TIER_REQUIRED -> "이전 단계 주문을 먼저 해금해야 합니다.";
            case AUGMENT_REQUIRED -> "전용 프리즘 증강 '용서받지 못할 저주'가 필요합니다.";
            default -> throw new IllegalStateException("Unexpected curriculum result: " + result);
        }));
        refresh();
    }

    private static Item item(Upgrade upgrade) {
        return switch (upgrade) {
            case SPELL_POWER -> Items.BLAZE_ROD;
            case CUSTOM_WANDS -> Items.BREEZE_ROD;
            case DARK_ARTS_DEFENSE -> Items.NETHERITE_CHESTPLATE;
            case MAGIC_HISTORY -> Items.BOOK;
            case ADVANCED_SPELLS -> Items.ENCHANTED_BOOK;
            case SORTING_HAT -> Items.LEATHER_HELMET;
            case DEATH_EATER -> Items.SKELETON_SKULL;
            case MENTOR -> Items.DRIED_GHAST;
            case DUELING_PRACTICE -> Items.IRON_SWORD;
            case SPELL_PRACTICE -> Items.ENCHANTED_BOOK;
            case SPELL_TRANSFER -> Items.ENDER_PEARL;
            case POTIONS -> Items.POTION;
            case QUIDDITCH -> Items.FEATHER;
            case TRANSFIGURATION -> Items.BARREL;
            case EXPLOSIVE_BARRELS -> Items.TNT;
        };
    }

    private static List<String> description(Upgrade upgrade) {
        return switch (upgrade) {
            case SPELL_POWER -> List.of("수업마다 모든 학생의 공격력 +" + percent("spellPowerPerLevel", .04) + "%");
            case DARK_ARTS_DEFENSE -> List.of("수업마다 모든 학생의 최대 체력 +" + percent("darkArtsDefensePerLevel", .03) + "%");
            case MAGIC_HISTORY -> List.of("수업마다 모든 학생의 주문 숙련도 획득량 +" + percent("magicHistoryPerLevel", .25) + "%");
            case ADVANCED_SPELLS -> List.of("마법사가 한 단계 더 높은 주문을 사용할 수 있습니다.",
                    "신입생 3단계 · 기숙사 마법사 4단계 · 대마법사 5단계",
                    "래번클로 마법사는 기존처럼 5단계까지 사용합니다. 주문 해금은 별도입니다.");
            case SORTING_HAT -> List.of("최대 숙련도의 신입생을 기숙사 마법사로 진급시킬 수 있습니다.",
                    "학생을 우클릭하여 기숙사를 선택하세요. 진급 비용은 별도입니다.");
            case CUSTOM_WANDS -> List.of(
                    "그리핀도르: 공격 주기 -" + wizardValue(MagicSchoolTowers.GRYFFINDOR.id(), "wandAttackIntervalReduction", 1, 1) + "틱",
                    "후플푸프: 최대 체력 +" + wizardValue(MagicSchoolTowers.HUFFLEPUFF.id(), "wandHealthBonus", .10, 100) + "%",
                    "래번클로: 공격력 +" + wizardValue(MagicSchoolTowers.RAVENCLAW.id(), "wandDamageBonus", .05, 100)
                            + "%, 최대 체력 +" + wizardValue(MagicSchoolTowers.RAVENCLAW.id(), "wandHealthBonus", .05, 100) + "%",
                    "슬리데린: 공격력 +" + wizardValue(MagicSchoolTowers.SLYTHERIN.id(), "wandDamageBonus", .10, 100) + "%",
                    "진급한 각 기숙사의 대마법사에게도 적용됩니다.");
            case DEATH_EATER -> List.of("ON일 때 매 웨이브에 죽음을 먹는 자 1기를 소환합니다.",
                    "마그마 큐브와 같은 기본 전투 수치·라운드 성장, 마법 피해를 사용합니다.",
                    "처치 시 생존한 내 모든 마법사의 숙련도 +라운드 × " + globalValue("deathEaterProficiencyPerRound", 2),
                    "전투 중 변경은 다음 웨이브부터 적용됩니다.");
            case MENTOR -> List.of("전투 시작 시 " + globalValue("mentorRadius", 1) + "칸 이내에 더 높은 티어의 내 마법사가 있으면",
                    "숙련도 +" + wizardValue(MagicSchoolTowers.FRESHMAN.id(), "mentorProficiency", 8, 1) + " (T1) / +"
                            + wizardValue(MagicSchoolTowers.GRYFFINDOR.id(), "mentorProficiency", 15, 1) + " (T2), 라운드당 1회");
            case SPELL_TRANSFER -> List.of("루모스 대상에게 입힌 피해의 " + percent("spellTransferDamageRatio", .10) + "%를",
                    "다른 루모스 대상 최대 " + globalValue("spellTransferMaxTargets", 5) + "기에게 마법 피해로 전달합니다.",
                    "학생마다 " + wizardValue(MagicSchoolTowers.CONFIG_ID, "spellTransferCooldownTicks", 70, 1) + "틱 재사용 대기시간 · 동시 공격 1회 발동");
            case DUELING_PRACTICE -> List.of("ON일 때 전투 시작 시 내 학생들이 최대 체력의 " + percent("duelingPracticeHealthRatio", .15) + "%를 잃고,",
                    "잃은 체력의 " + percent("duelingPracticeProficiencyRatio", .30) + "%만큼 주문 숙련도를 얻습니다.",
                    "피해 감소 무시 · 기존 숙련도 획득 보정 적용", "전투 중 변경은 다음 웨이브부터 적용됩니다.");
            case SPELL_PRACTICE -> List.of("전투 시작 시 장착한 주문 단계 × " + globalValue("spellPracticePerTier", 2) + "의 주문 숙련도를 얻습니다.",
                    "이전 라운드와 같은 주문이면 숙련도 +" + globalValue("spellPracticeRepeatBonus", 2) + " 추가",
                    "저주는 5단계로 계산 · 기존 숙련도 획득 보정 적용");
            case POTIONS -> List.of("치명적인 피해를 버티고 최대 체력의 " + percent("potionsHealRatio", .08) + "%를 회복합니다.",
                    "학생마다 라운드당 1회");
            case QUIDDITCH -> List.of("내 웨이브 방어 성공 시 즉시 " + globalValue("quidditchBaseReward", 5) + " 다이아를 얻습니다.",
                    "발동마다 보상 +" + globalValue("quidditchRewardIncrease", 5) + " 다이아 (최대 " + globalValue("quidditchMaxIncreases", 5) + "회 증가)");
            case TRANSFIGURATION -> List.of("내 학생이 적을 처치한 자리에 통을 소환합니다.",
                    "어그로 " + globalValue("barrelAggro", 100) + " · 받는 피해는 공격당 1 · 기본 체력 " + globalValue("barrelHealth", 1),
                    globalValue("barrelHealthRound1", 5) + " / " + globalValue("barrelHealthRound2", 15) + " / "
                            + globalValue("barrelHealthRound3", 25) + "라운드에 체력 +1",
                    "플레이어 공유 재사용 대기시간 " + globalValue("transfigurationCooldownTicks", 60) + "틱");
            case EXPLOSIVE_BARRELS -> List.of("선행 필요: 변신술 수업", "통 대신 적군 판정의 체력 1 폭탄통을 소환합니다.",
                    "파괴 시 " + globalValue("explosiveBarrelRadius", 2) + "칸 이내 적에게",
                    "파괴자 공격력의 " + percent("explosiveBarrelDamageRatio", .30) + "% 마법 피해를 입힙니다.",
                    "내 라인의 모든 마법사가 사망하면 폭발 없이 제거됩니다.");
        };
    }

    private static String percent(String key, double fallback) {
        return wizardValue(MagicSchoolTowers.CONFIG_ID, key, fallback, 100);
    }

    private static String globalValue(String key, double fallback) {
        return wizardValue(MagicSchoolTowers.CONFIG_ID, key, fallback, 1);
    }

    private static String wizardValue(String id, String key, double fallback, int scale) {
        return BigDecimal.valueOf(TowerBalanceRuntime.ability(id, key, fallback))
                .multiply(BigDecimal.valueOf(scale)).stripTrailingZeros().toPlainString();
    }

    @Override
    public void onTick() {
        if (!MagicSchoolCurriculum.canManageSchool(game, player.getUUID(), school)) close();
        else if (displayedRound != game.currentRound()
                || displayedAvailable != MagicSchoolCurriculum.canUpgradeThisRound(player.getUUID(), game.currentRound())) refresh();
    }
}
