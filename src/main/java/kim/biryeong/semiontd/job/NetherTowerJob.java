package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NetherTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "nether");

    public NetherTowerJob() {
        super(
                ID,
                Component.literal("네더 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>전투 중 체력을 잃으므로 흡혈 계열을 함께 준비하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>체력이 낮을수록 강해지는 효과를 이용해 위험한 상태를 유지하세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>처음 쓰러지면 좀비로 부활하지만 다시 쓰러지면 돌아오지 않습니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return NetherTowers.isNetherTower(towerType);
    }
}
