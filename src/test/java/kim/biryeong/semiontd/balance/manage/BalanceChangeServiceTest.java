package kim.biryeong.semiontd.balance.manage;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.balance.manage.BalanceChangeService.Boundary;
import kim.biryeong.semiontd.balance.manage.BalanceChangeService.RuntimeView;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BalanceChangeServiceTest {
    private static final String DAMAGE = "tower:/towers/t1_pig_tower/damage";
    private static final String HEALTH = "tower:/towers/t1_pig_tower/maxHealth";
    private static final String SCOPE = "balance:apply domain:tower mode:NEXT_MATCH server:test";
    private static BalanceBundle defaults;
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        defaults = BalanceBundle.defaults();
    }

    @Test
    void validationIsPureAndUnknownFieldsAreReadOnly() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        try (var service = service(runtime)) {
            String index = Files.readString(directory.resolve("index.json"));
            BalancePatch patch = patch(service, ApplyMode.NEXT_MATCH, DAMAGE, 6);
            assertTrue(service.validate(patch, "operator", SCOPE).valid());
            assertEquals(defaults.revision(), service.currentRevision());
            assertEquals(index, Files.readString(directory.resolve("index.json")));
            assertEquals(0, runtime.applied);
            assertFalse(service.fields().stream().filter(field -> field.id().equals("tower:/schemaVersion")).findFirst().orElseThrow().editable());
            JsonObject copy = defaults.toJson();
            copy.remove("tower");
            assertNotNull(defaults.toJson().get("tower"));
        }
    }

    @Test
    void oneBadFieldRejectsWholePatchAndGuardsExpectedValueAndMode() {
        try (var service = service(new FakeRuntime())) {
            BalancePatch valid = patch(service, ApplyMode.NEXT_MATCH, DAMAGE, 6);
            BalancePatch mixed = new BalancePatch(valid.baseRevision(), valid.applyMode(), "reason", null,
                    List.of(valid.changes().getFirst(), new BalanceChange("tower:/schemaVersion", 2, 3)));
            assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(mixed, "operator", SCOPE)).status());
            BalancePatch stale = new BalancePatch(valid.baseRevision(), valid.applyMode(), "reason", null,
                    List.of(new BalanceChange(DAMAGE, -1, 6)));
            assertEquals(409, assertThrows(BalanceException.class, () -> service.validate(stale, "operator", SCOPE)).status());
            assertEquals(422, assertThrows(BalanceException.class,
                    () -> service.validate(patch(service, ApplyMode.NOW, HEALTH, 90), "operator", SCOPE)).status());
            assertEquals(defaults.revision(), service.currentRevision());
        }
    }

    @Test
    void mixedModesIntersectAndIntegerRoundingIsExplicit() {
        try (var service = service(new FakeRuntime())) {
            BalancePatch patch = patch(service, ApplyMode.NEXT_PREPARE, "tower:/towers/t1_pig_tower/attackIntervalTicks", 21.6);
            BalanceValidation validation = service.validate(patch, "operator", SCOPE);
            assertEquals(22, validation.changes().getFirst().after());
            assertEquals(List.of(ApplyMode.NEXT_PREPARE, ApplyMode.NEXT_MATCH), validation.supportedModes());
            assertEquals(1, validation.warnings().size());
            BalancePatch negativeAggro = patch(service, ApplyMode.NOW, "tower:/towers/t1_pig_tower/aggroPriority", -5);
            assertTrue(service.validate(negativeAggro, "operator", SCOPE).valid());
            BalancePatch augment = patch(service, ApplyMode.NEXT_MATCH,
                    "augment:/parameters/semiontd:tactical_designation_1/damageReduction", 0.25);
            assertTrue(service.validate(augment, "operator", SCOPE).valid());
        }
    }

    @Test
    void towerAbilityFieldsAllowImmediateEditingWithoutGuessingUnits() {
        try (var service = service(new FakeRuntime())) {
            var fields = service.fields().stream().filter(field -> field.id().startsWith("tower:/abilities/")
                    || field.id().startsWith("tower:/villagerAdv/")
                    || field.id().startsWith("tower:/illusionCloneQueue/")).toList();
            assertEquals(defaults.tower().abilities().values().stream().mapToLong(java.util.Map::size).sum(),
                    fields.stream().filter(field -> field.id().startsWith("tower:/abilities/")).count());
            assertFalse(fields.isEmpty());
            for (var field : fields) {
                assertTrue(field.editable(), field.id());
                assertEquals(List.of(ApplyMode.NOW, ApplyMode.NEXT_PREPARE, ApplyMode.NEXT_MATCH), field.modes(), field.id());
            }
            String damage = "tower:/abilities/t1_pig_tower/damagePerStack";
            assertEquals("config", fields.stream().filter(field -> field.id().equals(damage)).findFirst().orElseThrow().unit());
            for (ApplyMode mode : ApplyMode.values()) {
                assertTrue(service.validate(patch(service, mode, damage, 7.25), "operator", SCOPE).valid());
            }
            assertTrue(service.validate(patch(service, ApplyMode.NOW,
                    "tower:/abilities/hero_party_weapon_tome/aggroPriority", -15), "operator", SCOPE).valid());
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(
                        patch(service, ApplyMode.NOW, damage, invalid), "operator", SCOPE)).status());
            }
            // Keep the existing ability-specific integer and ratio validators, not just the UI bounds.
            assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(patch(service, ApplyMode.NOW,
                    "tower:/abilities/illager_raid/damageBonusCap", 1.1), "operator", SCOPE)).status());
            assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(patch(service, ApplyMode.NOW,
                    "tower:/illusionCloneQueue/maxSpawnsPerTick", 0), "operator", SCOPE)).status());
        }
        JsonObject json = defaults.toJson();
        json.getAsJsonObject("tower").getAsJsonObject("abilities").getAsJsonObject("t1_pig_tower").addProperty("futureAbility", 1);
        var unknown = new BalanceFieldRegistry(defaults).fields(BalanceBundle.fromJson(json), null).stream()
                .filter(field -> field.id().endsWith("/futureAbility")).findFirst().orElseThrow();
        assertFalse(unknown.editable());
    }

    @Test
    void allAugmentAndTraitParametersAreEditableAtNextMatchWithValidatedUnits() {
        try (var service = service(new FakeRuntime())) {
            var fields = service.fields().stream().filter(field ->
                    field.id().startsWith("augment:/parameters/") || field.domain().equals("trait")).toList();
            assertEquals(426, fields.stream().filter(field -> field.domain().equals("augment")).count());
            assertEquals(32, fields.stream().filter(field -> field.domain().equals("trait")).count());
            var changes = fields.stream().map(field -> {
                assertTrue(field.editable(), field.id());
                assertNotEquals("unknown", field.unit(), field.id());
                assertEquals(List.of(ApplyMode.NEXT_MATCH), field.modes(), field.id());
                double step = field.numberType().equals("integer") ? 1 : .01;
                double after = field.value() + step;
                if (field.max() != null && after > field.max()) after = field.value() - step;
                return new BalanceChange(field.id(), field.value(), after);
            }).toList();
            var patch = new BalancePatch(service.currentRevision(), ApplyMode.NEXT_MATCH, "parameter coverage", null, changes);
            var validation = service.validate(patch, "operator", SCOPE);
            assertTrue(validation.valid());
            assertEquals(fields.size(), validation.changes().size());
            assertTrue(validation.warnings().isEmpty());
            assertEquals(defaults.revision(), service.currentRevision());
            for (ApplyMode mode : List.of(ApplyMode.NOW, ApplyMode.NEXT_PREPARE)) {
                assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(
                        new BalancePatch(patch.baseRevision(), mode, patch.reason(), null, changes), "operator", SCOPE)).status());
            }
        }
    }

    @Test
    void parameterLimitsRejectInvalidValuesAndRoundCountsWithoutRoundingRatios() {
        try (var service = service(new FakeRuntime())) {
            var invalid = java.util.Map.of(
                    "augment:/parameters/semiontd:folding_barricade_blueprint/damagePerHitCap", 0.0,
                    "augment:/parameters/semiontd:job_engineer_towers_g2/delayTicks", 0.0,
                    "augment:/parameters/semiontd:job_adversary_towers_g1/healRatio", 1.1,
                    "augment:/parameters/semiontd:job_gamble_g2/statReversalChance", 1.1,
                    "trait:/traits/rapid_deployment/refundRateAfterWave", 1.1,
                    "trait:/traits/ignite/tickIntervalSeconds", 0.0,
                    "trait:/traits/ignite/durationSeconds", (double) Integer.MAX_VALUE,
                    "trait:/traits/finishing_blow/healthRatioThreshold", -0.1);
            invalid.forEach((id, value) -> assertEquals(422, assertThrows(BalanceException.class,
                    () -> service.validate(patch(service, ApplyMode.NEXT_MATCH, id, value), "operator", SCOPE), id).status()));
            var ticks = service.validate(patch(service, ApplyMode.NEXT_MATCH,
                    "augment:/parameters/semiontd:job_engineer_towers_g2/delayTicks", 21.6), "operator", SCOPE);
            assertEquals(22, ticks.changes().getFirst().after());
            assertEquals(1, ticks.warnings().size());
            var ratio = service.validate(patch(service, ApplyMode.NEXT_MATCH,
                    "trait:/traits/fortitude/maxHealthBonus", .345), "operator", SCOPE);
            assertEquals(.345, ratio.changes().getFirst().after());
            assertTrue(ratio.warnings().isEmpty());
        }
    }

    @Test
    void traitMetadataUsesConfigPathAndUnknownLegacyTraitFieldsStayReadOnly() {
        var catalog = WebCatalogExporter.snapshot(1, defaults.wave(), defaults.economy(), defaults.summon(), defaults.augment());
        var registry = new BalanceFieldRegistry(defaults, catalog, Set.of());
        JsonObject json = defaults.toJson();
        json.getAsJsonObject("trait").getAsJsonObject("traits").getAsJsonObject("fortitude").addProperty("futureBonus", 1);
        var fields = registry.fields(BalanceBundle.fromJson(json), null);
        var trait = fields.stream().filter(field -> field.id().equals("trait:/traits/fortitude/maxHealthBonus")).findFirst().orElseThrow();
        assertNotEquals("fortitude", trait.entityName());
        assertFalse(trait.description().isBlank());
        assertFalse(fields.stream().filter(field -> field.id().endsWith("/futureBonus")).findFirst().orElseThrow().editable());
    }

    @Test
    void validationHashBindsActorScopeAndOriginalRequest() {
        try (var service = service(new FakeRuntime())) {
            BalancePatch patch = patch(service, ApplyMode.NEXT_MATCH, DAMAGE, 6);
            String hash = service.validate(patch, "operator", SCOPE).validationHash();
            assertEquals(409, assertThrows(BalanceException.class,
                    () -> service.submit(key(), patch, hash, "other", "web", SCOPE)).status());
            assertEquals(409, assertThrows(BalanceException.class,
                    () -> service.submit(key(), patch, hash, "operator", "web", SCOPE + " domain:wave")).status());
            assertTrue(service.history().isEmpty());
        }
    }

    @Test
    void nextMatchDoesNotChangeRuntimeEarlyAndRetryReturnsDurableReceipt() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        String key = key();
        BalancePatch patch;
        String hash;
        try (var service = service(runtime)) {
            patch = patch(service, ApplyMode.NEXT_MATCH, DAMAGE, 6);
            hash = service.validate(patch, "operator", SCOPE).validationHash();
            BalanceDeployment scheduled = service.submit(key, patch, hash, "operator", "web", SCOPE);
            assertEquals(key, scheduled.requestId());
            assertEquals(DeploymentState.SCHEDULED, scheduled.state());
            assertNull(scheduled.effectiveRevision());
            service.onBoundary(Boundary.TICK);
            assertEquals(0, runtime.applied);
            assertEquals(6, service.fields().stream().filter(field -> field.id().equals(DAMAGE)).findFirst().orElseThrow().scheduledValue());
            assertEquals(409, assertThrows(BalanceException.class,
                    () -> service.submit(key(), patch, hash, "operator", "web", SCOPE)).status());
            service.onBoundary(Boundary.BEFORE_MATCH);
            awaitTerminal(service, key);
            assertEquals(1, runtime.applied);
            assertEquals(DeploymentState.APPLIED, service.submit(key, patch, hash, "operator", "web", SCOPE).state());
        }
        try (var reloaded = service(new FakeRuntime())) {
            assertEquals(6, reloaded.currentBundle().tower().towers().get("t1_pig_tower").damage());
            assertEquals(DeploymentState.APPLIED, reloaded.submit(key, patch, hash, "operator", "web", SCOPE).state());
        }
    }

    @Test
    void restartRequiresReviewAndCancellationReleasesSinglePendingSlot() {
        String key = key();
        try (var service = service(new FakeRuntime())) {submit(service, key, ApplyMode.NEXT_MATCH, 6);}
        FakeRuntime runtime = new FakeRuntime();
        try (var reloaded = service(runtime)) {
            assertEquals(DeploymentState.REQUIRES_REVIEW, reloaded.state().pending().state());
            reloaded.onBoundary(Boundary.BEFORE_MATCH);
            assertEquals(0, runtime.applied);
            assertEquals(DeploymentState.CANCELLED, reloaded.cancel(key, "operator").state());
            assertNull(reloaded.state().pending());
            assertEquals(DeploymentState.SCHEDULED, submit(reloaded, key(), ApplyMode.NEXT_MATCH, 7).state());
        }
    }

    @Test
    void idleLobbyAllowsNowForPrepareFieldsButNotNextMatchOnlyFields() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        runtime.gameId = null;
        try (var service = service(runtime)) {
            service.onBoundary(Boundary.TICK);
            var health = service.fields().stream().filter(field -> field.id().equals(HEALTH)).findFirst().orElseThrow();
            assertEquals(List.of(ApplyMode.NOW, ApplyMode.NEXT_PREPARE, ApplyMode.NEXT_MATCH), health.modes());
            var patch = patch(service, ApplyMode.NOW, HEALTH, 123);
            var validation = service.validate(patch, "operator", SCOPE);
            String request = key();
            service.submit(request, patch, validation.validationHash(), "operator", "web", SCOPE);
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, request);
            assertEquals(DeploymentState.APPLIED, service.deployment(request).state());
            assertEquals(123, service.currentBundle().tower().towers().get("t1_pig_tower").maxHealth());
            assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(patch(service, ApplyMode.NOW,
                    "trait:/traits/fortitude/maxHealthBonus", .4), "operator", SCOPE)).status());
            runtime.gameId = "started-game";
            service.onBoundary(Boundary.TICK);
            assertFalse(service.fields().stream().filter(field -> field.id().equals(HEALTH)).findFirst().orElseThrow().modes().contains(ApplyMode.NOW));
            assertEquals(422, assertThrows(BalanceException.class, () -> service.validate(
                    patch(service, ApplyMode.NOW, HEALTH, 124), "operator", SCOPE)).status());
        }
    }

    @Test
    void idlePrepareReservationAppliesOnTickAndCannotBypassPracticeBlock() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        runtime.gameId = null;
        runtime.safe = false;
        try (var service = service(runtime)) {
            service.onBoundary(Boundary.TICK);
            assertFalse(service.fields().stream().filter(field -> field.id().equals(HEALTH)).findFirst().orElseThrow().modes().contains(ApplyMode.NOW));
            var patch = patch(service, ApplyMode.NEXT_PREPARE, HEALTH, 123);
            var validation = service.validate(patch, "operator", SCOPE);
            String request = key();
            service.submit(request, patch, validation.validationHash(), "operator", "web", SCOPE);
            service.onBoundary(Boundary.TICK);
            assertEquals(DeploymentState.SCHEDULED, service.deployment(request).state());
            assertEquals(0, runtime.applied);
            runtime.safe = true;
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, request);
            assertEquals(DeploymentState.APPLIED, service.deployment(request).state());
            service.onBoundary(Boundary.TICK);
            assertEquals(1, runtime.applied);
        }
    }

    @Test
    void idleNowRequestRequiresReviewIfMatchStartsBeforeApplication() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        runtime.gameId = null;
        try (var service = service(runtime)) {
            service.onBoundary(Boundary.TICK);
            var patch = patch(service, ApplyMode.NOW, HEALTH, 123);
            var validation = service.validate(patch, "operator", SCOPE);
            String request = key();
            service.submit(request, patch, validation.validationHash(), "operator", "web", SCOPE);
            runtime.gameId = "started-game";
            runtime.safe = false;
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, request);
            assertEquals(DeploymentState.REQUIRES_REVIEW, service.deployment(request).state());
            assertEquals(0, runtime.applied);
            assertEquals(defaults.revision(), service.currentRevision());
        }
    }

    @Test
    void prepareReservationCannotMigrateToAnotherMatch() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        String key = key();
        try (var service = service(runtime)) {
            service.onBoundary(Boundary.TICK);
            submit(service, key, ApplyMode.NEXT_PREPARE, 6);
            runtime.gameId = "another-game";
            service.onBoundary(Boundary.BEFORE_PREPARE);
            awaitTerminal(service, key);
            assertEquals(DeploymentState.REQUIRES_REVIEW, service.deployment(key).state());
            assertEquals(0, runtime.applied);
        }
    }

    @Test
    void rollbackIsNewRequestAndDuplicateSurvivesRevisionChange() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        try (var service = service(runtime)) {
            String initial = service.currentRevision();
            String first = key();
            submit(service, first, ApplyMode.NEXT_MATCH, 6);
            service.onBoundary(Boundary.BEFORE_MATCH);
            awaitTerminal(service, first);
            String base = service.currentRevision();
            String rollback = key();
            service.rollback(rollback, initial, base, ApplyMode.NEXT_MATCH, "restore", "operator", "web", SCOPE, true);
            service.onBoundary(Boundary.BEFORE_MATCH);
            awaitTerminal(service, rollback);
            assertEquals(initial, service.currentRevision());
            assertEquals(2, service.history().size());
            assertEquals(DeploymentState.APPLIED, service.rollback(rollback, initial, base,
                    ApplyMode.NEXT_MATCH, "restore", "operator", "web", SCOPE, true).state());
            service.onBoundary(Boundary.TICK);
            service.onBoundary(Boundary.TICK);
            assertEquals(1, runtime.announced);
            assertEquals(409, assertThrows(BalanceException.class, () -> service.rollback(rollback, initial, base,
                    ApplyMode.NEXT_MATCH, "restore", "operator", "web", SCOPE, false)).status());
        }
    }

    @Test
    void persistenceFailureBeforeApplicationFailsClosed() {
        FailingStore store = new FailingStore(directory);
        FakeRuntime runtime = new FakeRuntime();
        try (var service = new BalanceChangeService(store, "test", defaults, registry(), runtime)) {
            store.fail = true;
            assertEquals(503, assertThrows(BalanceException.class, () -> submit(service, key(), ApplyMode.NOW, 6)).status());
            service.onBoundary(Boundary.TICK);
            assertNotNull(service.state().writeBlocked());
            assertEquals(0, runtime.applied);
        }
    }

    @Test
    void failureAfterRuntimeSwitchRequiresReviewAndBlocksFurtherWrites() throws Exception {
        FailingStore store = new FailingStore(directory);
        FakeRuntime runtime = new FakeRuntime();
        try (var service = new BalanceChangeService(store, "test", defaults, registry(), runtime)) {
            String key = key();
            submit(service, key, ApplyMode.NOW, 6);
            store.fail = true;
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, key);
            assertEquals(DeploymentState.REQUIRES_REVIEW, service.deployment(key).state());
            assertNotNull(service.state().writeBlocked());
            assertEquals(1, runtime.applied);
            assertEquals(6, service.currentBundle().tower().towers().get("t1_pig_tower").damage());
        }
    }

    @Test
    void corruptActiveRevisionNeverSilentlyImportsDefaults() throws Exception {
        try (var service = service(new FakeRuntime())) {
            Files.writeString(directory.resolve("revisions").resolve(service.currentRevision() + ".json"), "{}");
        }
        try (var service = service(new FakeRuntime())) {
            assertNotNull(service.state().writeBlocked());
            assertEquals(503, assertThrows(BalanceException.class, () -> submit(service, key(), ApplyMode.NOW, 6)).status());
        }
    }

    @Test
    void legacyBaselineSurvivesOfflineEditsBeforeAndAfterManagedChanges() throws Exception {
        try (var service = service(new FakeRuntime())) {assertEquals(defaults.revision(), service.legacyRevision());}
        JsonObject edited = defaults.toJson();
        edited.getAsJsonObject("economy").addProperty("startingDiamond", defaults.economy().startingDiamond() + 1);
        BalanceBundle offlineEdit = BalanceBundle.fromJson(edited);
        try (var service = new BalanceChangeService(directory, "test", offlineEdit, registry(), new FakeRuntime())) {
            assertEquals(defaults.revision(), service.legacyRevision());
            assertNotEquals(offlineEdit.revision(), service.legacyRevision());
            String key = key();
            submit(service, key, ApplyMode.NOW, 6);
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, key);
            assertNotEquals(service.currentRevision(), service.legacyRevision());
        }
        try (var service = new BalanceChangeService(directory, "test", offlineEdit, registry(), new FakeRuntime())) {
            assertEquals(defaults.revision(), service.legacyRevision());
            assertEquals(6, service.currentBundle().tower().towers().get("t1_pig_tower").damage());
            assertNotEquals(service.currentRevision(), service.legacyRevision());
        }
    }

    @Test
    void runtimeFailureKeepsOldRevisionAndFatalRecoveryBlocksWrites() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        runtime.fail = true;
        try (var service = service(runtime)) {
            String key = key();
            submit(service, key, ApplyMode.NOW, 6);
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, key);
            assertEquals(DeploymentState.FAILED, service.deployment(key).state());
            assertEquals(defaults.revision(), service.currentRevision());
            runtime.fatal = true;
            key = key();
            submit(service, key, ApplyMode.NOW, 6);
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, key);
            assertNotNull(service.state().writeBlocked());
        }
    }

    @Test
    void pendingDiskWriteDoesNotBlockMainThreadOrPermitEarlyApply() throws Exception {
        BlockingStore store = new BlockingStore(directory);
        FakeRuntime runtime = new FakeRuntime();
        try (var service = new BalanceChangeService(store, "test", defaults, registry(), runtime)) {
            store.block = true;
            String key = key();
            var submission = java.util.concurrent.CompletableFuture.supplyAsync(() -> submit(service, key, ApplyMode.NOW, 6));
            try {
                assertTrue(store.writing.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1), () -> service.onBoundary(Boundary.TICK));
                assertEquals(0, runtime.applied);
            } finally {store.release.countDown();}
            assertEquals(DeploymentState.SCHEDULED, submission.get(5, java.util.concurrent.TimeUnit.SECONDS).state());
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, key);
            assertEquals(1, runtime.applied);
        }
    }

    private BalanceChangeService service(FakeRuntime runtime) {
        return new BalanceChangeService(directory, "test", defaults, registry(), runtime);
    }

    @Test
    void fieldCacheTracksValuesReservationsAndIdleEligibilityButNotTicks() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        String beforeRestart;
        try (var service = service(runtime)) {
            service.onBoundary(Boundary.TICK);
            var first = service.fieldListing();
            assertEquals(first.fieldsVersion(), service.state().fieldsVersion());
            service.onBoundary(Boundary.TICK);
            assertSame(first, service.fieldListing());
            String key = key();
            submit(service, key, ApplyMode.NEXT_MATCH, 6);
            var scheduled = service.fieldListing();
            assertEquals(first.revision(), scheduled.revision());
            assertNotEquals(first.fieldsVersion(), scheduled.fieldsVersion());
            assertEquals(6, scheduled.fields().stream().filter(f -> f.id().equals(DAMAGE)).findFirst().orElseThrow().scheduledValue());
            assertSame(scheduled, service.fieldListing());
            service.cancel(key, "operator");
            assertEquals(first.fieldsVersion(), service.fieldListing().fieldsVersion());
            runtime.gameId = null;
            service.onBoundary(Boundary.TICK);
            var idle = service.fieldListing();
            assertNotEquals(first.fieldsVersion(), idle.fieldsVersion());
            assertTrue(idle.fields().stream().filter(f -> f.id().equals(HEALTH)).findFirst().orElseThrow().modes().contains(ApplyMode.NOW));
            runtime.safe = false; // Practice can block idle modes without a NORMAL game ID.
            service.onBoundary(Boundary.TICK);
            assertNotEquals(idle.fieldsVersion(), service.state().fieldsVersion());
            runtime.safe = true;
            service.onBoundary(Boundary.TICK);
            String applied = key();
            submit(service, applied, ApplyMode.NOW, 7);
            service.onBoundary(Boundary.TICK);
            awaitTerminal(service, applied);
            var changed = service.fieldListing();
            assertNotEquals(idle.fieldsVersion(), changed.fieldsVersion());
            assertEquals(service.currentRevision(), changed.revision());
            assertEquals(7, changed.fields().stream().filter(f -> f.id().equals(DAMAGE)).findFirst().orElseThrow().value());
            assertSame(changed, service.fieldListing());
            beforeRestart = changed.fieldsVersion();
        }
        try (var restarted = service(runtime)) {
            restarted.onBoundary(Boundary.TICK);
            assertNotEquals(beforeRestart, restarted.state().fieldsVersion(), "Restart must refresh metadata/defaults");
        }
    }

    @Test
    void announcementsFollowDurableApplyOnceAndRespectSilentRequests() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        try (var service = service(runtime)) {
            for (ApplyMode mode : ApplyMode.values()) {
                for (Boolean notify : new Boolean[] {true, false, null}) {
                    BalancePatch original = patch(service, mode, DAMAGE, runtime.applied + 10);
                    BalancePatch patch = new BalancePatch(original.baseRevision(), mode, original.reason(), null, original.changes(), notify);
                    String hash = service.validate(patch, "operator", SCOPE).validationHash();
                    String key = key();
                    int before = runtime.announced;
                    service.submit(key, patch, hash, "operator", "web", SCOPE);
                    assertEquals(before, runtime.announced, "Scheduling must not announce");
                    Boundary boundary = mode == ApplyMode.NEXT_MATCH ? Boundary.BEFORE_MATCH
                            : mode == ApplyMode.NEXT_PREPARE ? Boundary.BEFORE_PREPARE : Boundary.TICK;
                    service.onBoundary(boundary);
                    awaitTerminal(service, key);
                    assertEquals(DeploymentState.APPLIED, service.deployment(key).state());
                    assertEquals(before, runtime.announced, "Persistence worker must not broadcast");
                    service.onBoundary(Boundary.TICK);
                    assertEquals(before + (Boolean.TRUE.equals(notify) ? 1 : 0), runtime.announced);
                    assertEquals(DeploymentState.APPLIED, service.submit(key, patch, hash, "operator", "web", SCOPE).state());
                    service.onBoundary(Boundary.TICK);
                    assertEquals(before + (Boolean.TRUE.equals(notify) ? 1 : 0), runtime.announced, "Replay must not announce again");
                }
            }
        }
        try (var recovered = service(runtime)) {
            int before = runtime.announced;
            recovered.onBoundary(Boundary.TICK);
            assertEquals(before, runtime.announced, "Restart must not rebroadcast history");
        }
    }

    @Test
    void notificationOptionIsValidatedPersistedAndBoundToRequestIdentity() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        String key = key();
        try (var service = service(runtime)) {
            BalancePatch original = patch(service, ApplyMode.NEXT_MATCH, DAMAGE, 6);
            BalancePatch notify = new BalancePatch(original.baseRevision(), original.applyMode(), original.reason(), null, original.changes(), true);
            String originalHash = service.validate(original, "operator", SCOPE).validationHash();
            String notifyHash = service.validate(notify, "operator", SCOPE).validationHash();
            assertNotEquals(originalHash, notifyHash);
            // Old requests did not serialize notifyPlayers, including in the normalized patch.
            var legacy = BalanceBundle.GSON.toJsonTree(original).getAsJsonObject();
            legacy.remove("notifyPlayers");
            assertEquals(BalanceBundle.digest(BalanceBundle.canonical(BalanceBundle.GSON.toJsonTree(
                    new Object[] {"validation", "test", legacy, legacy, "operator", SCOPE}))), originalHash);
            assertEquals(409, assertThrows(BalanceException.class,
                    () -> service.submit(key, notify, originalHash, "operator", "web", SCOPE)).status());
            service.submit(key, notify, notifyHash, "operator", "web", SCOPE);
            assertEquals(409, assertThrows(BalanceException.class,
                    () -> service.submit(key, original, originalHash, "operator", "web", SCOPE)).status());
            var index = new BalanceRevisionStore(directory).loadOrCreate(defaults);
            assertEquals(Boolean.TRUE, index.receipts().getFirst().patch().notifyPlayers());
        }
        try (var recovered = service(runtime)) {
            recovered.onBoundary(Boundary.BEFORE_MATCH);
            assertEquals(DeploymentState.REQUIRES_REVIEW, recovered.deployment(key).state());
            assertEquals(0, runtime.announced);
        }
    }

    @Test
    void failedApplyOrFailedJournalNeverAnnounces() throws Exception {
        for (boolean diskFailure : new boolean[] {false, true}) {
            FakeRuntime runtime = new FakeRuntime();
            FailingStore store = new FailingStore(directory.resolve(Boolean.toString(diskFailure)));
            try (var service = new BalanceChangeService(store, "test", defaults, registry(), runtime)) {
                BalancePatch original = patch(service, ApplyMode.NOW, DAMAGE, 6);
                BalancePatch patch = new BalancePatch(original.baseRevision(), original.applyMode(), original.reason(), null, original.changes(), true);
                String key = key();
                service.submit(key, patch, service.validate(patch, "operator", SCOPE).validationHash(), "operator", "web", SCOPE);
                store.fail = diskFailure;
                runtime.fail = !diskFailure;
                service.onBoundary(Boundary.TICK);
                awaitTerminal(service, key);
                assertEquals(diskFailure ? DeploymentState.REQUIRES_REVIEW : DeploymentState.FAILED, service.deployment(key).state());
                service.onBoundary(Boundary.TICK);
                assertEquals(0, runtime.announced);
            }
        }
    }

    private static BalanceFieldRegistry registry() {return new BalanceFieldRegistry(defaults, null, Set.of("t1_pig_tower"));}
    private static String key() {return UUID.randomUUID().toString();}
    private static BalancePatch patch(BalanceChangeService service, ApplyMode mode, String field, double value) {
        double before = BalanceFieldRegistry.flatten(service.currentBundle().toJson()).get(field);
        return new BalancePatch(service.currentRevision(), mode, "balance test", null, List.of(new BalanceChange(field, before, value)));
    }
    private static BalanceDeployment submit(BalanceChangeService service, String key, ApplyMode mode, double value) {
        BalancePatch patch = patch(service, mode, DAMAGE, value);
        return service.submit(key, patch, service.validate(patch, "operator", SCOPE).validationHash(), "operator", "web", SCOPE);
    }
    private static void awaitTerminal(BalanceChangeService service, String key) throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (service.deployment(key).state() == DeploymentState.APPLYING) {Thread.sleep(5);}
        });
    }

    private static class FakeRuntime implements BalanceChangeService.Runtime {
        int applied;
        int announced;
        final Thread mainThread = Thread.currentThread();
        public void broadcastApplied(BalanceDeployment deployment) {
            assertSame(mainThread, Thread.currentThread());
            assertEquals(DeploymentState.APPLIED, deployment.state());
            announced++;
        }
        boolean fail;
        boolean fatal;
        boolean safe = true;
        String gameId = "test-game";
        public RuntimeView view() {return new RuntimeView(gameId, 1, "PREPARE", "catalog", safe);}
        public String apply(BalanceBundle candidate, ApplyMode mode, String requestId, String revision) {
            if (fatal) {throw new BalanceChangeService.FatalRollbackException("failed recovery", null);}
            if (fail) {throw new IllegalStateException("failed apply");}
            applied++;
            return "SYNCED";
        }
    }

    private static class FailingStore extends BalanceRevisionStore {
        volatile boolean fail;
        FailingStore(Path path) {super(path);}
        @Override public void saveIndex(Index index) throws IOException {
            if (fail) {throw new IOException("injected disk failure");}
            super.saveIndex(index);
        }
    }

    private static class BlockingStore extends BalanceRevisionStore {
        volatile boolean block;
        final java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        BlockingStore(Path path) {super(path);}
        @Override public void saveIndex(Index index) throws IOException {
            if (block) {
                writing.countDown();
                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {throw new IOException("test timeout");}
                } catch (InterruptedException exception) {Thread.currentThread().interrupt(); throw new IOException(exception);}
            }
            super.saveIndex(index);
        }
    }
}
