package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchResult;

public final class FileMatchResultRepository implements MatchResultRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, MatchResult>>() {
    }.getType();

    private final Path path;
    private final Map<MatchId, MatchResult> matchResults = new LinkedHashMap<>();
    private boolean loaded;

    public FileMatchResultRepository(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void saveMatchResult(MatchResult matchResult) {
        ensureLoaded();
        matchResults.put(matchResult.matchId(), matchResult);
        save();
    }

    @Override
    public synchronized Optional<MatchResult> findMatchResult(MatchId matchId) {
        ensureLoaded();
        return Optional.ofNullable(matchResults.get(matchId));
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (path == null || Files.notExists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, MatchResult> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, MatchResult> entry : raw.entrySet()) {
                try {
                    matchResults.put(MatchId.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException exception) {
                    SemionTd.LOGGER.warn("Skipping invalid match result key {}.", entry.getKey());
                }
            }
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to load match result store {}.", path, exception);
        }
    }

    private void save() {
        if (path == null) {
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, MatchResult> raw = new LinkedHashMap<>();
            for (Map.Entry<MatchId, MatchResult> entry : matchResults.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, RAW_TYPE, writer);
            }
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save match result store {}.", path, exception);
            throw new PersistenceException("Failed to save match result store " + path, exception);
        }
    }
}
