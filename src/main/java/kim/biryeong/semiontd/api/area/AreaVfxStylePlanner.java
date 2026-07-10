package kim.biryeong.semiontd.api.area;

@FunctionalInterface
public interface AreaVfxStylePlanner {
    void plan(AreaVfxContext context, AreaVfxOutput output);
}
