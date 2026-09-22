package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.GameArena;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class JobAugmentSelectionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JobRegistry.registerBuiltIns();
    }

    @Test
    void everyBuilderHasExactlyFourJobCards() {
        List<AugmentDefinition> cards = jobCards();
        assertEquals(124, cards.size());
        var counts = cards.stream().collect(Collectors.groupingBy(AugmentDefinition::requiredJobId, Collectors.counting()));
        assertEquals(31, counts.size());
        counts.forEach((job, count) -> assertEquals(4L, count.longValue(), job));
    }

    @TestFactory
    Stream<DynamicTest> everyJobCardIsEligibleOnlyForItsRequiredJob() {
        return jobCards().stream().map(card -> DynamicTest.dynamicTest(card.id(), () -> {
            SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
            SemionPlayer player = new SemionPlayer(UUID.randomUUID(), "job-card-owner", TeamId.RED, 1,
                    new PlayerEconomy(EconomyConfig.defaultConfig()));
            try {
                for (var job : JobRegistry.all()) {
                    player.assignJob(job);
                    assertEquals(card.requiredJobId().equals(job.id().toString()),
                            game.augmentService().isEligible(game, player, card.id()),
                            card.id() + " with " + job.id());
                }
            } finally {
                game.close();
            }
        }));
    }

    private static List<AugmentDefinition> jobCards() {
        return AugmentCatalog.definitions().stream().filter(card -> card.requiredJobId() != null).toList();
    }
}
