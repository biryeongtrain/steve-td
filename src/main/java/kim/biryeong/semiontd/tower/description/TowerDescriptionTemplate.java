package kim.biryeong.semiontd.tower.description;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

public final class TowerDescriptionTemplate {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat PRECISE_NUMBER_FORMAT = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final String HEALTH_COLOR = "#fc5454";
    private static final String REGENERATION_COLOR = "#20985d";
    private static final String LIFE_STEAL_COLOR = "#e32042";
    private static final String DAMAGE_REDUCTION_COLOR = "#f3ba59";
    private static final String ATTACK_DAMAGE_COLOR = "#ec8d34";
    private static final String MAGIC_DAMAGE_COLOR = "#796CFF";
    private static final String ATTACK_SPEED_COLOR = "#ffe78d";
    private static final String MOVEMENT_SPEED_COLOR = "#F1E7D4";
    private static final String ATTACK_RANGE_COLOR = "#f0e6d2";
    private static final String RESISTANCE_COLOR = "#53DFFF";
    private static final String AGGRO_PRIORITY_COLOR = "#a80000";
    private static final String DIAMOND_GRADIENT = "<gradient:#ffffff:#d5fff6:#a1fbe8:#4aedd9:#20c5b5:#1aaaa7:#11727a:#145e53>";
    private static final String GRADIENT_CLOSE = "</gradient>";

    private TowerDescriptionTemplate() {
    }

    public static TowerDescriptionFactory of(List<String> template) {
        List<String> lines = template == null ? List.of() : List.copyOf(template);
        return type -> render(lines, type);
    }

    public static List<String> render(List<String> template, TowerType type) {
        if (template == null || template.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>(template.size());
        for (String line : template) {
            rendered.add(renderLine(line, type));
        }
        return List.copyOf(rendered);
    }

    private static String renderLine(String line, TowerType type) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(line);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(renderPlaceholder(matcher.group(1), type)));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String renderPlaceholder(String placeholder, TowerType type) {
        int formatSeparator = placeholder.lastIndexOf(':');
        String expression = formatSeparator < 0 ? placeholder.trim() : placeholder.substring(0, formatSeparator).trim();
        String format = formatSeparator < 0 ? "number" : placeholder.substring(formatSeparator + 1).trim();
        try {
            return format(evaluate(expression, type), format);
        } catch (IllegalArgumentException exception) {
            return "{" + placeholder + "}";
        }
    }

    private static double evaluate(String expression, TowerType type) {
        if (expression.isBlank()) {
            throw new IllegalArgumentException("Blank tower description expression.");
        }
        double result = 0.0;
        char operator = '+';
        int tokenStart = 0;
        for (int index = 0; index <= expression.length(); index++) {
            if (index < expression.length() && expression.charAt(index) != '*' && expression.charAt(index) != '/') {
                continue;
            }
            String token = expression.substring(tokenStart, index).trim();
            double value = value(token, type);
            if (operator == '*') {
                result *= value;
            } else if (operator == '/') {
                result = value == 0.0 ? 0.0 : result / value;
            } else {
                result = value;
            }
            if (index < expression.length()) {
                operator = expression.charAt(index);
                tokenStart = index + 1;
            }
        }
        return result;
    }

