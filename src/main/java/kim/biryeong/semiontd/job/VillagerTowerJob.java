package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class VillagerTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_towers");
    private static final Set<String> ALLOWED_TOWER_IDS = Set.of(
            VillagerTowers.T1_SPLASH_TOWER.id(),
            VillagerTowers.T2_LIBRARIAN_TOWER.id(),
            VillagerTowers.T3_CLERIC_TOWER.id(),
            VillagerTowers.T1_GOLEM_TOWER.id(),
            VillagerTowers.T2_GOLEM_TOWER.id(),
            VillagerTowers.T3_GOLEM_TOWER.id(),
            VillagerTowers.T1_ALLAY_TOWER.id(),
            VillagerTowers.T2_ALLAY_TOWER.id(),
            VillagerTowers.T2_WEAPON_SMITH_TOWER.id(),
            VillagerTowers.T3_ARMORER_TOWER.id(),
            VillagerTowers.T3_WEAPON_SMITH_TOWER.id(),
            VillagerTowers.T1_CAT_TOWER.id(),
            VillagerTowers.T2_ANTI_TANKER_CAT_TOWER.id(),
            VillagerTowers.T2_LANE_CLEAR_CAT_TOWER.id(),
            VillagerTowers.T3_ANTI_TANKER_CAT_TOWER.id(),
            VillagerTowers.T3_LANE_CLEAR_CAT_TOWER.id()
    );

    public VillagerTowerJob() {
        super(
                ID,
                Component.literal("주민 빌더"),
                List.of(
                        SemionText.mini("<gray>초반부터 최대한의 타워를 생존시켜 강해지는 형태의 빌더입니다.</gray>"),
                        SemionText.mini("<gray>각 타워의 티어를 올려야 생존 및 처치 보너스를 받기 때문에 업그레이드를 우선하는 것을 권장합니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return towerType != null && ALLOWED_TOWER_IDS.contains(towerType.id());
    }
}
