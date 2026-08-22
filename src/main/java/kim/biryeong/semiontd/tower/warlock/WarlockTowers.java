package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;
import static kim.biryeong.semiontd.tower.warlock.WarlockFormatting.warlockText;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import kim.biryeong.semiontd.entity.visual.FrogVisual;
import kim.biryeong.semiontd.entity.visual.SheepVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.frog.FrogVariants;
import net.minecraft.world.item.DyeColor;

import java.util.List;

public final class WarlockTowers {
    public static final String CONFIG_ID = "warlock_global";

    private WarlockTowers() {
    }

    public static final TowerType BASE_WARLOCK_TOWER = tower("base_warlock_tower", "흑마법사 타워", 0, 80, 4, 5, 20, 30, byId(EntityType.WITCH), baseWarlockDescription());
    public static final TowerType RANGED_WARLOCK_TOWER = tower("ranged_warlock_tower", "원거리 흑마법사 타워", 0, 100, 7, 8, 20, 20, byId(EntityType.WITCH), rangedWarlockDescription());
    public static final TowerType MELEE_WARLOCK_TOWER = tower("melee_warlock_tower", "근거리 흑마법사 타워", 0, 120, 3, 7, 20, 80, byId(EntityType.WITCH), meleeWarlockDescription());
    public static final TowerType T1_SLAVE = tower("t1_slave", "희생\"양\"", 50, 75, 2, 4, 20, 30, SheepVisual.builder().color(DyeColor.RED).build(), List.of("<gray>" + warlockText("흑마법사") + "가 데려온 양입니다.</gray>"));
    public static final TowerType T2_SLAVE = tower("t2_slave", "희생\"양\"", 85, 120, 2, 8, 20, 50, SheepVisual.builder().color(DyeColor.PINK).build(), List.of("<gray>" + warlockText("흑마법사") + "가 데려온 희귀한 양입니다.</gray>", "<gray>사망 시 주위 20블록 내 적이 받는 " + attackDamageText("피해") + "를 " + attackDamageText("10%") + " 증가시킵니다.</gray>"));
    public static final TowerType T3_SLAVE = tower("t3_slave", "희생\"양\"", 135, 185, 2, 12, 20, 70, SheepVisual.builder().color(DyeColor.WHITE).build(), List.of("<gray>" + warlockText("흑마법사") + "가 데려온 양입니다. 희귀했던 색을 잃어 화가 났습니다.</gray>", "<gray>사망 시 주위 20블록 내 적이 받는 " + attackDamageText("피해") + "를 " + attackDamageText("10%") + " 증가시킵니다.</gray>"));
    public static final TowerType T1_RANGED_SLAVE = tower("t1_ranged_slave", "애완 박쥐", 55, 70, 7, 5, 17, 20, byId(EntityType.BAT), List.of("<gray>" + warlockText("흑마법사") + "가 키우는 박쥐입니다.</gray>", "<gray>애완동물도 얄짤없네요.</gray>"));
    public static final TowerType T2_RANGED_SLAVE = tower("t2_ranged_slave", "애완 개구리", 90, 120, 7, 8, 15, 15, FrogVisual.builder().variant(FrogVariants.COLD).build(), List.of("<gray>" + warlockText("흑마법사") + "가 키우는 개구리입니다.</gray>", "<gray>사망 시 주위 20블록 내 적의 " + attackSpeedText("공격 속도") + "를 " + attackSpeedText("10%") + " 감소시킵니다.</gray>"));
    public static final TowerType T3_RANGED_SLAVE = tower("t3_ranged_slave", "애완 개구리", 140, 185, 7, 12, 13, 15, FrogVisual.builder().variant(FrogVariants.WARM).build(), List.of("<gray>" + warlockText("흑마법사") + "가 키우는 개구리입니다.</gray>", "<gray>사망 시 주위 20블록 내 적의 " + attackSpeedText("공격 속도") + "를 " + attackSpeedText("10%") + " 감소시킵니다.</gray>"));

