package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonParser;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.rating.RatingConfig;
import org.junit.jupiter.api.Test;

class ConfigGameplayDefaultsTest {
    @Test
    void augmentsAndRatingAreEnabledWithoutChangingTeamMatchmaking() {
        assertTrue(AugmentConfig.defaults().enabled());
        assertTrue(AugmentConfig.defaults().publicPoolEnabled());
        assertTrue(RatingConfig.defaultConfig().enabled());
        assertFalse(RatingConfig.defaultConfig().teamEloMatchmakingEnabled());
    }

    @Test
    void explicitAugmentOptOutStillOverridesTheEnabledDefaults() {
        var config = AugmentConfig.fromJson(JsonParser.parseString(
                "{\"enabled\":false,\"publicPoolEnabled\":false}").getAsJsonObject());
        assertFalse(config.enabled());
        assertFalse(config.publicPoolEnabled());
    }
}
