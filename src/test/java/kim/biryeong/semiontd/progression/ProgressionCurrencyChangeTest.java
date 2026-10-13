package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange.Operation;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProgressionCurrencyChangeTest {
    @TempDir
    Path directory;

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID offline = UUID.randomUUID();

    private Path path() {
        return directory.resolve("profiles.json");
    }

    private ProgressionService service() {
        return new ProgressionService(ProgressionConfig.defaultConfig(), path());
    }

    private Map<UUID, String> targets() {
        Map<UUID, String> targets = new LinkedHashMap<>();
        targets.put(first, "First");
        targets.put(second, "Second");
        return targets;
    }

    @Test
    void bothOperationsPersistAllTargetsAndPreserveOtherProfileFieldsAndOfflineBalances() {
        SemionPlayerProfile initial = SemionPlayerProfile.fresh("First").recordMatch("First", true, 100)
                .purchaseCosmetic("First", "crown", 10).updateSelectedCosmetics("First", List.of("crown"))
                .updateSelectedSkybox("First", "night").updateTipsEnabled("First", false)
                .rememberRecentBuildCode("First", "TEST");
        new SemionProgressionStore(path()).putProfile(first, initial);
        var service = service();
        service.grantCosmeticCurrency(offline, "Offline", 400).orElseThrow();
        assertEquals(new ProgressionCurrencyChange(Status.SUCCESS, 2, 60),
                service.changeCosmeticCurrency(targets(), 30, Operation.GIVE));
        var reloaded = service();
        assertEquals(initial.grantCosmeticCurrency("First", 30), reloaded.profile(null, first, "First"));
        assertEquals(30, reloaded.profile(null, second, "Second").cosmeticCurrency());
        assertEquals(new ProgressionCurrencyChange(Status.SUCCESS, 2, 40),
                reloaded.changeCosmeticCurrency(targets(), 20, Operation.TAKE));
        var restarted = service();
        assertEquals(initial.grantCosmeticCurrency("First", 10), restarted.profile(null, first, "First"));
        assertEquals(10, restarted.profile(null, second, "Second").cosmeticCurrency());
        assertEquals(400, restarted.profile(null, offline, "Offline").cosmeticCurrency());
    }

    @Test
    void matchRewardsAndAdministrativeChangesShareTheSamePersistedBalance() {
        var config = new ProgressionConfig(30, 30, 20);
        var service = new ProgressionService(config, path());
        assertTrue(service.changeCosmeticCurrency(targets(), 10, Operation.GIVE).succeeded());
        var match = new kim.biryeong.semiontd.game.MatchResult(List.of(
                new kim.biryeong.semiontd.game.MatchParticipantResult(first, "First", kim.biryeong.semiontd.game.TeamId.RED, true),
                new kim.biryeong.semiontd.game.MatchParticipantResult(second, "Second", kim.biryeong.semiontd.game.TeamId.BLUE, false)),
                java.util.Set.of(offline), java.util.Set.of(kim.biryeong.semiontd.game.TeamId.RED), 5);
        assertEquals(60, service.applyMatchResult(null, match).get(first).currencyAwarded());
        assertEquals(70, service.profile(null, first, "First").cosmeticCurrency());
        assertEquals(60, service.profile(null, second, "Second").cosmeticCurrency());
        assertTrue(service.changeCosmeticCurrency(targets(), 5, Operation.TAKE).succeeded());
        var reloaded = new ProgressionService(config, path());
        assertTrue(reloaded.applyMatchResult(null, match).isEmpty());
        assertEquals(65, reloaded.profile(null, first, "First").cosmeticCurrency());
        assertEquals(55, reloaded.profile(null, second, "Second").cosmeticCurrency());
        assertEquals(1, reloaded.profile(null, first, "First").wins());
        assertEquals(1, reloaded.profile(null, second, "Second").losses());
    }

    @Test
    void zeroNegativeAndNullOperationDoNotCreateProfilesOrFiles() {
        var service = service();
        for (long amount : List.of(0L, -1L, Long.MIN_VALUE)) {
            for (Operation operation : Operation.values()) {
                assertEquals(Status.INVALID_AMOUNT, service.changeCosmeticCurrency(targets(), amount, operation).status());
            }
        }
        assertEquals(Status.INVALID_AMOUNT, service.changeCosmeticCurrency(targets(), 1, null).status());
        assertFalse(Files.exists(path()));
    }

    @Test
    void emptyAndInvalidTargetsDoNotCreateData() {
        var service = service();
        assertEquals(Status.NO_TARGETS, service.changeCosmeticCurrency(Map.of(), 1, Operation.GIVE).status());
        assertEquals(Status.NO_TARGETS, service.changeCosmeticCurrency(null, 1, Operation.TAKE).status());
        assertEquals(Status.INVALID_TARGET, service.changeCosmeticCurrency(Map.of(first, ""), 1, Operation.GIVE).status());
        Map<UUID, String> nullId = targets();
        nullId.put(null, "Invalid");
        assertEquals(Status.INVALID_TARGET, service.changeCosmeticCurrency(nullId, 1, Operation.GIVE).status());
        Map<UUID, String> nullName = targets();
        nullName.put(first, null);
        assertEquals(Status.INVALID_TARGET, service.changeCosmeticCurrency(nullName, 1, Operation.GIVE).status());
        assertFalse(Files.exists(path()));
    }

    @Test
    void duplicateUuidHasOneBalanceAndOneShare() {
        Map<UUID, String> targets = targets();
        targets.put(first, "FirstRenamed");
        var result = service().changeCosmeticCurrency(targets, 7, Operation.GIVE);
        assertEquals(2, result.targetCount());
        assertEquals(14, result.totalAmount());
        assertEquals(7, service().profile(null, first, "FirstRenamed").cosmeticCurrency());
    }

    @Test
    void insufficientBalanceRejectsWholeBatchWithoutPartialMutation() throws Exception {
        var service = service();
        service.grantCosmeticCurrency(first, "First", 100).orElseThrow();
        service.grantCosmeticCurrency(second, "Second", 4).orElseThrow();
        byte[] before = Files.readAllBytes(path());
        var result = service.changeCosmeticCurrency(targets(), 5, Operation.TAKE);
        assertEquals(new ProgressionCurrencyChange(Status.INSUFFICIENT_FUNDS, 2, 0), result);
        assertArrayEquals(before, Files.readAllBytes(path()));
        assertEquals(100, service.profile(null, first, "First").cosmeticCurrency());
        assertEquals(4, service().profile(null, second, "Second").cosmeticCurrency());
    }

    @Test
    void missingProfileIsZeroAndCannotBeDebited() throws Exception {
        var service = service();
        service.grantCosmeticCurrency(first, "First", 100).orElseThrow();
        byte[] before = Files.readAllBytes(path());
        assertEquals(Status.INSUFFICIENT_FUNDS, service.changeCosmeticCurrency(targets(), 1, Operation.TAKE).status());
        assertArrayEquals(before, Files.readAllBytes(path()));
        assertFalse(Files.readString(path()).contains(second.toString()));
    }

    @Test
    void exactBalanceCanBeRemovedWithoutChangingMatchStatistics() {
        var service = service();
        service.changeCosmeticCurrency(targets(), 20, Operation.GIVE);
        assertTrue(service.changeCosmeticCurrency(targets(), 20, Operation.TAKE).succeeded());
        var profile = service().profile(null, first, "First");
        assertEquals(0, profile.cosmeticCurrency());
        assertEquals(0, profile.gamesPlayed());
    }

    @Test
    void individualAndAggregateOverflowRejectWholeBatch() throws Exception {
        var service = service();
        service.grantCosmeticCurrency(first, "First", 100).orElseThrow();
        service.grantCosmeticCurrency(second, "Second", Long.MAX_VALUE).orElseThrow();
        byte[] before = Files.readAllBytes(path());
        assertEquals(Status.OVERFLOW, service.changeCosmeticCurrency(targets(), 1, Operation.GIVE).status());
        assertEquals(Status.OVERFLOW, service.changeCosmeticCurrency(targets(), Long.MAX_VALUE, Operation.GIVE).status());
        assertArrayEquals(before, Files.readAllBytes(path()));
        assertEquals(100, service.profile(null, first, "First").cosmeticCurrency());
        assertEquals(Long.MAX_VALUE, service.profile(null, second, "Second").cosmeticCurrency());
    }

    @Test
    void maximumSingleBalanceCanBeGrantedAndRemoved() {
        var service = service();
        assertTrue(service.changeCosmeticCurrency(Map.of(first, "First"), Long.MAX_VALUE, Operation.GIVE).succeeded());
        assertTrue(service().changeCosmeticCurrency(Map.of(first, "First"), Long.MAX_VALUE, Operation.TAKE).succeeded());
        assertEquals(0, service().profile(null, first, "First").cosmeticCurrency());
    }

    @Test
    void failedAtomicReplacementLeavesMemoryUnchangedAndRemovesTemporaryFile() throws Exception {
        var service = service();
        service.changeCosmeticCurrency(targets(), 100, Operation.GIVE);
        Path backup = directory.resolve("original.json");
        Files.move(path(), backup);
        Files.createDirectory(path());
        Path sentinel = Files.writeString(path().resolve("sentinel"), "storage unavailable");
        for (Operation operation : Operation.values()) {
            assertEquals(new ProgressionCurrencyChange(Status.PERSISTENCE_FAILED, 2, 0),
                    service.changeCosmeticCurrency(targets(), 10, operation));
            assertEquals(100, service.profile(null, first, "First").cosmeticCurrency());
            assertEquals(100, service.profile(null, second, "Second").cosmeticCurrency());
            assertEquals("storage unavailable", Files.readString(sentinel));
        }
        try (var children = Files.list(directory)) {
            assertTrue(children.noneMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
        Files.delete(sentinel);
        Files.delete(path());
        Files.move(backup, path());
        assertEquals(100, service().profile(null, first, "First").cosmeticCurrency());
        assertTrue(service.changeCosmeticCurrency(targets(), 10, Operation.GIVE).succeeded());
        assertEquals(110, service().profile(null, second, "Second").cosmeticCurrency());
    }

    @Test
    void corruptOrUnreadableStoreIsNotOverwrittenByBulkChanges() throws Exception {
        for (String invalid : List.of("{broken", "null", "{\"invalid-id\":{}}", "{\"" + first + "\":null}")) {
            Files.writeString(path(), invalid);
            assertEquals(Status.PERSISTENCE_FAILED, service().changeCosmeticCurrency(targets(), 10, Operation.GIVE).status());
            assertEquals(invalid, Files.readString(path()));
        }
        Files.delete(path());
        Files.createDirectory(path());
        assertEquals(Status.PERSISTENCE_FAILED, service().changeCosmeticCurrency(targets(), 10, Operation.GIVE).status());
        assertTrue(Files.isDirectory(path()));
    }
}