    private static final List<TowerType> ALL = List.of(
            BASE_WARLOCK_TOWER,
            RANGED_WARLOCK_TOWER,
            MELEE_WARLOCK_TOWER,
            T1_SLAVE,
            T2_SLAVE,
            T3_SLAVE,
            T1_RANGED_SLAVE,
            T2_RANGED_SLAVE,
            T3_RANGED_SLAVE
    );

    static {
        TowerDescriptionRegistry.registerTemplate(BASE_WARLOCK_TOWER, baseWarlockDescription());
        TowerDescriptionRegistry.registerTemplate(RANGED_WARLOCK_TOWER, rangedWarlockDescription());
        TowerDescriptionRegistry.registerTemplate(MELEE_WARLOCK_TOWER, meleeWarlockDescription());
        TowerDescriptionRegistry.registerTemplate(T2_SLAVE, List.of("<gray>" + warlockText("흑마법사") + "가 데려온 희귀한 양입니다.</gray>", "<gray>사망 시 주위 {ability.deathEffectRadius:number}블록 내 적이 받는 " + attackDamageText("피해") + "를 " + attackDamageText("{ability.towerDamageTakenBonus:percent}") + " 증가시킵니다.</gray>"));
        TowerDescriptionRegistry.registerTemplate(T3_SLAVE, List.of("<gray>" + warlockText("흑마법사") + "가 데려온 양입니다. 희귀했던 색을 잃어 화가 났습니다.</gray>", "<gray>사망 시 주위 {ability.deathEffectRadius:number}블록 내 적이 받는 " + attackDamageText("피해") + "를 " + attackDamageText("{ability.towerDamageTakenBonus:percent}") + " 증가시킵니다.</gray>"));
        TowerDescriptionRegistry.registerTemplate(T2_RANGED_SLAVE, List.of("<gray>" + warlockText("흑마법사") + "가 키우는 개구리입니다.</gray>", "<gray>사망 시 주위 {ability.deathEffectRadius:number}블록 내 적의 " + attackSpeedText("공격 속도") + "를 " + attackSpeedText("{ability.attackSpeedReduction:percent}") + " 감소시킵니다.</gray>"));
        TowerDescriptionRegistry.registerTemplate(T3_RANGED_SLAVE, List.of("<gray>" + warlockText("흑마법사") + "가 키우는 개구리입니다.</gray>", "<gray>사망 시 주위 {ability.deathEffectRadius:number}블록 내 적의 " + attackSpeedText("공격 속도") + "를 " + attackSpeedText("{ability.attackSpeedReduction:percent}") + " 감소시킵니다.</gray>"));
    }

    private static List<String> baseWarlockDescription() {
        return List.of(
                "<gray>원거리 또는 근거리 흑마법사를 선택할 수 있습니다.</gray>",
                "<gray>" + warlockText("흑마법사") + " 타워는 1기만 설치할 수 있습니다.</gray>"
        );
    }

    private static List<String> rangedWarlockDescription() {
        return List.of(
                "<gray>" + healthText("체력 " + ability(RANGED_THRESHOLD, "percent")) + " 이하이면 주위 " + globalAbility(SACRIFICE_RADIUS, "blocks") + " 내 아군 타워를 흡수합니다.</gray>",
                "<gray>흡수 시 " + healthText("최대 체력 증가분") + "에 " + healthText("체력 " + globalAbility(ABSORPTION_HEAL, "integer")) + "을 더해 회복합니다.</gray>",
                "<gray>흡수한 타워 " + healthText("체력") + "과 " + attackDamageText("피해") + "의 " + ability(RANGED_ROUND_STAT, "percent") + "를 이번 라운드 동안 획득합니다.</gray>",
                "<gray>흡수한 타워마다 " + healthText("체력 +" + ability(RANGED_PERMANENT_HEALTH, "percent")) + ", " + attackDamageText("피해 +" + ability(RANGED_PERMANENT_DAMAGE, "percent")) + "를 영구 누적합니다.</gray>",
                "<gray>생존 중인 " + attackDamageText("개구리 계열") + "마다 " + healthText("체력 +" + ability(RANGED_PET_HEALTH, "percent")) + ", " + attackDamageText("피해 +" + ability(RANGED_PET_DAMAGE, "percent")) + "를 얻습니다.",
                "<gray>최대 " + healthText("체력 +" + ability(RANGED_PET_HEALTH_CAP, "percent")) + ", " + attackDamageText("피해 +" + ability(RANGED_PET_DAMAGE_CAP, "percent")) + "까지 증가합니다.</gray>",
                "<gray>이 타워만 생존한 상태에서 " + healthText("체력 " + globalAbility(AWAKENING_THRESHOLD, "percent")) + " 이하이면 각성합니다.</gray>",
                "<gray>각성 시 " + healthText("체력") + "을 회복하고, " + regenerationText("재생") + "이 증가합니다.</gray>",
                "<gray>능력치는 높아질수록 증가 효율이 감소합니다.</gray>"
        );
    }

