package kim.biryeong.semiontd.balance.manage;

import java.util.List;

public final class BalanceDtos {
    private BalanceDtos() {}

    public record BalanceChange(String fieldId, double expectedValue, double value) {}

    public record BalancePatch(String baseRevision, ApplyMode applyMode, String reason,
                               String sourceReference, List<BalanceChange> changes, Boolean notifyPlayers) {
        public BalancePatch(String baseRevision, ApplyMode applyMode, String reason,
                            String sourceReference, List<BalanceChange> changes) {
            this(baseRevision, applyMode, reason, sourceReference, changes, null);
        }
        public BalancePatch {
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    public record BalanceField(String id, String domain, String entityId, String entityName,
                               String label, String description, String builderId, String builderName,
                               Integer tier, String rarity, String unit, double value, Double defaultValue,
                               Double scheduledValue, Double min, Double max, Double step, boolean editable,
                               List<ApplyMode> modes, String numberType, String rounding,
                               List<String> affectedEntities, String source, String verification) {}

    public record ValidatedChange(String fieldId, double before, double after, String label, String domain) {}

    public record BalanceValidation(boolean valid, String validationHash, String baseRevision,
                                    List<ValidatedChange> changes, List<String> warnings,
                                    List<ApplyMode> supportedModes) {}

    public enum DeploymentState {SCHEDULED, APPLYING, APPLIED, FAILED, CANCELLED, REQUIRES_REVIEW}

    public record BalanceDeployment(String requestId, DeploymentState state, String previousRevision,
                                    String effectiveRevision, ApplyMode applyMode, String reason,
                                    String actor, String source, long createdAt, Long appliedAt,
                                    String scheduledFor, String catalogSyncStatus, String error,
                                    List<BalanceChange> changes) {
        public BalanceDeployment {changes = List.copyOf(changes);}
    }

    public record GameState(String id, int round, String phase) {}

    public record BalanceState(String serverId, boolean online, String revision, String catalogVersion,
                               GameState game, BalanceDeployment pending, long updatedAt, String writeBlocked,
                               String fieldsVersion) {}

    public static final class BalanceException extends RuntimeException {
        private final int status;

        public BalanceException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {return status;}
    }
}
