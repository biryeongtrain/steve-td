package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ResonanceTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "resonance_towers");

    public ResonanceTowerJob() {
        super(
                ID,
                Component.literal("무블룸 빌더"),
                List.of(
                        SemionText.mini("<gray>다른 종의 무블룸을 1칸 안에 모아 공명 단계를 올리는 빌더입니다.</gray>"),
                        SemionText.mini("<gray>자신을 제외한 주변 무블룸 1/3/5기와 연결되면 공명 1/2/3단계가 열립니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return ResonanceTowers.isResonanceTower(towerType);
    }
}