    private static List<String> meleeWarlockDescription() {
        return List.of(
                "<gray>" + healthText("체력 " + ability(MELEE_THRESHOLD, "percent")) + " 이하이면 주위 " + globalAbility(SACRIFICE_RADIUS, "blocks") + " 내 아군 타워를 흡수합니다.</gray>",
                "<gray>흡수 시 " + healthText("최대 체력 증가분") + "에 " + healthText("체력 " + globalAbility(ABSORPTION_HEAL, "integer")) + "을 더해 회복합니다.</gray>",
                "<gray>흡수한 타워 " + healthText("체력") + "과 " + attackDamageText("피해") + "의 " + ability(MELEE_ROUND_STAT, "percent") + "를 이번 라운드 동안 획득합니다.</gray>",
                "<gray>흡수한 타워마다 " + healthText("체력 +" + ability(MELEE_PERMANENT_HEALTH, "percent")) + ", " + attackDamageText("피해 +" + ability(MELEE_PERMANENT_DAMAGE, "percent")) + "를 영구 누적합니다.</gray>",
                "<gray>생존 중인 " + healthText("양 계열") + "마다 " + healthText("체력 +" + ability(MELEE_PET_HEALTH, "percent")) + ", " + attackDamageText("피해 +" + ability(MELEE_PET_DAMAGE, "percent")) + "를 얻습니다.</gray>",
                "<gray>최대 " + healthText("체력 +" + ability(MELEE_PET_HEALTH_CAP, "percent")) + ", " + attackDamageText("피해 +" + ability(MELEE_PET_DAMAGE_CAP, "percent")) + "까지 증가합니다.</gray>",
                "<gray>이 타워만 생존한 상태에서 " + healthText("체력 " + globalAbility(AWAKENING_THRESHOLD, "percent")) + " 이하이면 각성합니다.</gray>",
                "<gray>각성 시 " + healthText("체력") + "을 회복하고, " + attackDamageText("추가 피해") + "와 " + movementSpeedText("이동 속도") + "가 증가합니다.</gray>",
                "<gray>능력치는 높아질수록 증가 효율이 감소합니다.</gray>"
        );
    }

    private static String ability(WarlockConfig.Ability ability, String format) {
        return "{ability." + ability.key() + ":" + format + "}";
    }

    private static String globalAbility(WarlockConfig.Ability ability, String format) {
        return "{ability." + CONFIG_ID + "." + ability.key() + ":" + format + "}";
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isWarlockTower(TowerType towerType) {
        return isWarlockCore(towerType) || isMeleeSlave(towerType) || isRangedSlave(towerType);
    }

    public static boolean isWarlockCore(TowerType towerType) {
        if (towerType == null) {return false;}
        String id = towerType.id();
        return id.equals(BASE_WARLOCK_TOWER.id()) || id.equals(RANGED_WARLOCK_TOWER.id()) || id.equals(MELEE_WARLOCK_TOWER.id());
    }

    public static boolean isMeleeSlave(TowerType towerType) {
        if (towerType == null) {return false;}
        String id = towerType.id();
        return id.equals(T1_SLAVE.id()) || id.equals(T2_SLAVE.id()) || id.equals(T3_SLAVE.id());
    }

    public static boolean isRangedSlave(TowerType towerType) {
        if (towerType == null) {return false;}
        String id = towerType.id();
        return id.equals(T1_RANGED_SLAVE.id()) || id.equals(T2_RANGED_SLAVE.id()) || id.equals(T3_RANGED_SLAVE.id());
    }
}
