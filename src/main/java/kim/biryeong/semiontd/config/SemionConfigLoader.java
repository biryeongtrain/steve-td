package kim.biryeong.semiontd.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import org.slf4j.Logger;

public final class SemionConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SemionConfigLoader() {
    }

    public static LoadedConfigs load(Path configDir, Logger logger) {
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            logger.warn("Failed to create config directory {}; using defaults.", configDir, exception);
            return new LoadedConfigs(
                    EconomyConfig.defaultConfig(),
                    WaveConfig.defaultConfig(),
                    MapConfig.defaultConfig(),
                    ProgressionConfig.defaultConfig(),
                    SemionPersistenceConfig.defaultConfig(),
                    TowerBalanceConfig.defaultConfig(),
                    SummonConfig.defaultConfig()
            );
        }

        EconomyConfig economy = loadOrCreateEconomy(
                configDir.resolve("economy.json"),
                EconomyConfig.defaultConfig(),
                logger
        );
        WaveConfig waves = loadOrCreateWithLegacy(
                configDir.resolve("wave.json"),
                configDir.resolve("waves.json"),
                WaveConfig.defaultConfig(),
                WaveConfig.class,
                logger
        );
        MapConfig map = loadOrCreate(
                configDir.resolve("map.json"),
                MapConfig.defaultConfig(),
                MapConfig.class,
                logger
        );
        ProgressionConfig progression = loadOrCreate(
                configDir.resolve("progression.json"),
                ProgressionConfig.defaultConfig(),
                ProgressionConfig.class,
                logger
        );
        SemionPersistenceConfig persistence = loadOrCreate(
                configDir.resolve("persistence.json"),
                SemionPersistenceConfig.defaultConfig(),
                SemionPersistenceConfig.class,
                logger
        );
        TowerBalanceConfig towerBalance = loadOrCreateTowerBalance(
                configDir.resolve("tower_balance.json"),
                TowerBalanceConfig.defaultConfig(),
                logger
        );
        SummonConfig summons = loadOrCreateSummons(
                configDir.resolve("summons.json"),
                SummonConfig.defaultConfig(),
                logger
        );
        return new LoadedConfigs(economy, waves, map, progression, persistence, towerBalance, summons);
    }

    private static <T> T loadOrCreate(Path path, T defaults, Class<T> type, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            T value = GSON.fromJson(reader, type);
            return value == null ? defaults : value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static <T> T loadOrCreateWithLegacy(Path preferred, Path legacy, T defaults, Class<T> type, Logger logger) {
        if (Files.exists(preferred)) {
            return loadOrCreate(preferred, defaults, type, logger);
        }
        if (Files.exists(legacy)) {
            return loadOrCreate(legacy, defaults, type, logger);
        }
        write(preferred, defaults, logger);
        return defaults;
    }

    private static EconomyConfig loadOrCreateEconomy(Path path, EconomyConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            EconomyConfig loaded = GSON.fromJson(json, EconomyConfig.class);
            EconomyConfig value = loaded == null ? defaults : loaded;
            boolean towerLimitMissing = !hasObjectProperty(json, "towerLimit");
            if (towerLimitMissing) {
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static TowerBalanceConfig loadOrCreateTowerBalance(
            Path path,
            TowerBalanceConfig defaults,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            TowerBalanceConfig value = GSON.fromJson(reader, TowerBalanceConfig.class);
            TowerBalanceConfig loaded = value == null ? defaults : value;
            TowerBalanceConfig merged = loaded.withMissingDefaults(defaults);
            if (!merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static SummonConfig loadOrCreateSummons(Path path, SummonConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            SummonConfig value = GSON.fromJson(reader, SummonConfig.class);
            SummonConfig loaded = value == null ? defaults : value;
            SummonConfig merged = loaded.withMissingDefaults(defaults);
            if (!merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static void write(Path path, Object value, Logger logger) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        } catch (IOException exception) {
            logger.warn("Failed to write default config {}.", path, exception);
        }
    }

    private static boolean hasObjectProperty(String json, String key) {
        try {
            if (!(JsonParser.parseString(json) instanceof JsonObject object)) {
                return false;
            }
            return object.has(key) && !object.get(key).isJsonNull();
        } catch (JsonParseException exception) {
            return false;
        }
    }

    public record LoadedConfigs(
            EconomyConfig economy,
            WaveConfig waves,
            MapConfig map,
            ProgressionConfig progression,
            SemionPersistenceConfig persistence,
            TowerBalanceConfig towerBalance,
            SummonConfig summons
    ) {
    }
}
