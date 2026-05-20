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
            UndeadTowers.T3_MELEE_TOWER.id()
    );

    public UndeadTowerJob() {
        super(
                ID,
                Component.literal("언데드 지휘관"),
                List.of(
                        SemionText.mini("<gray>흡혈, 피격 반격, 처치 성장으로 장기 교전에 강한 언데드 타워 직업입니다.</gray>"),
                        SemionText.mini("<gray>좀비 트리는 탱킹과 반격, 스켈레톤 트리는 원거리 분산 화력 또는 근접 성장 화력으로 분기합니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return towerType != null && ALLOWED_TOWER_IDS.contains(towerType.id());
    }
}
