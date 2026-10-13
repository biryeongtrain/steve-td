package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import kim.biryeong.semiontd.balance.manage.BalanceRevisionStore.Index;
import kim.biryeong.semiontd.balance.manage.BalanceRevisionStore.Receipt;

/** Validates on request threads; only onBoundary may touch game state. */
public final class BalanceChangeService implements AutoCloseable {
    public enum Boundary {TICK, BEFORE_PREPARE, BEFORE_MATCH}

    public record RuntimeView(String gameId, int round, String phase, String catalogVersion, boolean nextMatchSafe) {}

    public interface Runtime {
        RuntimeView view();
        /** Must restore the old state before throwing, or throw FatalRollbackException if restoration failed. */
        String apply(BalanceBundle candidate, ApplyMode mode, String requestId, String revision);
        default String writeBlocked() {return null;}
        /** Called on the persistence worker after apply; only immutable snapshot export belongs here. */
        default String afterApplied() {return "SYNCED";}
        /** Called once on the main thread, after successful application is durably recorded. */
        default void broadcastApplied(BalanceDeployment deployment) {}
        /** Public, immutable changelog export on the persistence worker; never exposes request credentials. */
        default void publishUpdates(List<BalanceDeployment> deployments, List<BalanceField> fields) {}
    }

    public static final class FatalRollbackException extends IllegalStateException {
        public FatalRollbackException(String message, Throwable cause) {super(message, cause);}
    }

    private final String serverId;
    private final BalanceRevisionStore store;
    private final BalanceFieldRegistry registry;
    private final Runtime runtime;
    private final BalanceBundle serverConfig;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService persistence = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "semion-balance-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private final BalanceReceiptLedger receipts = new BalanceReceiptLedger();
    private final ArrayDeque<BalanceDeployment> announcements = new ArrayDeque<>();
    private final String fieldsEpoch = UUID.randomUUID().toString();
    private volatile FieldListing cachedFields;
    private BalanceBundle active;
    private String activeRevision;
    private String legacyRevision;
    private boolean persistenceBusy;
    private String writeBlocked;
    private Exception recoveryFailure;
    private String runtimeWriteBlocked;
    private RuntimeView view = new RuntimeView(null, 0, "UNKNOWN", null, false);
    private long updatedAt = System.currentTimeMillis();
    private long lastAppliedAt;

    public BalanceChangeService(Path directory, String serverId, BalanceBundle initial,
                                BalanceFieldRegistry registry, Runtime runtime) {
        this(new BalanceRevisionStore(directory), serverId, initial, registry, runtime);
    }

    public BalanceChangeService(BalanceRevisionStore store, String serverId, BalanceBundle initial,
                                BalanceFieldRegistry registry, Runtime runtime) {
        this.store = Objects.requireNonNull(store);
        this.serverId = Objects.requireNonNull(serverId);
        this.registry = Objects.requireNonNull(registry);
        this.runtime = Objects.requireNonNull(runtime);
        this.serverConfig = Objects.requireNonNull(initial);
        active = initial;
        activeRevision = initial.revision();
        legacyRevision = activeRevision;
        try {
            Index index = store.loadOrCreate(initial);
            active = store.readRevision(index.activeRevision(), serverConfig);
            activeRevision = index.activeRevision();
            legacyRevision = index.legacyRevision() != null ? index.legacyRevision()
                    : index.receipts().isEmpty() ? activeRevision : index.receipts().getFirst().patch().baseRevision();
            writeBlocked = index.writeBlocked();
            boolean recovered = false;
            for (Receipt receipt : index.receipts()) {
                if (receipt.deployment().appliedAt() != null) {lastAppliedAt = Math.max(lastAppliedAt, receipt.deployment().appliedAt());}
                if (unfinished(receipt.deployment().state())) {
                    receipt = withDeployment(receipt, transition(receipt.deployment(), DeploymentState.REQUIRES_REVIEW,
                            null, "서버 재시작 후 예약을 다시 확인해야 합니다.", "PENDING"));
                    recovered = true;
                }
                receipts.put(receipt.idempotencyKey(), receipt);
            }
            if (writeBlocked == null && adoptServerSettings()) {recovered = true;}
            if (recovered) {store.saveIndex(index());}
        } catch (IOException | RuntimeException exception) {
            recoveryFailure = exception;
            writeBlocked = "밸런스 저장소 복구에 실패했습니다. 운영자 확인이 필요합니다.";
        }
    }

