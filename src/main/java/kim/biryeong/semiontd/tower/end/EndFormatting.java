package kim.biryeong.semiontd.tower.end;

public final class EndFormatting {
    private static final String END_COLOR = "#cc00fa";

    private EndFormatting() {
    }

    public static String endText(String text) {
        return "<" + END_COLOR + ">" + text + "</" + END_COLOR + ">";
    }
}
