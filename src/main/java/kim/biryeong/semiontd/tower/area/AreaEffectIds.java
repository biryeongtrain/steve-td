package kim.biryeong.semiontd.tower.area;

import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.resources.ResourceLocation;

public final class AreaEffectIds {
    private AreaEffectIds() {
    }

    public static ResourceLocation tower(Tower tower, String effect) {
        String towerId = tower == null || tower.type() == null ? "unknown" : tower.type().id();
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "tower/" + towerId + "/" + effect);
    }
}
