package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * [스킬 배정] 창의 8·9번 슬롯에 넣는 패시브.
 *
 * <p>스킬과 달리 키에 묶이지 않고, 넣어 두기만 하면 항상 켜져 있습니다. 다이아로 사고, 빼면 낸 값을 전부
 * 돌려받습니다. 같은 패시브는 한 슬롯에만 둘 수 있습니다. 수치는 {@code tower_balance.json}의
 * {@code demon_lord_passive_<key>} 능력치에서 읽습니다.
 */
public enum DemonLordPassive {
    /** 마검 평타가 대상 주변까지 베고, 준 피해의 일부만큼 회복합니다. */
    BLOOD_CLEAVE("blood_cleave", "흡혈 참격", Items.REDSTONE),
    /** 웨이브마다 아군 타워 몇 개를 복제해 내 라인에 잠시 세웁니다. */
    LEGION_ECHO("legion_echo", "군단의 잔영", Items.ARMOR_STAND),
    /** 주는 피해가 늘고 받는 피해가 줄어듭니다. */
    DREAD("dread", "마왕의 위압", Items.NETHERITE_SCRAP),
    /** 좌클릭이 근접 평타 대신 앞으로 날아가며 꿰뚫는 검기가 됩니다. */
    BLADE_WAVE("blade_wave", "검기", Items.PRISMARINE_SHARD),
    /** 전투 중에도 날 수 있습니다. */
    DARK_FLIGHT("dark_flight", "어둠의 비상", Items.PHANTOM_MEMBRANE),
    /** 몇 라운드 동안 모든 능력치가 크게 오르지만, 끝나면 레벨과 스탯이 1레벨로 돌아갑니다. */
    DOOM_PACT("doom_pact", "파멸의 계약", Items.WITHER_ROSE),
    /** 웨이브가 시작될 때 인컴 타워의 침공군이 적 레인 대신 무작위 아군 라인을 지킵니다. */
    INVASION_GUARD("invasion_guard", "침공군 호위", Items.SHIELD);

    private final String key;
    private final String displayName;
    private final Item item;

    DemonLordPassive(String key, String displayName, Item item) {
        this.key = key;
        this.displayName = displayName;
        this.item = item;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Item item() {
        return item;
    }

    public String configId() {
        return "demon_lord_passive_" + key;
    }

    public double ability(String name, double fallback) {
        return TowerBalanceRuntime.ability(configId(), name, fallback);
    }

    public long cost() {
        return Math.max(0L, Math.round(ability("cost", 150.0)));
    }

    /** 창에 보여 줄 설명. 현재 설정값을 채워 넣습니다. */
    public List<String> description() {
        return switch (this) {
            case BLOOD_CLEAVE -> List.of(
                    "마검 평타가 대상 주변 " + number(ability("cleaveRadius", 2.5)) + "칸까지 베어,",
                    "주변 적에게 평타 피해의 " + percent(ability("cleaveRatio", 0.6)) + "를 줍니다.",
                    "준 피해의 " + percent(ability("lifeStealRatio", 0.15)) + "만큼 회복합니다"
                            + " (한 번에 최대 체력의 " + percent(ability("lifeStealCap", 0.04)) + "까지).",
                    "스킬 범위 스탯을 받으면 베는 범위도 넓어집니다."
            );
            case LEGION_ECHO -> List.of(
                    "웨이브가 시작될 때마다 아군 라인의 타워 " + (int) ability("copies", 5.0) + "개를",
                    "무작위로 복제해 내 라인에 그 웨이브 동안 세웁니다.",
                    "한 플레이어에 하나뿐인 타워(흑마법사, 엔더 드래곤 등)와",
                    "싸우지 않는 타워는 복제하지 않습니다. 아군이 없으면 효과가 없습니다."
            );
            case DREAD -> List.of(
                    "주는 피해 +" + percent(ability("damageBonus", 0.1)) + ", 받는 피해 -"
                            + percent(ability("defenseBonus", 0.1)) + "."
            );
            case BLADE_WAVE -> List.of(
                    "좌클릭이 근접 평타 대신 바라보는 방향으로 검기를 날립니다.",
                    "검기는 " + number(ability("range", 14.0)) + "칸까지 꿰뚫고 나가며, 경로의 모든 적에게",
                    "평타 피해의 " + percent(ability("damageRatio", 0.6)) + "를 줍니다. 평타 간격이 다 차야 나갑니다.",
                    "흡혈 참격이 있으면 폭발과 흡혈은 처음 맞은 적에게서만 터집니다."
            );
            case DARK_FLIGHT -> List.of(
                    "전투 중에도 날 수 있습니다(전투 밖에서는 원래 날 수 있습니다).",
                    "레인 바닥에서 " + number(ability("maxAltitude", 10.0)) + "칸 위까지만 오를 수 있습니다."
            );
            case DOOM_PACT -> List.of(
                    "장착한 뒤 " + (int) ability("rounds", 5.0) + "라운드 동안 체력 x" + number(ability("healthMultiplier", 2.5))
                            + ", 피해 x" + number(ability("damageMultiplier", 2.5)) + ",",
                    "받는 피해 -" + percent(ability("defenseBonus", 0.3)) + ", 재사용 대기시간 x"
                            + number(ability("cooldownMultiplier", 0.6)) + ", 스킬 범위 x" + number(ability("rangeMultiplier", 1.3)) + ",",
                    "이동 속도 +" + percent(ability("moveSpeedBonus", 0.25)) + "가 됩니다.",
                    "계약이 끝나면 레벨이 1이 되고 찍은 스탯과 남은 포인트가 모두 사라지며,",
                    "패시브도 사라집니다(환불 없음). 첫 웨이브가 시작된 뒤에는 뺄 수 없습니다."
            );
            case INVASION_GUARD -> List.of(
                    "웨이브가 시작될 때 인컴 타워의 침공군이 적 레인으로 가지 않고,",
                    "각자 무작위 아군 라인(내 라인 포함)에 나타나 그 웨이브 동안 라인을 지킵니다.",
                    "체력은 보낼 때의 " + percent(ability("healthRatio", 1.0)) + ", 공격력은 "
                            + percent(ability("damageRatio", 1.0)) + "입니다. 인컴은 그대로 받습니다."
            );
        };
    }

    public static DemonLordPassive fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (DemonLordPassive passive : values()) {
            if (passive.key.equals(normalized)) {
                return passive;
            }
        }
        return null;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }
}
