package kim.biryeong.semiontd.tower.hero;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class HeroShopGui extends SimpleGui {
    private static final int[] STATUS_SLOTS = {1, 2, 3, 4, 5};
    private static final int[] WEAPON_SLOTS = {10, 11, 12, 13, 14};
    private static final int[] UPGRADE_SLOTS = {19, 20, 21, 22, 23};
    private static final int[] SKILL_LEVELS = {1, 3, 5};

    private final ServerPlayer player;
    private final SemionGame game;

    public HeroShopGui(ServerPlayer player, SemionGame game) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.game = game;
        setTitle(Component.literal("용사 상점"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < 54; slot++) {
            clearSlot(slot);
        }
        HeroPartyState state = HeroPartyStates.state(player.getUUID());
        SemionPlayer semionPlayer = game.players().get(player.getUUID());
        long diamond = semionPlayer == null ? 0 : semionPlayer.economy().diamond();
        boolean editable = game.phase() == RoundPhase.PREPARE_AND_SUMMON
                && HeroPartyStates.hasActiveHero(game, player.getUUID());

        int index = 0;
        for (HeroWeapon weapon : HeroWeapon.values()) {
            int level = state.weaponLevel(weapon);
            boolean owned = state.owns(weapon);
            boolean equipped = state.equippedWeapon() == weapon;
            WeaponStatus status = weaponStatus(owned, equipped, editable, diamond,
                    HeroPartyBalance.weaponPurchaseCost(weapon));
            double currentDamage = effectiveWeaponDamage(weapon, level, state.adventurePoints());
            GuiElementBuilder weaponButton = new GuiElementBuilder(weapon.item())
                    .setName(Component.literal(weapon.displayName() + " +" + level)
                            .withStyle(status.color))
                    .addLoreLine(Component.literal("공격력 " + number(currentDamage)
                            + " · 사거리 " + number(HeroPartyBalance.weaponRange(weapon))
                            + " · " + HeroPartyBalance.weaponAttackInterval(weapon) + "틱"))
                    .addLoreLine(Component.literal("강화 배율 " + percent(HeroPartyBalance.weaponMultiplier(level)))
                            .withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal(equipped ? "착용 중" : owned ? "클릭: 착용" : "구매 " + HeroPartyBalance.weaponPurchaseCost(weapon) + " 다이아")
                            .withStyle(equipped ? ChatFormatting.GREEN : owned ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
                    .setCallback((slot, type, action) -> {
                        HeroPartyStates.ActionResult result = owned
                                ? HeroPartyStates.equipWeapon(game, player.getUUID(), weapon)
                                : HeroPartyStates.purchaseWeapon(game, player.getUUID(), weapon);
                        notifyResult(result);
                        refresh();
                    });
            weaponButton.glow(equipped);
            addSkillLore(weaponButton, weapon, level);
            if (!editable) {
                weaponButton.addLoreLine(Component.literal("웨이브 중에는 조회만 가능합니다.").withStyle(ChatFormatting.RED));
            }
            setSlot(STATUS_SLOTS[index], statusButton(weapon, status, diamond));
            setSlot(WEAPON_SLOTS[index], weaponButton);

            int nextLevel = level + 1;
            Item upgradeItem = nextLevel > HeroPartyBalance.MAX_WEAPON_LEVEL ? Items.NETHER_STAR : Items.ANVIL;
            GuiElementBuilder upgradeButton = new GuiElementBuilder(upgradeItem)
                    .setName(Component.literal(nextLevel > HeroPartyBalance.MAX_WEAPON_LEVEL
                            ? "최대 강화"
                            : weapon.displayName() + " +" + nextLevel + " 강화"))
                    .setCallback((slot, type, action) -> {
                        HeroPartyStates.ActionResult result = HeroPartyStates.upgradeWeapon(game, player.getUUID(), weapon);
                        notifyResult(result);
                        refresh();
                    });
            if (!owned) {
                upgradeButton.addLoreLine(Component.literal("무기를 먼저 구매하세요.").withStyle(ChatFormatting.RED));
            } else if (nextLevel > HeroPartyBalance.MAX_WEAPON_LEVEL) {
                upgradeButton.addLoreLine(Component.literal("최대 단계입니다.").withStyle(ChatFormatting.GREEN));
            } else {
                double nextDamage = effectiveWeaponDamage(weapon, nextLevel, state.adventurePoints());
                upgradeButton.addLoreLine(Component.literal("공격력 " + number(currentDamage) + " → " + number(nextDamage)));
                upgradeButton.addLoreLine(Component.literal("비용 " + HeroPartyBalance.weaponUpgradeCost(nextLevel) + " 다이아")
                        .withStyle(ChatFormatting.AQUA));
                String unlockedSkill = skillDescription(weapon, nextLevel);
                if (!unlockedSkill.isEmpty()) {
                    upgradeButton.addLoreLine(Component.literal("신규 스킬: " + unlockedSkill).withStyle(ChatFormatting.GOLD));
                }
            }
            setSlot(UPGRADE_SLOTS[index], upgradeButton);
            index++;
        }

        int nextArmor = state.armorLevel() + 1;
        GuiElementBuilder armorButton = new GuiElementBuilder(Items.DIAMOND_CHESTPLATE)
                .setName(Component.literal("갑옷 +" + state.armorLevel()).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("추가 체력 " + number(HeroPartyBalance.armorHealth(state.armorLevel()))))
                .addLoreLine(Component.literal("피해 감소 " + percent(HeroPartyBalance.armorReduction(state.armorLevel()))))
                .setCallback((slot, type, action) -> {
                    notifyResult(HeroPartyStates.upgradeArmor(game, player.getUUID()));
                    refresh();
                });
        if (nextArmor > HeroPartyBalance.MAX_ARMOR_LEVEL) {
            armorButton.addLoreLine(Component.literal("최대 단계입니다.").withStyle(ChatFormatting.GREEN));
        } else {
            armorButton.addLoreLine(Component.literal("다음: 체력 +" + number(HeroPartyBalance.armorHealth(nextArmor))
                    + " · 감소 " + percent(HeroPartyBalance.armorReduction(nextArmor))));
            armorButton.addLoreLine(Component.literal("비용 " + HeroPartyBalance.armorUpgradeCost(nextArmor) + " 다이아")
                    .withStyle(ChatFormatting.AQUA));
        }
        setSlot(31, armorButton);
        boolean armorVisible = state.armorVisible();
        setSlot(32, new GuiElementBuilder(armorVisible ? Items.ARMOR_STAND : Items.GRAY_DYE)
                .setName(Component.literal(armorVisible ? "방어구 표시 중" : "방어구 숨김")
                        .withStyle(armorVisible ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .addLoreLine(Component.literal(armorVisible ? "클릭: 방어구 숨기기" : "클릭: 방어구 표시하기"))
                .addLoreLine(Component.literal("체력·피해 감소 효과는 유지됩니다.").withStyle(ChatFormatting.AQUA))
                .glow(armorVisible)
                .setCallback((slot, type, action) -> {
                    notifyResult(HeroPartyStates.toggleArmorVisibility(game, player.getUUID()));
                    refresh();
                }));

        HeroPartyState.HeroQuestSnapshot quest = state.quest();
        setSlot(39, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(Component.literal("현재 퀘스트").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal(quest == null ? "배정된 퀘스트가 없습니다." : quest.label()))
                .addLoreLine(Component.literal(quest == null ? "" : number(quest.progress()) + "/" + number(quest.target()) + " · 보상 " + quest.reward() + "점")));
        setSlot(41, new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Component.literal("파티 현황").withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal("확정 동료 " + state.committedCompanions().size() + "/" + HeroPartyBalance.MAX_COMPANIONS))
                .addLoreLine(Component.literal(state.committedCompanions().isEmpty()
                        ? "아직 확정한 동료가 없습니다."
                        : state.committedCompanions().stream().map(HeroCompanionRole::displayName).sorted().toList().toString())));
        setSlot(48, new GuiElementBuilder(Items.DIAMOND)
                .setName(Component.literal("다이아 " + diamond).withStyle(ChatFormatting.AQUA))
                .addLoreLine(Component.literal("모험 점수 " + state.adventurePoints()))
                .addLoreLine(Component.literal("표시 공격력에 모험 보너스 적용").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal(editable ? "구매·교체 가능" : "조회 전용").withStyle(editable ? ChatFormatting.GREEN : ChatFormatting.RED)));
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((slot, type, action) -> close()));
    }

    private void notifyResult(HeroPartyStates.ActionResult result) {
        if (result == HeroPartyStates.ActionResult.SUCCESS) {
            return;
        }
        String message = switch (result) {
            case PLAYER_NOT_IN_GAME -> "현재 경기 참가자가 아닙니다.";
            case INVALID_PHASE -> "준비 단계에서만 장비를 변경할 수 있습니다.";
            case HERO_REQUIRED -> "용사를 설치해야 상점을 사용할 수 있습니다.";
            case UNKNOWN_WEAPON -> "알 수 없는 무기입니다.";
            case ALREADY_OWNED -> "이미 보유한 무기입니다.";
            case NOT_OWNED -> "무기를 먼저 구매하세요.";
            case MAX_LEVEL -> "이미 최대 강화 단계입니다.";
            case NOT_ENOUGH_DIAMOND -> "다이아가 부족합니다.";
            case SUCCESS -> "";
        };
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    static double effectiveWeaponDamage(HeroWeapon weapon, int level, int adventurePoints) {
        return HeroPartyBalance.weaponDamage(weapon)
                * HeroPartyBalance.weaponMultiplier(level)
                * HeroPartyBalance.partyDamageMultiplier(adventurePoints);
    }

    static WeaponStatus weaponStatus(boolean owned, boolean equipped, boolean editable, long diamond, long cost) {
        if (equipped) {
            return WeaponStatus.EQUIPPED;
        }
        if (owned) {
            return WeaponStatus.OWNED;
        }
        if (!editable) {
            return WeaponStatus.READ_ONLY;
        }
        return diamond >= cost ? WeaponStatus.PURCHASABLE : WeaponStatus.UNAFFORDABLE;
    }

    private static GuiElementBuilder statusButton(HeroWeapon weapon, WeaponStatus status, long diamond) {
        long cost = HeroPartyBalance.weaponPurchaseCost(weapon);
        return switch (status) {
            case EQUIPPED -> new GuiElementBuilder(Items.LIME_STAINED_GLASS_PANE)
                    .setName(Component.literal("착용 중 · " + weapon.displayName()).withStyle(status.color))
                    .addLoreLine(Component.literal("현재 전투에 사용됩니다.").withStyle(ChatFormatting.GRAY));
            case OWNED -> new GuiElementBuilder(Items.YELLOW_STAINED_GLASS_PANE)
                    .setName(Component.literal("보유 · " + weapon.displayName()).withStyle(status.color))
                    .addLoreLine(Component.literal("아래 무기를 클릭해 착용").withStyle(ChatFormatting.GRAY));
            case PURCHASABLE -> new GuiElementBuilder(Items.LIGHT_BLUE_STAINED_GLASS_PANE)
                    .setName(Component.literal("구매 가능 · " + weapon.displayName()).withStyle(status.color))
                    .addLoreLine(Component.literal(cost + " 다이아").withStyle(ChatFormatting.AQUA));
            case UNAFFORDABLE -> new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                    .setName(Component.literal("다이아 부족 · " + weapon.displayName()).withStyle(status.color))
                    .addLoreLine(Component.literal("필요 " + cost + " · 보유 " + diamond).withStyle(ChatFormatting.RED));
            case READ_ONLY -> new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal("조회 전용 · " + weapon.displayName()).withStyle(status.color))
                    .addLoreLine(Component.literal("준비 단계에서 구매할 수 있습니다.").withStyle(ChatFormatting.GRAY));
        };
    }

    static String skillDescription(HeroWeapon weapon, int level) {
        if (weapon == null) {
            return "";
        }
        return switch (weapon) {
            case SWORD -> switch (level) {
                case 1 -> "보조 대상 50%";
                case 3 -> "5회마다 피해 감소 20%";
                case 5 -> "5번째 공격 220%·광역 80%";
                default -> "";
            };
            case GREATSWORD -> switch (level) {
                case 1 -> "반경 2.5에 40%";
                case 3 -> "빈사 대상 피해 +35%";
                case 5 -> "4회마다 반경 3.5에 150%";
                default -> "";
            };
            case LONGBOW -> switch (level) {
                case 1 -> "추가 2대상 65%·40%";
                case 3 -> "4회마다 피해 증폭 15%";
                case 5 -> "최대 체력 비례 피해";
                default -> "";
            };
            case STAFF -> switch (level) {
                case 1 -> "연쇄 2대상 60%·35%";
                case 3 -> "이동 속도 20% 감소";
                case 5 -> "6회마다 반경 3에 180%";
                default -> "";
            };
            case TOME -> switch (level) {
                case 1 -> "3회마다 아군 20 회복";
                case 3 -> "회복 대상 피해 감소 10%";
                case 5 -> "6회마다 파티 회복·광역 공격";
                default -> "";
            };
        };
    }

    private static void addSkillLore(GuiElementBuilder button, HeroWeapon weapon, int level) {
        for (int skillLevel : SKILL_LEVELS) {
            boolean unlocked = level >= skillLevel;
            button.addLoreLine(Component.literal((unlocked ? "해금 " : "잠김 ")
                            + "+" + skillLevel + " " + skillDescription(weapon, skillLevel))
                    .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
    }

    private static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6
                ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    enum WeaponStatus {
        EQUIPPED(ChatFormatting.GREEN),
        OWNED(ChatFormatting.YELLOW),
        PURCHASABLE(ChatFormatting.AQUA),
        UNAFFORDABLE(ChatFormatting.RED),
        READ_ONLY(ChatFormatting.GRAY);

        private final ChatFormatting color;

        WeaponStatus(ChatFormatting color) {
            this.color = color;
        }
    }
}
