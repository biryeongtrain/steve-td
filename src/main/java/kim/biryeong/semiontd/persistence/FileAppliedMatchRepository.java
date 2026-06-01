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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;

public final class FileAppliedMatchRepository implements AppliedMatchRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, Long>>() {
    }.getType();

    private final Path path;
    private final Map<String, Long> appliedMatches = new HashMap<>();
    private boolean loaded;

    public FileAppliedMatchRepository(Path path) {
        this.path = path;
    }

    @Override
    public synchronized boolean hasApplied(UUID matchId, String subsystem) {
        ensureLoaded();
        return appliedMatches.containsKey(key(matchId, subsystem));
    }

    @Override
    public synchronized boolean markApplied(UUID matchId, String subsystem, long appliedAtEpochMillis) {
        ensureLoaded();
        String key = key(matchId, subsystem);
        if (appliedMatches.containsKey(key)) {
            return false;
        }
        appliedMatches.put(key, Math.max(0L, appliedAtEpochMillis));
        if (!save()) {
            appliedMatches.remove(key);
            return false;
        }
        return true;
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
            Map<String, Long> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw != null) {
                appliedMatches.putAll(raw);
            }
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to load applied match store {}.", path, exception);
        }
    }

    private boolean save() {
        if (path == null) {
            return true;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(appliedMatches, RAW_TYPE, writer);
            }
            return true;
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save applied match store {}.", path, exception);
            return false;
        }
    }

    private static String key(UUID matchId, String subsystem) {
        Objects.requireNonNull(matchId, "matchId");
        String normalizedSubsystem = Objects.requireNonNull(subsystem, "subsystem").trim();
        if (normalizedSubsystem.isEmpty()) {
            throw new IllegalArgumentException("subsystem cannot be blank");
        }
        return matchId + ":" + normalizedSubsystem;
    }
}
