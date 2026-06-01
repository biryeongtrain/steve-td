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
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;

public final class FileRatingRepository implements RatingRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, PlayerRatingProfile>>() {
    }.getType();

    private final Path path;
    private final Map<UUID, PlayerRatingProfile> profiles = new LinkedHashMap<>();
    private boolean loaded;

    public FileRatingRepository(Path path) {
        this.path = path;
    }

    @Override
    public synchronized Optional<PlayerRatingProfile> findProfile(UUID playerId) {
        ensureLoaded();
        return Optional.ofNullable(profiles.get(playerId));
    }

    @Override
    public synchronized PlayerRatingProfile saveProfile(UUID playerId, PlayerRatingProfile profile) {
        ensureLoaded();
        profiles.put(playerId, profile);
        save();
        return profile;
    }

    @Override
    public synchronized Map<UUID, PlayerRatingProfile> findAllProfiles() {
        ensureLoaded();
        return Map.copyOf(profiles);
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
            Map<String, PlayerRatingProfile> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw == null) {
                return;
            }
            for (Map.Entry<String, PlayerRatingProfile> entry : raw.entrySet()) {
                try {
                    profiles.put(UUID.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException exception) {
                    SemionTd.LOGGER.warn("Skipping invalid rating profile key {}.", entry.getKey());
                }
            }
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to load rating profile store {}.", path, exception);
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
            Map<String, PlayerRatingProfile> raw = new LinkedHashMap<>();
            for (Map.Entry<UUID, PlayerRatingProfile> entry : profiles.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(raw, RAW_TYPE, writer);
            }
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save rating profile store {}.", path, exception);
            throw new PersistenceException("Failed to save rating profile store " + path, exception);
        }
    }
}