    private boolean adoptServerSettings() throws IOException {
        BalanceBundle baseline = store.readRevision(legacyRevision, serverConfig);
        JsonObject reconciled = baseline.toJson();
        Map<String, Double> previous = BalanceFieldRegistry.flatten(reconciled);
        Map<String, Double> current = BalanceFieldRegistry.flatten(active.toJson());
        List<BalanceChange> edits = new ArrayList<>();
        for (BalanceField field : registry.fields(serverConfig, null)) {
            Double before = previous.get(field.id());
            Double managed = current.get(field.id());
            if (!field.editable() || before == null || managed == null || Double.compare(before, field.value()) == 0) {continue;}
            BalanceFieldRegistry.set(reconciled, field.id(), field.value());
            if (Double.compare(managed, field.value()) != 0) {edits.add(new BalanceChange(field.id(), managed, field.value()));}
        }
        List<BalanceChange> imported = List.of();
        // Changed file values win; unchanged file fields retain web edits. Import no partial structural delta.
        if (BalanceBundle.canonical(reconciled).equals(BalanceBundle.canonical(serverConfig.toJson()))) {
            try {
                if (!edits.isEmpty()) {
                    var patch = new BalancePatch(activeRevision, ApplyMode.NEXT_MATCH,
                            "서버 파일 변경 동기화 (변경된 항목은 파일 우선)", "server-config-migration", edits, false);
                    Candidate candidate = validateCandidate(patch, "system", "system:startup", new Snapshot(active, activeRevision, true));
                    if (!candidate.validation().warnings().isEmpty()) {throw new BalanceException(422, "파일 값을 자동 반올림하지 않습니다.");}
                    active = candidate.bundle();
                    imported = candidate.normalized().changes();
                }
                baseline = serverConfig;
            } catch (BalanceException invalid) {
                // Keep the old baseline so runtime conflict checking blocks writes without importing a partial patch.
            }
        }
        String nextActive = active.revision();
        String nextLegacy = baseline.revision();
        if (nextActive.equals(activeRevision) && nextLegacy.equals(legacyRevision)) {return false;}
        store.saveRevision(active);
        store.saveRevision(baseline);
        String key = UUID.randomUUID().toString();
        BalancePatch patch = new BalancePatch(activeRevision, ApplyMode.NEXT_MATCH,
                "서버 설정 동기화 (변경된 항목은 파일 우선)", "server-config-migration", imported, false);
        long appliedAt = lastAppliedAt = Math.max(System.currentTimeMillis(), lastAppliedAt + 1);
        BalanceDeployment deployment = new BalanceDeployment(key, DeploymentState.APPLIED, activeRevision, nextActive,
                patch.applyMode(), patch.reason(), "system", "startup", appliedAt, appliedAt, "STARTUP", "NOT_REQUESTED", null, imported);
        String hash = fingerprint("server-config-migration", activeRevision, nextActive, nextLegacy);
        receipts.put(key, new Receipt(key, hash, patch, nextActive, hash, "system:startup", null, deployment));
        activeRevision = nextActive;
        legacyRevision = nextLegacy;
        return true;
    }

    public BalanceBundle currentBundle() {return locked(() -> active);}
    public String currentRevision() {return locked(() -> activeRevision);}
    public String legacyRevision() {return legacyRevision;}
    Exception recoveryFailure() {return recoveryFailure;}

    public BalanceState state() {
        return locked(() -> {
            List<BalanceDeployment> pending = pendingDeployments();
            return new BalanceState(serverId, true, activeRevision, view.catalogVersion(),
                view.gameId() == null ? null : new GameState(view.gameId(), view.round(), view.phase()),
                pending.isEmpty() ? null : pending.getFirst(), updatedAt, blockedReason(), fieldsVersion(), pending);
        });
    }

    private List<BalanceDeployment> pendingDeployments() {
        return receipts.values().stream().map(Receipt::deployment).filter(deployment -> unfinished(deployment.state())).toList();
    }

