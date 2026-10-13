package kim.biryeong.semiontd.tower.magicschool;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public enum MagicSchoolSpell {
    EXPELLIARMUS("expelliarmus", 1, "엑스펠리아르무스", .8,
            "라운드 첫 공격 대상은 {disarmTicks:s}초간 공격할 수 없습니다.", Map.of("disarmTicks", 40.0)),
    MUGGLE_WAND("muggle_wand", 1, "머글의 지팡이", 1,
            "공격 주기는 {fixedIntervalTicks}틱으로 고정됩니다.\n전용 실버 증강 '머글의 지팡이'로 해금합니다.",
            Map.of("fixedDamage", 80.0, "fixedIntervalTicks", 20.0)),
    STUPEFY("stupefy", 2, "스튜페파이", 1,
            "대상별 명중을 합산하여 매 {hitsToStun}회마다 {stunTicks:s}초 기절시킵니다.", Map.of("hitsToStun", 3.0, "stunTicks", 10.0)),
    PROTEGO("protego", 2, "프로테고", .5,
            "받는 피해가 라운드 종료까지 {damageReduction:p}% 감소합니다.\n전투 시작 시 어그로가 라운드 종료까지 {waveAggroBonus} 증가합니다.", Map.of("damageReduction", .3, "waveAggroBonus", 60.0)),
    WINGARDIUM_LEVIOSA("wingardium_leviosa", 2, "윙가르디움 레비오우사", .85,
            "전투 시작 {intervalTicks:s}초 후부터 {intervalTicks:s}초마다 주변 {radius}칸 내 적에게 공격력 {liftDamageMultiplier:p}%의 마법 피해를 입히고 공중에 띄워 {stunTicks:s}초간 기절시킵니다.",
            Map.of("intervalTicks", 100.0, "radius", 6.0, "stunTicks", 24.0, "liftPower", .8, "liftDamageMultiplier", .25)),
    EXPULSO("expulso", 3, "엑스펄소", .8,
            "주 대상 주변 {radius}칸의 다른 적에게 공격력 {secondaryMultiplier:p}%의 마법 피해를 입힙니다.", Map.of("radius", 1.2, "secondaryMultiplier", .5)),
    LUMOS("lumos", 3, "루모스", .8,
            "대상과 주변 {radius}칸에 루모스를 부여합니다.", Map.of("radius", 2.0, "magicVulnerability", .12)),
    EPISKEY("episkey", 3, "에피스키", .9,
            "공격 시 주변 {radius}칸의 체력 비율이 가장 낮은 아군 마법사를 공격력 {healingMultiplier:p}%만큼 회복합니다.\n각 마법사는 {recipientCooldownTicks:s}초에 한 번만 이 회복을 받습니다.", Map.of("radius", 8.0, "healingMultiplier", .75, "recipientCooldownTicks", 60.0)),
    SECTUMSEMPRA("sectumsempra", 4, "섹툼셈프라", 1,
            "주변 {radius}칸의 추가 대상 1기에 공격력 {secondaryMultiplier:p}%의 마법 피해를 입힙니다.\n피해를 받은 적의 회복량을 {healingReductionTicks:s}초간 {healingReduction:p}% 감소시킵니다.", Map.of("radius", 3.0, "secondaryMultiplier", .5, "healingReduction", .65, "healingReductionTicks", 100.0)),
    BOMBARDA("bombarda", 4, "봄바르다", .9,
            "주 대상 주변 {radius}칸의 다른 적에게 공격력 {secondaryMultiplier:p}%의 마법 피해를 입힙니다.", Map.of("radius", 2.4, "secondaryMultiplier", .70)),
    PROTEGO_MAXIMA("protego_maxima", 4, "프로테고 맥시마", .8,
            "자신의 받는 피해 {damageReduction:p}% 감소.\n전투 시작 시 자신과 주변 {radius}칸 아군의 받는 피해 {auraReduction:p}% 감소 (최대 {maxAuraStacks}회, 자기 보호 및 다른 피해 감소와 곱연산).\n전투 시작 시 자신의 어그로가 {waveAggroBonus} 증가합니다.\n보호 효과와 어그로 증가는 라운드 종료까지 유지됩니다.", Map.of("damageReduction", .30, "radius", 6.0, "auraReduction", .05, "maxAuraStacks", 3.0, "waveAggroBonus", 60.0)),
    EXPECTO_PATRONUM("expecto_patronum", 5, "엑스펙토 패트로눔", 1,
            "주변 {radius}칸의 추가 대상에게 공격력 {secondaryMultiplier:p}%의 마법 피해를 입힙니다.\n추가 대상은 1기로 시작하며 매 {attacksPerExtraTarget}번째 공격마다 1기 증가합니다.\n부족한 추가 공격은 주 대상에게 적용합니다. 라운드마다 초기화됩니다.", Map.of("radius", 3.0, "secondaryMultiplier", .25, "attacksPerExtraTarget", 2.0)),
    LUMOS_MAXIMA("lumos_maxima", 5, "루모스 맥시마", 1,
            "대상과 주변 {radius}칸에 루모스를 부여합니다.", Map.of("radius", 4.0)),
    RENNERVATE("rennervate", 5, "레네르바테", 1.1,
            "전투 시작 및 {intervalTicks:s}초마다 주변 {radius}칸 아군의 디버프를 해제합니다.\n{buffTicks:s}초간 공격력 +{damageBonus:p}% (중첩 불가).", Map.of("radius", 8.0, "intervalTicks", 100.0, "buffTicks", 60.0, "damageBonus", .12)),
    AVADA_KEDAVRA("avada_kedavra", 6, "아바다케다브라", 1,
            "공격 속도 -{attackSpeedPenalty:p}%.", Map.of("attackSpeedPenalty", .6, "maxHealthMultiplier", 2.0)),
    CRUCIO("crucio", 6, "크루시오", .15,
            "이후 {dotTicks:s}초간 {dotIntervalTicks}틱마다 공격력 {dotMultiplier:p}%의 마법 피해를 입힙니다.\n크루시오의 직접·지속 피해를 받은 적은 받는 마법 피해가 {vulnerabilityPerStack:p}% 증가합니다 (최대 {maxStacks}회 중첩, 라운드 종료까지).", Map.of("dotMultiplier", .15, "dotTicks", 40.0, "dotIntervalTicks", 2.0, "vulnerabilityPerStack", .1, "maxStacks", 20.0)),
    IMPERIO("imperio", 6, "임페리오", 1,
            "대상은 {controlTicks:s}초간 타워 대신 자신의 아군을 공격력 100%로 공격합니다.\n아군이 없으면 자신을 공격합니다. 재사용 대기시간이 없습니다.\n조종당하는 적은 공격 대상의 최하순위가 됩니다.", Map.of("controlTicks", 40.0, "controlVersion", 1.0));

    private static final Map<String, MagicSchoolSpell> BY_ID = indexById();
    private static final Pattern PARAMETER = Pattern.compile("\\{([a-zA-Z]+)(?::([ps]))?}");
    private static final Pattern ATTACK_COEFFICIENT = Pattern.compile("(?:공격력(?:의)?|대상 최대 체력의) [+-]?\\d+(?:\\.\\d+)?%");
    private final String id;
    private final int tier;
    private final String displayName;
    private final String description;
    private final Map<String, Double> defaults;

    MagicSchoolSpell(String id, int tier, String displayName, double damageMultiplier, String description, Map<String, Double> parameters) {
        this.id = id;
        this.tier = tier;
        this.displayName = displayName;
        this.description = description;
        var values = new LinkedHashMap<>(parameters);
        values.put("damageMultiplier", damageMultiplier);
        this.defaults = Map.copyOf(values);
    }

    public String id() { return id; }
    public int tier() { return tier; }
    public String displayName() { return displayName; }
    public String configId() { return "magic_school_spell_" + id; }
    public Map<String, Double> defaultAbilities() { return defaults; }
    public double value(String key) { return TowerBalanceRuntime.ability(configId(), key, defaults.get(key)); }
    public int ticks(String key) { return (int) Math.round(value(key)); }
    public double damageMultiplier() { return value("damageMultiplier"); }
    public boolean curse() { return tier == 6; }
    public int requiredSpellTier() { return curse() ? 5 : tier; }
    public long changeCost() {
        return MagicSchoolCurriculum.integer("spellChangeTier" + tier + "Cost", defaultChangeCost(tier));
    }
    static int defaultChangeCost(int tier) {
        if (tier < 1 || tier > 6) throw new IllegalArgumentException("Spell tier must be between 1 and 6");
        return tier == 1 ? 0 : 20 + (tier - 2) * 15;
    }
    public boolean spreadsLumos() { return this == LUMOS || this == LUMOS_MAXIMA; }

    private String render(String template) {
        return PARAMETER.matcher(template).replaceAll(match -> {
            BigDecimal value = BigDecimal.valueOf(value(match.group(1)));
            if ("p".equals(match.group(2))) value = value.movePointRight(2);
            if ("s".equals(match.group(2))) value = value.divide(BigDecimal.valueOf(20));
            return value.stripTrailingZeros().toPlainString();
        });
    }

    public List<String> effectLines() {
        String attack = switch (this) {
            case MUGGLE_WAND -> "기본 공격이 {fixedDamage}의 물리 피해를 입힙니다.";
            case AVADA_KEDAVRA -> "기본 공격이 대상 최대 체력의 {maxHealthMultiplier:p}%의 마법 피해를 입힙니다.";
            default -> "기본 공격이 공격력 {damageMultiplier:p}%의 마법 피해를 입힙니다.";
        };
        var lines = new java.util.ArrayList<>(List.of(render(attack)));
        lines.addAll(List.of(render(description).split("\n")));
        if (spreadsLumos()) {
            lines.add(LUMOS.render("루모스: 받는 마법 피해 +{magicVulnerability:p}%, 발광 (라운드 종료까지)."));
            lines.add("마법사는 루모스 디버프가 부여된 대상을 우선 공격합니다.");
            lines.add("단, 루모스·루모스 맥시마를 장착한 마법사는 루모스가 없는 적을 우선 공격합니다.");
        }
        if (curse()) {
            lines.add("주문 숙련도 " + MagicSchoolCurriculum.integer("curseProficiencyThreshold", 500)
                    + " 이하에서는 공격 속도가 추가로 " + BigDecimal.ONE.subtract(BigDecimal.valueOf(
                    MagicSchoolCurriculum.value("curseLowProficiencyAttackSpeedMultiplier", .1)))
                    .movePointRight(2).stripTrailingZeros().toPlainString() + "% 감소합니다 (곱연산).");
            lines.add("플레이어마다 이 저주를 한 마법사에게만 장착할 수 있습니다.");
        }
        return List.copyOf(lines);
    }

    public String effectDescription() { return String.join(" ", effectLines()); }

    public static String highlightAttackCoefficients(String text) {
        return ATTACK_COEFFICIENT.matcher(text).replaceAll("<color:#ffb86c>$0</color>");
    }

    private static Map<String, MagicSchoolSpell> indexById() {
        Map<String, MagicSchoolSpell> spells = new LinkedHashMap<>();
        for (MagicSchoolSpell spell : values()) {
            spells.putIfAbsent(spell.id, spell);
        }
        return Map.copyOf(spells);
    }

    public static Optional<MagicSchoolSpell> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }
}
