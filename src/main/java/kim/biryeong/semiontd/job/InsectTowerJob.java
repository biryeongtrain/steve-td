package kim.biryeong.semiontd.job;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.insect.InsectBalance;
import kim.biryeong.semiontd.tower.insect.InsectAugments;
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
                SemionText.mini("<green><bold>시작</bold></green> <gray>스포너 " + reviveRadius
                        + "칸 안에 벌레를 배치하세요. 첫 웨이브에는 최대 체력이 "
                        + format(InsectBalance.freshPowerMultiplier(), "number") + "배, 받는 피해가 "
                        + format(InsectBalance.freshDamageTakenMultiplier(), "number") + "배입니다.</gray>"),
                SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>벌레는 사망 시 최대 체력에 비례한 마법 폭발을 일으킵니다. 벌은 적에게 접근해 자폭합니다.</gray>"),
                SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>부활할수록 체력이 줄고 받는 피해와 부활 대기가 늘어납니다. "
                        + "스포너가 깨지면 연결된 부활도 취소됩니다.</gray>")
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

    @Override public void onMatchStarted(JobContext context) {InsectAugments.clear(context.player().uuid());}
    @Override public void onRoundEnded(JobContext context, int round) {InsectAugments.clear(context.player().uuid());}
    @Override public void onEliminated(JobContext context) {InsectAugments.clear(context.player().uuid());}
    @Override public void onMatchClosed(JobContext context) {InsectAugments.clear(context.player().uuid());}
}
