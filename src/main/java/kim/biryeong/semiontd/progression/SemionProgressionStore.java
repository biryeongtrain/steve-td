package kim.biryeong.semiontd.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange.Operation;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange.Status;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;

public final class SemionProgressionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, SemionPlayerProfile>>() {
    }.getType();

    private final Path path;
    private final Map<UUID, SemionPlayerProfile> profiles = new HashMap<>();
    private boolean loaded;
    private boolean loadFailed;

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

    public synchronized boolean putProfilePersisted(UUID playerId, SemionPlayerProfile profile) {
        ensureLoaded();
        SemionPlayerProfile previous = profiles.put(playerId, profile);
        if (save(false)) {
            return true;
        }
        if (previous == null) {
            profiles.remove(playerId);
        } else {
            profiles.put(playerId, previous);
        }
        save();
        return false;
    }

    public synchronized ProgressionCurrencyChange changeCosmeticCurrency(
            Map<UUID, String> requestedTargets,
            long amount,
            Operation operation
    ) {
        Map<UUID, String> targets = requestedTargets == null ? Map.of() : new LinkedHashMap<>(requestedTargets);
        int count = targets.size();
        if (amount <= 0 || operation == null) {
            return new ProgressionCurrencyChange(Status.INVALID_AMOUNT, count, 0);
        }
        if (targets.isEmpty()) {
            return new ProgressionCurrencyChange(Status.NO_TARGETS, 0, 0);
        }
        if (targets.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getValue().isBlank())) {
            return new ProgressionCurrencyChange(Status.INVALID_TARGET, count, 0);
        }
        ensureLoaded();
        if (loadFailed) {
            return new ProgressionCurrencyChange(Status.PERSISTENCE_FAILED, count, 0);
        }
        Map<UUID, SemionPlayerProfile> updated = new HashMap<>(profiles);
        long total;
        try {
            total = Math.multiplyExact(amount, count);
            for (var target : targets.entrySet()) {
                SemionPlayerProfile current = profiles.getOrDefault(target.getKey(), SemionPlayerProfile.fresh(target.getValue()));
                if (operation == Operation.TAKE && current.cosmeticCurrency() < amount) {
                    return new ProgressionCurrencyChange(Status.INSUFFICIENT_FUNDS, count, 0);
                }
                updated.put(target.getKey(), operation == Operation.GIVE
                        ? current.grantCosmeticCurrency(target.getValue(), amount)
                        : current.takeCosmeticCurrency(target.getValue(), amount));
            }
        } catch (ArithmeticException exception) {
            return new ProgressionCurrencyChange(Status.OVERFLOW, count, 0);
        }
        if (!saveSnapshot(updated, false)) {
            return new ProgressionCurrencyChange(Status.PERSISTENCE_FAILED, count, 0);
        }
        profiles.clear();
        profiles.putAll(updated);
        return new ProgressionCurrencyChange(Status.SUCCESS, count, total);
    }

    public synchronized boolean clearSelectedCosmetic(String cosmeticId) {
        ensureLoaded();
        Map<UUID, SemionPlayerProfile> previous = new HashMap<>();
        for (Map.Entry<UUID, SemionPlayerProfile> entry : profiles.entrySet()) {
            SemionPlayerProfile profile = entry.getValue();
            if (!profile.isCosmeticSelected(cosmeticId)) {
                continue;
            }
            previous.put(entry.getKey(), profile);
            entry.setValue(profile.updateSelectedCosmetics(
                    profile.lastKnownName(),
                    profile.selectedCosmeticIds().stream().filter(id -> !id.equals(cosmeticId)).toList()
            ));
        }
        if (previous.isEmpty() || save(false)) {
            return true;
        }
        profiles.putAll(previous);
        save();
        return false;
    }

    public synchronized Optional<Map<UUID, MatchProgressionReward>> recordMatch(
            MatchResult matchResult,
            ProgressionConfig progressionConfig
    ) {
        ensureLoaded();
        Map<UUID, MatchProgressionReward> rewards = new LinkedHashMap<>();
        for (MatchParticipantResult participant : matchResult.participants()) {
            long reward = participant.winner()
                    ? progressionConfig.rewardForWin()
                    : progressionConfig.rewardForLoss();
            SemionPlayerProfile existing = profiles.get(participant.playerId());
            SemionPlayerProfile base = existing == null
                    ? SemionPlayerProfile.fresh(participant.playerName())
                    : existing;
            SemionPlayerProfile updated = base.recordMatch(participant.playerName(), participant.winner(), reward);
            profiles.put(participant.playerId(), updated);
            rewards.put(participant.playerId(), new MatchProgressionReward(participant.winner(), reward, updated));
        }
        return save() ? Optional.of(Map.copyOf(rewards)) : Optional.empty();
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
                loadFailed = true;
                return;
            }
            for (Map.Entry<String, SemionPlayerProfile> entry : raw.entrySet()) {
                try {
                    if (entry.getValue() == null) {
                        loadFailed = true;
                    } else {
                        profiles.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                } catch (IllegalArgumentException exception) {
                    loadFailed = true;
                    SemionTd.LOGGER.warn("Skipping invalid progression profile key {}.", entry.getKey());
                }
            }
        } catch (IOException exception) {
            loadFailed = true;
            SemionTd.LOGGER.warn("Failed to load progression store {}.", path, exception);
        } catch (JsonParseException exception) {
            loadFailed = true;
            SemionTd.LOGGER.warn("Failed to parse progression store {}.", path, exception);
        }
    }

    private boolean save() {
        return save(true);
    }

    private boolean save(boolean appendFallback) {
        return saveSnapshot(profiles, appendFallback);
    }

    private boolean saveSnapshot(Map<UUID, SemionPlayerProfile> snapshot, boolean appendFallback) {
        if (path == null) {
            return true;
        }

        Map<String, SemionPlayerProfile> raw = new HashMap<>();
        for (Map.Entry<UUID, SemionPlayerProfile> entry : snapshot.entrySet()) {
            raw.put(entry.getKey().toString(), entry.getValue());
        }
        Path temporary = null;
        try {
            Path destination = path.toAbsolutePath();
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "progression-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(raw, RAW_TYPE, writer);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            SemionTd.LOGGER.warn("Failed to save progression store {}.", path, exception);
            if (appendFallback) {
                appendFallbackLog(raw);
            }
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    SemionTd.LOGGER.warn("Failed to remove progression temporary file {}.", temporary, exception);
                }
            }
        }
    }

    private void appendFallbackLog(Map<String, SemionPlayerProfile> raw) {
        String payload = GSON.toJson(Map.of("type", "progression_profiles", "payload", raw));
        Path fallbackPath = path == null ? null : path.resolveSibling("progression-fallback.log");
        if (fallbackPath == null) {
            SemionTd.LOGGER.error("Persistence log fallback: {}", payload);
            return;
        }
        try {
            Files.writeString(
                    fallbackPath,
                    payload + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            SemionTd.LOGGER.error("Failed to append progression fallback log {}; writing to application log.", fallbackPath, exception);
            SemionTd.LOGGER.error("Persistence log fallback: {}", payload);
        }
    }
}
