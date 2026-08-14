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
                List.of(SemionText.mini("<gray>심해 수압을 쌓아 터뜨리는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>거북이가 <yellow>몬스터가 오는 쪽</yellow> 경로에 고압 구역을 일렬로 깔고, 돌고래가 적에게 압력을 쌓습니다. 구역 안에서는 더 빠르게 쌓이고 적은 느려집니다.</gray>"),
                SemionText.mini("<aqua>압력이 가득 차거나 적이 구역을 벗어나면 <yellow>수압</yellow>이 터져 쌓인 만큼 큰 피해를 줍니다.</aqua>"),
                SemionText.mini("<gray>우파루파와 전달체는 아군을 보조하고 압력 상한과 수압 배율을 올립니다.</gray>"),
                SemionText.mini("<gray>거북이를 늘리거나 승급하면 구역 벽이 길어지고, 구역이 겹치는 자리에서는 효과가 합쳐집니다.</gray>")
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