    private Map<String, Double> scheduledValues() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (BalanceDeployment deployment : pendingDeployments()) {
            if (deployment.state() == DeploymentState.SCHEDULED || deployment.state() == DeploymentState.APPLYING) {
                deployment.changes().forEach(change -> values.put(change.fieldId(), change.value()));
            }
        }
        return Map.copyOf(values);
    }

    public List<BalanceField> fields() {
        return fieldListing().fields();
    }

    public record FieldListing(String revision, String fieldsVersion, List<BalanceField> fields) {}

    public FieldListing fieldListing() {
        FieldSnapshot snapshot = locked(() -> new FieldSnapshot(active, scheduledValues(), isIdle(), activeRevision, fieldsVersion()));
        FieldListing cached = cachedFields;
        if (cached != null && cached.fieldsVersion().equals(snapshot.version())) {return cached;}
        // Generate outside the game-state lock; retain only the latest complete list.
        FieldListing result = new FieldListing(snapshot.revision(), snapshot.version(),
                registry.fieldsWithScheduledValues(snapshot.active(), snapshot.pending(), snapshot.idle()));
        cachedFields = result;
        return result;
    }

    private String fieldsVersion() {
        // Restart invalidates metadata/defaults; ordinary ticks and rounds do not affect the fields.
        return fingerprint("fields-v2", fieldsEpoch, activeRevision, pendingDeployments(), isIdle());
    }
    public BalanceBundle revision(String revision) {
        try {return store.readRevision(revision, serverConfig);}
        catch (IOException exception) {throw new BalanceException(404, "설정 버전을 읽을 수 없습니다.");}
    }

    public BalanceDeployment deployment(String requestId) {
        return locked(() -> findRequest(requestId).deployment());
    }

    public List<BalanceDeployment> history() {
        return locked(() -> receipts.values().stream().map(Receipt::deployment)
                .sorted(Comparator.comparingLong(BalanceDeployment::createdAt).reversed()).toList());
    }

    public BalanceValidation validate(BalancePatch patch, String actor, String scope) {
        Snapshot snapshot = locked(() -> new Snapshot(active, activeRevision, isIdle()));
        return validateCandidate(patch, actor, scope, snapshot).validation();
    }

    public BalanceDeployment submit(String idempotencyKey, BalancePatch patch, String validationHash,
                                     String actor, String source, String scope) {
        String fingerprint = fingerprint("submit", patch, validationHash, actor, source, scope);
        BalanceDeployment replay = locked(() -> replay(idempotencyKey, fingerprint));
        if (replay != null) {return replay;}
        Snapshot snapshot = locked(() -> {requireWritable(); return new Snapshot(active, activeRevision, isIdle());});
        Candidate candidate = validateCandidate(patch, actor, scope, snapshot);
        if (!Objects.equals(validationHash, candidate.validation().validationHash())) {
            throw new BalanceException(409, "검사한 내용이 변경되었습니다. 다시 검사하세요.");
        }
        return schedule(idempotencyKey, candidate, validationHash, actor, source, scope, fingerprint);
    }

    public BalanceDeployment rollback(String idempotencyKey, String targetRevision, String baseRevision,
                                       ApplyMode mode, String reason, String actor, String source, String scope) {
        return rollback(idempotencyKey, targetRevision, baseRevision, mode, reason, actor, source, scope, null);
    }

    public BalanceDeployment rollback(String idempotencyKey, String targetRevision, String baseRevision,
                                       ApplyMode mode, String reason, String actor, String source, String scope, Boolean notifyPlayers) {
        String fingerprint = notifyPlayers == null
                ? fingerprint("rollback", targetRevision, baseRevision, mode, reason, actor, source, scope)
                : fingerprint("rollback", targetRevision, baseRevision, mode, reason, actor, source, scope, notifyPlayers);
        BalanceDeployment replay = locked(() -> replay(idempotencyKey, fingerprint));
        if (replay != null) {return replay;}
        Snapshot snapshot = locked(() -> {requireWritable(); return new Snapshot(active, activeRevision, isIdle());});
        if (!snapshot.revision().equals(baseRevision)) {throw new BalanceException(409, "기준 버전이 변경되었습니다.");}
        BalanceBundle target = revision(targetRevision);
        Map<String, Double> desired = BalanceFieldRegistry.flatten(target.toJson());
        Map<String, Double> current = BalanceFieldRegistry.flatten(snapshot.bundle().toJson());
        if (!desired.keySet().equals(current.keySet())) {throw new BalanceException(422, "필드 구성이 다른 버전은 되돌릴 수 없습니다.");}
        List<BalanceChange> changes = current.entrySet().stream()
                .filter(entry -> Double.compare(entry.getValue(), desired.get(entry.getKey())) != 0)
                .map(entry -> new BalanceChange(entry.getKey(), entry.getValue(), desired.get(entry.getKey()))).toList();
        BalancePatch patch = new BalancePatch(baseRevision, mode, reason, "rollback:" + targetRevision, changes, notifyPlayers);
        Candidate candidate = validateCandidate(patch, actor, scope, snapshot);
        return schedule(idempotencyKey, candidate, candidate.validation().validationHash(), actor, source, scope, fingerprint);
    }

    public BalanceDeployment cancel(String requestId, String actor) {
        Cancellation cancellation = locked(() -> {
            requireWritable();
            Receipt receipt = findRequest(requestId);
            if (receipt.deployment().state() == DeploymentState.CANCELLED) {
                return new Cancellation(receipt, null);
            }
            if (receipt.deployment().state() != DeploymentState.SCHEDULED
                    && receipt.deployment().state() != DeploymentState.REQUIRES_REVIEW) {
                throw new BalanceException(409, "이미 적용을 시작한 요청은 취소할 수 없습니다.");
            }
            BalanceDeployment result = transition(receipt.deployment(), DeploymentState.CANCELLED, null,
                    "취소 작업자: " + required(actor, "작업자"), "NOT_APPLIED");
            Receipt updated = withDeployment(receipt, result);
            receipts.put(receipt.idempotencyKey(), updated);
            persistenceBusy = true;
            return new Cancellation(updated, index());
        });
        Receipt cancelled = cancellation.receipt();
        if (cancellation.index() == null) {return cancelled.deployment();}
        try {store.saveIndex(cancellation.index());}
        catch (IOException exception) {
            locked(() -> {writeBlocked = "예약 취소 이력을 저장하지 못해 변경을 차단했습니다."; return null;});
            throw new BalanceException(503, "예약 취소 이력을 저장하지 못했습니다.");
        }
        return locked(() -> {
            persistenceBusy = false;
            return cancelled.deployment();
        });
    }

    /** Called on the main thread before lifecycle mutation. Request threads hold this lock only for state changes. */
    public void onBoundary(Boundary boundary) {
        lock.lock();
        try {
            while (!announcements.isEmpty()) {
                BalanceDeployment announcement = announcements.removeFirst();
                try {runtime.broadcastApplied(announcement);}
                catch (RuntimeException exception) {
                    kim.biryeong.semiontd.SemionTd.LOGGER.warn("Balance announcement failed: {}", announcement.requestId(), exception);
                }
            }
            view = runtime.view();
            updatedAt = System.currentTimeMillis();
            runtimeWriteBlocked = runtime.writeBlocked();
            if (blockedReason() != null || persistenceBusy || !receipts.hasScheduled()) {return;}
            List<Receipt> results = new ArrayList<>();
            List<BalanceBundle> revisions = new ArrayList<>();
            // Process every eligible reservation at this boundary before the match takes its snapshot.
            for (Receipt receipt : receipts.scheduledSnapshot()) {
                BalanceDeployment deployment = receipt.deployment();
                if (deployment.state() != DeploymentState.SCHEDULED) {continue;}
                if (deployment.applyMode() == ApplyMode.NEXT_MATCH
                        && (boundary != Boundary.BEFORE_MATCH || !view.nextMatchSafe())) {continue;}
                if (deployment.applyMode() == ApplyMode.NEXT_PREPARE && receipt.targetGameId() != null
                        && (boundary == Boundary.BEFORE_MATCH || !receipt.targetGameId().equals(view.gameId()))) {
                    results.add(withDeployment(receipt, transition(deployment, DeploymentState.REQUIRES_REVIEW, null,
                            "예약한 경기가 종료되어 다시 확인해야 합니다.", "NOT_APPLIED")));
                    continue;
                }
                if (deployment.applyMode() == ApplyMode.NEXT_PREPARE && boundary != Boundary.BEFORE_PREPARE
                        && !(receipt.targetGameId() == null && isIdle()
                        && (boundary == Boundary.TICK || boundary == Boundary.BEFORE_MATCH))) {continue;}
                Candidate candidate;
                try {
                    candidate = rebase(receipt);
                } catch (BalanceException changedState) {
                    results.add(withDeployment(receipt, transition(deployment, DeploymentState.REQUIRES_REVIEW, null,
                            "현재 설정이나 경기 상태에서 적용할 수 없습니다: " + changedState.getMessage(), "NOT_APPLIED")));
                    continue;
                }
                // Keep the signed request intact; the deployment records the actual before/after values.
                deployment = new BalanceDeployment(deployment.requestId(), deployment.state(), activeRevision, null,
                        deployment.applyMode(), deployment.reason(), deployment.actor(), deployment.source(), deployment.createdAt(),
                        null, deployment.scheduledFor(), "PENDING", null, candidate.normalized().changes());
                receipt = new Receipt(receipt.idempotencyKey(), receipt.fingerprint(), receipt.patch(), candidate.bundle().revision(),
                        receipt.validationHash(), receipt.scope(), receipt.targetGameId(), deployment);
                revisions.add(candidate.bundle());
                try {
                    String sync = candidate.validation() == null ? "SYNCED"
                            : runtime.apply(candidate.bundle(), deployment.applyMode(), deployment.requestId(), receipt.candidateRevision());
                    active = candidate.bundle();
                    activeRevision = receipt.candidateRevision();
                    results.add(withDeployment(receipt, transition(deployment, DeploymentState.APPLIED, activeRevision, null, sync)));
                } catch (RuntimeException exception) {
                    if (exception instanceof FatalRollbackException) {writeBlocked = "게임 상태 복원이 실패하여 변경을 차단했습니다.";}
                    results.add(withDeployment(receipt, transition(deployment, DeploymentState.FAILED, null,
                            exception instanceof FatalRollbackException ? writeBlocked : "게임 적용이 실패했습니다. 이전 설정을 유지합니다.", "NOT_APPLIED")));
                    if (writeBlocked != null) {break;}
                }
            }
            if (!results.isEmpty()) {finishAsync(results, revisions);}
        } finally {lock.unlock();}
    }

    private Candidate rebase(Receipt receipt) {
        BalancePatch original = receipt.patch();
        Map<String, Double> values = BalanceFieldRegistry.flatten(active.toJson());
        List<BalanceChange> changes = new ArrayList<>();
        for (BalanceChange change : original.changes()) {
            Double before = values.get(change.fieldId());
            if (before == null) {throw new BalanceException(422, "필드가 없어졌습니다: " + change.fieldId());}
            changes.add(new BalanceChange(change.fieldId(), before, change.value()));
        }
        BalancePatch patch = new BalancePatch(activeRevision, original.applyMode(), original.reason(), original.sourceReference(), changes, original.notifyPlayers());
        List<BalanceChange> changed = changes.stream().filter(change -> Double.compare(change.expectedValue(), change.value()) != 0).toList();
        if (changed.isEmpty()) {return new Candidate(active, patch, null);}
        Candidate validated = validateCandidate(new BalancePatch(activeRevision, patch.applyMode(), patch.reason(), patch.sourceReference(),
                changed, patch.notifyPlayers()), receipt.deployment().actor(), receipt.scope(), new Snapshot(active, activeRevision, isIdle()));
        return new Candidate(validated.bundle(), patch, validated.validation());
    }

    private BalanceDeployment schedule(String key, Candidate candidate, String hash,
                                         String actor, String source, String scope, String fingerprint) {
        String candidateRevision = candidate.bundle().revision();
        Receipt receipt = locked(() -> {
            BalanceDeployment replay = replay(key, fingerprint);
            if (replay != null) {return receipts.get(key);}
            requireWritable();
            required(source, "요청 출처");
            BalancePatch patch = candidate.normalized();
            if (!activeRevision.equals(patch.baseRevision())) {throw new BalanceException(409, "기준 버전이 변경되었습니다.");}
            BalanceDeployment deployment = new BalanceDeployment(key, DeploymentState.SCHEDULED, activeRevision, null,
                    patch.applyMode(), patch.reason(), actor, source, System.currentTimeMillis(), null,
                    patch.applyMode().name(), "PENDING", null, patch.changes());
            Receipt created = new Receipt(key, fingerprint, patch, candidateRevision, hash, scope, view.gameId(), deployment);
            receipts.put(key, created);
            persistenceBusy = true;
            return created;
        });
        if (receipt.deployment().state() != DeploymentState.SCHEDULED || locked(() -> !persistenceBusy)) {return receipt.deployment();}
        Index snapshot = locked(this::index);
        try {
            store.readRevision(receipt.deployment().previousRevision());
            store.saveRevision(candidate.bundle());
            store.saveIndex(snapshot);
            locked(() -> {persistenceBusy = false; return null;});
            return receipt.deployment();
        } catch (IOException exception) {
            locked(() -> {
                writeBlocked = "배포 요청을 영속 저장하지 못해 변경을 차단했습니다.";
                receipts.put(key, withDeployment(receipt, transition(receipt.deployment(), DeploymentState.REQUIRES_REVIEW,
                        null, writeBlocked, "NOT_APPLIED")));
                return null;
            });
            throw new BalanceException(503, "배포 요청을 영속 저장하지 못해 변경을 차단했습니다.");
        }
    }

    private Candidate validateCandidate(BalancePatch patch, String actor, String scope, Snapshot snapshot) {
        required(actor, "작업자");
        required(scope, "권한 범위");
        if (patch == null || patch.applyMode() == null || patch.changes().isEmpty() || patch.changes().size() > 1000) {
            throw new BalanceException(422, "변경 항목은 1개 이상 1000개 이하여야 합니다.");
        }
        required(patch.reason(), "변경 사유");
        if (patch.reason().length() > 2000 || patch.sourceReference() != null && patch.sourceReference().length() > 2000) {
            throw new BalanceException(422, "변경 사유 또는 참조가 너무 깁니다.");
        }
        if (!snapshot.revision().equals(patch.baseRevision())) {throw new BalanceException(409, "기준 버전이 변경되었습니다.");}
        if (patch.changes().stream().anyMatch(change -> change == null || change.fieldId() == null)) {
            throw new BalanceException(422, "필드 ID가 필요합니다.");
        }
        Map<String, BalanceField> fields = new LinkedHashMap<>();
        registry.fields(snapshot.bundle(), null, snapshot.idle()).forEach(field -> fields.put(field.id(), field));
        JsonObject json = snapshot.bundle().toJson();
        EnumSet<ApplyMode> modes = EnumSet.allOf(ApplyMode.class);
        List<BalanceChange> normalized = new ArrayList<>();
        List<ValidatedChange> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (BalanceChange change : patch.changes().stream().sorted(Comparator.comparing(BalanceChange::fieldId)).toList()) {
            BalanceField field = fields.get(change.fieldId());
            if (field == null || !field.editable() || !seen.add(change.fieldId())) {throw new BalanceException(422, "알 수 없거나 읽기 전용 또는 중복된 필드입니다: " + change.fieldId());}
            if (!Double.isFinite(change.expectedValue()) || !Double.isFinite(change.value())) {throw new BalanceException(422, "숫자는 유한해야 합니다.");}
            if (Double.compare(field.value(), change.expectedValue()) != 0) {throw new BalanceException(409, "예상한 현재값이 다릅니다: " + field.id());}
            double value = field.numberType().equals("integer") ? (double) Math.round(change.value()) : change.value();
            if (field.min() != null && value < field.min() || field.max() != null && value > field.max()) {throw new BalanceException(422, "허용 범위를 벗어났습니다: " + field.id());}
            if (value == field.value()) {throw new BalanceException(422, "실제 값이 바뀌지 않는 항목입니다: " + field.id());}
            if (value != change.value()) {warnings.add(field.label() + ": 정수 " + value + "으로 반올림합니다.");}
            modes.retainAll(field.modes());
            BalanceFieldRegistry.set(json, field.id(), value);
            normalized.add(new BalanceChange(field.id(), field.value(), value));
            changes.add(new ValidatedChange(field.id(), field.value(), value, field.label(), field.domain()));
        }
        if (!modes.contains(patch.applyMode())) {throw new BalanceException(422, "일부 항목이 선택한 적용 시점을 지원하지 않습니다.");}
        BalanceBundle candidate;
        try {
            candidate = BalanceBundle.fromJson(json);
            if (!BalanceBundle.canonical(json).equals(BalanceBundle.canonical(candidate.toJson()))) {
                throw new IllegalArgumentException("Configuration would silently normalize values.");
            }
        } catch (RuntimeException exception) {throw new BalanceException(422, "후보 설정 검증에 실패했습니다.");}
        BalancePatch ordered = new BalancePatch(patch.baseRevision(), patch.applyMode(), patch.reason(), patch.sourceReference(), normalized, patch.notifyPlayers());
        String hash = fingerprint("validation", serverId, patch, ordered, actor, scope);
        return new Candidate(candidate, ordered, new BalanceValidation(true, hash, patch.baseRevision(),
                List.copyOf(changes), List.copyOf(warnings), List.copyOf(modes)));
    }

    private void finishAsync(List<Receipt> results, List<BalanceBundle> revisions) {
        // Serialize writes, not reservations. No disk I/O holds the main-thread state lock.
        persistenceBusy = true;
        for (Receipt receipt : results) {
            BalanceDeployment result = receipt.deployment();
            receipts.put(receipt.idempotencyKey(), withDeployment(receipt, transition(result, DeploymentState.APPLYING,
                    result.effectiveRevision(), result.error(), result.catalogSyncStatus())));
        }
        persistence.execute(() -> {
            try {
                for (BalanceBundle revision : revisions) {store.saveRevision(revision);}
                String sync = "SYNCED";
                if (results.stream().anyMatch(receipt -> receipt.deployment().state() == DeploymentState.APPLIED
                        && "PENDING".equals(receipt.deployment().catalogSyncStatus()))) {
                    try {sync = runtime.afterApplied();} catch (RuntimeException exception) {sync = "FAILED";}
                }
                String catalogSync = sync;
                List<Receipt> durable = results.stream().map(receipt -> {
                    BalanceDeployment result = receipt.deployment();
                    if (result.state() != DeploymentState.APPLIED || !"PENDING".equals(result.catalogSyncStatus())) {return receipt;}
                    return withDeployment(receipt, new BalanceDeployment(result.requestId(), result.state(), result.previousRevision(),
                            result.effectiveRevision(), result.applyMode(), result.reason(), result.actor(), result.source(), result.createdAt(),
                            result.appliedAt(), result.scheduledFor(), catalogSync, result.error(), result.changes()));
                }).toList();
                Index snapshot = locked(() -> {
                    Map<String, Receipt> journal = receipts.copy();
                    durable.forEach(receipt -> journal.put(receipt.idempotencyKey(), receipt));
                    return new Index(1, activeRevision, List.copyOf(journal.values()), writeBlocked, legacyRevision);
                });
                store.saveIndex(snapshot);
                publishUpdates(durable.stream().map(Receipt::deployment).toList());
                locked(() -> {
                    for (Receipt receipt : durable) {
                        receipts.put(receipt.idempotencyKey(), receipt);
                        if (receipt.deployment().state() == DeploymentState.APPLIED && Boolean.TRUE.equals(receipt.patch().notifyPlayers())) {
                            announcements.addLast(receipt.deployment());
                        }
                    }
                    persistenceBusy = false;
                    return null;
                });
            } catch (IOException exception) {
                locked(() -> {
                    writeBlocked = "게임 상태와 저장소의 일치 여부를 확인할 수 없어 변경을 차단했습니다.";
                    for (Receipt receipt : results) {
                        BalanceDeployment result = receipt.deployment();
                        receipts.put(receipt.idempotencyKey(), withDeployment(receipt, transition(result,
                                DeploymentState.REQUIRES_REVIEW, result.effectiveRevision(), writeBlocked, result.catalogSyncStatus())));
                    }
                    return null;
                });
            }
        });
    }

    private Index index() {return new Index(1, activeRevision, List.copyOf(receipts.values()), writeBlocked, legacyRevision);}

    void publishUpdates(List<BalanceDeployment> deployments) {
        List<BalanceDeployment> applied = deployments.stream()
                .filter(result -> result.state() == DeploymentState.APPLIED && !result.changes().isEmpty()).toList();
        if (applied.isEmpty()) {return;}
        try {
            BalanceBundle snapshot = locked(() -> active);
            runtime.publishUpdates(applied, registry.fields(snapshot, null));
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(BalanceChangeService.class)
                    .warn("Failed to export public balance updates; startup will retry", failure);
        }
    }
    private BalanceDeployment replay(String key, String fingerprint) {
        required(key, "중복 요청 키");
        if (key.length() > 200) {throw new BalanceException(422, "중복 요청 키가 너무 깁니다.");}
        try {
            if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) {throw new IllegalArgumentException();}
        } catch (IllegalArgumentException exception) {throw new BalanceException(422, "요청 키는 UUID여야 합니다.");}
        Receipt receipt = receipts.get(key);
        if (receipt == null) {return null;}
        if (!receipt.fingerprint().equals(fingerprint)) {throw new BalanceException(409, "같은 요청 키에 다른 내용을 사용할 수 없습니다.");}
        if (persistenceBusy && receipt.deployment().state() == DeploymentState.SCHEDULED) {
            throw new BalanceException(409, "요청을 저장하고 있습니다. 잠시 후 다시 시도하세요.");
        }
        return receipt.deployment();
    }

    private Receipt findRequest(String id) {
        return receipts.values().stream().filter(receipt -> receipt.deployment().requestId().equals(id)).findFirst()
                .orElseThrow(() -> new BalanceException(404, "배포 요청이 없습니다."));
    }

    private static boolean unfinished(DeploymentState state) {
        return state == DeploymentState.SCHEDULED || state == DeploymentState.APPLYING || state == DeploymentState.REQUIRES_REVIEW;
    }

    private static Receipt withDeployment(Receipt receipt, BalanceDeployment deployment) {
        return new Receipt(receipt.idempotencyKey(), receipt.fingerprint(), receipt.patch(), receipt.candidateRevision(),
                receipt.validationHash(), receipt.scope(), receipt.targetGameId(), deployment);
    }

    private BalanceDeployment transition(BalanceDeployment previous, DeploymentState state, String revision, String error, String sync) {
        return new BalanceDeployment(previous.requestId(), state, previous.previousRevision(), revision, previous.applyMode(),
                previous.reason(), previous.actor(), previous.source(), previous.createdAt(),
                state == DeploymentState.APPLIED ? Long.valueOf(lastAppliedAt = Math.max(System.currentTimeMillis(), lastAppliedAt + 1))
                        : previous.appliedAt(), previous.scheduledFor(), sync, error, previous.changes());
    }

    private static String fingerprint(Object... parts) {
        try {
            var json = BalanceBundle.GSON.toJsonTree(parts).getAsJsonArray();
            // Preserve fingerprints of requests created before the optional notification field existed.
            for (var part : json) {
                if (part.isJsonObject() && part.getAsJsonObject().has("notifyPlayers")
                        && part.getAsJsonObject().get("notifyPlayers").isJsonNull()) {
                    part.getAsJsonObject().remove("notifyPlayers");
                }
            }
            return BalanceBundle.digest(BalanceBundle.canonical(json));
        }
        catch (IllegalArgumentException exception) {throw new BalanceException(422, "요청에 유효하지 않은 숫자가 있습니다.");}
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {throw new BalanceException(422, name + "이 필요합니다.");}
        return value;
    }
    private String blockedReason() {return writeBlocked != null ? writeBlocked : runtimeWriteBlocked;}
    private boolean isIdle() {return view.gameId() == null && view.nextMatchSafe();}
    private void requireWritable() {
        if (blockedReason() != null) {throw new BalanceException(503, blockedReason());}
        if (persistenceBusy) {throw new BalanceException(409, "요청을 저장하고 있습니다. 잠시 후 다시 시도하세요.");}
    }
    private <T> T locked(Supplier<T> operation) {
        lock.lock();
        try {return operation.get();} finally {lock.unlock();}
    }
    private record Candidate(BalanceBundle bundle, BalancePatch normalized, BalanceValidation validation) {}
    private record Snapshot(BalanceBundle bundle, String revision, boolean idle) {}
    private record FieldSnapshot(BalanceBundle active, Map<String, Double> pending, boolean idle, String revision, String version) {}
    private record Cancellation(Receipt receipt, Index index) {}
    @Override public void close() {persistence.shutdown();}
}
