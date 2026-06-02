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
import kim.biryeong.semiontd.rating.RatingMatchResult;

public final class FileRatingEventRepository implements RatingEventRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, RatingMatchResult>>() {
    }.getType();

    private final Path path;
    private final Map<MatchId, RatingMatchResult> matchResults = new LinkedHashMap<>();
    private boolean loaded;

    public FileRatingEventRepository(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void saveMatchResult(RatingMatchResult ratingMatchResult) {
        ensureLoaded();
        matchResults.put(ratingMatchResult.matchId(), ratingMatchResult);
        save();
    }

    @Override
    public synchronized Optional<RatingMatchResult> findMatchResult(MatchId matchId) {
        ensureLoaded();
        return Optional.ofNullable(matchResults.get(matchId));
    }

    @Override
    public synchronized Map<MatchId, RatingMatchResult> findAllMatchResults() {
        ensureLoaded();
        return Map.copyOf(matchResults);
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
            Map<String, RatingMatchResult> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, RatingMatchResult> entry : raw.entrySet()) {
                try {
                    matchResults.put(MatchId.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException exception) {
                    SemionTd.LOGGER.warn("Skipping invalid rating event key {}.", entry.getKey());
                }
            }
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to load rating event store {}.", path, exception);
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
            Map<String, RatingMatchResult> raw = new LinkedHashMap<>();
            for (Map.Entry<MatchId, RatingMatchResult> entry : matchResults.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, RAW_TYPE, writer);
            }
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save rating event store {}.", path, exception);
            throw new PersistenceException("Failed to save rating event store " + path, exception);
        }
    }
}
