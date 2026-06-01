package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchResult;

public final class LoggingMatchResultRepository implements MatchResultRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path logPath;
    private final Map<MatchId, MatchResult> saved = new LinkedHashMap<>();

    public LoggingMatchResultRepository() {
        this(null);
    }

    public LoggingMatchResultRepository(Path logPath) {
        this.logPath = logPath;
    }

    @Override
    public synchronized void saveMatchResult(MatchResult matchResult) {
        saved.put(matchResult.matchId(), matchResult);
        String payload = GSON.toJson(Map.of(
                "type", "match_result",
                "matchId", matchResult.matchId().toString(),
                "payload", matchResult
        ));
        append(payload);
    }

    @Override
    public synchronized Optional<MatchResult> findMatchResult(MatchId matchId) {
        return Optional.ofNullable(saved.get(matchId));
    }

    private void append(String payload) {
        if (logPath == null) {
            SemionTd.LOGGER.error("Persistence log fallback: {}", payload);
            return;
        }
        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    logPath,
                    payload + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            SemionTd.LOGGER.error("Failed to append match-result fallback log {}; writing to application log.", logPath, exception);
            SemionTd.LOGGER.error("Persistence log fallback: {}", payload);
        }
    }
}
