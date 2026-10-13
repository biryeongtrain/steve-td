package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.UUID;
import java.util.function.BiConsumer;

public final class WarlockAwakeningVfxTestHooks {
    private WarlockAwakeningVfxTestHooks() {
    }

    public static void setObserver(BiConsumer<UUID, String> observer) {
        TowerVfxService.setWarlockAwakeningTestObserver(observer);
    }
}
