package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.atlantis.AtlantisPressure;
import kim.biryeong.semiontd.tower.atlantis.AtlantisStates;
import kim.biryeong.semiontd.tower.atlantis.AtlantisTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AtlantisTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "atlantis");

    public AtlantisTowerJob() {
        super(
                ID,
                Component.literal("아틀란티스 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>거북이를 몬스터가 오는 쪽에 놓아 고압 구역을 만드세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>구역 안의 적에게 돌고래로 압력을 쌓아 수압 폭발을 일으키세요.</gray>"),
                        SemionText.mini("<gold><bold>연계</bold></gold> <gray>우파루파와 전달체는 압력 상한과 폭발 피해를 높입니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return AtlantisTowers.isAtlantisTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return AtlantisTowers.isAtlantisTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        clearState(context);
    }

    @Override
    public void onEliminated(JobContext context) {
        clearState(context);
    }

    private static void clearState(JobContext context) {
        AtlantisStates.clear(context.player().uuid());
        AtlantisPressure.clearPlayer(context.player().uuid());
    }
}
