package kim.biryeong.semiontd.progression;

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
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;

public final class SemionProgressionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, SemionPlayerProfile>>() {
    }.getType();

    private final Path path;
    private final Map<UUID, SemionPlayerProfile> profiles = new HashMap<>();
    private boolean loaded;

    public SemionProgressionStore(Path path) {
        this.path = path;
    }

    public synchronized SemionPlayerProfile getOrCreateProfile(UUID playerId, String playerName) {
        ensureLoaded();
        SemionPlayerProfile existing = profiles.get(playerId);
        if (existing != null) {
            SemionPlayerProfile updated = existing.updateName(playerName);
            if (!updated.equals(existing)) {
                profiles.put(playerId, updated);
                save();
            }
            return updated;
        }

        SemionPlayerProfile created = SemionPlayerProfile.fresh(playerName);
        profiles.put(playerId, created);
        save();
        return created;
    }

    public synchronized SemionPlayerProfile putProfile(UUID playerId, SemionPlayerProfile profile) {
        ensureLoaded();
        profiles.put(playerId, profile);
        save();
        return profile;
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
            Map<String, SemionPlayerProfile> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, SemionPlayerProfile> entry : raw.entrySet()) {
                try {
                    profiles.put(UUID.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException exception) {
                    SemionTd.LOGGER.warn("Skipping invalid progression profile key {}.", entry.getKey());
                }
            }
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to load progression store {}.", path, exception);
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
            Map<String, SemionPlayerProfile> raw = new HashMap<>();
            for (Map.Entry<UUID, SemionPlayerProfile> entry : profiles.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, RAW_TYPE, writer);
            }
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save progression store {}.", path, exception);
        }
    }
}