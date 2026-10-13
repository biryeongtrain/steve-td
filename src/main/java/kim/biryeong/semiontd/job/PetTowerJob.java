package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.pet.PetTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class PetTowerJob extends SemionJob {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "pet_towers");

    public PetTowerJob() {
        super(
                ID,
                Component.literal("반려동물 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>주인을 세우고 그 주변 1칸에 반려를 배치하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>라운드가 지날수록 유대가 쌓여 반려가 성장하고 승급 자격을 얻습니다.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>개는 붙여서, 고양이는 마당마다 한 마리만 두세요.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return PetTowers.isPetTower(towerType);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return PetTowers.isPetTower(towerType);
    }
}
