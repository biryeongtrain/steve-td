package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.function.Consumer;

/** Test-source bridge for the package-private VFX observer. */
public final class AreaEffectVfxTestHooks {
    private AreaEffectVfxTestHooks() {
    }

    public static void setMagicSchoolObserver(Consumer<MagicSchoolSpellVfx.Visual> observer) {
        TowerVfxService.setMagicSchoolTestObserver(observer);
    }

    public static void setObserver(Consumer<AreaEffectVfxEvent> observer) {
        TowerVfxService.setAreaEffectTestObserver(observer);
    }
}
