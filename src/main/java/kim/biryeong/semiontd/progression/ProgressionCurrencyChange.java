package kim.biryeong.semiontd.progression;

public record ProgressionCurrencyChange(Status status, int targetCount, long totalAmount) {
    public enum Operation {
        GIVE,
        TAKE
    }

    public enum Status {
        SUCCESS,
        INVALID_AMOUNT,
        INVALID_TARGET,
        NO_TARGETS,
        OVERFLOW,
        INSUFFICIENT_FUNDS,
        PERSISTENCE_FAILED
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }
}
