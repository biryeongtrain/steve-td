package kim.biryeong.semiontd.buildguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildGuideServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void recordedActionsKeepInsertionOrderAndSaleRefund() {
        BuildGuideService service = new BuildGuideService(null);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        SemionGame game = gameWithPlayer(playerId, "player");
        GridPosition position = new GridPosition(3, 64, 5);

        service.startMatch(game);
        service.recordTowerPlacement(game, playerId, "tower:t1", position, 25);
        service.recordTowerUpgrade(game, playerId, "tower:upgrade", position, 60);
        service.recordTowerSale(game, playerId, "tower:t2", position, 42);
        service.recordSummon(game, playerId, "summon:zombie", 8, 2, TeamId.BLUE, 1, 2);
        service.recordEmeraldProductionUpgrade(game, playerId, 1, 10, 1);

        List<BuildAction> actions = service.recordedActions(playerId);
        assertEquals(List.of(
                BuildActionType.TOWER_PLACE,
                BuildActionType.TOWER_UPGRADE,
                BuildActionType.TOWER_SELL,
                BuildActionType.SUMMON,
                BuildActionType.EMERALD_PRODUCTION_UPGRADE
        ), actions.stream().map(BuildAction::type).toList());
        assertEquals(position, actions.get(2).position());
        assertEquals(0L, actions.get(2).cost());
        assertEquals(42L, actions.get(2).incomeGain());
    }

    @Test
    void configReloadKeepsActiveMatchRecording() {
        BuildGuideService service = new BuildGuideService(null);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        SemionGame game = gameWithPlayer(playerId, "player");

        service.startMatch(game);
        service.recordTowerPlacement(game, playerId, "tower:t1", new GridPosition(3, 64, 5), 25);
        service.configure(null);

        assertEquals(1, service.recordedActions(playerId).size());
    }

    @Test
    void lastRecordingSurvivesUntilNextMatchFinishes() {
        BuildGuideService service = new BuildGuideService(null);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        SemionGame finishedGame = gameWithPlayer(playerId, "player");
        finishedGame.players().get(playerId).assignTraitLoadout(new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.SUPPLY_DEPOT_ID
        ));

        service.startMatch(finishedGame);
        service.recordSummon(finishedGame, playerId, "zombie", 10, 1, TeamId.BLUE, 1, 2);
        service.finishMatch(finishedGame, 3);

        // SemionGameManager finishes build recording as soon as the match enters ENDED,
        // then runs the normal delayed match-result finish path later. The second call must
        // not erase the already captured last-match build before the next match starts.
        service.finishMatch(finishedGame, 3);

        Optional<BuildGuide> published = service.publishLastRecording(playerId, "마지막 경기 빌드");
        assertTrue(published.isPresent());
        assertTrue(published.get().actions().stream().anyMatch(action -> action.type() == BuildActionType.SUMMON));
        assertEquals(BuiltInTraits.STRENGTH_IN_NUMBERS_ID.toString(), published.get().traitLoadout().primaryTraitId());
        assertEquals(BuiltInTraits.SUPPLY_DEPOT_ID.toString(), published.get().traitLoadout().secondaryTraitId());

        service.startMatch(gameWithPlayer(playerId, "player"));

        assertTrue(service.publishLastRecording(playerId, "다음 경기 중 저장").isPresent());
    }

    @Test
    void automaticMatchBuildsArePublicPersistentAndIdempotent() {
        Path database = tempDir.resolve("semiontd.db");
        BuildGuideService service = new BuildGuideService(database);
        UUID recordedPlayer = UUID.fromString("00000000-0000-0000-0000-000000000204");
        UUID emptyPlayer = UUID.fromString("00000000-0000-0000-0000-000000000205");
        MatchResult result = new MatchResult(
                List.of(
                        participant(recordedPlayer, "recorded", List.of(BuildAction.towerPlace(
                                1, "tower:test", new GridPosition(1, 2, 3), 10
                        ))),
                        participant(emptyPlayer, "empty", List.of())
                ),
                Set.of(),
                Set.of(TeamId.RED),
                7
        );

        assertEquals(1, service.publishMatchBuilds(result));
        BuildGuide guide = service.automaticGuide(result, recordedPlayer).orElseThrow();
        assertTrue(guide.code().matches("[A-HJ-NP-Z2-9]{6}"));
        assertTrue(guide.isPublic());
        assertTrue(service.track(UUID.randomUUID(), guide.code()));
        assertTrue(service.automaticGuide(result, emptyPlayer).isEmpty());
        assertEquals(0, service.publishMatchBuilds(result));

        BuildGuideService reloaded = new BuildGuideService(database);
        assertEquals(guide.code(), reloaded.automaticGuide(result, recordedPlayer).orElseThrow().code());
    }

    @Test
    void legacyJsonMigratesOnceAndRemainsUntouched() throws Exception {
        Path database = tempDir.resolve("semiontd.db");
        Path legacyJson = tempDir.resolve("build_guides.json");
        BuildGuide first = guide("ABC234", "기존 빌드");
        Files.writeString(legacyJson, new GsonBuilder().create().toJson(Map.of(first.code(), first)));

        BuildGuideStore migrated = new BuildGuideStore(database, legacyJson);
        assertEquals(first.title(), migrated.find(first.code()).orElseThrow().title());

        BuildGuide second = guide("XYZ789", "나중 JSON 변경");
        Files.writeString(legacyJson, new GsonBuilder().create().toJson(Map.of(second.code(), second)));
        BuildGuideStore reloaded = new BuildGuideStore(database, legacyJson);

        assertTrue(reloaded.find(first.code()).isPresent());
        assertFalse(reloaded.find(second.code()).isPresent());
        assertTrue(Files.exists(legacyJson));
    }

    private static MatchParticipantResult participant(UUID playerId, String name, List<BuildAction> actions) {
        return new MatchParticipantResult(
                playerId,
                name,
                TeamId.RED,
                true,
                PlayerMatchStatsSnapshot.empty(),
                "semion-td:test",
                List.of(1),
                List.of(1),
                TraitLoadoutSnapshot.none(),
                List.of(),
                actions
        );
    }

    private static BuildGuide guide(String code, String title) {
        return new BuildGuide(
                code,
                title,
                UUID.fromString("00000000-0000-0000-0000-000000000206"),
                "legacy",
                "semion-td:test",
                TraitLoadoutSnapshot.none(),
                3,
                100L,
                BuildGuide.VISIBILITY_PRIVATE,
                List.of(BuildAction.emeraldProductionUpgrade(1, 1, 10, 1))
        );
    }

    private static SemionGame gameWithPlayer(UUID playerId, String playerName) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        game.players().put(playerId, new SemionPlayer(
                playerId,
                playerName,
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        ));
        return game;
    }
}
