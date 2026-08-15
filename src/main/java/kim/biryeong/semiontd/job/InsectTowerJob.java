package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InsectTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "insect_towers");

    public InsectTowerJob() {
        super(ID, Component.literal("벌레 빌더"), List.of(
                SemionText.mini("<gray><light_purple>스포너</light_purple> 곁에서 계속 <green>부활</green>하며 시간을 끄는 물량 빌더입니다.</gray>")));
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("새로 배치한 기본 벌레는 <gold>처음 참여하는 웨이브</gold>에 체력과 피해가 크게 증가합니다."),
                SemionText.mini("살아 있는 자기 <light_purple>스포너</light_purple> 반경 안에서 죽으면 잠시 후 <green>부활</green>합니다."),
                SemionText.mini("같은 라운드에 다시 죽을수록 부활이 늦어지고 <red>받는 피해</red>가 증가합니다."),
                SemionText.mini("스포너가 파괴되거나 범위 밖에서 죽으면 그 라운드에는 더는 부활하지 못합니다.")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return InsectTowers.isInsectTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return InsectTowers.isInsectTower(towerType);
    }
}
