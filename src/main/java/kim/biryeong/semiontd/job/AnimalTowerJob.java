package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AnimalTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "animal_towers");
    private static final Set<String> ALLOWED_TOWER_IDS = Set.of(
            AnimalTowers.T1_PIG_TOWER.id(),
            AnimalTowers.T2_PIG_TOWER.id(),
            AnimalTowers.T3_PIG_TOWER.id(),
            AnimalTowers.T4_PIG_LEADER_TOWER.id(),
            AnimalTowers.T1_WOLF_TOWER.id(),
            AnimalTowers.T2_WOLF_DPS_TOWER.id(),
            AnimalTowers.T3_WOLF_DPS_TOWER.id(),
            AnimalTowers.T4_WOLF_LEADER_TOWER.id(),
            AnimalTowers.T1_RABBIT_TOWER.id(),
            AnimalTowers.T2_RABBIT_TOWER.id(),
            AnimalTowers.T3_RABBIT_TOWER.id(),
            AnimalTowers.T4_RABBIT_LEADER_TOWER.id(),
            AnimalTowers.T1_FOX_TOWER.id(),
            AnimalTowers.T2_FOX_TOWER.id(),
            AnimalTowers.T3_FOX_TOWER.id(),
            AnimalTowers.T4_FOX_LEADER_TOWER.id()
    );

    public AnimalTowerJob() {
        super(
                ID,
                Component.literal("동물 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>한 동물 계열을 골라 같은 타워를 모으세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>무리를 완성하면 계열마다 1기를 우두머리로 승급하세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>무리가 풀리면 우두머리의 주변 강화도 약해집니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return towerType != null && ALLOWED_TOWER_IDS.contains(towerType.id());
    }
}
