package kim.biryeong.semiontd.ui;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class UiFontMetrics {
    private static final int[][] UNIFONT_RANGES = loadRanges();

    private UiFontMetrics() {
    }

    public static int unifontAdvance(int codePoint) {
        int low = 0;
        int high = UNIFONT_RANGES.length - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int[] range = UNIFONT_RANGES[middle];
            if (codePoint < range[0]) {
                high = middle - 1;
            } else if (codePoint > range[1]) {
                low = middle + 1;
            } else {
                return range[2];
            }
        }
        return -1;
    }

    private static int[][] loadRanges() {
        try (var input = UiFontMetrics.class.getResourceAsStream("/semiontd/ui/unifont-advances.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled UI font advances");
            }
            var ranges = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("ranges");
            int[][] result = new int[ranges.size()][3];
            for (int index = 0; index < ranges.size(); index++) {
                var range = ranges.get(index).getAsJsonArray();
                for (int field = 0; field < 3; field++) {
                    result[index][field] = range.get(field).getAsInt();
                }
            }
            return result;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read bundled UI font advances", exception);
        }
    }
}
