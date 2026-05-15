package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerFaction;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

abstract class FactionTowerJob extends SemionJob {
    private final TowerFaction faction;

    protected FactionTowerJob(String path, String displayName, TowerFaction faction, List<Component> description) {
        super(ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path), Component.literal(displayName), description);
        this.faction = faction;
    }

    public final TowerFaction faction() {
        return faction;
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return ProductionTowerCatalog.behavior(towerType)
                .map(behavior -> behavior.faction() == faction)
                .orElse(true);
    }
}
