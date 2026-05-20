package kim.biryeong.semiontd.summon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.config.SummonConfig;

public final class SummonDescriptionFactory {
    private SummonDescriptionFactory() {
    }

    public static List<String> describe(SummonConfig.SummonDefinition definition) {
        ArrayList<String> lines = new ArrayList<>();
        addAbilityLines(lines, definition);
        return List.copyOf(lines);
    }

    private static void addAbilityLines(ArrayList<String> lines, SummonConfig.SummonDefinition definition) {
        switch (definition.id()) {
            case "wolf", "cave_spider", "stray", "goat", "bogged", "breeze", "vindicator" ->
                    addTowerDebuffLine(lines, definition, "공격속도", "magnitude");
            case "horse", "shulker" ->
                    addTowerDebuffLine(lines, definition, "사거리", "magnitude");
            case "elder_guardian" ->
                    addLine(lines, "반경 " + blocks(value(definition, "radius", 8.0))
                            + " 내 가까운 타워 최대 " + integer(definition, "maxTargets", 3)
                            + "기의 공격속도를 " + seconds(definition, "durationTicks", 100)
                            + "간 " + percent(value(definition, "attackSpeedMagnitude", 0.30))
                            + ", 사거리를 " + percent(value(definition, "rangeMagnitude", 0.20))
                            + " 감소시킵니다. (" + seconds(definition, "cooldownTicks", 80) + " 쿨타임)");
            case "turtle" ->
                    addAllyBuffLine(lines, definition, "받는 피해", "magnitude", "감소");
            case "allay" ->
                    addLine(lines, "반경 " + blocks(value(definition, "radius", 6.0))
                            + " 내 아군 유닛 최대 " + integer(definition, "maxTargets", 6)
                            + "기를 " + number(value(definition, "healAmount", 8.0))
                            + " 회복시킵니다. (" + seconds(definition, "cooldownTicks", 120) + " 쿨타임)");
            case "fox" ->
                    addAllyBuffLine(lines, definition, "공격력", "magnitude", "증가");
            case "ocelot" ->
                    addAllyBuffLine(lines, definition, "이동속도", "magnitude", "증가");
            case "witch" ->
                    addLine(lines, "반경 " + blocks(value(definition, "radius", 7.0))
                            + " 내 아군 유닛 최대 " + integer(definition, "maxTargets", 8)
                            + "기의 이동속도를 " + seconds(definition, "durationTicks", 80)
                            + "간 " + percent(value(definition, "moveMagnitude", 0.30))
                            + ", 공격력을 " + percent(value(definition, "attackMagnitude", 0.25))
                            + ", 공격속도를 " + percent(value(definition, "attackSpeedMagnitude", 0.25))
                            + " 증가시킵니다. (" + seconds(definition, "cooldownTicks", 60) + " 쿨타임)");
            case "evoker" ->
                    addLine(lines, "반경 " + blocks(value(definition, "radius", 8.0))
                            + " 내 아군 유닛 최대 " + integer(definition, "maxTargets", 10)
                            + "기의 공격력을 " + seconds(definition, "durationTicks", 80)
                            + "간 " + percent(value(definition, "attackMagnitude", 0.25))
                            + " 증가시키고 받는 피해를 " + percent(value(definition, "damageReductionMagnitude", 0.25))
                            + " 감소시킵니다. (" + seconds(definition, "cooldownTicks", 60) + " 쿨타임)");
            case "guardian", "blaze", "ghast", "wither_skeleton", "warden" ->
                    addSiegeLine(lines, definition);
            default -> {
            }
        }
    }

    private static void addTowerDebuffLine(
            ArrayList<String> lines,
            SummonConfig.SummonDefinition definition,
            String statName,
            String magnitudeKey
    ) {
        addLine(lines, "반경 " + blocks(value(definition, "radius", 6.0))
                + " 내 가까운 타워 최대 " + integer(definition, "maxTargets", 1)
                + "기의 " + statName + "를 " + seconds(definition, "durationTicks", 80)
                + "간 " + percent(value(definition, magnitudeKey, 0.10))
                + " 감소시킵니다. (" + seconds(definition, "cooldownTicks", 80) + " 쿨타임)");
    }

    private static void addAllyBuffLine(
            ArrayList<String> lines,
            SummonConfig.SummonDefinition definition,
            String statName,
            String magnitudeKey,
            String verb
    ) {
        addLine(lines, "반경 " + blocks(value(definition, "radius", 6.0))
                + " 내 자신 포함 아군 유닛 최대 " + integer(definition, "maxTargets", 8)
                + "기의 " + statName + "를 " + seconds(definition, "durationTicks", 80)
                + "간 " + percent(value(definition, magnitudeKey, 0.15))
                + " " + verb + "시킵니다. (" + seconds(definition, "cooldownTicks", 60) + " 쿨타임)");
    }

    private static void addSiegeLine(ArrayList<String> lines, SummonConfig.SummonDefinition definition) {
        addLine(lines, "방어 대상에게 "
                + number(value(definition, "bonusDamage", 20.0))
                + " 고정 피해를 줍니다. (" + seconds(definition, "cooldownTicks", 80) + " 쿨타임)");
    }

    private static double value(SummonConfig.SummonDefinition definition, String key, double fallback) {
        return definition.abilityValues().getOrDefault(key, fallback);
    }

    private static int integer(SummonConfig.SummonDefinition definition, String key, int fallback) {
        return Math.max(0, (int) Math.round(value(definition, key, fallback)));
    }

    private static String seconds(SummonConfig.SummonDefinition definition, String key, int fallbackTicks) {
        return number(integer(definition, key, fallbackTicks) / 20.0) + "초";
    }

    private static String blocks(double value) {
        return number(value) + "블록";
    }

    private static String percent(double value) {
        return number(value * 100.0) + "%";
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void addLine(ArrayList<String> lines, String line) {
        if (line == null || line.isBlank() || lines.contains(line)) {
            return;
        }
        lines.add(line);
    }
}
