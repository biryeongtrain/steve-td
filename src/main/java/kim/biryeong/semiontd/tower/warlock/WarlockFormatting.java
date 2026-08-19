package kim.biryeong.semiontd.tower.warlock;

public final class WarlockFormatting {
    private static final String WARLOCK_COLOR = "dark_purple";

    private WarlockFormatting() {
    }

    public static String warlockText(String text) {
        return "<" + WARLOCK_COLOR + ">" + text + "</" + WARLOCK_COLOR + ">";
    }
}
