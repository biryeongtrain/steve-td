package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class UndeadTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "undead_towers");
    private static final Set<String> ALLOWED_TOWER_IDS = Set.of(
            UndeadTowers.T1_ZOMBIE_TOWER.id(),
            UndeadTowers.T2_ZOMBIE_TOWER.id(),
            UndeadTowers.T3_ZOMBIE_TOWER.id(),
            UndeadTowers.T1_SKELETON_TOWER.id(),
            UndeadTowers.T2_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T2_MELEE_TOWER.id(),
            UndeadTowers.T3_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T3_MELEE_TOWER.id(),
            UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id(),
            UndeadTowers.T2_UNDEAD_ANIMAL_TOWER.id()
    );

    public UndeadTowerJob() {
        super(
                ID,
                Component.literal("언데드 빌더"),
                List.of(
                        SemionText.mini("<gray>다른 타워에 비해 성능은 낮지만 피흡이 달려있는 타워입니다.</gray>"),
                        SemionText.mini("<yellow>테스트중</yellow>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return towerType != null && ALLOWED_TOWER_IDS.contains(towerType.id());
    }
}
