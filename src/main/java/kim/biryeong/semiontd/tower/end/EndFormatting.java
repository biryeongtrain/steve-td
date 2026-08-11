package kim.biryeong.semiontd.tower.end;

public final class EndFormatting {
    private static final String END_COLOR = "#cc00fa";
    private static final String WARNING_COLOR = "dark_red";

    private EndFormatting() {
    }

    public static String endText(String text) {
        return "<" + END_COLOR + ">" + text + "</" + END_COLOR + ">";
    }

    public static String warningText(String text) {
        return "<" + WARNING_COLOR + ">" + text + "</" + WARNING_COLOR + ">";
    }
}
