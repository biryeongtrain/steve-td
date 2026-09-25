package kim.biryeong.semiontd.report;

import com.google.gson.JsonObject;
import java.util.UUID;

public record BugReport(UUID id, long createdAtEpochMillis, UUID playerId, String playerName,
                        String content, JsonObject snapshot) {
    public BugReport {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(playerId);
        java.util.Objects.requireNonNull(playerName);
        content = validateContent(content);
        snapshot = java.util.Objects.requireNonNull(snapshot).deepCopy();
    }

    @Override
    public JsonObject snapshot() { return snapshot.deepCopy(); }

    public static String validateContent(String value) {
        if (value == null || value.isBlank() || value.length() > 1000
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '§')) {
            throw new IllegalArgumentException("신고 내용은 제어 문자 없이 1~1,000자로 입력해 주세요.");
        }
        return value.strip();
    }
}