    private static double value(String token, TowerType type) {
        if (token.startsWith("ability.")) {
            String abilityKey = token.substring("ability.".length());
            int idSeparator = abilityKey.lastIndexOf('.');
            if (idSeparator > 0 && idSeparator < abilityKey.length() - 1) {
                double configured = TowerBalanceRuntime.ability(
                        abilityKey.substring(0, idSeparator),
                        abilityKey.substring(idSeparator + 1),
                        Double.NaN
                );
                if (!Double.isNaN(configured)) {
                    return configured;
                }
            }
            return TowerBalanceRuntime.ability(type.id(), abilityKey);
        }
        if (token.startsWith("stat.")) {
            return stat(type, token.substring("stat.".length()));
        }
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unknown tower description token: " + token, exception);
        }
    }

    private static double stat(TowerType type, String key) {
        return switch (key) {
            case "mineralCost" -> type.mineralCost();
            case "maxHealth" -> type.maxHealth();
            case "range" -> type.range();
            case "damage" -> type.damage();
            case "attackIntervalTicks" -> type.attackIntervalTicks();
            case "attackIntervalSeconds" -> type.attackIntervalTicks() / 20.0;
            case "attacksPerSecond" -> 20.0 / Math.max(1, type.attackIntervalTicks());
            case "aggroPriority" -> type.aggroPriority();
            default -> throw new IllegalArgumentException("Unknown tower stat token: " + key);
        };
    }

    public static String format(double value, String format) {
        return switch (format) {
            case "integer", "int" -> Long.toString(Math.round(value));
            case "percent" -> formatNumber(value * 100.0) + "%";
            case "percent_integer" -> Math.round(value * 100.0) + "%";
            case "seconds", "second" -> formatNumber(value / 20.0) + "초";
            case "blocks", "block" -> formatNumber(value) + "블록";
            case "precise_blocks", "precise_block" -> formatPreciseNumber(value) + "블록";
            case "attack_damage", "ad" -> formatAttackDamage(value, "");
            case "health", "hp" -> formatHealth(value, "");
            case "aggro", "priority" -> formatAggroPriority(value, "");
            case "attack_range", "range" -> formatAttackRange(value, "");
            case "attack_speed", "as" -> formatAttackSpeed(value, "");
            case "movement_speed", "move_speed" -> formatMovementSpeed(value, "");
            case "sell_price", "sell" -> formatSellPrice(value, "");
            case "number", "num", "" -> formatNumber(value);
            default -> throw new IllegalArgumentException("Unknown tower description format: " + format);
        };
    }

    public static String formatIncrease(double baseValue, double currentValue) {
        if (!Double.isFinite(baseValue) || !Double.isFinite(currentValue) || baseValue <= 0.0 || currentValue <= baseValue) {
            return "";
        }
        long increasePercent = Math.round(((currentValue - baseValue) / baseValue) * 100.0);
        if (increasePercent <= 0L) {
            return "";
        }
        return " <white>(</white><green>+" + increasePercent + "%</green><white>)</white>";
    }

    private static String styledProgressStat(String color, String icon, String label, String value, String progress) {
        return "<" + color + ">" + icon + " " + label + "</" + color + "><white>: </white><" + color + ">" + value + "</" + color + ">" + (progress.isEmpty() ? "" : "<white> " + progress + "</white>");
    }

    public static String stackProgress(int currentStacks, int stacksPerStep, double currentValue, double maximumValue) {
        if (stacksPerStep <= 0 || currentValue >= maximumValue - 0.0001) {
            return "(MAX)";
        }
        int nextStacks = (currentStacks / stacksPerStep + 1) * stacksPerStep;
        return "(" + nextStacks + ")";
    }

    public static String splashProgress(int currentStacks, int... thresholds) {
        for (int threshold : thresholds) {
            if (currentStacks < threshold) {
                return "(" + threshold + ")";
            }
        }
        return "(MAX)";
    }

    private static String progressSuffix(String progress) {
        return progress.isEmpty() ? "" : "<white> " + progress + "</white>";
    }

    public static String formatNumber(double value) {
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }

    private static String formatPreciseNumber(double value) {
        synchronized (PRECISE_NUMBER_FORMAT) {
            return PRECISE_NUMBER_FORMAT.format(value);
        }
    }

    public static String healthText(String text) {
        return "<" + HEALTH_COLOR + ">" + text + "</" + HEALTH_COLOR + ">";
    }

    public static String regenerationText(String text) {
        return "<" + REGENERATION_COLOR + ">" + text + "</" + REGENERATION_COLOR + ">";
    }

    public static String lifeStealText(String text) {
        return "<" + LIFE_STEAL_COLOR + ">" + text + "</" + LIFE_STEAL_COLOR + ">";
    }

    public static String attackDamageText(String text) {
        return "<" + ATTACK_DAMAGE_COLOR + ">" + text + "</" + ATTACK_DAMAGE_COLOR + ">";
    }

    public static String magicDamageText(String text) {
        return "<" + MAGIC_DAMAGE_COLOR + ">" + text + "</" + MAGIC_DAMAGE_COLOR + ">";
    }

    public static String damageReductionText(String text) {
        return "<" + DAMAGE_REDUCTION_COLOR + ">" + text + "</" + DAMAGE_REDUCTION_COLOR + ">";
    }

    public static String attackSpeedText(String text) {
        return "<" + ATTACK_SPEED_COLOR + ">" + text + "</" + ATTACK_SPEED_COLOR + ">";
    }

    public static String movementSpeedText(String text) {
        return "<" + MOVEMENT_SPEED_COLOR + ">" + text + "</" + MOVEMENT_SPEED_COLOR + ">";
    }

    public static String attackRangeText(String text) {
        return "<" + ATTACK_RANGE_COLOR + ">" + text + "</" + ATTACK_RANGE_COLOR + ">";
    }

    public static String formatHealth(double value, String progress) {
        return styledProgressStat(HEALTH_COLOR, "\u2764", "체력", formatNumber(value), progress);
    }

    public static String formatHealth(double currentValue, double maxValue, String progress) {
        return styledProgressStat(HEALTH_COLOR, "\u2764", "체력", formatNumber(currentValue) + "<dark_gray>/</dark_gray>" + formatNumber(maxValue), progress);
    }

    public static String formatPermanentHealth(double value, String progress) {
        return styledProgressStat(HEALTH_COLOR, "\u2764", "영구 체력", "+" + formatNumber(value), progress);
    }

    public static String formatRegeneration(double value, String progress) {
        return styledProgressStat(REGENERATION_COLOR, "➕", "재생", "+" + formatNumber(value) + " HP/s", progress);
    }

    public static String formatLifeSteal(double value, String progress) {
        return styledProgressStat(LIFE_STEAL_COLOR, "\uD83E\uDE78", "생명력 흡수", "+" + format(value, "percent"), progress);
    }

    public static String formatDamageReduction(double value, String progress) {
        return styledProgressStat(DAMAGE_REDUCTION_COLOR, "\uD83D\uDEE1", "피해 감소", "+" + format(value, "percent"), progress);
    }

    public static String formatIncomeDebuffResistance(double value, String progress) {
        return styledProgressStat(RESISTANCE_COLOR, "\uD83D\uDEE1", "디버프 저항", "+" + format(value, "percent"), progress);
    }

    public static String formatAttackDamage(double value, String progress) {
        return styledProgressStat(ATTACK_DAMAGE_COLOR, "\uD83E\uDE93", "피해", formatNumber(value), progress);
    }

    public static String formatMagicDamage(double value, String progress) {
        return styledProgressStat(MAGIC_DAMAGE_COLOR, "\uD83D\uDD25", "피해", formatNumber(value), progress);
    }

    public static String formatPermanentDamage(double value, String progress) {
        return styledProgressStat(ATTACK_DAMAGE_COLOR, "\uD83E\uDE93", "영구 피해", "+" + formatNumber(value), progress);
    }

    public static String formatAttackSpeed(double value, String progress) {
        return styledProgressStat(ATTACK_SPEED_COLOR, "\u26A1", "공격 속도", formatNumber(value) + "회/초", progress);
    }

    public static String formatAttackSpeed(double value, int ticks, String progress) {
        return styledProgressStat(ATTACK_SPEED_COLOR, "\u26A1", "공격 속도", formatNumber(value) + "회/초 <white>(</white><" + ATTACK_SPEED_COLOR + ">" + ticks + "틱</" + ATTACK_SPEED_COLOR + "><white>)</white>", progress);
    }

    public static String formatAttackSpeedReduction(int ticks, String progress) {
        return styledProgressStat(ATTACK_SPEED_COLOR, "\u26A1", "공격 속도", "-" + ticks + "틱", progress);
    }

    public static String formatMovementSpeed(double value, String progress) {
        return styledProgressStat(MOVEMENT_SPEED_COLOR, "\uD83D\uDC5F", "이동 속도", "+" + format(value, "percent"), progress);
    }

    public static String formatSplashRange(double value, String progress) {
        return styledProgressStat(ATTACK_SPEED_COLOR, "⭕", "공격 범위", "+" + formatNumber(value) + " 블록", progress);
    }

    public static String formatAttackRange(double value, String progress) {
        return styledProgressStat(ATTACK_RANGE_COLOR, "\uD83C\uDFF9", "사거리", formatNumber(value) + " 블록", progress);
    }

    public static String formatFinalDamage(double value, String progress) {
        return styledProgressStat(ATTACK_DAMAGE_COLOR, "\u2694", "최종 피해", "+" + format(value, "percent"), progress);
    }

    public static String formatBonusRange(double value, String progress) {
        return styledProgressStat(ATTACK_RANGE_COLOR, "\uD83C\uDFF9", "추가 사거리", "+" + formatNumber(value) + " 블록", progress);
    }

    public static String formatEmerald(double value, boolean affordable, String progress) {
        return "<green>\u2B22 " + formatNumber(value) + " 에메랄드</green>" + (affordable ? " <green>(구매 가능)</green>" : " <red>(부족)</red>") + progressSuffix(progress);
    }

    public static String formatKillReward(double value, String progress) {
        return DIAMOND_GRADIENT + "\uD83D\uDC8E 처치 보상<white>: </white>" + formatNumber(value) + " 다이아" + GRADIENT_CLOSE + progressSuffix(progress);
    }

    public static String formatIncome(double value, double ratio, String progress) {
        return "<yellow>\uD83D\uDCC8 인컴<white>: </white>+" + formatNumber(value) + " <white>(</white>" + format(ratio, "percent") + "<white>)</white></yellow>" + progressSuffix(progress);
    }

    public static String formatDefense(double value, String progress) {
        return styledProgressStat(DAMAGE_REDUCTION_COLOR, "\uD83D\uDEE1", "방어", formatNumber(value), progress);
    }

    public static String formatResistance(double value, String progress) {
        return styledProgressStat(RESISTANCE_COLOR, "\u2726", "저항", formatNumber(value), progress);
    }

    public static String formatAggroPriority(double value, String progress) {
        return styledProgressStat(AGGRO_PRIORITY_COLOR, "\uD83D\uDCA2", "어그로", Long.toString(Math.round(value)), progress);
    }

    public static String formatDamageType(String value, String progress) {
        return "<gold>\uD83D\uDD25 피해 유형</gold><white>: </white><gold>" + value + "</gold>" + progressSuffix(progress);
    }

    public static String formatAttackKind(String icon, String value, String progress) {
        return "<gray>" + icon + " 공격 방식</gray><white>: </white><gray>" + value + "</gray>" + progressSuffix(progress);
    }

    public static String formatSize(double width, double height, String progress) {
        return "<white>\uD83D\uDCCF 크기<white>: </white>" + formatNumber(width) + "x" + formatNumber(height) + "</white>" + progressSuffix(progress);
    }

    public static String formatAbility(String value, String progress) {
        return "<yellow>⭐ 능력</yellow><white>: </white><yellow>" + value + "</yellow>" + progressSuffix(progress);
    }

    public static String formatSellPrice(double value, String progress) {
        return DIAMOND_GRADIENT + "\uD83D\uDC8E 판매가<white>: </white>" + Math.round(value) + " 다이아" + GRADIENT_CLOSE + (progress.isEmpty() ? "" : "<white> " + progress + "</white>");
    }
}
