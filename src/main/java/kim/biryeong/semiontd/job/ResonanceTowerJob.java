package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
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
                List.of()
        );
    }

    @Override
    public List<Component> description() {
        String linkRange = format(
                TowerBalanceRuntime.ability(ResonanceTowers.FOCUS_CRYSTAL.id(), "linkRange"),
                "number"
        );
        return List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>서로 다른 무블룸을 " + linkRange + "칸 안에 붙여 배치하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>연결한 종류가 늘수록 공명 단계가 올라 고유 효과가 강해집니다.</gray>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return ResonanceTowers.isResonanceTower(towerType);
    }
}
