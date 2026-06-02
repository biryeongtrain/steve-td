package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.rating.RatingConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class SemionConfigLoaderTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void loadCreatesRatingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("rating.json")));
        assertEquals(RatingConfig.defaultConfig(), configs.rating());
    }

    @Test
    void loadReadsRatingConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("rating.json"), """
                {
                  "enabled": true,
                  "eloKFactor": 48.0,
                  "initialDisplayElo": 1200,
                  "initialMu": 1200.0,
                  "initialSigma": 250.0,
                  "leaderboardLimit": 25,
                  "minimumParticipants": 2,
                  "excludeSpectators": true,
                  "contributionWeightingEnabled": false,
                  "contributionMultiplierMin": 0.9,
                  "contributionMultiplierMax": 1.1,
                  "defenseContributionWeight": 0.25,
                  "pressureContributionWeight": 0.25,
                  "economyContributionWeight": 0.25,
                  "assistContributionWeight": 0.25
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(48.0, configs.rating().eloKFactor());
        assertEquals(1200, configs.rating().initialDisplayElo());
        assertEquals(25, configs.rating().leaderboardLimit());
        assertEquals(false, configs.rating().contributionWeightingEnabled());
    }
}
