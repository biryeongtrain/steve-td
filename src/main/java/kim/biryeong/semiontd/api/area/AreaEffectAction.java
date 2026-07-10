package kim.biryeong.semiontd.api.area;

@FunctionalInterface
public interface AreaEffectAction<T> {
    AreaEffectOutcome apply(T target);
}
