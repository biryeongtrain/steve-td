package kim.biryeong.semiontd.api.area;

import java.util.List;

public record AreaEffectResult<T>(int candidateCount, List<AreaEffectHit<T>> hits, int appliedCount, int killedCount) {
    public AreaEffectResult {
        candidateCount = Math.max(0, candidateCount);
        hits = hits == null ? List.of() : List.copyOf(hits);
        appliedCount = Math.max(0, appliedCount);
        killedCount = Math.max(0, killedCount);
    }

    public static <T> AreaEffectResult<T> empty() {
        return new AreaEffectResult<>(0, List.of(), 0, 0);
    }
}
