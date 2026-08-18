package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.insect.InsectBalance;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InsectTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "insect_towers");

    public InsectTowerJob() {
        super(ID, Component.literal("벌레 빌더"), List.of());
    }

    @Override
    public List<Component> description() {
        String reviveRadius = format(InsectBalance.spawnerRadius(), "number");
        return List.of(
                SemionText.mini("<green><bold>시작</bold></green> <gray>스포너를 먼저 놓고 " + reviveRadius + "칸 안에 벌레를 배치하세요.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>새 벌레의 첫 웨이브 강화와 부활로 전선을 버티세요.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>반복 사망으로 부활이 느려지고, 스포너가 깨지면 대기 중인 부활도 취소됩니다.</gray>")
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
