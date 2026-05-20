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
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat(
            "0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT)
    );

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
            return TowerBalanceRuntime.ability(type.id(), token.substring("ability.".length()));
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
            case "aggroPriority" -> type.aggroPriority();
            default -> throw new IllegalArgumentException("Unknown tower stat token: " + key);
        };
    }

    private static String format(double value, String format) {
        return switch (format) {
            case "integer", "int" -> Long.toString(Math.round(value));
            case "percent" -> formatNumber(value * 100.0) + "%";
            case "seconds", "second" -> formatNumber(value / 20.0) + "초";
            case "blocks", "block" -> formatNumber(value) + "블록";
            case "number", "" -> formatNumber(value);
            default -> throw new IllegalArgumentException("Unknown tower description format: " + format);
        };
    }

    private static String formatNumber(double value) {
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }
}
