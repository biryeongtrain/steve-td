package kim.biryeong.semiontd.game;

public record BalancePatchEvent(
        String requestId,
        String previousRevision,
        String effectiveRevision,
        int round,
        long serverTick,
        String applyMode
) {
}
