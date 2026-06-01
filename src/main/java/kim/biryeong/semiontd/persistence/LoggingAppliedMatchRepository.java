package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;

public final class LoggingAppliedMatchRepository implements AppliedMatchRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path logPath;
    private final Set<String> applied = new HashSet<>();

    public LoggingAppliedMatchRepository() {
        this(null);
    }

    public LoggingAppliedMatchRepository(Path logPath) {
        this.logPath = logPath;
    }

    @Override
    public synchronized boolean hasApplied(MatchId matchId, String subsystem) {
        return applied.contains(key(matchId, subsystem));
    }

    @Override
    public synchronized boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis) {
        String normalizedSubsystem = normalizeSubsystem(subsystem);
        String key = key(matchId, normalizedSubsystem);
        if (!applied.add(key)) {
            return false;
        }
        String payload = GSON.toJson(Map.of(
                "type", "applied_match",
                "matchId", matchId.toString(),
                "subsystem", normalizedSubsystem,
                "appliedAtEpochMillis", Math.max(0L, appliedAtEpochMillis)
        ));
        append(payload);
        return true;
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
            SemionTd.LOGGER.error("Failed to append applied-match fallback log {}; writing to application log.", logPath, exception);
            SemionTd.LOGGER.error("Persistence log fallback: {}", payload);
        }
    }

    private static String key(MatchId matchId, String subsystem) {
        Objects.requireNonNull(matchId, "matchId");
        return matchId + ":" + normalizeSubsystem(subsystem);
    }

    private static String normalizeSubsystem(String subsystem) {
        String normalized = Objects.requireNonNull(subsystem, "subsystem").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("subsystem cannot be blank");
        }
        return normalized;
    }
}
